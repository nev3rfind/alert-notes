package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.ReminderShare
import com.alertnotes.domain.model.ReminderShareWithProfile
import com.alertnotes.domain.model.RelationshipType
import com.alertnotes.domain.model.ShareStatus
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed sharing edges. Foundation implementation: it can create,
 * observe, and resolve share records, and it derives approval-vs-auto from
 * the family graph — but it performs no delivery or scheduling. The reminder
 * body stays in the owner's local Room database; only a title snapshot and
 * the relationship live here. Mirrors the friend/family repositories'
 * listener + deterministic-id patterns so the later delivery session can
 * build on it without reshaping data.
 */
@Singleton
class ReminderSharingRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val firestore: FirebaseFirestore,
    private val logger: AppLogger,
) : ReminderSharingRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val outgoingShares: Flow<List<ReminderShareWithProfile>> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else shares("ownerUid", user.uid) { it.recipientUid }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val incomingShares: Flow<List<ReminderShareWithProfile>> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else shares("recipientUid", user.uid) { it.ownerUid }
        }

    override suspend fun shareReminder(
        reminderId: Long,
        reminderTitle: String,
        recipientUid: String,
    ) {
        val owner = auth.currentUser?.uid ?: return
        // Family members with auto-delivery skip approval; everyone else is
        // approval-required. Reading the family edge keeps that decision in
        // one place for the future delivery layer and the security rules.
        val familyMember = friendRepository.family.first().firstOrNull { it.uid == recipientUid }
        val autoDeliver = familyMember?.permissions?.autoReceiveReminders == true
        val relationship = if (familyMember != null) {
            RelationshipType.FAMILY
        } else {
            RelationshipType.FRIEND
        }
        val status = if (autoDeliver) ShareStatus.AUTO_ACCEPTED else ShareStatus.PENDING
        shareDocument(owner, reminderId, recipientUid).set(
            mapOf(
                "reminderId" to reminderId,
                "ownerUid" to owner,
                "recipientUid" to recipientUid,
                "relationship" to relationship.name,
                "approvalRequired" to !autoDeliver,
                "status" to status.name,
                "title" to reminderTitle,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        logger.i(TAG, "Reminder shared (foundation record only, no delivery yet)")
    }

    override suspend fun acceptShare(shareId: String) = setStatus(shareId, ShareStatus.ACCEPTED)

    override suspend fun declineShare(shareId: String) = setStatus(shareId, ShareStatus.DECLINED)

    override suspend fun revokeShare(shareId: String) = setStatus(shareId, ShareStatus.REVOKED)

    private suspend fun setStatus(shareId: String, status: ShareStatus) {
        firestore.collection(FirestoreSchema.REMINDER_SHARES)
            .document(shareId)
            .set(mapOf("status" to status.name), SetOptions.merge())
            .await()
    }

    private fun shares(
        field: String,
        uid: String,
        otherUid: (ReminderShare) -> String,
    ): Flow<List<ReminderShareWithProfile>> = callbackFlow {
        val registration = firestore.collection(FirestoreSchema.REMINDER_SHARES)
            .whereEqualTo(field, uid)
            .addSnapshotListener { snapshot, error ->
                trySend(if (error != null) emptyList() else snapshot?.documents.orEmpty())
            }
        awaitClose { registration.remove() }
    }.map { documents ->
        documents.map { it.toShare() }
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
            .mapNotNull { share ->
                publicProfileOf(otherUid(share))?.let { ReminderShareWithProfile(share, it) }
            }
    }

    private suspend fun publicProfileOf(uid: String): PublicProfile? = runCatching {
        firestore.collection(FirestoreSchema.USERS).document(uid)
            .collection(FirestoreSchema.SECTION_PUBLIC).document(FirestoreSchema.SECTION_DOC)
            .get().await().takeIf { it.exists() }?.toPublicProfile()
    }.getOrNull()

    private fun DocumentSnapshot.toShare(): ReminderShare = ReminderShare(
        id = id,
        reminderId = getLong("reminderId") ?: 0L,
        ownerUid = getString("ownerUid").orEmpty(),
        recipientUid = getString("recipientUid").orEmpty(),
        relationship = RelationshipType.entries
            .firstOrNull { it.name == getString("relationship") } ?: RelationshipType.FRIEND,
        approvalRequired = getBoolean("approvalRequired") != false,
        status = ShareStatus.entries
            .firstOrNull { it.name == getString("status") } ?: ShareStatus.REVOKED,
        title = getString("title").orEmpty(),
        createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
    )

    private fun shareDocument(
        ownerUid: String,
        reminderId: Long,
        recipientUid: String,
    ): DocumentReference = firestore.collection(FirestoreSchema.REMINDER_SHARES)
        .document("${ownerUid}_${reminderId}_$recipientUid")

    private companion object {
        const val TAG = "ReminderSharing"
    }
}
