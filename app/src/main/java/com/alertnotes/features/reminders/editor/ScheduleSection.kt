package com.alertnotes.features.reminders.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayString
import com.alertnotes.core.ui.components.AppDatePickerDialog
import com.alertnotes.core.ui.components.AppSegmentedControl
import com.alertnotes.core.ui.components.AppTimePickerDialog
import com.alertnotes.core.ui.components.AppToggleRow
import com.alertnotes.core.ui.components.OptionPickerDialog
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.components.SettingValueRow
import com.alertnotes.core.ui.components.WeekdaySelector
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.ActiveHours
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

private val DefaultActiveHours = ActiveHours(LocalTime.of(8, 0), LocalTime.of(18, 0))

/** Recurring choices only — one-time is handled by the At / In modes. */
private val RecurringKinds: List<RecurrenceKind> = listOf(
    RecurrenceKind.EVERY_MINUTES,
    RecurrenceKind.EVERY_HOURS,
    RecurrenceKind.DAILY,
    RecurrenceKind.WEEKLY,
    RecurrenceKind.MONTHLY,
    RecurrenceKind.CUSTOM_INTERVAL,
)

/**
 * Scheduling in three clear modes — a specific moment (At), a duration from
 * now (In), or a repeating schedule (Recurring). Only the controls for the
 * selected mode are visible; day/hour/date-range constraints appear for
 * recurring schedules, where they apply.
 */
@Composable
fun ScheduleSection(
    draft: Reminder,
    validation: EditorValidation,
    scheduleMode: ScheduleMode,
    triggerIn: Duration?,
    onUpdate: DraftUpdate,
    onChangeRecurrenceKind: (RecurrenceKind) -> Unit,
    onScheduleModeChange: (ScheduleMode) -> Unit,
    onTriggerInChange: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showKindPicker by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.editor_section_schedule), modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.medium,
            ),
        ) {
            AppSegmentedControl(
                options = ScheduleMode.entries.toList(),
                selected = scheduleMode,
                onSelect = onScheduleModeChange,
                label = { stringResource(it.labelRes) },
            )
        }
        AnimatedContent(
            targetState = scheduleMode,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "scheduleMode",
        ) { mode ->
            when (mode) {
                ScheduleMode.TRIGGER_AT ->
                    OneTimeParameters(draft = draft, onUpdate = onUpdate)

                ScheduleMode.TRIGGER_IN ->
                    TriggerInPicker(selected = triggerIn, onSelect = onTriggerInChange)

                ScheduleMode.RECURRING -> Column {
                    SettingValueRow(
                        title = stringResource(R.string.editor_recurrence),
                        value = stringResource(draft.recurrence.kind.labelRes),
                        onClick = { showKindPicker = true },
                    )
                    RecurrenceParameters(
                        kind = draft.recurrence.kind,
                        draft = draft,
                        onUpdate = onUpdate,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.large,
            ),
        ) {
            ValidationHint(messageRes = validation.scheduleError)
        }

        // Constraints apply to recurring schedules; one-shot modes hide them.
        AnimatedVisibility(
            visible = scheduleMode == ScheduleMode.RECURRING,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                ActiveDaysEditor(draft = draft, validation = validation, onUpdate = onUpdate)
                ActiveHoursEditor(draft = draft, onUpdate = onUpdate)
                DateRangeEditor(draft = draft, validation = validation, onUpdate = onUpdate)
            }
        }
    }

    if (showKindPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.editor_recurrence),
            options = RecurringKinds,
            selected = draft.recurrence.kind,
            optionLabel = { stringResource(it.labelRes) },
            onSelect = onChangeRecurrenceKind,
            onDismiss = { showKindPicker = false },
        )
    }
}

// region Trigger-in picker

