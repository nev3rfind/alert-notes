package com.alertnotes.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alertnotes.domain.model.AcknowledgementType
import com.alertnotes.domain.model.ActiveHours
import com.alertnotes.domain.model.ChecklistItem
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.DrawingPosition
import com.alertnotes.domain.model.DrawingSize
import com.alertnotes.domain.model.FloatingCardPosition
import com.alertnotes.domain.model.FloatingCardSize
import com.alertnotes.domain.model.OverlayPreference
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.ReminderTheme
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.domain.model.SwipeDirection
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.json.Json

/**
 * Room row for a reminder. Everything is stored as SQLite primitives —
 * enums as names, durations as seconds/minutes, times of day as minutes
 * since midnight, dates as ISO strings, instants as epoch millis, and the
 * active-day set as a 7-bit mask. Recurrence is a discriminator column plus
 * optional parameter columns, so new kinds never need a schema redesign.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index("is_enabled"),
        Index("next_trigger_at"),
        Index("updated_at"),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val type: String,
    /** Vector drawing serialized as JSON; null for text reminders. */
    val drawing: String?,
    /** Checklist items serialized as JSON; null for other types. */
    val checklist: String?,
    @ColumnInfo(name = "drawing_size", defaultValue = "SIZE_60")
    val drawingSize: String,
    @ColumnInfo(name = "drawing_position", defaultValue = "CENTER")
    val drawingPosition: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,
    val priority: String,
    @ColumnInfo(defaultValue = "PRIMARY_ORANGE")
    val theme: String,
    @ColumnInfo(name = "swipe_direction", defaultValue = "UP")
    val swipeDirection: String,
    @ColumnInfo(name = "display_mode")
    val displayMode: String,
    @ColumnInfo(name = "floating_position")
    val floatingPosition: String,
    @ColumnInfo(name = "floating_size")
    val floatingSize: String,
    @ColumnInfo(name = "auto_dismiss_seconds")
    val autoDismissSeconds: Long?,
    val acknowledgement: String,
    @ColumnInfo(name = "dismiss_countdown_seconds")
    val dismissCountdownSeconds: Long?,
    @ColumnInfo(name = "requires_biometric")
    val requiresBiometric: Boolean,
    @ColumnInfo(name = "biometric_pin_fallback")
    val biometricPinFallback: Boolean,
    @ColumnInfo(name = "snooze_enabled")
    val snoozeEnabled: Boolean,
    @ColumnInfo(name = "snooze_durations_minutes")
    val snoozeDurationsMinutes: String,
    @ColumnInfo(name = "history_enabled")
    val historyEnabled: Boolean,
    @ColumnInfo(name = "vibration_enabled")
    val vibrationEnabled: Boolean,
    @ColumnInfo(name = "sound_enabled")
    val soundEnabled: Boolean,
    @ColumnInfo(name = "wake_screen")
    val wakeScreen: Boolean,
    @ColumnInfo(name = "show_on_lock_screen")
    val showOnLockScreen: Boolean,
    @ColumnInfo(name = "overlay_preference")
    val overlayPreference: String,
    @ColumnInfo(name = "recurrence_type")
    val recurrenceType: String,
    @ColumnInfo(name = "recurrence_trigger_at")
    val recurrenceTriggerAt: Long?,
    @ColumnInfo(name = "recurrence_interval_minutes")
    val recurrenceIntervalMinutes: Long?,
    @ColumnInfo(name = "recurrence_time_of_day_minutes")
    val recurrenceTimeOfDayMinutes: Int?,
    @ColumnInfo(name = "recurrence_day_of_month")
    val recurrenceDayOfMonth: Int?,
    @ColumnInfo(name = "active_days")
    val activeDaysMask: Int,
    @ColumnInfo(name = "active_hours_start_minutes")
    val activeHoursStartMinutes: Int?,
    @ColumnInfo(name = "active_hours_end_minutes")
    val activeHoursEndMinutes: Int?,
    @ColumnInfo(name = "start_date")
    val startDate: String?,
    @ColumnInfo(name = "end_date")
    val endDate: String?,
    @ColumnInfo(name = "time_zone")
    val timeZone: String,
    @ColumnInfo(name = "next_trigger_at")
    val nextTriggerAt: Long?,
    @ColumnInfo(name = "last_triggered_at")
    val lastTriggeredAt: Long?,
    @ColumnInfo(name = "created_at")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtMillis: Long,
)

// region Domain mapping

fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    title = title,
    description = description,
    type = type.toEnumOrDefault(ReminderType.TEXT),
    drawing = drawing?.toReminderDrawingOrNull(),
    checklist = checklist?.toChecklistOrEmpty() ?: emptyList(),
    drawingSize = drawingSize.toEnumOrDefault(DrawingSize.SIZE_60),
    drawingPosition = drawingPosition.toEnumOrDefault(DrawingPosition.CENTER),
    isEnabled = isEnabled,
    isArchived = isArchived,
    priority = priority.toEnumOrDefault(ReminderPriority.NORMAL),
    theme = theme.toEnumOrDefault(ReminderTheme.PRIMARY_ORANGE),
    swipeDirection = swipeDirection.toEnumOrDefault(SwipeDirection.UP),
    displayMode = displayMode.toEnumOrDefault(DisplayMode.FULL_SCREEN),
    floatingCardPosition = floatingPosition.toFloatingCardPosition(),
    floatingCardSize = floatingSize.toEnumOrDefault(FloatingCardSize.MEDIUM),
    autoDismissAfter = autoDismissSeconds?.let(Duration::ofSeconds),
    acknowledgement = acknowledgement.toEnumOrDefault(AcknowledgementType.NONE),
    dismissCountdown = dismissCountdownSeconds?.let(Duration::ofSeconds),
    requiresBiometric = requiresBiometric,
    biometricPinFallback = biometricPinFallback,
    snoozeEnabled = snoozeEnabled,
    allowedSnoozeDurations = snoozeDurationsMinutes.toDurationList(),
    historyEnabled = historyEnabled,
    vibrationEnabled = vibrationEnabled,
    soundEnabled = soundEnabled,
    wakeScreen = wakeScreen,
    showOnLockScreen = showOnLockScreen,
    overlayPreference = overlayPreference.toEnumOrDefault(OverlayPreference.AUTO),
    recurrence = toRecurrence(),
    activeDays = activeDaysMask.toActiveDays(),
    activeHours = toActiveHours(),
    startDate = startDate?.toLocalDateOrNull(),
    endDate = endDate?.toLocalDateOrNull(),
    timeZone = timeZone.toZoneIdOrDefault(),
    nextTriggerAt = nextTriggerAt?.let(Instant::ofEpochMilli),
    lastTriggeredAt = lastTriggeredAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    title = title,
    description = description,
    type = type.name,
    drawing = drawing?.takeUnless { it.isEmpty }?.toJson(),
    checklist = checklist.takeIf { it.isNotEmpty() }?.toChecklistJson(),
    drawingSize = drawingSize.name,
    drawingPosition = drawingPosition.name,
    isEnabled = isEnabled,
    isArchived = isArchived,
    priority = priority.name,
    theme = theme.name,
    swipeDirection = swipeDirection.name,
    displayMode = displayMode.name,
    floatingPosition = floatingCardPosition.name,
    floatingSize = floatingCardSize.name,
    autoDismissSeconds = autoDismissAfter?.seconds,
    acknowledgement = acknowledgement.name,
    dismissCountdownSeconds = dismissCountdown?.seconds,
    requiresBiometric = requiresBiometric,
    biometricPinFallback = biometricPinFallback,
    snoozeEnabled = snoozeEnabled,
    snoozeDurationsMinutes = allowedSnoozeDurations.joinToString(",") { it.toMinutes().toString() },
    historyEnabled = historyEnabled,
    vibrationEnabled = vibrationEnabled,
    soundEnabled = soundEnabled,
    wakeScreen = wakeScreen,
    showOnLockScreen = showOnLockScreen,
    overlayPreference = overlayPreference.name,
    recurrenceType = recurrence.typeName(),
    recurrenceTriggerAt = (recurrence as? Recurrence.OneTime)?.triggerAt?.toEpochMilli(),
    recurrenceIntervalMinutes = when (val r = recurrence) {
        is Recurrence.EveryMinutes -> r.minutes
        is Recurrence.EveryHours -> r.hours * MINUTES_PER_HOUR
        is Recurrence.CustomInterval -> r.interval.toMinutes()
        else -> null
    },
    recurrenceTimeOfDayMinutes = when (val r = recurrence) {
        is Recurrence.Daily -> r.timeOfDay.toMinuteOfDay()
        is Recurrence.Weekly -> r.timeOfDay.toMinuteOfDay()
        is Recurrence.Monthly -> r.timeOfDay.toMinuteOfDay()
        else -> null
    },
    recurrenceDayOfMonth = (recurrence as? Recurrence.Monthly)?.dayOfMonth,
    activeDaysMask = activeDays.toMask(),
    activeHoursStartMinutes = activeHours?.start?.toMinuteOfDay(),
    activeHoursEndMinutes = activeHours?.end?.toMinuteOfDay(),
    startDate = startDate?.toString(),
    endDate = endDate?.toString(),
    timeZone = timeZone.id,
    nextTriggerAt = nextTriggerAt?.toEpochMilli(),
    lastTriggeredAt = lastTriggeredAt?.toEpochMilli(),
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

// endregion

// region Recurrence persistence

