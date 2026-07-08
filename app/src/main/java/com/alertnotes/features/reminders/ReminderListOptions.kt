package com.alertnotes.features.reminders

import androidx.annotation.StringRes
import com.alertnotes.R
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.isRecurring
import java.time.Duration
import java.time.Instant

/** List orderings offered by the sort menu, in display order. */
enum class ReminderSort(@param:StringRes val labelRes: Int) {
    /** Soonest to fire first; unscheduled reminders sink to the bottom. */
    NEXT_TRIGGER(R.string.sort_next_trigger),
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    ALPHABETICAL(R.string.sort_alphabetical),
    PRIORITY(R.string.sort_priority),
    RECENTLY_MODIFIED(R.string.sort_recently_modified),
}

/**
 * Live presentation status of a reminder. Computed against the pending
 * display queue and the current time whenever the underlying data changes.
 */
enum class ReminderDisplayStatus(@param:StringRes val labelRes: Int) {
    /** Its alert is on screen right now (head of the display queue). */
    RUNNING(R.string.reminder_status_running),

    /** Due and waiting behind another alert in the display queue. */
    QUEUED(R.string.reminder_status_queued),

    /** Enabled and firing soon (within 24 hours). */
    UPCOMING(R.string.reminder_status_upcoming),

    /** Enabled with a future occurrence further out. */
    WAITING(R.string.reminder_status_waiting),

    /** A one-time reminder that has fired. */
    COMPLETED(R.string.reminder_status_completed),

    /** No future occurrence (past its end date or never scheduled). */
    EXPIRED(R.string.reminder_status_expired),

    /** Switched off by the user. */
    DISABLED(R.string.reminder_status_disabled),
}

/** How far ahead a trigger counts as "upcoming" rather than "waiting". */
private val UPCOMING_WINDOW: Duration = Duration.ofHours(24)

fun Reminder.displayStatus(
    runningReminderId: Long?,
    queuedReminderIds: Set<Long>,
    now: Instant,
): ReminderDisplayStatus {
    val nextTrigger = nextTriggerAt
    return when {
        id == runningReminderId -> ReminderDisplayStatus.RUNNING
        id in queuedReminderIds -> ReminderDisplayStatus.QUEUED
        !isEnabled -> ReminderDisplayStatus.DISABLED
        nextTrigger != null ->
            if (nextTrigger <= now.plus(UPCOMING_WINDOW)) {
                ReminderDisplayStatus.UPCOMING
            } else {
                ReminderDisplayStatus.WAITING
            }

        recurrence is Recurrence.OneTime && lastTriggeredAt != null ->
            ReminderDisplayStatus.COMPLETED

        else -> ReminderDisplayStatus.EXPIRED
    }
}

/** Toggleable filter chips. Active filters are combined with AND. */
enum class ReminderFilter(@param:StringRes val labelRes: Int) {
    RUNNING(R.string.filter_running),
    QUEUED(R.string.filter_queued),
    UPCOMING(R.string.filter_upcoming),
    WAITING(R.string.filter_waiting),
    COMPLETED(R.string.filter_completed),
    EXPIRED(R.string.filter_expired),
    DISABLED(R.string.filter_disabled),
    ONE_TIME(R.string.filter_one_time),
    RECURRING(R.string.filter_recurring),
    HIGH_PRIORITY(R.string.filter_high_priority),
    CRITICAL(R.string.filter_critical),
}

fun ReminderFilter.matches(reminder: Reminder, status: ReminderDisplayStatus): Boolean =
    when (this) {
        ReminderFilter.RUNNING -> status == ReminderDisplayStatus.RUNNING
        ReminderFilter.QUEUED -> status == ReminderDisplayStatus.QUEUED
        ReminderFilter.UPCOMING -> status == ReminderDisplayStatus.UPCOMING
        ReminderFilter.WAITING -> status == ReminderDisplayStatus.WAITING
        ReminderFilter.COMPLETED -> status == ReminderDisplayStatus.COMPLETED
        ReminderFilter.EXPIRED -> status == ReminderDisplayStatus.EXPIRED
        ReminderFilter.DISABLED -> status == ReminderDisplayStatus.DISABLED
        ReminderFilter.ONE_TIME -> reminder.recurrence is Recurrence.OneTime
        ReminderFilter.RECURRING -> reminder.recurrence.isRecurring
        ReminderFilter.HIGH_PRIORITY -> reminder.priority == ReminderPriority.HIGH
        ReminderFilter.CRITICAL -> reminder.priority == ReminderPriority.CRITICAL
    }

fun List<Reminder>.sortedBy(sort: ReminderSort): List<Reminder> = when (sort) {
    ReminderSort.NEXT_TRIGGER ->
        sortedWith(compareBy(nullsLast()) { it.nextTriggerAt })

    ReminderSort.NEWEST -> sortedByDescending { it.createdAt }
    ReminderSort.OLDEST -> sortedBy { it.createdAt }
    ReminderSort.ALPHABETICAL -> sortedBy { it.title.lowercase() }
    ReminderSort.PRIORITY ->
        sortedWith(compareByDescending<Reminder> { it.priority.rank }.thenBy { it.title.lowercase() })

    ReminderSort.RECENTLY_MODIFIED -> sortedByDescending { it.updatedAt }
}