private val TriggerInPresets: List<Duration> = listOf(
    Duration.ofMinutes(1),
    Duration.ofMinutes(2),
    Duration.ofMinutes(5),
    Duration.ofMinutes(10),
    Duration.ofMinutes(20),
    Duration.ofMinutes(30),
    Duration.ofHours(1),
    Duration.ofHours(2),
    Duration.ofHours(4),
    Duration.ofHours(6),
    Duration.ofHours(12),
    Duration.ofHours(24),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerInPicker(
    selected: Duration?,
    onSelect: (Duration) -> Unit,
) {
    val isCustom = selected != null && selected !in TriggerInPresets
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            TriggerInPresets.forEach { preset ->
                FilterChip(
                    selected = preset == selected,
                    onClick = {
                        showCustom = false
                        onSelect(preset)
                    },
                    label = { Text(text = preset.toPresetLabel()) },
                )
            }
            FilterChip(
                selected = isCustom || showCustom,
                onClick = { showCustom = !showCustom },
                label = { Text(text = stringResource(R.string.schedule_in_custom)) },
            )
        }
        AnimatedVisibility(
            visible = isCustom || showCustom,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val totalMinutes = selected?.toMinutes() ?: 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                NumberField(
                    label = stringResource(R.string.unit_hours),
                    value = totalMinutes / 60,
                    onValueChange = { hours ->
                        onSelect(Duration.ofMinutes(hours * 60 + totalMinutes % 60))
                    },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.unit_minutes),
                    value = totalMinutes % 60,
                    onValueChange = { minutes ->
                        onSelect(Duration.ofMinutes((totalMinutes / 60) * 60 + minutes))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Duration.toPresetLabel(): String = if (toMinutes() < 60) {
    pluralStringResource(R.plurals.duration_minutes, toMinutes().toInt(), toMinutes().toInt())
} else {
    pluralStringResource(R.plurals.duration_hours, toHours().toInt(), toHours().toInt())
}

// endregion

// region Recurrence parameters

@Composable
private fun RecurrenceParameters(
    kind: RecurrenceKind,
    draft: Reminder,
    onUpdate: DraftUpdate,
) {
    when (kind) {
        RecurrenceKind.NONE -> {
            Text(
                text = stringResource(R.string.editor_recurrence_none_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.large,
                ),
            )
        }

        RecurrenceKind.ONE_TIME -> OneTimeParameters(draft, onUpdate)

        RecurrenceKind.EVERY_MINUTES -> {
            val minutes = (draft.recurrence as? Recurrence.EveryMinutes)?.minutes ?: 0
            IntervalField(
                labelRes = R.string.editor_interval_minutes,
                value = minutes,
                onValueChange = { value -> onUpdate { it.copy(recurrence = Recurrence.EveryMinutes(value)) } },
            )
        }

        RecurrenceKind.EVERY_HOURS -> {
            val hours = (draft.recurrence as? Recurrence.EveryHours)?.hours ?: 0
            IntervalField(
                labelRes = R.string.editor_interval_hours,
                value = hours,
                onValueChange = { value -> onUpdate { it.copy(recurrence = Recurrence.EveryHours(value)) } },
            )
        }

        RecurrenceKind.DAILY, RecurrenceKind.WEEKLY -> {
            val time = when (val recurrence = draft.recurrence) {
                is Recurrence.Daily -> recurrence.timeOfDay
                is Recurrence.Weekly -> recurrence.timeOfDay
                else -> LocalTime.NOON
            }
            TimeOfDayRow(
                time = time,
                onTimeChange = { newTime ->
                    onUpdate {
                        it.copy(
                            recurrence = if (kind == RecurrenceKind.DAILY) {
                                Recurrence.Daily(newTime)
                            } else {
                                Recurrence.Weekly(newTime)
                            },
                        )
                    }
                },
            )
            if (kind == RecurrenceKind.WEEKLY) {
                Text(
                    text = stringResource(R.string.editor_weekly_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                )
            }
        }

        RecurrenceKind.MONTHLY -> MonthlyParameters(draft, onUpdate)

        RecurrenceKind.CUSTOM_INTERVAL -> CustomIntervalParameters(draft, onUpdate)
    }
}

@Composable
private fun OneTimeParameters(draft: Reminder, onUpdate: DraftUpdate) {
    val recurrence = draft.recurrence as? Recurrence.OneTime ?: return
    val zoned: ZonedDateTime = recurrence.triggerAt.atZone(draft.timeZone)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Column {
        SettingValueRow(
            title = stringResource(R.string.editor_one_time_date),
            value = zoned.toLocalDate().toDisplayString(),
            onClick = { showDatePicker = true },
        )
        SettingValueRow(
            title = stringResource(R.string.editor_one_time_time),
            value = zoned.toLocalTime().toDisplayString(),
            onClick = { showTimePicker = true },
        )
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = zoned.toLocalDate(),
            onConfirm = { date ->
                onUpdate {
                    it.copy(
                        recurrence = Recurrence.OneTime(
                            date.atTime(zoned.toLocalTime()).atZone(it.timeZone).toInstant(),
                        ),
                    )
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        AppTimePickerDialog(
            title = stringResource(R.string.editor_one_time_time),
            initial = zoned.toLocalTime(),
            onConfirm = { time ->
                onUpdate {
                    it.copy(
                        recurrence = Recurrence.OneTime(
                            zoned.toLocalDate().atTime(time).atZone(it.timeZone).toInstant(),
                        ),
                    )
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun MonthlyParameters(draft: Reminder, onUpdate: DraftUpdate) {
    val recurrence = draft.recurrence as? Recurrence.Monthly ?: return
    Column {
        IntervalField(
            labelRes = R.string.editor_monthly_day,
            value = recurrence.dayOfMonth.toLong(),
            onValueChange = { value ->
                onUpdate {
                    it.copy(
                        recurrence = recurrence.copy(dayOfMonth = value.toInt().coerceIn(1, 31)),
                    )
                }
            },
        )
        TimeOfDayRow(
            time = recurrence.timeOfDay,
            onTimeChange = { time ->
                onUpdate { it.copy(recurrence = recurrence.copy(timeOfDay = time)) }
            },
        )
    }
}

private enum class IntervalUnit(val minutes: Long) { MINUTES(1), HOURS(60), DAYS(1_440) }

@Composable
private fun CustomIntervalParameters(draft: Reminder, onUpdate: DraftUpdate) {
    val recurrence = draft.recurrence as? Recurrence.CustomInterval ?: return
    val totalMinutes = recurrence.interval.toMinutes()
    val unit = when {
        totalMinutes > 0 && totalMinutes % IntervalUnit.DAYS.minutes == 0L -> IntervalUnit.DAYS
        totalMinutes > 0 && totalMinutes % IntervalUnit.HOURS.minutes == 0L -> IntervalUnit.HOURS
        else -> IntervalUnit.MINUTES
    }
    val value = if (totalMinutes > 0) totalMinutes / unit.minutes else 0

    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            NumberField(
                label = stringResource(R.string.editor_custom_interval_value),
                value = value,
                onValueChange = { newValue ->
                    onUpdate {
                        it.copy(
                            recurrence = Recurrence.CustomInterval(
                                Duration.ofMinutes(newValue * unit.minutes),
                            ),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        AppSegmentedControl(
            options = IntervalUnit.entries.toList(),
            selected = unit,
            onSelect = { newUnit ->
                onUpdate {
                    it.copy(
                        recurrence = Recurrence.CustomInterval(
                            Duration.ofMinutes((if (value > 0) value else 1) * newUnit.minutes),
                        ),
                    )
                }
            },
            label = { stringResource(it.labelRes()) },
        )
    }
}

@Composable
private fun IntervalField(
    labelRes: Int,
    value: Long,
    onValueChange: (Long) -> Unit,
) {
    NumberField(
        label = stringResource(labelRes),
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = if (value <= 0) "" else value.toString(),
        onValueChange = { text ->
            val digits = text.filter(Char::isDigit).take(4)
            onValueChange(digits.toLongOrNull() ?: 0L)
        },
        modifier = modifier,
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun TimeOfDayRow(time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    SettingValueRow(
        title = stringResource(R.string.editor_time_of_day),
        value = time.toDisplayString(),
        onClick = { showTimePicker = true },
    )
    if (showTimePicker) {
        AppTimePickerDialog(
            title = stringResource(R.string.editor_time_of_day),
            initial = time,
            onConfirm = { newTime ->
                onTimeChange(newTime)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

// endregion

// region Constraints

@Composable
private fun ActiveDaysEditor(
    draft: Reminder,
    validation: EditorValidation,
    onUpdate: DraftUpdate,
) {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
        Text(
            text = stringResource(R.string.editor_active_days),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
        )
        WeekdaySelector(
            selectedDays = draft.activeDays,
            onDayToggle = { day ->
                onUpdate {
                    val updated = if (day in it.activeDays) it.activeDays - day else it.activeDays + day
                    it.copy(activeDays = updated)
                }
            },
        )
        ValidationHint(messageRes = validation.activeDaysError)
    }
}

@Composable
private fun ActiveHoursEditor(draft: Reminder, onUpdate: DraftUpdate) {
    var pickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val activeHours = draft.activeHours

    AppToggleRow(
        title = stringResource(R.string.editor_active_hours),
        supportingText = stringResource(R.string.editor_active_hours_subtitle),
        checked = activeHours != null,
        onCheckedChange = { limited ->
            onUpdate { it.copy(activeHours = if (limited) DefaultActiveHours else null) }
        },
    )
    AnimatedVisibility(
        visible = activeHours != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val hours = activeHours ?: DefaultActiveHours
        Column {
            SettingValueRow(
                title = stringResource(R.string.editor_active_hours_start),
                value = hours.start.toDisplayString(),
                onClick = { pickerTarget = PICKER_START },
            )
            SettingValueRow(
                title = stringResource(R.string.editor_active_hours_end),
                value = hours.end.toDisplayString(),
                onClick = { pickerTarget = PICKER_END },
            )
        }
    }

    val target = pickerTarget
    if (target != null && activeHours != null) {
        AppTimePickerDialog(
            title = stringResource(
                if (target == PICKER_START) R.string.editor_active_hours_start else R.string.editor_active_hours_end,
            ),
            initial = if (target == PICKER_START) activeHours.start else activeHours.end,
            onConfirm = { time ->
                onUpdate {
                    val current = it.activeHours ?: DefaultActiveHours
                    it.copy(
                        activeHours = if (target == PICKER_START) {
                            current.copy(start = time)
                        } else {
                            current.copy(end = time)
                        },
                    )
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    }
}

@Composable
private fun DateRangeEditor(
    draft: Reminder,
    validation: EditorValidation,
    onUpdate: DraftUpdate,
) {
    var pickerTarget by rememberSaveable { mutableStateOf<String?>(null) }

    Column {
        SettingValueRow(
            title = stringResource(R.string.editor_start_date),
            value = draft.startDate?.toDisplayString()
                ?: stringResource(R.string.editor_date_not_set),
            onClick = { pickerTarget = PICKER_START },
            onClear = draft.startDate?.let {
                { onUpdate { current -> current.copy(startDate = null) } }
            },
            clearContentDescription = stringResource(R.string.cd_clear_start_date),
        )
        SettingValueRow(
            title = stringResource(R.string.editor_end_date),
            value = draft.endDate?.toDisplayString()
                ?: stringResource(R.string.editor_date_not_set),
            onClick = { pickerTarget = PICKER_END },
            onClear = draft.endDate?.let {
                { onUpdate { current -> current.copy(endDate = null) } }
            },
            clearContentDescription = stringResource(R.string.cd_clear_end_date),
        )
        Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large)) {
            ValidationHint(messageRes = validation.dateRangeError)
        }
    }

    val target = pickerTarget
    if (target != null) {
        AppDatePickerDialog(
            initial = if (target == PICKER_START) draft.startDate else draft.endDate,
            onConfirm = { date ->
                onUpdate {
                    if (target == PICKER_START) it.copy(startDate = date) else it.copy(endDate = date)
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    }
}

private const val PICKER_START = "start"
private const val PICKER_END = "end"

// endregion

private fun IntervalUnit.labelRes(): Int = when (this) {
    IntervalUnit.MINUTES -> R.string.unit_minutes
    IntervalUnit.HOURS -> R.string.unit_hours
    IntervalUnit.DAYS -> R.string.unit_days
}
