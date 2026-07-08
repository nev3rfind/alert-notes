package com.alertnotes.domain.repository

import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderStats
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reminder persistence. The UI layer depends only on this
 * interface; Room is an implementation detail of the data layer.
 *
 * Mutations here only persist — alarm bookkeeping is orchestrated by
 * `ReminderSchedulingCoordinator`, which is what ViewModels call for
 * anything that affects scheduling.
 */
interface ReminderRepository {

    /** All non-archived reminders, most recently updated first. */
    fun observeReminders(): Flow<List<Reminder>>

    /** Dashboard counters; [dueHorizon] bounds the "due soon" bucket. */
    fun observeStats(dueHorizon: Instant): Flow<ReminderStats>

    /** Enabled reminders with a known next trigger, soonest first. */
    fun observeUpcoming(limit: Int): Flow<List<Reminder>>

    /** Most recently updated non-archived reminders. */
    fun observeRecentlyUpdated(limit: Int): Flow<List<Reminder>>

    suspend fun getReminder(id: Long): Reminder?

    /** Enabled, non-archived reminders — the set that needs alarms. */
    suspend fun getSchedulableReminders(): List<Reminder>

    /**
     * Inserts when [Reminder.id] is [Reminder.NEW_ID], updates otherwise.
     * Returns the effective id.
     */
    suspend fun save(reminder: Reminder): Long

    suspend fun setEnabled(id: Long, isEnabled: Boolean)

    suspend fun setArchived(id: Long, isArchived: Boolean)

    /** Persists the scheduler-computed next occurrence (null = none). */
    suspend fun setNextTrigger(id: Long, nextTriggerAt: Instant?)

    /** Records that the reminder actually fired at [triggeredAt]. */
    suspend fun markTriggered(id: Long, triggeredAt: Instant)

    suspend fun delete(id: Long)

    /** Ids of every reminder, archived included — used to cancel all alarms. */
    suspend fun getAllReminderIds(): List<Long>

    /** Deletes everything; queue and history rows follow via CASCADE. */
    suspend fun deleteAll()
}
