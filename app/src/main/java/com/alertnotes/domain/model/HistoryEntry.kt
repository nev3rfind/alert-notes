package com.alertnotes.domain.model

import java.time.Instant

/** How an alert was resolved; recorded in history when enabled. */
enum class AcknowledgeMethod {
    BUTTON,
    TAP,
    SWIPE,
    TICK_GESTURE,
    SIGNATURE,
    CHECKLIST,

    /** Live camera proof captured at dismissal. */
    PHOTO,
    AUTO,
    NOTIFICATION,
    SNOOZE,
}

/**
 * One firing of a reminder, kept when the reminder has history enabled.
 * The title is snapshotted so history stays meaningful after edits.
 */
data class HistoryEntry(
    val id: Long,
    val reminderId: Long,
    val title: String,
    val triggeredAt: Instant,
    val dismissedAt: Instant?,
    val method: AcknowledgeMethod?,
    /** Minutes the user snoozed for, when [method] is [AcknowledgeMethod.SNOOZE]. */
    val snoozedMinutes: Long?,
    /** The drawn signature, when [method] is [AcknowledgeMethod.SIGNATURE]. */
    val signature: ReminderDrawing? = null,
)
