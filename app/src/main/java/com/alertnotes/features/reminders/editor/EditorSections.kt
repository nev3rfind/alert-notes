package com.alertnotes.features.reminders.editor

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppSegmentedControl
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.components.AppToggleRow
import com.alertnotes.core.ui.components.OptionPickerDialog
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.components.SettingValueRow
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.AcknowledgementType
import com.alertnotes.domain.model.ChecklistItem
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.DrawingPosition
import com.alertnotes.domain.model.DrawingSize
import com.alertnotes.domain.model.FloatingCardPosition
import com.alertnotes.domain.model.FloatingCardSize
import com.alertnotes.domain.model.OverlayPreference
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.domain.model.SwipeDirection
import com.alertnotes.features.alerts.spec
import com.alertnotes.features.drawing.DrawingCanvasDialog
import com.alertnotes.features.drawing.DrawingView
import java.time.Duration

/** A change to the draft, applied through the ViewModel. */
typealias DraftUpdate = ((Reminder) -> Reminder) -> Unit

// region General

@Composable
fun GeneralSection(
    draft: Reminder,
    validation: EditorValidation,
    onUpdate: DraftUpdate,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.editor_section_general), modifier = modifier) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            AppTextField(
                value = draft.title,
                onValueChange = { newTitle -> onUpdate { it.copy(title = newTitle) } },
                label = stringResource(R.string.reminder_field_title),
                isError = validation.titleError != null,
                errorText = validation.titleError?.let { stringResource(it) },
            )
            AppTextField(
                value = draft.description,
                onValueChange = { newText -> onUpdate { it.copy(description = newText) } },
                label = stringResource(R.string.reminder_field_notes),
                singleLine = false,
                minLines = 2,
            )
            LabeledControl(labelRes = R.string.editor_type) {
                AppSegmentedControl(
                    options = ReminderType.entries.toList(),
                    selected = draft.type,
                    onSelect = { type -> onUpdate { it.copy(type = type) } },
                    label = { stringResource(it.labelRes()) },
                )
            }
            AnimatedVisibility(
                visible = draft.type == ReminderType.DRAWING,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                DrawingEditorBlock(draft = draft, onUpdate = onUpdate)
            }
            AnimatedVisibility(
                visible = draft.type == ReminderType.CHECKLIST,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ChecklistEditorBlock(
                    draft = draft,
                    validation = validation,
                    onUpdate = onUpdate,
                )
            }
            LabeledControl(labelRes = R.string.editor_priority) {
                AppSegmentedControl(
                    options = ReminderPriority.entries.toList(),
                    selected = draft.priority,
                    onSelect = { priority -> onUpdate { it.copy(priority = priority) } },
                    label = { stringResource(it.labelRes()) },
                )
            }
            AnimatedVisibility(
                visible = draft.priority == ReminderPriority.CRITICAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    text = stringResource(R.string.editor_critical_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.extraSmall,
                    ),
                )
            }
        }
        AppToggleRow(
            title = stringResource(R.string.editor_enabled),
            supportingText = stringResource(R.string.editor_enabled_subtitle),
            checked = draft.isEnabled,
            onCheckedChange = { enabled -> onUpdate { it.copy(isEnabled = enabled) } },
        )
    }
}

/** Drawing thumbnail plus the entry point into the full canvas studio. */
@Composable
private fun DrawingEditorBlock(
    draft: Reminder,
    onUpdate: DraftUpdate,
) {
    var showCanvas by rememberSaveable { mutableStateOf(false) }
    val drawing = draft.drawing
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        if (drawing != null && !drawing.isEmpty) {
            DrawingView(
                drawing = drawing,
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.medium,
                    ),
            )
        }
        SecondaryButton(
            text = stringResource(
                if (drawing == null || drawing.isEmpty) {
                    R.string.editor_add_drawing
                } else {
                    R.string.editor_edit_drawing
                },
            ),
            onClick = { showCanvas = true },
            icon = Icons.Outlined.Draw,
        )
        LabeledControl(labelRes = R.string.editor_drawing_size) {
            AppSegmentedControl(
                options = DrawingSize.entries.toList(),
                selected = draft.drawingSize,
                onSelect = { size -> onUpdate { it.copy(drawingSize = size) } },
                label = { it.percentLabel() },
            )
        }
        LabeledControl(labelRes = R.string.editor_drawing_position) {
            AppSegmentedControl(
                options = DrawingPosition.entries.toList(),
                selected = draft.drawingPosition,
                onSelect = { position -> onUpdate { it.copy(drawingPosition = position) } },
                label = { stringResource(it.labelRes()) },
            )
        }
    }
    if (showCanvas) {
        DrawingCanvasDialog(
            initial = drawing,
            inkColor = draft.theme.spec.accent,
            onSave = { updated ->
                onUpdate { it.copy(drawing = updated) }
                showCanvas = false
            },
            onDismiss = { showCanvas = false },
        )
    }
}

