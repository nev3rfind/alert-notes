package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.FriendRequest
import com.alertnotes.domain.model.FriendRequestStatus
import com.alertnotes.domain.model.FriendRequestWithProfile
import com.alertnotes.domain.model.FriendUser
import com.alertnotes.domain.model.FriendshipState
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed friend graph. Requests live in a top-level collection
 * under deterministic `{from}_{to}` ids (duplicates structurally
 * impossible); accepted friendships are thin per-user edge documents. All
 * queries are equality-only or document-id ranges, so no composite indexes
 * are required. Every transition below is also the contract a future Cloud
 * Function enforces server-side — the client performs the same checks the
 * security rules do, never more.
 */
@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val logger: AppLogger,
) : FriendRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val friends: Flow<List<FriendUser>> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else friendEdges(user.uid)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val incomingRequests: Flow<List<FriendRequestWithProfile>> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                pendingRequests(field = "toUid", uid = user.uid, profileOf = { it.fromUid })
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val outgoingRequests: Flow<List<FriendRequestWithProfile>> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                pendingRequests(field = "fromUid", uid = user.uid, profileOf = { it.toUid })
            }
        }

    override suspend fun search(query: String): List<FriendUser> = runFriendOp {
        val term = query.trim()
        if (term.length < MIN_QUERY_LENGTH) return@runFriendOp emptyList()
        val selfUid = auth.currentUser?.uid
        val byUid = LinkedHashMap<String, PublicProfile>()
        // Primary: username prefix over the reservation ids — free of any
        // index requirements and always case-insensitive.
        val reservations = firestore.collection(FirestoreSchema.USERNAMES)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), term.lowercase())
            .whereLessThan(FieldPath.documentId(), term.lowercase() + QUERY_END)
            .limit(SEARCH_LIMIT)
            .get()
            .await()
        for (doc in reservations.documents) {
            val uid = doc.getString("uid") ?: continue
            if (uid == selfUid || byUid.containsKey(uid)) continue
            publicProfileOf(uid)?.let { byUid[uid] = it }
        }
        // Secondary: display-name prefix via a collection-group query. Needs
        // a collection-group index on public.displayName; until that exists
        // in the console the search degrades gracefully to username-only.
        runCatching {
            firestore.collectionGroup(FirestoreSchema.SECTION_PUBLIC)
                .orderBy("displayName")
                .startAt(term)
                .endAt(term + QUERY_END)
                .limit(SEARCH_LIMIT)
                .get()
                .await()
        }.onFailure {
            logger.d(TAG, "Display-name search unavailable: ${it.message}")
        }.getOrNull()?.documents?.forEach { doc ->
            val uid = doc.ownerUid() ?: return@forEach
            if (uid != selfUid && !byUid.containsKey(uid)) {
                byUid[uid] = doc.toPublicProfile()
            }
        }
        byUid.map { (uid, profile) -> FriendUser(uid = uid, profile = profile) }
    }

    override fun observePublicProfile(uid: String): Flow<PublicProfile?> = callbackFlow {
        val registration = publicDocument(uid).addSnapshotListener { snapshot, error ->
            trySend(if (error != null) null else snapshot?.takeIf { it.exists() }?.toPublicProfile())
        }
        awaitClose { registration.remove() }
    }

    override fun observeFriendshipState(uid: String): Flow<FriendshipState> {
        if (uid == auth.currentUser?.uid) return flowOf(FriendshipState.SELF)
        return combine(friends, incomingRequests, outgoingRequests) { friendList, incoming, outgoing ->
            when {
                friendList.any { it.uid == uid } -> FriendshipState.FRIENDS
                outgoing.any { it.request.toUid == uid } -> FriendshipState.REQUEST_SENT
                incoming.any { it.request.fromUid == uid } -> FriendshipState.REQUEST_RECEIVED
                else -> FriendshipState.NONE
            }
        }
    }

    override suspend fun sendRequest(toUid: String) = runFriendOp {
        val me = requireUid()
        if (toUid == me) throw FriendException(FriendError.SELF_REQUEST)
        if (friendEdge(me, toUid).get().await().exists()) {
            throw FriendException(FriendError.ALREADY_FRIENDS)
        }
        // Both directions: a request from them counts as pending too —
        // accepting it is the right move, not doubling up.
        val mine = requestDocument(me, toUid).get().await()
        val theirs = requestDocument(toUid, me).get().await()
        if (mine.isPending() || theirs.isPending()) {
            throw FriendException(FriendError.ALREADY_PENDING)
        }
        requestDocument(me, toUid).set(
            mapOf(
                "fromUid" to me,
                "toUid" to toUid,
                "status" to FriendRequestStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "respondedAt" to null,
            ),
        ).await()
        Unit
    }

    override suspend fun acceptRequest(requestId: String) = runFriendOp {
        val me = requireUid()
        val snapshot = firestore.collection(FirestoreSchema.FRIEND_REQUESTS)
            .document(requestId).get().await()
        val fromUid = snapshot.getString("fromUid")
        if (!snapshot.isPending() || snapshot.getString("toUid") != me || fromUid == null) {
            throw FriendException(FriendError.UNKNOWN)
        }
        // One atomic batch: the request closes and BOTH edge documents plus
        // both friend counters appear together — a forged half-friendship
        // can never exist. (The same invariant the Cloud Function keeps.)
        firestore.runBatch { batch ->
            batch.update(
                snapshot.reference,
                mapOf(
                    "status" to FriendRequestStatus.ACCEPTED.name,
                    "respondedAt" to FieldValue.serverTimestamp(),
                ),
            )
            val since = FieldValue.serverTimestamp()
            batch.set(friendEdge(me, fromUid), mapOf("uid" to fromUid, "since" to since))
            batch.set(friendEdge(fromUid, me), mapOf("uid" to me, "since" to since))
            batch.set(statisticsDocument(me), FRIEND_COUNT_UP, SetOptions.merge())
            batch.set(statisticsDocument(fromUid), FRIEND_COUNT_UP, SetOptions.merge())
        }.await()
        Unit
    }

    override suspend fun rejectRequest(requestId: String) =
        closeRequest(requestId, FriendRequestStatus.REJECTED, mustBeField = "toUid")

    override suspend fun cancelRequest(requestId: String) =
        closeRequest(requestId, FriendRequestStatus.CANCELLED, mustBeField = "fromUid")

    override suspend fun removeFriend(friendUid: String) = runFriendOp {
        val me = requireUid()
        firestore.runBatch { batch ->
            batch.delete(friendEdge(me, friendUid))
            batch.delete(friendEdge(friendUid, me))
            batch.set(statisticsDocument(me), FRIEND_COUNT_DOWN, SetOptions.merge())
            batch.set(statisticsDocument(friendUid), FRIEND_COUNT_DOWN, SetOptions.merge())
        }.await()
        Unit
    }

    // region internals

    private fun friendEdges(uid: String): Flow<List<FriendUser>> = callbackFlow {
        val registration = firestore.collection(FirestoreSchema.USERS).document(uid)
            .collection(FirestoreSchema.FRIENDS)
            .addSnapshotListener { snapshot, error ->
                trySend(
                    if (error != null) {
                        emptyList()
                    } else {
                        snapshot?.documents?.map { it.id to it.instantField("since") }.orEmpty()
                    },
                )
            }
        awaitClose { registration.remove() }
    }.map { edges ->
        edges.sortedByDescending { it.second ?: Instant.EPOCH }
            .mapNotNull { (friendUid, since) ->
                publicProfileOf(friendUid)?.let { FriendUser(friendUid, it, since) }
            }
    }

    private fun pendingRequests(
        field: String,
        uid: String,
        profileOf: (FriendRequest) -> String,
    ): Flow<List<FriendRequestWithProfile>> = callbackFlow {
        // Equality-only filters: served by automatic indexes; ordering
        // happens client-side to avoid a composite index requirement.
        val registration = firestore.collection(FirestoreSchema.FRIEND_REQUESTS)
            .whereEqualTo(field, uid)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .addSnapshotListener { snapshot: com.google.firebase.firestore.QuerySnapshot?, error ->
                trySend(if (error != null) emptyList() else snapshot?.documents.orEmpty())
            }
        awaitClose { registration.remove() }
    }.map { documents ->
        documents.map { it.toFriendRequest() }
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
            .mapNotNull { request ->
                publicProfileOf(profileOf(request))
                    ?.let { FriendRequestWithProfile(request, it) }
            }
    }

    private suspend fun closeRequest(
        requestId: String,
        newStatus: FriendRequestStatus,
        mustBeField: String,
    ) = runFriendOp {
        val me = requireUid()
        val reference = firestore.collection(FirestoreSchema.FRIEND_REQUESTS).document(requestId)
        val snapshot = reference.get().await()
        if (!snapshot.isPending() || snapshot.getString(mustBeField) != me) {
            throw FriendException(FriendError.UNKNOWN)
        }
        reference.update(
            mapOf(
                "status" to newStatus.name,
                "respondedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    /** One-shot profile fetch; deleted accounts simply drop out of lists. */
    private suspend fun publicProfileOf(uid: String): PublicProfile? = runCatching {
        publicDocument(uid).get().await().takeIf { it.exists() }?.toPublicProfile()
    }.getOrNull()

    private fun DocumentSnapshot.toFriendRequest(): FriendRequest {
        val createdAt = instantField("createdAt")
        val stored = FriendRequestStatus.entries
            .firstOrNull { it.name == getString("status") }
            ?: FriendRequestStatus.EXPIRED
        // Requests nobody answered eventually stop cluttering both lists.
        val status = if (
            stored == FriendRequestStatus.PENDING &&
            createdAt != null &&
            createdAt.isBefore(Instant.now().minus(REQUEST_TTL))
        ) {
            FriendRequestStatus.EXPIRED
        } else {
            stored
        }
        return FriendRequest(
            id = id,
            fromUid = getString("fromUid").orEmpty(),
            toUid = getString("toUid").orEmpty(),
            status = status,
            createdAt = createdAt,
        )
    }

    private fun DocumentSnapshot.isPending(): Boolean =
        exists() && getString("status") == FriendRequestStatus.PENDING.name

    private fun requestDocument(fromUid: String, toUid: String): DocumentReference =
        firestore.collection(FirestoreSchema.FRIEND_REQUESTS).document("${fromUid}_$toUid")

    private fun friendEdge(ownerUid: String, friendUid: String): DocumentReference =
        firestore.collection(FirestoreSchema.USERS).document(ownerUid)
            .collection(FirestoreSchema.FRIENDS).document(friendUid)

    private fun publicDocument(uid: String): DocumentReference =
        firestore.collection(FirestoreSchema.USERS).document(uid)
            .collection(FirestoreSchema.SECTION_PUBLIC).document(FirestoreSchema.SECTION_DOC)

    private fun statisticsDocument(uid: String): DocumentReference =
        firestore.collection(FirestoreSchema.USERS).document(uid)
            .collection(FirestoreSchema.SECTION_STATISTICS).document(FirestoreSchema.SECTION_DOC)

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw FriendException(FriendError.UNKNOWN)

    private inline fun <T> runFriendOp(block: () -> T): T = try {
        block()
    } catch (exception: FriendException) {
        throw exception
    } catch (exception: Exception) {
        throw FriendException(
            when {
                exception is IOException -> FriendError.NETWORK
                exception is FirebaseFirestoreException &&
                    exception.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                    FriendError.NETWORK

                else -> FriendError.UNKNOWN
            },
            exception,
        )
    }

    private companion object {
        const val TAG = "FriendRepository"
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_LIMIT = 8L
        const val QUERY_END = ""
        val REQUEST_TTL: Duration = Duration.ofDays(30)
        val FRIEND_COUNT_UP = mapOf("friendCount" to FieldValue.increment(1))
        val FRIEND_COUNT_DOWN = mapOf("friendCount" to FieldValue.increment(-1))
    }
}
