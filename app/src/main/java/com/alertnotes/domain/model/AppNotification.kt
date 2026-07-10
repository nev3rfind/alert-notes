package com.alertnotes.domain.model

import java.time.Instant

/**
 * What an event in the Notification Centre is about; drives the icon,
 * category filter, and tap destination. Unknown persisted values read as
 * SYSTEM so a malformed document can never crash the centre.
 */
enum class NotificationCategory {
    FRIEND_REQUEST,
    FAMILY_INVITATION,
    REMINDER_INVITATION,
    REMINDER_ACCEPTED,
    REMINDER_REJECTED,
    REMINDER_CANCELLED,
    REMINDER_UPDATED,
    REMINDER_TRIGGERED,
    CHAT_MESSAGE,
    SYSTEM,
}

/**
 * One persistent Notification Centre entry, `users/{uid}/notifications/{id}`.
 * Push notifications are transient; these are the durable record that
 * survives dismissal. Deterministic ids dedupe repeatable events (one entry
 * per conversation, one per share lifecycle step) so the centre never
 * floods. [refId] carries the domain key the tap destination needs — a chat
 * partner uid, a share id, or empty for list destinations.
 */
data class AppNotification(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val senderUid: String,
    val refId: String,
    val read: Boolean,
    val archived: Boolean,
    val createdAt: Instant?,
) {
    /** Case-insensitive match against title and body for centre search. */
    fun matchesQuery(query: String): Boolean =
        query.isBlank() ||
            title.contains(query, ignoreCase = true) ||
            body.contains(query, ignoreCase = true)
}
