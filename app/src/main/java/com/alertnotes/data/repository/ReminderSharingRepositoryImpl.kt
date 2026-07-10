package com.alertnotes.data.repository

import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
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
import com.alertnotes.domain.model.ReminderOwnership
import com.alertnotes.domain.model.ReminderShare
import com.alertnotes.domain.model.ReminderShareWithProfile
import com.alertnotes.domain.model.RelationshipType
import com.alertnotes.domain.model.ShareStatus
import com.alertnotes.domain.model.SystemMessageKind
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.ChatRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.alertnotes.domain.scheduling.NextTriggerCalculator
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
 * Firestore-backed reminder sharing. Deterministic
 * `{owner}_{reminderId}_{recipient}` ids make duplicate shares impossible;
 * the reminder body travels as the same versioned backup-DTO JSON the ZIP
 * export uses, so both formats evolve together. Delivery reconstructs the
 * reminder locally and schedules it through the normal coordinator — after
 * that it is an ordinary local reminder and works fully offline. Idempotency
 * lives in `recipientReminderId`: a share that already carries one is never
 * delivered again, so listener replays, process restarts, and re-opens can't
 * double-schedule.
 *
 * Ownership: RECIPIENTS_ONLY archives the owner's master copy (never
 * scheduled, hidden from lists — the archived flag is the offline engine's
 * own mechanism, so nothing in the alarm pipeline changes). The owner sweep
 * re-asserts that archive after every edit and pushes new content to every
 * live share; the recipient sweep applies what permissions allow and mirrors
 * fire/completion status back for the owner's dashboard.
 */
