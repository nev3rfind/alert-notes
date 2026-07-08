package com.alertnotes.domain.repository

import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.HistoryEntry
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderDrawing
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Reminder history. Writers (coordinator, presenter) only call the record
 * functions; the History screen reads the flow. Recording is a no-op for
 * reminders with history disabled — callers don't need to check.
 */
interface ReminderHistoryRepository {

    /** Most recent entries first (bounded). */
    fun observeHistory(): Flow<List<HistoryEntry>>

    suspend fun recordTriggered(reminder: Reminder, at: Instant)

    /**
     * Marks the latest open entry of the reminder resolved via [method];
     * signature acknowledgements pass the drawn [signature] for the archive.
     */
    suspend fun recordDismissed(
        reminder: Reminder,
        at: Instant,
        method: AcknowledgeMethod,
        signature: ReminderDrawing? = null,
    )

    suspend fun recordSnoozed(reminder: Reminder, at: Instant, snoozedFor: Duration)
}