/** Stable discriminator values — stored in the database, never rename. */
internal object RecurrenceTypes {
    const val NONE = "NONE"
    const val ONE_TIME = "ONE_TIME"
    const val EVERY_MINUTES = "EVERY_MINUTES"
    const val EVERY_HOURS = "EVERY_HOURS"
    const val CUSTOM_INTERVAL = "CUSTOM_INTERVAL"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
}

private fun Recurrence.typeName(): String = when (this) {
    is Recurrence.None -> RecurrenceTypes.NONE
    is Recurrence.OneTime -> RecurrenceTypes.ONE_TIME
    is Recurrence.EveryMinutes -> RecurrenceTypes.EVERY_MINUTES
    is Recurrence.EveryHours -> RecurrenceTypes.EVERY_HOURS
    is Recurrence.CustomInterval -> RecurrenceTypes.CUSTOM_INTERVAL
    is Recurrence.Daily -> RecurrenceTypes.DAILY
    is Recurrence.Weekly -> RecurrenceTypes.WEEKLY
    is Recurrence.Monthly -> RecurrenceTypes.MONTHLY
}

/** Corrupt or unknown recurrence data degrades to [Recurrence.None], never crashes. */
private fun ReminderEntity.toRecurrence(): Recurrence = when (recurrenceType) {
    RecurrenceTypes.ONE_TIME ->
        recurrenceTriggerAt?.let { Recurrence.OneTime(Instant.ofEpochMilli(it)) }

    RecurrenceTypes.EVERY_MINUTES ->
        recurrenceIntervalMinutes?.let { Recurrence.EveryMinutes(it) }

    RecurrenceTypes.EVERY_HOURS ->
        recurrenceIntervalMinutes?.let { Recurrence.EveryHours(it / MINUTES_PER_HOUR) }

    RecurrenceTypes.CUSTOM_INTERVAL ->
        recurrenceIntervalMinutes?.let { Recurrence.CustomInterval(Duration.ofMinutes(it)) }

    RecurrenceTypes.DAILY ->
        recurrenceTimeOfDayMinutes?.let { Recurrence.Daily(it.toLocalTime()) }

    RecurrenceTypes.WEEKLY ->
        recurrenceTimeOfDayMinutes?.let { Recurrence.Weekly(it.toLocalTime()) }

    RecurrenceTypes.MONTHLY -> {
        val time = recurrenceTimeOfDayMinutes?.toLocalTime()
        val day = recurrenceDayOfMonth
        if (time != null && day != null) Recurrence.Monthly(day, time) else null
    }

    else -> null
} ?: Recurrence.None

// endregion

// region Primitive conversions

private const val MINUTES_PER_HOUR = 60L

/** Monday = bit 0 … Sunday = bit 6. */
internal fun Set<DayOfWeek>.toMask(): Int =
    fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun Int.toActiveDays(): Set<DayOfWeek> =
    DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

private fun ReminderEntity.toActiveHours(): ActiveHours? {
    val start = activeHoursStartMinutes ?: return null
    val end = activeHoursEndMinutes ?: return null
    return ActiveHours(start.toLocalTime(), end.toLocalTime())
}

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

private fun Int.toLocalTime(): LocalTime =
    LocalTime.of((this / 60).coerceIn(0, 23), (this % 60).coerceIn(0, 59))

private fun String.toDurationList(): List<Duration> =
    split(',').mapNotNull { it.trim().toLongOrNull() }.map(Duration::ofMinutes)

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.toZoneIdOrDefault(): ZoneId =
    runCatching { ZoneId.of(this) }.getOrDefault(ZoneId.systemDefault())

/**
 * Positions gained corners in v0.4; rows written by older versions stored
 * the three original values, which map onto their centered equivalents.
 */
internal fun String.toFloatingCardPosition(): FloatingCardPosition = when (this) {
    "TOP" -> FloatingCardPosition.TOP_CENTER
    "BOTTOM" -> FloatingCardPosition.BOTTOM_CENTER
    else -> toEnumOrDefault(FloatingCardPosition.CENTER)
}

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

// endregion

// region Drawing persistence

/** Tolerant of schema evolution; corrupt payloads degrade to "no drawing". */
private val drawingJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

internal fun ReminderDrawing.toJson(): String = drawingJson.encodeToString(this)

internal fun String.toReminderDrawingOrNull(): ReminderDrawing? =
    runCatching { drawingJson.decodeFromString<ReminderDrawing>(this) }
        .getOrNull()
        ?.takeUnless { it.isEmpty }

internal fun List<ChecklistItem>.toChecklistJson(): String =
    drawingJson.encodeToString(this)

internal fun String.toChecklistOrEmpty(): List<ChecklistItem> =
    runCatching { drawingJson.decodeFromString<List<ChecklistItem>>(this) }
        .getOrDefault(emptyList())

// endregion
