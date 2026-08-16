package com.alertnotes.domain.repository

import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderOwnership
import com.alertnotes.domain.model.ReminderShare
import com.alertnotes.domain.model.ReminderShareWithProfile
import kotlinx.coroutines.flow.Flow

/**
 * Cloud reminder sharing. Friend shares wait as PENDING invitations; family
 * shares with auto-delivery are released as DELIVERED and picked up by the
 * recipient's device without approval. Accepting (or auto-receiving)
 * reconstructs the reminder from its payload, stores it in the local Room
 * database, and schedules it through the normal coordinator — after which it
 * behaves exactly like a local reminder, including fully offline.
 *
 * Ownership: RECIPIENTS_ONLY shares archive the creator's master copy so it
 * never schedules locally; the creator keeps full view/edit/cancel control
 * through the Shared Reminders dashboard. Owner edits are pushed to every
 * live share by [syncOwnedShares]; recipients apply them automatically when
 * family auto-delivery permits, otherwise an update request awaits approval.
 * All operations throw [com.alertnotes.domain.model.FriendException] with a
 * user-mappable reason on failure.
 */
interface ReminderSharingRepository {

    /** Shares the signed-in user has sent, newest first, live. */
    val outgoingShares: Flow<List<ReminderShareWithProfile>>

    /** Shares addressed to the signed-in user, newest first, live. */
    val incomingShares: Flow<List<ReminderShareWithProfile>>

    /**
     * Shares [reminder] with every uid in [recipientUids] under [ownership].
     * Per recipient the delivery mode is derived from the family graph:
     * auto-delivery permission → DELIVERED (no approval), otherwise PENDING
     * (friend workflow). Uploads the reminder body once per share document.
     * RECIPIENTS_ONLY additionally archives the local master copy so it can
     * never alert the creator.
     */
    suspend fun shareReminder(
        reminder: Reminder,
        recipientUids: List<String>,
        ownership: ReminderOwnership,
    )

    /**
     * Recipient approval: downloads the payload, stores and schedules the
     * reminder locally, then marks the share SCHEDULED. Idempotent — a share
     * that already carries a recipientReminderId is never delivered twice.
     */
    suspend fun acceptShare(share: ReminderShare)

    suspend fun declineShare(shareId: String)

    /** Owner withdraws a not-yet-scheduled share. */
    suspend fun cancelShare(shareId: String)

    /**
     * Owner tears down an assignment entirely: every live share for
     * [reminderId] is cancelled (recipients-only copies are removed from
     * recipient devices by their sweep) and the local master copy deleted.
     */
    suspend fun deleteOwnedReminder(reminderId: Long)

    /**
     * Cancels every live share of [reminderId] and notifies its recipients,
     * without touching the local reminder. Returns true when at least one
     * share was cancelled — callers use that to decide whether offering undo
     * would be honest, since restoring the local row does not re-issue a
     * cancelled share.
     */
    suspend fun cancelSharesFor(reminderId: Long): Boolean

    /** Recipient approves a pending content update and re-schedules. */
    suspend fun acceptShareUpdate(share: ReminderShare)

    /** Recipient keeps their current copy; the update is marked resolved. */
    suspend fun declineShareUpdate(share: ReminderShare)

    /**
     * Recipient-side sweep: delivers family auto-releases, applies permitted
     * content updates, removes revoked recipients-only reminders, and mirrors
     * TRIGGERED/COMPLETED back to the owner. Called by the app-scoped
     * delivery observer whenever incoming shares or local reminders change.
     */
    suspend fun syncIncomingShares()

    /**
     * Owner-side sweep: pushes edited content to every live share and keeps
     * recipients-only master copies archived. Called by the delivery
     * observer whenever outgoing shares or local reminders change.
     */
    suspend fun syncOwnedShares()
}
