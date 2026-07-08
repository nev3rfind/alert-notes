package com.alertnotes.domain.scheduling

import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.UserPreferences
import com.alertnotes.domain.repository.ReminderHistoryRepository
import com.alertnotes.domain.repository.ReminderQueueRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.SettingsRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single owner of alarm bookkeeping. Every mutation that can affect
 * scheduling flows through here, guaranteeing the core invariant:
 *
 * > A reminder has exactly one alarm iff it has a future occurrence,
 * > and `nextTriggerAt` in the database always mirrors that alarm.
 *
 * All entry points are serialized by a mutex: a rebuild sweep (boot, time
 * change, app start) can never interleave with a delete or an alarm firing —
 * without it, `rescheduleAll` racing `delete` could re-create a phantom
 * alarm for a reminder that no longer exists.
 *
 * ViewModels and broadcast receivers call this class; they never talk to
 * [ReminderScheduler] or mutate scheduling state directly.
 */
@Singleton
class ReminderSchedulingCoordinator @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val queueRepository: ReminderQueueRepository,
    private val historyRepository: ReminderHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val calculator: NextTriggerCalculator,
    private val scheduleEvents: ScheduleEvents,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) {

    private val mutex = Mutex()

    /** Persists [reminder] (insert or update) and refreshes its alarm. */
    suspend fun saveAndSchedule(reminder: Reminder): Long = mutex.withLock {
        val id = reminderRepository.save(reminder)
        refreshSchedule(reminder.copy(id = id))
        id
    }

    suspend fun setEnabled(id: Long, isEnabled: Boolean) {
        mutex.withLock {
            reminderRepository.setEnabled(id, isEnabled)
            val reminder = reminderRepository.getReminder(id) ?: return
            refreshSchedule(reminder)
        }
    }

    /** Cancels the alarm first so a fired-but-deleted race cannot reschedule. */
    suspend fun delete(id: Long) {
        mutex.withLock {
            scheduler.cancel(id)
            reminderRepository.delete(id)
            scheduleEvents.onScheduleChanged()
            logger.d(TAG, "Deleted reminder $id and cancelled its alarm")
        }
    }

    /** Re-inserts a deleted reminder (undo) with its original id and reschedules. */
    suspend fun restore(reminder: Reminder): Long = saveAndSchedule(reminder)

    /**
     * Called by the alarm receiver when [reminderId] fires: appends the
     * occurrence to the queue and lines up the next one.
     */
    suspend fun onReminderDue(reminderId: Long) {
        mutex.withLock {
            val reminder = reminderRepository.getReminder(reminderId)
            if (reminder == null) {
                logger.w(TAG, "Alarm fired for unknown reminder $reminderId — ignoring")
                return
            }
            if (!reminder.isEnabled || reminder.isArchived) {
                logger.w(TAG, "Alarm fired for inactive reminder $reminderId — cancelling")
                refreshSchedule(reminder)
                return
            }
            val now = timeProvider.now()
            // Spurious-fire guard: if this occurrence was already handled
            // (e.g. by the missed-occurrence recovery on app start racing the
            // alarm broadcast), the stored trigger is already well in the
            // future — enqueueing again would alert the user twice.
            val due = reminder.nextTriggerAt ?: now
            if (due > now.plusSeconds(SPURIOUS_FIRE_SLACK_SECONDS)) {
                logger.w(TAG, "Alarm for $reminderId already handled (next at $due) — skipping")
                return
            }
            if (settingsRepository.preferences.first().isPaused(now)) {
                // Global pause: swallow the occurrence and line up the next one
                // (refreshSchedule clamps the alarm past the pause window).
                logger.d(TAG, "Reminder $reminderId due while globally paused — skipped")
                refreshSchedule(reminder)
                return
            }

            queueRepository.enqueue(reminder, dueAt = due, enqueuedAt = now)
            // Anchor bookkeeping at the scheduled DUE time, not the broadcast
            // processing time — interval grids ("every 30 min") must not
            // drift later by a few seconds on every fire.
            reminderRepository.markTriggered(reminderId, due)
            historyRepository.recordTriggered(reminder, now)
            logger.d(TAG, "Reminder $reminderId due — queued (priority ${reminder.priority})")

            refreshSchedule(reminder.copy(lastTriggeredAt = due))
        }
    }

    /**
     * Postpones the next occurrence of [reminderId] by [snoozeFor] from now.
     * The recurrence itself is untouched: when the snoozed alarm fires,
     * [onReminderDue] recomputes the regular schedule as usual.
     */
    suspend fun snooze(reminderId: Long, snoozeFor: Duration) {
        mutex.withLock {
            val reminder = reminderRepository.getReminder(reminderId) ?: return
            if (!reminder.isEnabled || reminder.isArchived) return
            val snoozedUntil = timeProvider.now().plus(snoozeFor)
            reminderRepository.setNextTrigger(reminderId, snoozedUntil)
            scheduler.schedule(reminderId, snoozedUntil)
            scheduleEvents.onScheduleChanged()
            logger.d(TAG, "Reminder $reminderId snoozed until $snoozedUntil")
        }
    }

    /**
     * Rebuilds every alarm from the database. Called after boot, app update,
     * time/timezone changes, and on every app-process start — situations
     * where alarms may be lost (a force-stopped app loses all of them) or
     * the wall-clock mapping of occurrences has shifted.
     *
     * Two reliability guarantees live here:
     * - **Missed occurrences are delivered late, never dropped**: a stored
     *   trigger that came due while the device was off/rebooting/dead is
     *   queued for presentation before the schedule is recomputed. Without
     *   this, a one-time reminder due mid-reboot would silently never fire.
     * - **Snoozes survive rebuilds**: a stored future trigger earlier than
     *   the recurrence's natural next occurrence is a snooze — it is
     *   preserved, not recomputed away.
     */
    suspend fun rescheduleAll() {
        mutex.withLock {
            val now = timeProvider.now()
            val paused = settingsRepository.preferences.first().isPaused(now)
            val reminders = reminderRepository.getSchedulableReminders()
            var scheduled = 0
            var recovered = 0
            reminders.forEach { reminder ->
                runCatching {
                    val missedAt = reminder.nextTriggerAt?.takeIf { stored ->
                        stored <= now &&
                            (reminder.lastTriggeredAt == null || reminder.lastTriggeredAt < stored)
                    }
                    if (missedAt != null && !paused) {
                        queueRepository.enqueue(reminder, dueAt = missedAt, enqueuedAt = now)
                        reminderRepository.markTriggered(reminder.id, missedAt)
                        historyRepository.recordTriggered(reminder, now)
                        refreshSchedule(
                            reminder = reminder.copy(lastTriggeredAt = missedAt),
                            notifyWidgets = false,
                        )
                        recovered++
                    } else {
                        refreshSchedule(
                            reminder = reminder,
                            preserveEarlierStoredTrigger = true,
                            notifyWidgets = false,
                        )
                    }
                }
                    .onSuccess { scheduled++ }
                    .onFailure { logger.e(TAG, "Failed to reschedule reminder ${reminder.id}", it) }
            }
            // Consumed queue rows are pure bookkeeping residue; without a
            // purge the table grows without bound for recurring reminders.
            queueRepository.purgeConsumedBefore(now.minus(CONSUMED_RETENTION))
            // One widget/UI refresh for the whole sweep, not one per reminder.
            scheduleEvents.onScheduleChanged()
            logger.d(
                TAG,
                "Rescheduled $scheduled/${reminders.size} reminders ($recovered missed recovered)",
            )
        }
    }

    /**
     * Called when the global pause setting changes: every alarm is rebuilt
     * so pause takes effect (or lifts) immediately.
     */
    suspend fun onPauseChanged() {
        rescheduleAll()
    }

    /**
     * Deletes every reminder: alarms cancelled first, then rows removed
     * (queue and history follow via CASCADE), widgets refreshed.
     */
    suspend fun clearAllReminders() {
        mutex.withLock {
            val ids = reminderRepository.getAllReminderIds()
            ids.forEach { scheduler.cancel(it) }
            reminderRepository.deleteAll()
            scheduleEvents.onScheduleChanged()
            logger.d(TAG, "Cleared all reminders (${ids.size}) and their alarms")
        }
    }

    /**
     * Recomputes the next occurrence, persists it, and makes the platform
     * alarm match — the one code path that touches scheduling state. The
     * *displayed* next occurrence is always the true one; the platform alarm
     * is clamped past an active global pause (or suspended entirely while
     * paused indefinitely) so nothing fires until the pause lifts.
     *
     * [preserveEarlierStoredTrigger] keeps a stored future trigger that is
     * earlier than the natural next occurrence (i.e. a snooze) instead of
     * recomputing it away. Only rebuild sweeps pass true — after an edit the
     * stored value is stale by definition and must be recomputed.
     */
    private suspend fun refreshSchedule(
        reminder: Reminder,
        preserveEarlierStoredTrigger: Boolean = false,
        notifyWidgets: Boolean = true,
    ) {
        val now = timeProvider.now()
        val natural = calculator.nextTrigger(reminder, after = now)
        val stored = reminder.nextTriggerAt
        val next = if (
            preserveEarlierStoredTrigger && stored != null && stored > now &&
            (natural == null || stored < natural)
        ) {
            stored
        } else {
            natural
        }
        reminderRepository.setNextTrigger(reminder.id, next)
        val alarmAt = next?.let { clampForPause(it) }
        if (alarmAt != null) {
            scheduler.schedule(reminder.id, alarmAt)
            logger.d(TAG, "Reminder ${reminder.id} next occurrence at $next (alarm at $alarmAt)")
        } else {
            scheduler.cancel(reminder.id)
            logger.d(TAG, "Reminder ${reminder.id} has no schedulable occurrence — alarm cancelled")
        }
        if (notifyWidgets) scheduleEvents.onScheduleChanged()
    }

    /** Null while paused indefinitely; otherwise never earlier than pause end. */
    private suspend fun clampForPause(next: Instant): Instant? {
        val pausedUntil = settingsRepository.preferences.first().pausedUntil ?: return next
        if (timeProvider.now() >= pausedUntil) return next
        return if (pausedUntil == UserPreferences.PAUSE_INDEFINITE) {
            null
        } else {
            maxOf(next, pausedUntil)
        }
    }

    private companion object {
        const val TAG = "ReminderScheduling"
        const val SPURIOUS_FIRE_SLACK_SECONDS = 30L
        val CONSUMED_RETENTION: Duration = Duration.ofDays(1)
    }
}