/**
 * Checklist item editor: one text field per item with a remove affordance,
 * plus an add button. Items are unlimited; the reminder can only be saved
 * with at least one non-blank item (validated in the ViewModel).
 */
@Composable
private fun ChecklistEditorBlock(
    draft: Reminder,
    validation: EditorValidation,
    onUpdate: DraftUpdate,
) {
    val items = draft.checklist
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = item.text,
                    onValueChange = { newText ->
                        onUpdate { reminder ->
                            reminder.copy(
                                checklist = reminder.checklist.toMutableList().apply {
                                    if (index in indices) this[index] = ChecklistItem(newText)
                                },
                            )
                        }
                    },
                    label = stringResource(R.string.editor_checklist_item, index + 1),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onUpdate { reminder ->
                            reminder.copy(
                                checklist = reminder.checklist.toMutableList().apply {
                                    if (index in indices) removeAt(index)
                                },
                            )
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_remove_checklist_item),
                    )
                }
            }
        }
        SecondaryButton(
            text = stringResource(R.string.editor_add_checklist_item),
            onClick = {
                onUpdate { reminder ->
                    reminder.copy(checklist = reminder.checklist + ChecklistItem(""))
                }
            },
            icon = Icons.Outlined.Add,
        )
        if (validation.checklistError != null) {
            Text(
                text = stringResource(validation.checklistError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// endregion

// region Display

@Composable
fun DisplaySection(
    draft: Reminder,
    onUpdate: DraftUpdate,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.editor_section_display), modifier = modifier) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            ReminderPreview(reminder = draft)
            LabeledControl(labelRes = R.string.editor_theme) {
                ThemeSelector(
                    selected = draft.theme,
                    onSelect = { theme -> onUpdate { it.copy(theme = theme) } },
                )
            }
            LabeledControl(labelRes = R.string.editor_display_mode) {
                AppSegmentedControl(
                    options = DisplayMode.entries.toList(),
                    selected = draft.displayMode,
                    onSelect = { mode -> onUpdate { it.copy(displayMode = mode) } },
                    label = { stringResource(it.labelRes()) },
                )
            }
            AnimatedVisibility(
                visible = draft.displayMode == DisplayMode.FLOATING_CARD,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    LabeledControl(labelRes = R.string.editor_floating_position) {
                        PositionSelector(
                            selected = draft.floatingCardPosition,
                            onSelect = { position ->
                                onUpdate { it.copy(floatingCardPosition = position) }
                            },
                        )
                    }
                    LabeledControl(labelRes = R.string.editor_floating_size) {
                        AppSegmentedControl(
                            options = FloatingCardSize.entries.toList(),
                            selected = draft.floatingCardSize,
                            onSelect = { size -> onUpdate { it.copy(floatingCardSize = size) } },
                            label = { stringResource(it.labelRes()) },
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Dismissal

private val AutoDismissOptions: List<Duration?> = listOf(
    null,
    Duration.ofSeconds(1),
    Duration.ofSeconds(3),
    Duration.ofSeconds(5),
    Duration.ofSeconds(10),
    Duration.ofSeconds(30),
    Duration.ofMinutes(1),
)

private val DismissLockOptions: List<Duration?> = listOf(
    null,
    Duration.ofSeconds(3),
    Duration.ofSeconds(5),
    Duration.ofSeconds(10),
    Duration.ofSeconds(15),
)

@Composable
fun DismissalSection(
    draft: Reminder,
    onUpdate: DraftUpdate,
    modifier: Modifier = Modifier,
) {
    var showAutoDismissPicker by rememberSaveable { mutableStateOf(false) }
    var showAcknowledgementPicker by rememberSaveable { mutableStateOf(false) }
    var showDismissLockPicker by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.editor_section_dismissal), modifier = modifier) {
        SettingValueRow(
            title = stringResource(R.string.editor_auto_dismiss),
            value = draft.autoDismissAfter.toAutoDismissLabel(),
            onClick = { showAutoDismissPicker = true },
        )
        SettingValueRow(
            title = stringResource(R.string.editor_dismiss_lock),
            supportingText = stringResource(R.string.editor_dismiss_lock_subtitle),
            value = draft.dismissCountdown.toDismissLockLabel(),
            onClick = { showDismissLockPicker = true },
        )
        SettingValueRow(
            title = stringResource(R.string.editor_acknowledgement),
            supportingText = stringResource(R.string.editor_acknowledgement_subtitle),
            value = stringResource(draft.acknowledgement.labelRes()),
            onClick = { showAcknowledgementPicker = true },
        )
        AnimatedVisibility(
            visible = draft.acknowledgement == AcknowledgementType.SWIPE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large,
                ),
            ) {
                LabeledControl(labelRes = R.string.editor_swipe_direction) {
                    AppSegmentedControl(
                        options = SwipeDirection.entries.toList(),
                        selected = draft.swipeDirection,
                        onSelect = { direction -> onUpdate { it.copy(swipeDirection = direction) } },
                        label = { stringResource(it.labelRes()) },
                    )
                }
            }
        }
    }

    if (showAutoDismissPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.editor_auto_dismiss),
            options = AutoDismissOptions,
            selected = draft.autoDismissAfter,
            optionLabel = { it.toAutoDismissLabel() },
            onSelect = { duration -> onUpdate { it.copy(autoDismissAfter = duration) } },
            onDismiss = { showAutoDismissPicker = false },
        )
    }
    if (showDismissLockPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.editor_dismiss_lock),
            options = DismissLockOptions,
            selected = draft.dismissCountdown,
            optionLabel = { it.toDismissLockLabel() },
            onSelect = { duration -> onUpdate { it.copy(dismissCountdown = duration) } },
            onDismiss = { showDismissLockPicker = false },
        )
    }
    if (showAcknowledgementPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.editor_acknowledgement),
            options = AcknowledgementType.entries.toList(),
            selected = draft.acknowledgement,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { type -> onUpdate { it.copy(acknowledgement = type) } },
            onDismiss = { showAcknowledgementPicker = false },
        )
    }
}

