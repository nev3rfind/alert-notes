package com.alertnotes.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A reminder with everything future phases need (display, alerting, snooze,
 * security, scheduling constraints). Immutable — every change is a copy, which
 * keeps Compose recomposition cheap and state predictable.
 *
 * Scheduling-derived fields ([nextTriggerAt], [lastTriggeredAt]) are owned by
 * the scheduling coordinator; UI treats them as read-only.
 */
data class Reminder(
    val id: Long = NEW_ID,
    val title: String,
    val description: String = "",
    val type: ReminderType = ReminderType.TEXT,
    /** Hand drawing shown by DRAWING-type reminders; vector, theme-inked. */
    val drawing: ReminderDrawing? = null,
    /** Items shown (and required) by CHECKLIST-type reminders. */
    val checklist: List<ChecklistItem> = emptyList(),
    /** Fraction of the alert surface the drawing occupies. */
    val drawingSize: DrawingSize = DrawingSize.SIZE_60,
    /** Placement of the drawing inside the alert content. */
    val drawingPosition: DrawingPosition = DrawingPosition.CENTER,
    val isEnabled: Boolean = true,
    /** Archived reminders are hidden everywhere but kept for future restore UI. */
    val isArchived: Boolean = false,
    val priority: ReminderPriority = ReminderPriority.NORMAL,
    /** Visual identity of this reminder's alerts and list accent. */
    val theme: ReminderTheme = ReminderTheme.PRIMARY_ORANGE,

    // Presentation (consumed by the popup phase)
    val displayMode: DisplayMode = DisplayMode.FULL_SCREEN,
    val floatingCardPosition: FloatingCardPosition = FloatingCardPosition.CENTER,
    val floatingCardSize: FloatingCardSize = FloatingCardSize.MEDIUM,

    // Dismissal behaviour
    /** Auto-dismiss after this long; null keeps the alert until acted on. */
    val autoDismissAfter: Duration? = null,
    val acknowledgement: AcknowledgementType = AcknowledgementType.NONE,
    /** Direction a SWIPE acknowledgement must travel. */
    val swipeDirection: SwipeDirection = SwipeDirection.UP,
    /** Time the dismiss action stays locked after the alert appears. */
    val dismissCountdown: Duration? = null,
    val requiresBiometric: Boolean = false,
    /** When biometrics are unavailable or fail, allow the device PIN instead. */
    val biometricPinFallback: Boolean = true,

    // Snooze
    val snoozeEnabled: Boolean = true,
    val allowedSnoozeDurations: List<Duration> = DEFAULT_SNOOZE_DURATIONS,

    // Alerting behaviour
    val historyEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val wakeScreen: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val overlayPreference: OverlayPreference = OverlayPreference.AUTO,

    // Scheduling
    val recurrence: Recurrence = Recurrence.None,
    /** Days the reminder may fire. Recurring kinds never fire outside this set. */
    val activeDays: Set<DayOfWeek> = ALL_DAYS,
    /** Daily window the reminder may fire in; null means any time of day. */
    val activeHours: ActiveHours? = null,
    /** First day the reminder may fire (inclusive); null means no lower bound. */
    val startDate: LocalDate? = null,
    /** Last day the reminder may fire (inclusive); null means no upper bound. */
    val endDate: LocalDate? = null,
    /** Zone in which times-of-day and dates are interpreted. */
    val timeZone: ZoneId,

    // Scheduling state (managed by ReminderSchedulingCoordinator)
    val nextTriggerAt: Instant? = null,
    val lastTriggeredAt: Instant? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isRecurring: Boolean get() = recurrence.isRecurring

    /** Case-insensitive match against title and description. */
    fun matchesQuery(query: String): Boolean =
        query.isBlank() ||
            title.contains(query, ignoreCase = true) ||
            description.contains(query, ignoreCase = true)

    /**
     * Whether this reminder may be dismissed straight from the notification
     * shade, without ever showing the alert UI.
     *
     * Only reminders that ask for nothing qualify. Anything carrying a proof
     * requirement — biometrics, a photo, a location fix, a signature, a
     * checklist, or a dismiss-lock countdown — must be resolved on the alert
     * surface where that requirement is actually enforced, otherwise the
     * shade becomes a way to skip it.
     */
    fun allowsShadeDismissal(): Boolean =
        !requiresBiometric &&
            acknowledgement == AcknowledgementType.NONE &&
            checklist.isEmpty() &&
            dismissCountdown == null

    companion object {
        /** Room treats 0 as "not yet inserted" and generates a real id. */
        const val NEW_ID = 0L

        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        val DEFAULT_SNOOZE_DURATIONS: List<Duration> = listOf(
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
        )

        /** The snooze buttons the editor offers. */
        val SNOOZE_DURATION_OPTIONS: List<Duration> = listOf(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(20),
        )
    }
}
