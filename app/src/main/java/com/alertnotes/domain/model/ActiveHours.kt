package com.alertnotes.domain.model

import java.time.LocalTime

/**
 * Daily window during which a reminder may trigger, e.g. 08:00–18:00.
 * Windows that cross midnight (22:00–06:00) are supported.
 */
data class ActiveHours(
    val start: LocalTime,
    val end: LocalTime,
) {
    val crossesMidnight: Boolean get() = end < start

    /** Whether [time] falls inside the window (bounds inclusive). */
    operator fun contains(time: LocalTime): Boolean =
        if (crossesMidnight) {
            time >= start || time <= end
        } else {
            time in start..end
        }
}