@Composable
private fun Duration?.toDismissLockLabel(): String = when {
    this == null -> stringResource(R.string.editor_dismiss_lock_off)
    else -> pluralStringResource(
        R.plurals.duration_seconds,
        seconds.toInt(),
        seconds.toInt(),
    )
}

// endregion

// region Snooze

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeSection(
    draft: Reminder,
    validation: EditorValidation,
    onUpdate: DraftUpdate,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.editor_section_snooze), modifier = modifier) {
        AppToggleRow(
            title = stringResource(R.string.editor_snooze_enabled),
            supportingText = stringResource(R.string.editor_snooze_subtitle),
            checked = draft.snoozeEnabled,
            onCheckedChange = { enabled -> onUpdate { it.copy(snoozeEnabled = enabled) } },
        )
        AnimatedVisibility(
            visible = draft.snoozeEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large,
                ),
            ) {
                Text(
                    text = stringResource(R.string.editor_snooze_options),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    Reminder.SNOOZE_DURATION_OPTIONS.forEach { option ->
                        val isSelected = option in draft.allowedSnoozeDurations
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onUpdate { current ->
                                    val updated = if (isSelected) {
                                        current.allowedSnoozeDurations - option
                                    } else {
                                        (current.allowedSnoozeDurations + option).sorted()
                                    }
                                    current.copy(allowedSnoozeDurations = updated)
                                }
                            },
                            label = {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.duration_minutes,
                                        option.toMinutes().toInt(),
                                        option.toMinutes().toInt(),
                                    ),
                                )
                            },
                        )
                    }
                }
                ValidationHint(messageRes = validation.snoozeError)
            }
        }
    }
}

