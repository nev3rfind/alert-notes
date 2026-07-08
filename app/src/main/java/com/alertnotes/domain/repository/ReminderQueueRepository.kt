package com.alertnotes.domain.repository

import com.alertnotes.domain.model.QueuedReminder
import com.alertnotes.domain.model.Reminder
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The due-reminder queue. Every occurrence that fires is appended here and
 * survives until the popup engine (future phase) consumes it — simultaneous
 * reminders are never lost.
 */
interface ReminderQueueRepository {

    /** Pending entries, highest priority first, then earliest due time. */
    fun observePending(): Flow<List<QueuedReminder>>

    /** Appends a due occurrence of [reminder]. Returns the queue entry id. */
    suspend fun enqueue(reminder: Reminder, dueAt: Instant, enqueuedAt: Instant): Long

    /** Marks an entry presented/resolved. Entries are kept for auditing. */
    suspend fun markConsumed(entryId: Long)

    /** Deletes consumed entries enqueued before [cutoff] (housekeeping). */
    suspend fun purgeConsumedBefore(cutoff: Instant)
}
