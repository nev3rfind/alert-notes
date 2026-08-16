package com.alertnotes.features.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.permissions.AppPermission
import com.alertnotes.core.permissions.PermissionState
import com.alertnotes.core.permissions.PermissionStatus
import com.alertnotes.core.permissions.PermissionsManager
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.alertnotes.services.ReliabilityDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    private val permissionsManager: PermissionsManager,
    private val coordinator: ReminderSchedulingCoordinator,
    private val timeProvider: TimeProvider,
    diagnostics: ReliabilityDiagnostics,
) : ViewModel() {

    /** Reliability-relevant permissions only; biometrics are unrelated. */
    private val _permissionStates = MutableStateFlow(reliabilityStatuses())
    val permissionStates: StateFlow<List<PermissionState>> = _permissionStates.asStateFlow()

    val diagnosticEntries: StateFlow<List<ReliabilityDiagnostics.Entry>> = diagnostics.entries

    fun refreshPermissions() {
        _permissionStates.value = reliabilityStatuses()
    }

    fun openPermissionSettings(context: Context, permission: AppPermission) {
        val intent = permissionsManager.settingsIntent(permission) ?: return
        runCatching { context.startActivity(intent) }
    }

    /**
     * Schedules a REAL one-time reminder through the production pipeline —
     * scheduler, alarm, receiver, queue, presenter, dispatcher — so the test
     * proves exactly what a user reminder would do. It appears in the
     * reminder list like any other and can be deleted there.
     */
    fun scheduleTestReminder(inSeconds: Long, title: String) {
        viewModelScope.launch {
            val now = timeProvider.now()
            coordinator.saveAndSchedule(
                Reminder(
                    title = title,
                    recurrence = Recurrence.OneTime(now.plusSeconds(inSeconds)),
                    // Keep the diagnostics run out of the activity history.
                    historyEnabled = false,
                    timeZone = ZoneId.systemDefault(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun reliabilityStatuses(): List<PermissionState> =
        permissionsManager.allStatuses().filter { it.permission != AppPermission.BIOMETRIC }
}

/**
 * The permission assistant plus the built-in end-to-end delivery test:
 * every grant reminders depend on, with one-tap deep links, and a live
 * trace of the pipeline so failures explain themselves.
 */
@Composable
fun ReliabilityScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReliabilityViewModel = hiltViewModel(),
) {
    val permissionStates by viewModel.permissionStates.collectAsStateWithLifecycle()
    val entries by viewModel.diagnosticEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val testTitle = stringResource(R.string.reliability_test_reminder_title)

    // Grants change in system settings; re-check on every return.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.reliability_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.reliability_section_permissions)) {
                        Text(
                            text = stringResource(R.string.reliability_permissions_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.spacing.large,
                                vertical = MaterialTheme.spacing.small,
                            ),
                        )
                        permissionStates.forEach { state ->
                            ReliabilityPermissionRow(
                                state = state,
                                onEnable = {
                                    viewModel.openPermissionSettings(context, state.permission)
                                },
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.reliability_section_test)) {
                        Text(
                            text = stringResource(R.string.reliability_test_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.spacing.large,
                                vertical = MaterialTheme.spacing.small,
                            ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.spacing.large),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        ) {
                            SecondaryButton(
                                text = stringResource(R.string.reliability_test_10s),
                                onClick = { viewModel.scheduleTestReminder(10, testTitle) },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = stringResource(R.string.reliability_test_30s),
                                onClick = { viewModel.scheduleTestReminder(30, testTitle) },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = stringResource(R.string.reliability_test_1m),
                                onClick = { viewModel.scheduleTestReminder(60, testTitle) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = stringResource(R.string.reliability_test_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = MaterialTheme.spacing.large,
                                end = MaterialTheme.spacing.large,
                                bottom = MaterialTheme.spacing.medium,
                            ),
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.reliability_section_log)) {
                        if (entries.isEmpty()) {
                            Text(
                                text = stringResource(R.string.reliability_log_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.large),
                            )
                        } else {
                            Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                                entries.forEach { entry ->
                                    DiagnosticRow(entry = entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReliabilityPermissionRow(
    state: PermissionState,
    onEnable: () -> Unit,
) {
    val granted = state.status == PermissionStatus.GRANTED
    val notRequired = state.status == PermissionStatus.NOT_REQUIRED ||
        state.status == PermissionStatus.UNAVAILABLE
    AppListItem(
        title = (if (granted || notRequired) "✓  " else "✗  ") +
            stringResource(state.permission.reliabilityLabelRes()),
        supportingText = stringResource(state.permission.reliabilityWhyRes()),
        trailingContent = {
            when {
                granted -> Text(
                    text = stringResource(R.string.permission_status_granted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                notRequired -> Text(
                    text = stringResource(R.string.permission_status_not_required),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> TextButton(onClick = onEnable) {
                    Text(text = stringResource(R.string.reliability_enable))
                }
            }
        },
    )
}

@Composable
private fun DiagnosticRow(entry: ReliabilityDiagnostics.Entry) {
    Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall)) {
        Row {
            Text(
                text = timeFormatter.format(Instant.ofEpochMilli(entry.atMillis)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.stage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = MaterialTheme.spacing.small),
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun AppPermission.reliabilityLabelRes(): Int = when (this) {
    AppPermission.NOTIFICATIONS -> R.string.permission_notifications
    AppPermission.EXACT_ALARMS -> R.string.permission_exact_alarms
    AppPermission.DISPLAY_OVER_OTHER_APPS -> R.string.permission_overlay
    AppPermission.FULL_SCREEN_ALERTS -> R.string.permission_full_screen
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> R.string.permission_battery
    AppPermission.BIOMETRIC -> R.string.permission_biometric
}

private fun AppPermission.reliabilityWhyRes(): Int = when (this) {
    AppPermission.NOTIFICATIONS -> R.string.reliability_why_notifications
    AppPermission.EXACT_ALARMS -> R.string.reliability_why_exact
    AppPermission.DISPLAY_OVER_OTHER_APPS -> R.string.reliability_why_overlay
    AppPermission.FULL_SCREEN_ALERTS -> R.string.reliability_why_full_screen
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> R.string.reliability_why_battery
    AppPermission.BIOMETRIC -> R.string.permission_biometric_description
}
