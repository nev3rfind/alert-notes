package com.alertnotes.domain.scheduling

import com.alertnotes.domain.model.ActiveHours
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.intervalOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure occurrence engine: given a reminder and a point in time, computes the
 * next instant the reminder should fire, honouring recurrence, active days,
 * active hours, the start/end date range, and the reminder's time zone.
 *
 * No Android dependencies — fully covered by JVM unit tests.
 */
@Singleton
class NextTriggerCalculator @Inject constructor() {

    /**
     * Next trigger strictly after [after], or null when the reminder can
     * never fire again (disabled, archived, unscheduled, past its end date,
     * or its constraints exclude every occurrence).
     */
    fun nextTrigger(reminder: Reminder, after: Instant): Instant? {
        if (!reminder.isEnabled || reminder.isArchived) return null
        return when (val recurrence = reminder.recurrence) {
            is Recurrence.None -> null

            // A one-time reminder fires at the user's exact chosen moment;
            // only the date range constrains it, not days/hours.
            is Recurrence.OneTime ->
                recurrence.triggerAt.takeIf {
                    it > after && withinDateRange(reminder, it.atZone(reminder.timeZone).toLocalDate())
                }

            is Recurrence.EveryMinutes,
            is Recurrence.EveryHours,
            is Recurrence.CustomInterval,
            -> nextIntervalTrigger(reminder, after)

            is Recurrence.Daily -> nextDayBasedTrigger(reminder, recurrence.timeOfDay, after)
            is Recurrence.Weekly -> nextDayBasedTrigger(reminder, recurrence.timeOfDay, after)
            is Recurrence.Monthly -> nextMonthlyTrigger(reminder, recurrence, after)
        }
    }

    private fun nextIntervalTrigger(reminder: Reminder, after: Instant): Instant? {
        val interval = reminder.recurrence.intervalOrNull ?: return null
        var candidate = firstIntervalCandidateAfter(anchorOf(reminder), interval, after)
        repeat(MAX_INTERVAL_STEPS) {
            val zoned = candidate.atZone(reminder.timeZone)
            if (beyondEndDate(reminder, zoned.toLocalDate())) return null
            if (satisfiesConstraints(reminder, zoned)) return candidate
            candidate += interval
        }
        return null
    }

    /**
     * Interval occurrences tick on a fixed grid so edits and reschedules never
     * drift: anchored at the last actual trigger, else the start date, else
     * the creation time.
     */
    private fun anchorOf(reminder: Reminder): Instant =
        reminder.lastTriggeredAt
            ?: reminder.startDate?.atStartOfDay(reminder.timeZone)?.toInstant()
            ?: reminder.createdAt

    private fun firstIntervalCandidateAfter(anchor: Instant, interval: Duration, after: Instant): Instant {
        if (anchor > after) return anchor
        val elapsed = Duration.between(anchor, after)
        val steps = elapsed.toMillis() / interval.toMillis() + 1
        return anchor.plus(interval.multipliedBy(steps))
    }

    private fun nextDayBasedTrigger(reminder: Reminder, timeOfDay: LocalTime, after: Instant): Instant? {
        // The firing time never varies, so an out-of-window time can never fire.
        if (!withinActiveHours(reminder.activeHours, timeOfDay)) return null

        var date = maxOf(
            after.atZone(reminder.timeZone).toLocalDate(),
            reminder.startDate ?: LocalDate.MIN,
        )
        repeat(MAX_DAY_STEPS) {
            if (beyondEndDate(reminder, date)) return null
            if (date.dayOfWeek in reminder.activeDays && withinDateRange(reminder, date)) {
                val candidate = date.atTime(timeOfDay).atZone(reminder.timeZone).toInstant()
                if (candidate > after) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun nextMonthlyTrigger(
        reminder: Reminder,
        recurrence: Recurrence.Monthly,
        after: Instant,
    ): Instant? {
        if (!withinActiveHours(reminder.activeHours, recurrence.timeOfDay)) return null
        val dayOfMonth = recurrence.dayOfMonth.coerceIn(1, 31)

        var month = YearMonth.from(after.atZone(reminder.timeZone))
        reminder.startDate?.let { start -> month = maxOf(month, YearMonth.from(start)) }
        repeat(MAX_MONTH_STEPS) {
            val date = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
            if (beyondEndDate(reminder, date)) return null
            // activeDays is deliberately NOT consulted here, for the same
            // reason OneTime ignores it: the day-of-month IS the day selector.
            // Intersecting the two silently skipped whole months whenever the
            // chosen date happened to land on an excluded weekday — "the 15th
            // of every month" with weekends off vanished for any month whose
            // 15th was a Sunday, with no error and no way to tell.
            if (withinDateRange(reminder, date)) {
                val candidate = date.atTime(recurrence.timeOfDay).atZone(reminder.timeZone).toInstant()
                if (candidate > after) return candidate
            }
            month = month.plusMonths(1)
        }
        return null
    }

    private fun satisfiesConstraints(reminder: Reminder, moment: ZonedDateTime): Boolean =
        moment.dayOfWeek in reminder.activeDays &&
            withinActiveHours(reminder.activeHours, moment.toLocalTime()) &&
            withinDateRange(reminder, moment.toLocalDate())

    private fun withinActiveHours(activeHours: ActiveHours?, time: LocalTime): Boolean =
        activeHours == null || time in activeHours

    private fun withinDateRange(reminder: Reminder, date: LocalDate): Boolean =
        (reminder.startDate == null || date >= reminder.startDate) &&
            (reminder.endDate == null || date <= reminder.endDate)

    /** True once iteration has passed the end date and can stop early. */
    private fun beyondEndDate(reminder: Reminder, date: LocalDate): Boolean =
        reminder.endDate != null && date > reminder.endDate

    private companion object {
        /** Covers a 1-minute interval confined to a tiny daily window for weeks. */
        const val MAX_INTERVAL_STEPS = 100_000

        /** ~10 years of day-by-day search. */
        const val MAX_DAY_STEPS = 3_700

        /** 40 years of month-by-month search. */
        const val MAX_MONTH_STEPS = 480
    }
}
