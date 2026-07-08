package com.alertnotes.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * When a reminder repeats. New kinds can be added without touching the
 * database schema — persistence stores a type discriminator plus a small set
 * of optional parameter columns (see `data/entities/ReminderEntity`).
 *
 * Day-of-week selection is intentionally *not* part of [Weekly]: every
 * recurring kind is additionally constrained by the reminder's
 * `activeDays`, `activeHours`, and start/end dates, evaluated by
 * `NextTriggerCalculator`.
 */
sealed interface Recurrence {

    /** No schedule yet — the reminder never triggers (draft / migrated data). */
    data object None : Recurrence

    /** Fires exactly once at [triggerAt]. Ignores active days/hours by design. */
    data class OneTime(val triggerAt: Instant) : Recurrence

    data class EveryMinutes(val minutes: Long) : Recurrence

    data class EveryHours(val hours: Long) : Recurrence

    /** Arbitrary fixed interval for anything the presets don't cover. */
    data class CustomInterval(val interval: Duration) : Recurrence

    data class Daily(val timeOfDay: LocalTime) : Recurrence

    /** Fires at [timeOfDay] on the reminder's selected active days. */
    data class Weekly(val timeOfDay: LocalTime) : Recurrence

    /** [dayOfMonth] is clamped to the length of shorter months (31 → Feb 28). */
    data class Monthly(val dayOfMonth: Int, val timeOfDay: LocalTime) : Recurrence
}

val Recurrence.isRecurring: Boolean
    get() = this !is Recurrence.None && this !is Recurrence.OneTime

/** Fixed repeat interval, or null for non-interval kinds. Never below one minute. */
val Recurrence.intervalOrNull: Duration?
    get() = when (this) {
        is Recurrence.EveryMinutes -> Duration.ofMinutes(minutes)
        is Recurrence.EveryHours -> Duration.ofHours(hours)
        is Recurrence.CustomInterval -> interval
        else -> null
    }?.coerceAtLeast(Duration.ofMinutes(1))
