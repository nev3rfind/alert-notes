package com.alertnotes.domain.model

import java.time.Instant

/** Lifecycle of a queue entry. */
enum class QueueEntryState {
    /** Waiting for the popup engine (future phase) to present it. */
    PENDING,

    /** Presented (or otherwise resolved); kept for auditing. */
    CONSUMED,
}

/**
 * A reminder occurrence that became due. Occurrences are never discarded:
 * if several reminders fire together they all wait here, ordered by priority
 * then due time, until the popup engine consumes them.
 */
data class QueuedReminder(
    val id: Long,
    val reminderId: Long,
    val priority: ReminderPriority,
    /** The occurrence time that made this entry due. */
    val dueAt: Instant,
    val enqueuedAt: Instant,
    val state: QueueEntryState,
)