@Singleton
class ReminderSharingRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val reminderRepository: ReminderRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    private val calculator: NextTriggerCalculator,
    private val timeProvider: TimeProvider,
    private val firestore: FirebaseFirestore,
    private val chatRepository: ChatRepository,
    private val identity: OwnIdentityCache,
    private val notificationCentre: com.alertnotes.domain.repository.NotificationCentreRepository,
    private val logger: AppLogger,
) : ReminderSharingRepository {

    /** Chat context is best-effort — sharing never fails over a message. */
    private suspend fun narrate(
        otherUid: String,
        kind: SystemMessageKind,
        shareId: String? = null,
        shareTitle: String = "",
        shareSchedule: String = "",
    ) {
        runCatching {
            chatRepository.postSystemMessage(otherUid, kind, shareId, shareTitle, shareSchedule)
        }.onFailure { logger.d(TAG, "Chat narration skipped: ${it.message}") }
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
        ownership: ReminderOwnership,
    ) = runShareOp {
        val owner = auth.currentUser?.uid ?: throw FriendException(FriendError.UNKNOWN)
        val payload = encodePayload(reminder)
        val scheduleSummary = scheduleSummaryOf(reminder)
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
                    "ownership" to ownership.name,
                    "status" to status.name,
                    "title" to reminder.title,
                    "scheduleSummary" to scheduleSummary,
                    "payload" to payload,
                    "payloadVersion" to 1L,
                    "appliedVersion" to 0L,
                    "updateRequested" to false,
                    "contentUpdatedAt" to reminder.updatedAt.toEpochMilli(),
                    "recipientReminderId" to null,
                    "lastFiredAtMillis" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "respondedAt" to null,
                    "scheduledAt" to null,
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        // Assigning away means the creator must never be alerted: archive
        // the master copy through the coordinator so its alarm is cancelled
        // atomically. The copy stays fully editable from Shared Reminders.
        if (ownership == ReminderOwnership.RECIPIENTS_ONLY) {
            reminderRepository.getReminder(reminder.id)
                ?.takeIf { !it.isArchived }
                ?.let { coordinator.saveAndSchedule(it.copy(isArchived = true)) }
        }
        val narrationKind = if (ownership == ReminderOwnership.RECIPIENTS_ONLY) {
            SystemMessageKind.REMINDER_ASSIGNED
        } else {
            SystemMessageKind.REMINDER_SHARED
        }
        recipientUids.forEach { recipientUid ->
            val shareId = "${owner}_${reminder.id}_$recipientUid"
            narrate(
                recipientUid,
                narrationKind,
                shareId = shareId,
                shareTitle = reminder.title,
                shareSchedule = scheduleSummary,
            )
            notificationCentre.publish(
                recipientUid = recipientUid,
                category = com.alertnotes.domain.model.NotificationCategory.REMINDER_INVITATION,
                title = if (ownership == ReminderOwnership.RECIPIENTS_ONLY) {
                    "Reminder assigned to you"
                } else {
                    "Reminder invitation"
                },
                body = "${identity.displayName()} sent “${reminder.title}”",
                refId = shareId,
                dedupeKey = "share_${shareId}_invite",
            )
        }
        logger.i(TAG, "Reminder shared with ${recipientUids.size} recipient(s) as $ownership")
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
        narrate(
            share.ownerUid,
            SystemMessageKind.REMINDER_ACCEPTED,
            shareId = share.id,
            shareTitle = share.title,
            shareSchedule = share.scheduleSummary,
        )
        notificationCentre.publish(
            recipientUid = share.ownerUid,
            category = com.alertnotes.domain.model.NotificationCategory.REMINDER_ACCEPTED,
            title = "Reminder accepted",
            body = "${identity.displayName()} accepted “${share.title}”",
            refId = share.id,
            dedupeKey = "share_${share.id}_response",
        )
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
        shareId.substringBefore('_').takeIf { it.isNotBlank() }?.let { ownerUid ->
            narrate(ownerUid, SystemMessageKind.REMINDER_REJECTED)
            notificationCentre.publish(
                recipientUid = ownerUid,
                category = com.alertnotes.domain.model.NotificationCategory.REMINDER_REJECTED,
                title = "Reminder declined",
                body = "${identity.displayName()} declined a shared reminder",
                refId = shareId,
                dedupeKey = "share_${shareId}_response",
            )
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
        notifyCancelled(shareId)
        Unit
    }

    /** The id encodes the recipient: {owner}_{reminderId}_{recipient}. */
    private suspend fun notifyCancelled(shareId: String, title: String = "") {
        val recipientUid = shareId.substringAfterLast('_').takeIf { it.isNotBlank() } ?: return
        notificationCentre.publish(
            recipientUid = recipientUid,
            category = com.alertnotes.domain.model.NotificationCategory.REMINDER_CANCELLED,
            title = "Reminder cancelled",
            body = if (title.isBlank()) {
                "${identity.displayName()} cancelled a shared reminder"
            } else {
                "${identity.displayName()} cancelled “$title”"
            },
            refId = shareId,
            dedupeKey = "share_${shareId}_cancel",
        )
    }

    override suspend fun deleteOwnedReminder(reminderId: Long) = runShareOp {
        val owner = auth.currentUser?.uid ?: throw FriendException(FriendError.UNKNOWN)
        val shares = firestore.collection(FirestoreSchema.REMINDER_SHARES)
            .whereEqualTo("ownerUid", owner)
            .whereEqualTo("reminderId", reminderId)
            .get()
            .await()
            .documents
            .map { it.toShare() }
        shares
            .filter { it.status != ShareStatus.CANCELLED && it.status != ShareStatus.REJECTED }
            .forEach { share ->
                shareReference(share.id).set(
                    mapOf(
                        "status" to ShareStatus.CANCELLED.name,
                        "respondedAt" to FieldValue.serverTimestamp(),
                        "lastSyncAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
                notifyCancelled(share.id, share.title)
            }
        if (reminderRepository.getReminder(reminderId) != null) {
            coordinator.delete(reminderId)
        }
        logger.i(TAG, "Owned reminder $reminderId deleted; ${shares.size} share(s) cancelled")
    }

    override suspend fun acceptShareUpdate(share: ReminderShare) = runShareOp {
        applyContentUpdate(share)
    }

    override suspend fun declineShareUpdate(share: ReminderShare) = runShareOp {
        // Resolved without applying: the recipient keeps their current copy
        // and the owner's dashboard stops showing an outstanding update.
        shareReference(share.id).set(
            mapOf(
                "appliedVersion" to share.payloadVersion,
                "updateRequested" to false,
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        Unit
    }

    override suspend fun syncIncomingShares() {
        val me = auth.currentUser?.uid ?: return
        val shares = runCatching {
            firestore.collection(FirestoreSchema.REMINDER_SHARES)
                .whereEqualTo("recipientUid", me)
                .get()
                .await()
                .documents
                .map { it.toShare() }
        }.getOrDefault(emptyList())
        shares.forEach { share ->
            runCatching { syncIncomingShare(share) }
                .onFailure { logger.w(TAG, "Incoming sync failed for ${share.id}", it) }
        }
    }

    private suspend fun syncIncomingShare(share: ReminderShare) {
        when {
            // Family auto-delivery: store and schedule without approval.
            share.status == ShareStatus.DELIVERED -> deliverLocally(share)

            // Owner revoked an assignment: recipients-only copies belong to
            // the owner, so they are removed from this device. Me+others
            // copies were accepted by the recipient and stay theirs.
            share.status == ShareStatus.CANCELLED &&
                share.ownership == ReminderOwnership.RECIPIENTS_ONLY &&
                share.recipientReminderId != null -> {
                if (reminderRepository.getReminder(share.recipientReminderId) != null) {
                    coordinator.delete(share.recipientReminderId)
                    logger.i(TAG, "Revoked assignment ${share.id} removed locally")
                }
            }

            share.recipientReminderId != null &&
                (
                    share.status == ShareStatus.SCHEDULED ||
                        share.status == ShareStatus.TRIGGERED ||
                        share.status == ShareStatus.COMPLETED
                    ) -> {
                val local = reminderRepository.getReminder(share.recipientReminderId) ?: return
                // Permitted content updates apply silently; requested ones
                // wait for the approval card in Shared Reminders.
                if (share.hasPendingUpdate && !share.updateRequested) {
                    applyContentUpdate(share)
                }
                mirrorFireStatus(share, local)
            }
        }
    }

    /** Mirrors the recipient-side fire/completion state back to the owner. */
    private suspend fun mirrorFireStatus(share: ReminderShare, local: Reminder) {
        val fired = local.lastTriggeredAt ?: return
        val known = share.lastFiredAt?.toEpochMilli() ?: 0L
        if (fired.toEpochMilli() <= known) return
        val completed = !local.isRecurring && local.nextTriggerAt == null
        shareReference(share.id).set(
            mapOf(
                "status" to (if (completed) ShareStatus.COMPLETED else ShareStatus.TRIGGERED).name,
                "lastFiredAtMillis" to fired.toEpochMilli(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        notificationCentre.publish(
            recipientUid = share.ownerUid,
            category = com.alertnotes.domain.model.NotificationCategory.REMINDER_TRIGGERED,
            title = if (completed) "Reminder completed" else "Reminder triggered",
            body = "“${share.title}” fired on ${identity.displayName()}’s device",
            refId = share.id,
            dedupeKey = "share_${share.id}_fired",
        )
    }

    /** Re-applies the payload onto the recipient's existing local copy. */
    private suspend fun applyContentUpdate(share: ReminderShare) {
        val localId = share.recipientReminderId ?: return
        if (share.payload.isBlank()) return
        val backup = json.decodeFromString(BackupReminder.serializer(), share.payload)
        val updated = backup.toEntity().toDomain().copy(id = localId, isArchived = false)
        coordinator.saveAndSchedule(updated)
        shareReference(share.id).set(
            mapOf(
                "appliedVersion" to share.payloadVersion,
                "updateRequested" to false,
                "scheduledAt" to FieldValue.serverTimestamp(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        logger.i(TAG, "Content update v${share.payloadVersion} applied for ${share.id}")
    }

    override suspend fun syncOwnedShares() {
        val me = auth.currentUser?.uid ?: return
        val shares = runCatching {
            firestore.collection(FirestoreSchema.REMINDER_SHARES)
                .whereEqualTo("ownerUid", me)
                .get()
                .await()
                .documents
                .map { it.toShare() }
        }.getOrDefault(emptyList())
        val live = shares.filter {
            it.status != ShareStatus.CANCELLED && it.status != ShareStatus.REJECTED
        }
        if (live.isEmpty()) return
        val familyMembers = runCatching { friendRepository.family.first() }.getOrDefault(emptyList())
        live.forEach { share ->
            runCatching { syncOwnedShare(share, familyMembers) }
                .onFailure { logger.w(TAG, "Owner sync failed for ${share.id}", it) }
        }
    }

    private suspend fun syncOwnedShare(
        share: ReminderShare,
        familyMembers: List<com.alertnotes.domain.model.FamilyMember>,
    ) {
        val reminder = reminderRepository.getReminder(share.reminderId) ?: return
        // Recipients-only invariant: the master copy must stay archived even
        // after an editor save re-ran the scheduler.
        if (share.ownership == ReminderOwnership.RECIPIENTS_ONLY && !reminder.isArchived) {
            coordinator.saveAndSchedule(reminder.copy(isArchived = true))
        }
        if (reminder.updatedAt.toEpochMilli() <= share.contentUpdatedAt) return
        val delivered = share.recipientReminderId != null
        val autoUpdate = familyMembers
            .firstOrNull { it.uid == share.recipientUid }
            ?.permissions?.autoReceiveReminders == true
        shareReference(share.id).set(
            mapOf(
                "title" to reminder.title,
                "scheduleSummary" to scheduleSummaryOf(reminder),
                "payload" to encodePayload(reminder),
                "payloadVersion" to share.payloadVersion + 1,
                // Materialise the read-side fallback on legacy docs so the
                // version gap stays visible after this bump.
                "appliedVersion" to share.appliedVersion,
                "contentUpdatedAt" to reminder.updatedAt.toEpochMilli(),
                // Not-yet-delivered shares just carry fresher content; once
                // delivered, friends must approve while family auto-applies.
                "updateRequested" to (delivered && !autoUpdate),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        if (delivered) {
            notificationCentre.publish(
                recipientUid = share.recipientUid,
                category = com.alertnotes.domain.model.NotificationCategory.REMINDER_UPDATED,
                title = "Reminder updated",
                body = "${identity.displayName()} updated “${reminder.title}”",
                refId = share.id,
                dedupeKey = "share_${share.id}_update",
            )
        }
        logger.i(TAG, "Edit pushed to ${share.id} as v${share.payloadVersion + 1}")
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
                "appliedVersion" to share.payloadVersion,
                "scheduledAt" to FieldValue.serverTimestamp(),
                "lastSyncAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        logger.i(TAG, "Shared reminder delivered and scheduled locally")
    }

    /**
     * The payload carries pure content: archiving is owner-local mechanics
     * and trigger bookkeeping belongs to whichever device runs the copy —
     * leaking the owner's lastTriggeredAt would trip the recipient's
     * fire-mirroring into reporting a phantom trigger.
     */
    private fun encodePayload(reminder: Reminder): String = json.encodeToString(
        BackupReminder.serializer(),
        reminder.copy(
            isArchived = false,
            nextTriggerAt = null,
            lastTriggeredAt = null,
        ).toEntity().toBackup(),
    )

    /**
     * Human schedule preview. An archived (recipients-only) master has no
     * stored next trigger, so the natural occurrence is computed instead.
     */
    private fun scheduleSummaryOf(reminder: Reminder): String {
        val next = reminder.nextTriggerAt
            ?: calculator.nextTrigger(
                reminder.copy(isArchived = false),
                after = timeProvider.now(),
            )
        return next?.toDisplayDateTime(reminder.timeZone).orEmpty()
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

    private fun DocumentSnapshot.toShare(): ReminderShare {
        val payloadVersion = getLong("payloadVersion") ?: 1L
        return ReminderShare(
            id = id,
            reminderId = getLong("reminderId") ?: 0L,
            ownerUid = getString("ownerUid").orEmpty(),
            recipientUid = getString("recipientUid").orEmpty(),
            relationship = RelationshipType.entries
                .firstOrNull { it.name == getString("relationship") } ?: RelationshipType.FRIEND,
            approvalRequired = getBoolean("approvalRequired") != false,
            ownership = ReminderOwnership.entries
                .firstOrNull { it.name == getString("ownership") }
                ?: ReminderOwnership.ME_AND_RECIPIENTS,
            status = ShareStatus.entries
                .firstOrNull { it.name == getString("status") } ?: ShareStatus.CANCELLED,
            title = getString("title").orEmpty(),
            scheduleSummary = getString("scheduleSummary").orEmpty(),
            payload = getString("payload").orEmpty(),
            payloadVersion = payloadVersion,
            // Legacy docs predate versioning: treat them as up to date so
            // nothing re-applies until the owner actually edits.
            appliedVersion = getLong("appliedVersion") ?: payloadVersion,
            updateRequested = getBoolean("updateRequested") == true,
            contentUpdatedAt = getLong("contentUpdatedAt") ?: 0L,
            recipientReminderId = getLong("recipientReminderId"),
            lastFiredAt = getLong("lastFiredAtMillis")?.let(Instant::ofEpochMilli),
            createdAt = instantField("createdAt"),
            respondedAt = instantField("respondedAt"),
            scheduledAt = instantField("scheduledAt"),
            lastSyncAt = instantField("lastSyncAt"),
        )
    }

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
