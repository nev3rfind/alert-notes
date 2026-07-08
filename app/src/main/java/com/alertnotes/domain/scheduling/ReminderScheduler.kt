package com.alertnotes.domain.scheduling

import java.time.Instant

/**
 * Abstraction over the platform's alarm mechanism. Business logic depends
 * only on this interface; `services/AlarmManagerReminderScheduler` is the
 * current implementation and can be replaced (e.g. WorkManager fallback)
 * without touching any scheduling logic.
 *
 * Contract: at most one alarm exists per reminder id — scheduling again for
 * the same id replaces the previous alarm, so alarms can never accumulate.
 */
interface ReminderScheduler {

    /** Schedules (or replaces) the single alarm for [reminderId] at [triggerAt]. */
    fun schedule(reminderId: Long, triggerAt: Instant)

    /** Cancels the alarm for [reminderId], if any. Safe to call when none exists. */
    fun cancel(reminderId: Long)
}
