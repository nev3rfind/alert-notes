package com.alertnotes.domain.repository

import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderShare
import com.alertnotes.domain.model.ReminderShareWithProfile
import kotlinx.coroutines.flow.Flow

/**
 * Cloud reminder sharing. Friend shares wait as PENDING invitations; family
 * shares with auto-delivery are released as DELIVERED and picked up by the
 * recipient's device without approval. Accepting (or auto-receiving)
 * reconstructs the reminder from its payload, stores it in the local Room
 * database, and schedules it through the normal coordinator — after which it
 * behaves exactly like a local reminder, including fully offline. All
 * operations throw [com.alertnotes.domain.model.FriendException] with a
 * user-mappable reason on failure.
 */
interface ReminderSharingRepository {

    /** Shares the signed-in user has sent, newest first, live. */
    val outgoingShares: Flow<List<ReminderShareWithProfile>>

    /** Shares addressed to the signed-in user, newest first, live. */
    val incomingShares: Flow<List<ReminderShareWithProfile>>

    /**
     * Shares [reminder] with every uid in [recipientUids]. Per recipient the
     * mode is derived from the family graph: auto-delivery permission →
     * DELIVERED (no approval), otherwise PENDING (friend workflow). Uploads
     * the reminder body once per share document.
     */
    suspend fun shareReminder(reminder: Reminder, recipientUids: List<String>)

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
     * Recipient-side sweep for family auto-delivery: stores and schedules
     * every DELIVERED share not yet on this device. Called by the app-scoped
     * delivery observer whenever incoming shares change.
     */
    suspend fun deliverReleasedShares()
}