// endregion

// region Security

@Composable
fun SecuritySection(
    draft: Reminder,
    onUpdate: DraftUpdate,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.editor_section_security), modifier = modifier) {
        AppToggleRow(
            title = stringResource(R.string.editor_require_fingerprint),
            supportingText = stringResource(R.string.editor_require_fingerprint_subtitle),
            checked = draft.requiresBiometric,
            onCheckedChange = { required -> onUpdate { it.copy(requiresBiometric = required) } },
        )
        AnimatedVisibility(
            visible = draft.requiresBiometric,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                AppToggleRow(
                    title = stringResource(R.string.editor_pin_fallback),
                    supportingText = stringResource(R.string.editor_pin_fallback_subtitle),
                    checked = draft.biometricPinFallback,
                    onCheckedChange = { fallback ->
                        onUpdate { it.copy(biometricPinFallback = fallback) }
                    },
                )
                Text(
                    text = stringResource(R.string.editor_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.large,
                    ),
                )
            }
        }
    }
}

// endregion

// region Alerts

@Composable
fun AlertsSection(
    draft: Reminder,
    onUpdate: DraftUpdate,
    modifier: Modifier = Modifier,
) {
    var showOverlayPicker by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.editor_section_alerts), modifier = modifier) {
        AppToggleRow(
            title = stringResource(R.string.editor_wake_screen),
            checked = draft.wakeScreen,
            onCheckedChange = { value -> onUpdate { it.copy(wakeScreen = value) } },
        )
        AppToggleRow(
            title = stringResource(R.string.editor_show_lock_screen),
            checked = draft.showOnLockScreen,
            onCheckedChange = { value -> onUpdate { it.copy(showOnLockScreen = value) } },
        )
        AppToggleRow(
            title = stringResource(R.string.editor_vibration),
            checked = draft.vibrationEnabled,
            onCheckedChange = { value -> onUpdate { it.copy(vibrationEnabled = value) } },
        )
        AppToggleRow(
            title = stringResource(R.string.editor_sound),
            checked = draft.soundEnabled,
            onCheckedChange = { value -> onUpdate { it.copy(soundEnabled = value) } },
        )
        AnimatedVisibility(
            visible = draft.soundEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SoundPreviewRow(priority = draft.priority)
        }
        AppToggleRow(
            title = stringResource(R.string.editor_history),
            supportingText = stringResource(R.string.editor_history_subtitle),
            checked = draft.historyEnabled,
            onCheckedChange = { value -> onUpdate { it.copy(historyEnabled = value) } },
        )
        SettingValueRow(
            title = stringResource(R.string.editor_overlay),
            value = stringResource(draft.overlayPreference.labelRes()),
            onClick = { showOverlayPicker = true },
        )
    }

    if (showOverlayPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.editor_overlay),
            options = OverlayPreference.entries.toList(),
            selected = draft.overlayPreference,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { preference -> onUpdate { it.copy(overlayPreference = preference) } },
            onDismiss = { showOverlayPicker = false },
        )
    }
}

// endregion

// region Shared pieces

/** Small caption above a segmented control. */
@Composable
fun LabeledControl(
    @StringRes labelRes: Int,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraSmall),
        )
        content()
    }
}

/** Inline validation message shown in the error color; hidden when null. */
@Composable
fun ValidationHint(@StringRes messageRes: Int?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = messageRes != null, modifier = modifier) {
        if (messageRes != null) {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
            )
        }
    }
}

@Composable
fun Duration?.toAutoDismissLabel(): String = when {
    this == null -> stringResource(R.string.editor_auto_dismiss_manual)
    seconds < 60 -> pluralStringResource(
        R.plurals.duration_seconds,
        seconds.toInt(),
        seconds.toInt(),
    )

    else -> pluralStringResource(
        R.plurals.duration_minutes,
        toMinutes().toInt(),
        toMinutes().toInt(),
    )
}

