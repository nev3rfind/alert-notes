package com.alertnotes.data.repository

import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.backup.BackupReminder
import com.alertnotes.data.backup.toBackup
import com.alertnotes.data.backup.toEntity
import com.alertnotes.data.entities.toDomain
import com.alertnotes.data.entities.toEntity
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderShare
import com.alertnotes.domain.model.ReminderShareWithProfile
import com.alertnotes.domain.model.RelationshipType
import com.alertnotes.domain.model.ShareStatus
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.IOException
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
import kotlinx.serialization.json.Json

/**
 * Firestore-backed reminder sharing, V1. Deterministic
 * `{owner}_{reminderId}_{recipient}` ids make duplicate shares impossible;
 * the reminder body travels as the same versioned backup-DTO JSON the ZIP
 * export uses, so both formats evolve together. Delivery reconstructs the
 * reminder locally and schedules it through the normal coordinator — after
 * that it is an ordinary local reminder and works fully offline. Idempotency
 * lives in `recipientReminderId`: a share that already carries one is never
 * delivered again, so listener replays, process restarts, and re-opens can't
 * double-schedule.
 */
@Singleton
class ReminderSharingRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    private val firestore: FirebaseFirestore,
    private val chatRepository: com.alertnotes.domain.repository.ChatRepository,
    private val logger: AppLogger,
) : ReminderSharingRepository {

    /** Chat context is best-effort — sharing never fails over a message. */
    private suspend fun narrate(
        otherUid: String,
        kind: com.alertnotes.domain.model.SystemMessageKind,
    ) {
        runCatching { chatRepository.postSystemMessage(otherUid, kind) }
            .onFailure { logger.d(TAG, "Chat narration skipped: ${it.message}") }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
        reminder: Reminder,
        recipientUids: List<String>,
    ) = runShareOp {
        val owner = auth.currentUser?.uid ?: throw FriendException(FriendError.UNKNOWN)
        val payload = json.encodeToString(
            BackupReminder.serializer(),
            reminder.toEntity().toBackup(),
        )
        val scheduleSummary = reminder.nextTriggerAt
            ?.toDisplayDateTime(reminder.timeZone)
            .orEmpty()
        val familyMembers = friendRepository.family.first()
        recipientUids.forEach { recipientUid ->
            val familyMember = familyMembers.firstOrNull { it.uid == recipientUid }
            val autoDeliver = familyMember?.permissions?.autoReceiveReminders == true
            val relationship = if (familyMember != null) {
                RelationshipType.FAMILY
            } else {
                RelationshipType.FRIEND
            }
            // Family with auto-delivery is released immediately; everyone
            // else waits as a PENDING invitation.
            val status = if (autoDeliver) ShareStatus.DELIVERED else ShareStatus.PENDING
            shareDocument(owner, reminder.id, recipientUid).set(
                mapOf(
                    "reminderId" to reminder.id,
                    "ownerUid" to owner,
                    "recipientUid" to recipientUid,
                    "relationship" to relationship.name,
                    "approvalRequired" to !autoDeliver,
                    "status" to status.name,
                    "title" to reminder.title,
                    "scheduleSummary" to scheduleSummary,
                    "payload" to payload,
                    "recipientReminderId" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "respondedAt" to null,
                    "scheduledAt" to null,
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        recipientUids.forEach {
            narrate(it, com.alertnotes.domain.model.SystemMessageKind.REMINDER_SHARED)
        }
        logger.i(TAG, "Reminder shared with ${recipientUids.size} recipient(s)")
    }

    override suspend fun acceptShare(share: ReminderShare) = runShareOp {
        if (share.status != ShareStatus.PENDING) {
            // Already answered on another device — nothing to do.
            throw FriendException(FriendError.ALREADY_PENDING)
        }
        // ACCEPTED lands first so the sender's dashboard shows the approval
        // even if the download below is slow or interrupted.
        shareReference(share.id).set(
            mapOf(
                "status" to ShareStatus.ACCEPTED.name,
                "respondedAt" to FieldValue.serverTimestamp(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        deliverLocally(share)
        narrate(share.ownerUid, com.alertnotes.domain.model.SystemMessageKind.REMINDER_ACCEPTED)
    }

    override suspend fun declineShare(shareId: String) = runShareOp {
        shareReference(shareId).set(
            mapOf(
                "status" to ShareStatus.REJECTED.name,
                "respondedAt" to FieldValue.serverTimestamp(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        // The id encodes the owner: {owner}_{reminderId}_{recipient}.
        shareId.substringBefore('_').takeIf { it.isNotBlank() }?.let {
            narrate(it, com.alertnotes.domain.model.SystemMessageKind.REMINDER_REJECTED)
        }
        Unit
    }

    override suspend fun cancelShare(shareId: String) = runShareOp {
        shareReference(shareId).set(
            mapOf(
                "status" to ShareStatus.CANCELLED.name,
                "respondedAt" to FieldValue.serverTimestamp(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        Unit
    }

    override suspend fun deliverReleasedShares() {
        val me = auth.currentUser?.uid ?: return
        val released = runCatching {
            firestore.collection(FirestoreSchema.REMINDER_SHARES)
                .whereEqualTo("recipientUid", me)
                .whereEqualTo("status", ShareStatus.DELIVERED.name)
                .get()
                .await()
                .documents
                .map { it.toShare() }
        }.getOrDefault(emptyList())
        released.forEach { share ->
            runCatching { deliverLocally(share) }
                .onFailure { logger.w(TAG, "Auto-delivery failed for ${share.id}", it) }
        }
    }

    /**
     * The actual download: payload → entity → domain → coordinator. The
     * recipientReminderId guard makes this idempotent; the coordinator's
     * mutex makes the scheduling side safe.
     */
    private suspend fun deliverLocally(share: ReminderShare) {
        if (share.recipientReminderId != null) return
        if (share.payload.isBlank()) {
            logger.w(TAG, "Share ${share.id} has no payload — cannot deliver")
            return
        }
        val backup = json.decodeFromString(BackupReminder.serializer(), share.payload)
        // toEntity() resets the id, so this always inserts a NEW local
        // reminder; the sender's copy is untouched.
        val reminder = backup.toEntity().toDomain()
        val localId = coordinator.saveAndSchedule(reminder)
        shareReference(share.id).set(
            mapOf(
                "status" to ShareStatus.SCHEDULED.name,
                "recipientReminderId" to localId,
                "scheduledAt" to FieldValue.serverTimestamp(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        logger.i(TAG, "Shared reminder delivered and scheduled locally")
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
            .firstOrNull { it.name == getString("status") } ?: ShareStatus.CANCELLED,
        title = getString("title").orEmpty(),
        scheduleSummary = getString("scheduleSummary").orEmpty(),
        payload = getString("payload").orEmpty(),
        recipientReminderId = getLong("recipientReminderId"),
        createdAt = instantField("createdAt"),
        respondedAt = instantField("respondedAt"),
        scheduledAt = instantField("scheduledAt"),
        lastSyncAt = instantField("lastSyncAt"),
    )

    private fun shareReference(shareId: String): DocumentReference =
        firestore.collection(FirestoreSchema.REMINDER_SHARES).document(shareId)

    private fun shareDocument(
        ownerUid: String,
        reminderId: Long,
        recipientUid: String,
    ): DocumentReference = firestore.collection(FirestoreSchema.REMINDER_SHARES)
        .document("${ownerUid}_${reminderId}_$recipientUid")

    private suspend fun <T> runShareOp(block: suspend () -> T): T = try {
        block()
    } catch (exception: FriendException) {
        throw exception
    } catch (exception: Exception) {
        logger.e(TAG, "Share op failed: ${exception::class.simpleName}: ${exception.message}", exception)
        throw FriendException(
            when {
                exception is IOException -> FriendError.NETWORK
                exception is com.google.firebase.firestore.FirebaseFirestoreException &&
                    exception.code ==
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                    FriendError.NETWORK

                exception is com.google.firebase.firestore.FirebaseFirestoreException &&
                    exception.code ==
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    FriendError.PERMISSION

                else -> FriendError.UNKNOWN
            },
            exception,
        )
    }

    private companion object {
        const val TAG = "ReminderSharing"
    }
}
