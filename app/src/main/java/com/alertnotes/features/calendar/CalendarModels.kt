package com.alertnotes.features.calendar

import androidx.annotation.StringRes
import com.alertnotes.R
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.domain.model.isRecurring
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Calendar presentation modes, in segmented-control order. */
enum class CalendarMode(@param:StringRes val labelRes: Int) {
    MONTH(R.string.calendar_mode_month),
    WEEK(R.string.calendar_mode_week),
    DAY(R.string.calendar_mode_day),
    TIMELINE(R.string.calendar_mode_timeline),
}

/** One concrete firing of a reminder inside the projected window. */
data class CalendarOccurrence(
    val reminder: Reminder,
    val at: Instant,
    /** Device-local date/time, precomputed once during projection. */
    val date: LocalDate,
    val time: LocalTime,
)

/** Toggleable calendar filters; combined with AND like the list filters. */
enum class CalendarFilter(@param:StringRes val labelRes: Int) {
    ACTIVE(R.string.filter_active),
    DISABLED(R.string.filter_disabled),
    RECURRING(R.string.filter_recurring),
    ONE_TIME(R.string.filter_one_time),
    TEXT(R.string.type_text),
    DRAWING(R.string.type_drawing),
    CHECKLIST(R.string.type_checklist),
    HIGH_PRIORITY(R.string.filter_high_priority),
    CRITICAL(R.string.filter_critical),
}

fun CalendarFilter.matches(reminder: Reminder): Boolean = when (this) {
    CalendarFilter.ACTIVE -> reminder.isEnabled
    CalendarFilter.DISABLED -> !reminder.isEnabled
    CalendarFilter.RECURRING -> reminder.recurrence.isRecurring
    CalendarFilter.ONE_TIME -> reminder.recurrence is Recurrence.OneTime
    CalendarFilter.TEXT -> reminder.type == ReminderType.TEXT
    CalendarFilter.DRAWING -> reminder.type == ReminderType.DRAWING
    CalendarFilter.CHECKLIST -> reminder.type == ReminderType.CHECKLIST
    CalendarFilter.HIGH_PRIORITY -> reminder.priority == ReminderPriority.HIGH
    CalendarFilter.CRITICAL -> reminder.priority == ReminderPriority.CRITICAL
}

/** Timeline grouping buckets, in display order. */
enum class TimelineBucket(@param:StringRes val labelRes: Int) {
    TODAY(R.string.timeline_today),
    TOMORROW(R.string.timeline_tomorrow),
    THIS_WEEK(R.string.timeline_this_week),
    LATER(R.string.timeline_later),
}

fun timelineBucketFor(date: LocalDate, today: LocalDate): TimelineBucket = when {
    date <= today -> TimelineBucket.TODAY
    date == today.plusDays(1) -> TimelineBucket.TOMORROW
    date < today.plusDays(7) -> TimelineBucket.THIS_WEEK
    else -> TimelineBucket.LATER
}

/** Converts a projected instant into a device-local calendar occurrence. */
fun Reminder.toOccurrence(at: Instant, zone: ZoneId): CalendarOccurrence {
    val zoned = at.atZone(zone)
    return CalendarOccurrence(
        reminder = this,
        at = at,
        date = zoned.toLocalDate(),
        time = zoned.toLocalTime(),
    )
}
