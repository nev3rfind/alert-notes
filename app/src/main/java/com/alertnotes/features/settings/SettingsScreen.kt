package com.alertnotes.features.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.BuildConfig
import com.alertnotes.R
import com.alertnotes.biometric.BiometricGate
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppDatePickerDialog
import com.alertnotes.core.ui.components.AppTimePickerDialog
import com.alertnotes.core.permissions.AppPermission
import com.alertnotes.core.permissions.PermissionState
import com.alertnotes.core.permissions.PermissionStatus
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppOutlinedButton
import com.alertnotes.core.ui.components.AppToggleRow
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.ConfirmationDialog
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ContentMaxWidth = 640.dp

@Composable
fun SettingsScreen(
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val permissionStates by viewModel.permissionStates.collectAsStateWithLifecycle()
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var batteryExplanationFor by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val exitActivity = LocalActivity.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    val onPermissionClick: (PermissionState) -> Unit = { state ->
        when {
            state.permission == AppPermission.IGNORE_BATTERY_OPTIMIZATIONS &&
                state.status != PermissionStatus.GRANTED -> {
                // Explain first — recommended, never forced.
                batteryExplanationFor = true
            }

            state.permission == AppPermission.NOTIFICATIONS &&
                state.status == PermissionStatus.NOT_GRANTED &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> viewModel.openPermissionSettings(context, state.permission)
        }
    }

    // Statuses can change while the user visits system settings; re-check on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.nav_settings)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item {
                    ApplicationModeSection(
                        mode = preferences.appMode,
                        authUser = authUser,
                        viewModel = viewModel,
                    )
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_appearance)) {
                        AppListItem(
                            title = stringResource(R.string.settings_theme),
                            supportingText = stringResource(preferences.themeMode.labelRes()),
                            leadingIcon = Icons.Outlined.Palette,
                            onClick = { showThemeDialog = true },
                            trailingContent = { TrailingChevron() },
                        )
                        AppToggleRow(
                            title = stringResource(R.string.settings_dynamic_colors),
                            supportingText = stringResource(R.string.settings_dynamic_colors_subtitle),
                            checked = preferences.useDynamicColor,
                            onCheckedChange = viewModel::setUseDynamicColor,
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_notifications)) {
                        AppToggleRow(
                            title = stringResource(R.string.settings_notifications_toggle),
                            supportingText = stringResource(R.string.settings_notifications_subtitle),
                            checked = preferences.remindersNotificationsEnabled,
                            onCheckedChange = viewModel::setRemindersNotificationsEnabled,
                        )
                        AppToggleRow(
                            title = stringResource(R.string.settings_critical_interrupts),
                            supportingText = stringResource(R.string.settings_critical_interrupts_subtitle),
                            checked = preferences.criticalInterruptsEnabled,
                            onCheckedChange = viewModel::setCriticalInterruptsEnabled,
                        )
                    }
                }
                item {
                    ReminderControlsSection(
                        preferences = preferences,
                        viewModel = viewModel,
                    )
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_permissions)) {
                        permissionStates.forEach { state ->
                            PermissionRow(state = state, onClick = { onPermissionClick(state) })
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_history)) {
                        AppListItem(
                            title = stringResource(R.string.settings_history_row),
                            supportingText = stringResource(R.string.settings_history_row_subtitle),
                            leadingIcon = Icons.Outlined.History,
                            onClick = onOpenHistory,
                            trailingContent = { TrailingChevron() },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_security)) {
                        AppToggleRow(
                            title = stringResource(R.string.settings_biometric_lock),
                            supportingText = stringResource(
                                if (viewModel.isBiometricAvailable) {
                                    R.string.settings_biometric_lock_subtitle
                                } else {
                                    R.string.settings_biometric_unavailable
                                },
                            ),
                            checked = preferences.biometricLockEnabled,
                            onCheckedChange = viewModel::setBiometricLockEnabled,
                            enabled = viewModel.isBiometricAvailable,
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_privacy)) {
                        Text(
                            text = stringResource(R.string.settings_privacy_statement),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(MaterialTheme.spacing.large),
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_data)) {
                        AppListItem(
                            title = stringResource(R.string.settings_backup_row),
                            supportingText = stringResource(R.string.settings_backup_row_subtitle),
                            leadingIcon = Icons.Outlined.FolderZip,
                            onClick = onOpenBackup,
                            trailingContent = { TrailingChevron() },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_about)) {
                        AppListItem(
                            title = stringResource(R.string.settings_about_row),
                            leadingIcon = Icons.Outlined.Info,
                            onClick = onOpenAbout,
                            trailingContent = { TrailingChevron() },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.settings_section_developer)) {
                        AppListItem(
                            title = stringResource(R.string.settings_version),
                            supportingText = BuildConfig.VERSION_NAME,
                        )
                        AppListItem(
                            title = stringResource(R.string.settings_build_type),
                            supportingText = BuildConfig.BUILD_TYPE,
                        )
                    }
                }
                item {
                    // Standalone exit area at the very bottom — deliberately
                    // NOT one of the settings cards, so it reads as its own
                    // section. Closes only the UI: AlarmManager-backed
                    // schedules and all reminder data are untouched — alerts
                    // keep firing.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                bottom = MaterialTheme.spacing.large,
                            ),
                        )
                        AppOutlinedButton(
                            text = stringResource(R.string.settings_exit_app),
                            onClick = { exitActivity?.finishAndRemoveTask() },
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.settings_exit_app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                        )
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentThemeMode = preferences.themeMode,
            onSelect = { themeMode ->
                viewModel.setThemeMode(themeMode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (batteryExplanationFor) {
        ConfirmationDialog(
            title = stringResource(R.string.battery_dialog_title),
            message = stringResource(R.string.battery_dialog_message),
            confirmText = stringResource(R.string.battery_dialog_open_settings),
            onConfirm = {
                batteryExplanationFor = false
                viewModel.openPermissionSettings(
                    context,
                    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS,
                )
            },
            onDismiss = { batteryExplanationFor = false },
        )
    }
}

/** Global pause + clear-all, the "master switches" for the whole app. */
@Composable
private fun ReminderControlsSection(
    preferences: UserPreferences,
    viewModel: SettingsViewModel,
) {
    var showPauseDialog by rememberSaveable { mutableStateOf(false) }
    var showPauseDatePicker by rememberSaveable { mutableStateOf(false) }
    // Epoch day awaiting its time selection; -1 = none.
    var pendingPauseDate by rememberSaveable { mutableStateOf(-1L) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    val biometricTitle = stringResource(R.string.clear_all_biometric_title)
    val cancelLabel = stringResource(R.string.action_cancel)

    SectionCard(title = stringResource(R.string.settings_section_controls)) {
        val pausedUntil = preferences.pausedUntil
        AppListItem(
            title = stringResource(R.string.settings_pause_all),
            supportingText = when {
                pausedUntil == null -> stringResource(R.string.settings_pause_off)
                pausedUntil == UserPreferences.PAUSE_INDEFINITE ->
                    stringResource(R.string.settings_pause_indefinite)

                else -> stringResource(
                    R.string.settings_pause_until,
                    pausedUntil.toDisplayDateTime(ZoneId.systemDefault()),
                )
            },
            leadingIcon = Icons.Outlined.PauseCircle,
            onClick = { showPauseDialog = true },
            trailingContent = { TrailingChevron() },
        )
        AppListItem(
            title = stringResource(R.string.settings_clear_all),
            supportingText = stringResource(R.string.settings_clear_all_subtitle),
            leadingIcon = Icons.Outlined.DeleteForever,
            leadingIconTint = MaterialTheme.colorScheme.error,
            onClick = { showClearConfirm = true },
        )
    }

    if (showPauseDialog) {
        PauseDialog(
            isPaused = preferences.pausedUntil != null,
            onResume = {
                showPauseDialog = false
                viewModel.resumeReminders()
            },
            onPauseFor = { duration ->
                showPauseDialog = false
                viewModel.pauseFor(duration)
            },
            onPauseIndefinitely = {
                showPauseDialog = false
                viewModel.pauseIndefinitely()
            },
            onPickDateTime = {
                showPauseDialog = false
                showPauseDatePicker = true
            },
            onDismiss = { showPauseDialog = false },
        )
    }
    if (showPauseDatePicker) {
        AppDatePickerDialog(
            initial = LocalDate.now(),
            onConfirm = { date ->
                pendingPauseDate = date.toEpochDay()
                showPauseDatePicker = false
            },
            onDismiss = { showPauseDatePicker = false },
        )
    }
    if (pendingPauseDate >= 0) {
        AppTimePickerDialog(
            title = stringResource(R.string.settings_pause_pick_time),
            initial = LocalTime.of(8, 0),
            onConfirm = { time ->
                val until = LocalDate.ofEpochDay(pendingPauseDate)
                    .atTime(time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                pendingPauseDate = -1L
                viewModel.pauseUntil(until)
            },
            onDismiss = { pendingPauseDate = -1L },
        )
    }
    if (showClearConfirm) {
        ConfirmationDialog(
            title = stringResource(R.string.clear_all_title),
            message = stringResource(R.string.clear_all_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                showClearConfirm = false
                // Destructive: confirm, then authenticate, then wipe.
                if (activity is FragmentActivity) {
                    BiometricGate.authenticate(
                        activity = activity,
                        allowDeviceCredential = true,
                        title = biometricTitle,
                        cancelLabel = cancelLabel,
                        onSuccess = { viewModel.clearAllReminders() },
                    )
                } else {
                    viewModel.clearAllReminders()
                }
            },
            onDismiss = { showClearConfirm = false },
            isDestructive = true,
        )
    }
}

/** Pause options: resume, presets, until a picked moment, or indefinitely. */
@Composable
private fun PauseDialog(
    isPaused: Boolean,
    onResume: () -> Unit,
    onPauseFor: (Duration) -> Unit,
    onPauseIndefinitely: () -> Unit,
    onPickDateTime: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(R.string.settings_pause_all),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                if (isPaused) {
                    PauseOptionRow(text = stringResource(R.string.settings_pause_resume), onClick = onResume)
                }
                PauseOptionRow(
                    text = pluralStringResource(R.plurals.duration_hours, 1, 1),
                    onClick = { onPauseFor(Duration.ofHours(1)) },
                )
                PauseOptionRow(
                    text = pluralStringResource(R.plurals.duration_hours, 8, 8),
                    onClick = { onPauseFor(Duration.ofHours(8)) },
                )
                PauseOptionRow(
                    text = pluralStringResource(R.plurals.duration_hours, 24, 24),
                    onClick = { onPauseFor(Duration.ofHours(24)) },
                )
                PauseOptionRow(
                    text = stringResource(R.string.settings_pause_pick),
                    onClick = onPickDateTime,
                )
                PauseOptionRow(
                    text = stringResource(R.string.settings_pause_indefinite),
                    onClick = onPauseIndefinitely,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PauseOptionRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.medium),
    )
}

@Composable
private fun TrailingChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionRow(state: PermissionState, onClick: () -> Unit) {
    AppListItem(
        title = stringResource(state.permission.labelRes()),
        supportingText = stringResource(state.permission.descriptionRes()),
        leadingIcon = state.permission.icon(),
        leadingIconTint = MaterialTheme.colorScheme.secondary,
        onClick = onClick,
        trailingContent = {
            Text(
                text = stringResource(state.displayLabelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    state.status == PermissionStatus.GRANTED -> MaterialTheme.colorScheme.primary
                    state.isRecommended() -> MaterialTheme.colorScheme.tertiary
                    state.status == PermissionStatus.NOT_GRANTED ->
                        MaterialTheme.colorScheme.error

                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}

/** Optional capabilities read as "Recommended"; required ones as "Missing". */
private fun PermissionState.isRecommended(): Boolean =
    status == PermissionStatus.NOT_GRANTED &&
        (
            permission == AppPermission.IGNORE_BATTERY_OPTIMIZATIONS ||
                permission == AppPermission.DISPLAY_OVER_OTHER_APPS
            )

@StringRes
private fun PermissionState.displayLabelRes(): Int = when {
    status == PermissionStatus.GRANTED -> R.string.permission_status_granted
    isRecommended() -> R.string.permission_status_recommended
    status == PermissionStatus.NOT_GRANTED -> R.string.permission_status_missing
    else -> status.labelRes()
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun AppPermission.labelRes(): Int = when (this) {
    AppPermission.NOTIFICATIONS -> R.string.permission_notifications
    AppPermission.EXACT_ALARMS -> R.string.permission_exact_alarms
    AppPermission.DISPLAY_OVER_OTHER_APPS -> R.string.permission_overlay
    AppPermission.FULL_SCREEN_ALERTS -> R.string.permission_full_screen
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> R.string.permission_battery
    AppPermission.BIOMETRIC -> R.string.permission_biometric
}

/** Why granting helps — shown under every permission row. */
@StringRes
private fun AppPermission.descriptionRes(): Int = when (this) {
    AppPermission.NOTIFICATIONS -> R.string.permission_notifications_description
    AppPermission.EXACT_ALARMS -> R.string.permission_exact_alarms_description
    AppPermission.DISPLAY_OVER_OTHER_APPS -> R.string.permission_overlay_description
    AppPermission.FULL_SCREEN_ALERTS -> R.string.permission_full_screen_description
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> R.string.permission_battery_description
    AppPermission.BIOMETRIC -> R.string.permission_biometric_description
}

private fun AppPermission.icon(): ImageVector = when (this) {
    AppPermission.NOTIFICATIONS -> Icons.Outlined.Notifications
    AppPermission.EXACT_ALARMS -> Icons.Outlined.Alarm
    AppPermission.DISPLAY_OVER_OTHER_APPS -> Icons.Outlined.Layers
    AppPermission.FULL_SCREEN_ALERTS -> Icons.Outlined.Fullscreen
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> Icons.Outlined.BatteryAlert
    AppPermission.BIOMETRIC -> Icons.Outlined.Fingerprint
}

@StringRes
private fun PermissionStatus.labelRes(): Int = when (this) {
    PermissionStatus.GRANTED -> R.string.permission_status_granted
    PermissionStatus.NOT_GRANTED -> R.string.permission_status_not_granted
    PermissionStatus.NOT_REQUIRED -> R.string.permission_status_not_required
    PermissionStatus.UNAVAILABLE -> R.string.permission_status_unavailable
}
