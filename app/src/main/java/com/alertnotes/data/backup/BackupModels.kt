package com.alertnotes.data.backup

import com.alertnotes.data.entities.ReminderEntity
import com.alertnotes.data.entities.ReminderHistoryEntity
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import kotlinx.serialization.Serializable

/**
 * On-disk backup format (JSON inside the ZIP). Reminders are stored in the
 * same primitive shape as the database rows — including theme names and the
 * vector drawing JSON — so backups survive schema evolution the same way
 * the entity mappers do: unknown values degrade to defaults, never crash.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAtEpochMillis: Long,
    val settings: BackupSettings,
    val reminders: List<BackupReminder>,
    val history: List<BackupHistory> = emptyList(),
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val ZIP_ENTRY_NAME = "alertnotes_backup.json"
    }
}

@Serializable
data class BackupSettings(
    val themeMode: String,
    val useDynamicColor: Boolean,
    val remindersNotificationsEnabled: Boolean,
    val criticalInterruptsEnabled: Boolean,
    val biometricLockEnabled: Boolean,
)

fun UserPreferences.toBackup(): BackupSettings = BackupSettings(
    themeMode = themeMode.name,
    useDynamicColor = useDynamicColor,
    remindersNotificationsEnabled = remindersNotificationsEnabled,
    criticalInterruptsEnabled = criticalInterruptsEnabled,
    biometricLockEnabled = biometricLockEnabled,
)

fun BackupSettings.themeModeOrDefault(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM

@Serializable
data class BackupReminder(
    val title: String,
    val description: String,
    val type: String,
    val drawing: String? = null,
    val checklist: String? = null,
    val drawingSize: String,
    val drawingPosition: String,
    val isEnabled: Boolean,
    val isArchived: Boolean,
    val priority: String,
    val theme: String,
    val swipeDirection: String,
    val displayMode: String,
    val floatingPosition: String,
    val floatingSize: String,
    val autoDismissSeconds: Long? = null,
    val acknowledgement: String,
    val dismissCountdownSeconds: Long? = null,
    val requiresBiometric: Boolean,
    val biometricPinFallback: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeDurationsMinutes: String,
    val historyEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val soundEnabled: Boolean,
    val wakeScreen: Boolean,
    val showOnLockScreen: Boolean,
    val overlayPreference: String,
    val recurrenceType: String,
    val recurrenceTriggerAt: Long? = null,
    val recurrenceIntervalMinutes: Long? = null,
    val recurrenceTimeOfDayMinutes: Int? = null,
    val recurrenceDayOfMonth: Int? = null,
    val activeDaysMask: Int,
    val activeHoursStartMinutes: Int? = null,
    val activeHoursEndMinutes: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val timeZone: String,
    val lastTriggeredAt: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Original row id, used only to relink history within the same file. */
    val originalId: Long,
)

fun ReminderEntity.toBackup(): BackupReminder = BackupReminder(
    title = title,
    description = description,
    type = type,
    drawing = drawing,
    checklist = checklist,
    drawingSize = drawingSize,
    drawingPosition = drawingPosition,
    isEnabled = isEnabled,
    isArchived = isArchived,
    priority = priority,
    theme = theme,
    swipeDirection = swipeDirection,
    displayMode = displayMode,
    floatingPosition = floatingPosition,
    floatingSize = floatingSize,
    autoDismissSeconds = autoDismissSeconds,
    acknowledgement = acknowledgement,
    dismissCountdownSeconds = dismissCountdownSeconds,
    requiresBiometric = requiresBiometric,
    biometricPinFallback = biometricPinFallback,
    snoozeEnabled = snoozeEnabled,
    snoozeDurationsMinutes = snoozeDurationsMinutes,
    historyEnabled = historyEnabled,
    vibrationEnabled = vibrationEnabled,
    soundEnabled = soundEnabled,
    wakeScreen = wakeScreen,
    showOnLockScreen = showOnLockScreen,
    overlayPreference = overlayPreference,
    recurrenceType = recurrenceType,
    recurrenceTriggerAt = recurrenceTriggerAt,
    recurrenceIntervalMinutes = recurrenceIntervalMinutes,
    recurrenceTimeOfDayMinutes = recurrenceTimeOfDayMinutes,
    recurrenceDayOfMonth = recurrenceDayOfMonth,
    activeDaysMask = activeDaysMask,
    activeHoursStartMinutes = activeHoursStartMinutes,
    activeHoursEndMinutes = activeHoursEndMinutes,
    startDate = startDate,
    endDate = endDate,
    timeZone = timeZone,
    lastTriggeredAt = lastTriggeredAt,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    originalId = id,
)

/** New row (id 0): imports always insert, never clobber existing reminders. */
fun BackupReminder.toEntity(): ReminderEntity = ReminderEntity(
    id = 0,
    title = title,
    description = description,
    type = type,
    drawing = drawing,
    checklist = checklist,
    drawingSize = drawingSize,
    drawingPosition = drawingPosition,
    isEnabled = isEnabled,
    isArchived = isArchived,
    priority = priority,
    theme = theme,
    swipeDirection = swipeDirection,
    displayMode = displayMode,
    floatingPosition = floatingPosition,
    floatingSize = floatingSize,
    autoDismissSeconds = autoDismissSeconds,
    acknowledgement = acknowledgement,
    dismissCountdownSeconds = dismissCountdownSeconds,
    requiresBiometric = requiresBiometric,
    biometricPinFallback = biometricPinFallback,
    snoozeEnabled = snoozeEnabled,
    snoozeDurationsMinutes = snoozeDurationsMinutes,
    historyEnabled = historyEnabled,
    vibrationEnabled = vibrationEnabled,
    soundEnabled = soundEnabled,
    wakeScreen = wakeScreen,
    showOnLockScreen = showOnLockScreen,
    overlayPreference = overlayPreference,
    recurrenceType = recurrenceType,
    recurrenceTriggerAt = recurrenceTriggerAt,
    recurrenceIntervalMinutes = recurrenceIntervalMinutes,
    recurrenceTimeOfDayMinutes = recurrenceTimeOfDayMinutes,
    recurrenceDayOfMonth = recurrenceDayOfMonth,
    activeDaysMask = activeDaysMask,
    activeHoursStartMinutes = activeHoursStartMinutes,
    activeHoursEndMinutes = activeHoursEndMinutes,
    startDate = startDate,
    endDate = endDate,
    timeZone = timeZone,
    nextTriggerAt = null,
    lastTriggeredAt = lastTriggeredAt,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

@Serializable
data class BackupHistory(
    val reminderOriginalId: Long,
    val title: String,
    val triggeredAtMillis: Long,
    val dismissedAtMillis: Long? = null,
    val method: String? = null,
    val snoozedMinutes: Long? = null,
    val signature: String? = null,
)

fun ReminderHistoryEntity.toBackup(): BackupHistory = BackupHistory(
    reminderOriginalId = reminderId,
    title = title,
    triggeredAtMillis = triggeredAtMillis,
    dismissedAtMillis = dismissedAtMillis,
    method = method,
    snoozedMinutes = snoozedMinutes,
    signature = signature,
)
