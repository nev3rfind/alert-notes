package com.alertnotes.core.extensions

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Locale-aware short time, e.g. "9:00 AM" or "09:00". */
fun LocalTime.toDisplayString(): String =
    format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Locale-aware medium date, e.g. "Jul 6, 2026". */
fun LocalDate.toDisplayString(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Locale-aware date + time of this instant in [zone]. */
fun Instant.toDisplayDateTime(zone: ZoneId): String =
    atZone(zone).format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT),
    )

/** The seven weekdays starting from the locale's first day of week. */
fun weekDaysInLocaleOrder(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}
