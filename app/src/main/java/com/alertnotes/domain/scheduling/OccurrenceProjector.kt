package com.alertnotes.domain.scheduling

import com.alertnotes.domain.model.Reminder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Expands a reminder's recurrence into concrete occurrences inside a window —
 * the engine behind the calendar, timeline, and dashboard schedule. Pure
 * Kotlin, built entirely on [NextTriggerCalculator], so calendar rendering
 * can never disagree with actual alarm scheduling.
 *
 * Projection intentionally ignores the enabled flag (the calendar shows
 * paused reminders too; filters narrow); archived reminders never project.
 * Dense interval recurrences are capped at [maxOccurrences] per reminder per
 * window — callers surface "+N" style overflow rather than unbounded lists.
 */
@Singleton
class OccurrenceProjector @Inject constructor(
    private val calculator: NextTriggerCalculator,
) {

    fun occurrencesBetween(
        reminder: Reminder,
        from: Instant,
        until: Instant,
        maxOccurrences: Int = DEFAULT_MAX_PER_REMINDER,
    ): List<Instant> {
        if (reminder.isArchived || until <= from) return emptyList()
        val projectable = if (reminder.isEnabled) reminder else reminder.copy(isEnabled = true)
        val occurrences = ArrayList<Instant>()
        // nextTrigger is strictly-after; nudge back so `from` itself counts.
        var cursor = from.minusMillis(1)
        while (occurrences.size < maxOccurrences) {
            val next = calculator.nextTrigger(projectable, after = cursor) ?: break
            if (next >= until) break
            occurrences += next
            cursor = next
        }
        return occurrences
    }

    private companion object {
        /** Generous for month views yet bounded for "every minute" reminders. */
        const val DEFAULT_MAX_PER_REMINDER = 800
    }
}