@StringRes
fun ReminderType.labelRes(): Int = when (this) {
    ReminderType.TEXT -> R.string.type_text
    ReminderType.DRAWING -> R.string.type_drawing
    ReminderType.CHECKLIST -> R.string.type_checklist
}

/**
 * Which bundled sound this priority rings with, and a live preview button.
 * Critical always carries the dedicated alarm; everything else uses the
 * Alert Notes signature sound.
 */
@Composable
private fun SoundPreviewRow(priority: ReminderPriority) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val soundRes = if (priority == ReminderPriority.CRITICAL) {
        R.raw.alert_critical
    } else {
        R.raw.sound_noti
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (priority == ReminderPriority.CRITICAL) {
                    R.string.editor_sound_critical
                } else {
                    R.string.editor_sound_default
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                android.media.MediaPlayer.create(context, soundRes)?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            },
        ) {
            Text(text = stringResource(R.string.editor_sound_preview))
        }
    }
}

@StringRes
fun ReminderPriority.labelRes(): Int = when (this) {
    ReminderPriority.LOW -> R.string.priority_low
    ReminderPriority.NORMAL -> R.string.priority_normal
    ReminderPriority.HIGH -> R.string.priority_high
    ReminderPriority.CRITICAL -> R.string.priority_critical
}

@StringRes
fun DisplayMode.labelRes(): Int = when (this) {
    DisplayMode.FULL_SCREEN -> R.string.display_full_screen
    DisplayMode.FLOATING_CARD -> R.string.display_floating_card
}

@StringRes
fun FloatingCardPosition.labelRes(): Int = when (this) {
    FloatingCardPosition.TOP_LEFT -> R.string.position_top_left
    FloatingCardPosition.TOP_CENTER -> R.string.position_top_center
    FloatingCardPosition.TOP_RIGHT -> R.string.position_top_right
    FloatingCardPosition.CENTER -> R.string.position_center
    FloatingCardPosition.BOTTOM_LEFT -> R.string.position_bottom_left
    FloatingCardPosition.BOTTOM_CENTER -> R.string.position_bottom_center
    FloatingCardPosition.BOTTOM_RIGHT -> R.string.position_bottom_right
}

@StringRes
fun FloatingCardSize.labelRes(): Int = when (this) {
    FloatingCardSize.SMALL -> R.string.size_small
    FloatingCardSize.MEDIUM -> R.string.size_medium
    FloatingCardSize.LARGE -> R.string.size_large
}

@StringRes
fun AcknowledgementType.labelRes(): Int = when (this) {
    AcknowledgementType.NONE -> R.string.ack_none
    AcknowledgementType.TAP -> R.string.ack_tap
    AcknowledgementType.SWIPE -> R.string.ack_swipe
    AcknowledgementType.TICK_GESTURE -> R.string.ack_tick
    AcknowledgementType.SIGNATURE -> R.string.ack_signature
    AcknowledgementType.PHOTO -> R.string.ack_photo
}

@StringRes
fun SwipeDirection.labelRes(): Int = when (this) {
    SwipeDirection.UP -> R.string.direction_up
    SwipeDirection.DOWN -> R.string.direction_down
    SwipeDirection.LEFT -> R.string.direction_left
    SwipeDirection.RIGHT -> R.string.direction_right
}

/** "40%" style labels; percentages are locale-neutral here. */
fun DrawingSize.percentLabel(): String = "${(fraction * 100).toInt()}%"

@StringRes
fun DrawingPosition.labelRes(): Int = when (this) {
    DrawingPosition.TOP -> R.string.layout_top
    DrawingPosition.CENTER -> R.string.layout_center
    DrawingPosition.BOTTOM -> R.string.layout_bottom
}

@StringRes
fun OverlayPreference.labelRes(): Int = when (this) {
    OverlayPreference.AUTO -> R.string.overlay_auto
    OverlayPreference.PREFER_OVERLAY -> R.string.overlay_prefer
    OverlayPreference.NEVER_OVERLAY -> R.string.overlay_never
}

// endregion
