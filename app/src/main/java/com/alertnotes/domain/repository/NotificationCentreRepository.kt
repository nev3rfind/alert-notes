package com.alertnotes.domain.repository

import com.alertnotes.domain.model.AppNotification
import com.alertnotes.domain.model.NotificationCategory
import kotlinx.coroutines.flow.Flow

/**
 * The durable Notification Centre: every important social and sharing event
 * lands here and stays until the user archives or deletes it, independent of
 * transient push notifications. Entries live under the RECIPIENT's own
 * `users/{uid}/notifications` — event producers write into the recipient's
 * collection (rules constrain them to creating, with their own uid as
 * sender), while reading, updating, and deleting stay owner-only.
 *
 * Publishing is best-effort by design: a notification must never fail the
 * operation it narrates.
 */
interface NotificationCentreRepository {

    /** The signed-in user's entries, newest first, live. */
    val notifications: Flow<List<AppNotification>>

    /** Live count of unread, unarchived entries. */
    val unreadCount: Flow<Int>

    /**
     * Writes an entry into [recipientUid]'s centre. [dedupeKey], when set,
     * becomes the document id so repeats of the same logical event (new
     * messages in one conversation, one share's lifecycle) overwrite instead
     * of piling up; null generates a fresh entry. Never throws.
     */
    suspend fun publish(
        recipientUid: String,
        category: NotificationCategory,
        title: String,
        body: String,
        refId: String = "",
        dedupeKey: String? = null,
    )

    suspend fun markRead(id: String)

    suspend fun markAllRead()

    suspend fun setArchived(id: String, archived: Boolean)

    suspend fun delete(id: String)
}
