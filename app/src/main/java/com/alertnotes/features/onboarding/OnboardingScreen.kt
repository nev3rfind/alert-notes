package com.alertnotes.features.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.PrimaryCard
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.features.alerts.AlertIconBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    private val _permissionStates = MutableStateFlow(permissionsManager.allStatuses())
    val permissionStates: StateFlow<List<PermissionState>> = _permissionStates.asStateFlow()

    fun refreshPermissions() {
        _permissionStates.value = permissionsManager.allStatuses()
    }

    fun openPermissionSettings(context: Context, permission: AppPermission) {
        val intent = permissionsManager.settingsIntent(permission) ?: return
        runCatching { context.startActivity(intent) }
    }

    /** Finishing and skipping both complete onboarding — it never nags twice. */
    fun complete() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }
}

/**
 * First-run experience: a welcome page, then a guided permission page with
 * live statuses and why-it-helps explanations. Entirely skippable — the app
 * works without any grants, just less visibly. Progress through system
 * settings round-trips is picked up on resume.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableStateOf(0) }
    val permissionStates by viewModel.permissionStates.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = fadeIn() + slideInHorizontally { if (forward) it / 3 else -it / 3 }
                    val exit = fadeOut() + slideOutHorizontally { if (forward) -it / 3 else it / 3 }
                    enter togetherWith exit
                },
                label = "onboardingPage",
                modifier = Modifier.widthIn(max = 560.dp),
            ) { target ->
                when (target) {
                    0 -> WelcomePage(
                        onContinue = { page = 1 },
                        onSkip = viewModel::complete,
                    )

                    else -> PermissionsPage(
                        states = permissionStates,
                        onGrant = viewModel::openPermissionSettings,
                        onRefresh = viewModel::refreshPermissions,
                        onDone = viewModel::complete,
                        onBack = { page = 0 },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlertIconBadge(priority = ReminderPriority.NORMAL, size = 72.dp)
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.large),
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        FeatureLine(Icons.Outlined.Alarm, R.string.onboarding_feature_alarms)
        FeatureLine(Icons.Outlined.Fullscreen, R.string.onboarding_feature_alerts)
        FeatureLine(Icons.Outlined.Verified, R.string.onboarding_feature_privacy)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        PrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onSkip,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        ) {
            Text(text = stringResource(R.string.onboarding_skip))
        }
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, textRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
        )
    }
}

@Composable
private fun PermissionsPage(
    states: List<PermissionState>,
    onGrant: (Context, AppPermission) -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_permissions_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        // Biometrics need no setup step; everything else gets a card.
        states
            .filter { it.permission != AppPermission.BIOMETRIC }
            .forEach { state ->
                PermissionCard(
                    state = state,
                    onGrant = {
                        if (state.permission == AppPermission.NOTIFICATIONS &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onGrant(context, state.permission)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        PrimaryButton(
            text = stringResource(R.string.onboarding_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.padding(top = MaterialTheme.spacing.small)) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.onboarding_back))
            }
            TextButton(onClick = onDone) {
                Text(text = stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    state: PermissionState,
    onGrant: () -> Unit,
) {
    val granted = state.status == PermissionStatus.GRANTED
    val unavailable = state.status == PermissionStatus.UNAVAILABLE ||
        state.status == PermissionStatus.NOT_REQUIRED
    PrimaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = state.permission.onboardingIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(state.permission.onboardingTitleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MaterialTheme.spacing.medium),
                )
                Text(
                    text = stringResource(
                        when {
                            granted -> R.string.permission_status_granted
                            unavailable -> R.string.permission_status_not_required
                            else -> R.string.permission_status_missing
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        granted -> MaterialTheme.colorScheme.primary
                        unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )
            }
            Text(
                text = stringResource(state.permission.onboardingWhyRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            )
            if (!granted && !unavailable) {
                SecondaryButton(
                    text = stringResource(R.string.onboarding_grant),
                    onClick = onGrant,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                )
            }
        }
    }
}

private fun AppPermission.onboardingIcon(): ImageVector = when (this) {
    AppPermission.NOTIFICATIONS -> Icons.Outlined.NotificationsActive
    AppPermission.EXACT_ALARMS -> Icons.Outlined.Alarm
    AppPermission.DISPLAY_OVER_OTHER_APPS -> Icons.Outlined.Layers
    AppPermission.FULL_SCREEN_ALERTS -> Icons.Outlined.Fullscreen
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> Icons.Outlined.BatteryAlert
    AppPermission.BIOMETRIC -> Icons.Outlined.Verified
}

private fun AppPermission.onboardingTitleRes(): Int = when (this) {
    AppPermission.NOTIFICATIONS -> R.string.permission_notifications
    AppPermission.EXACT_ALARMS -> R.string.permission_exact_alarms
    AppPermission.DISPLAY_OVER_OTHER_APPS -> R.string.permission_overlay
    AppPermission.FULL_SCREEN_ALERTS -> R.string.permission_full_screen
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> R.string.permission_battery
    AppPermission.BIOMETRIC -> R.string.permission_biometric
}

private fun AppPermission.onboardingWhyRes(): Int = when (this) {
    AppPermission.NOTIFICATIONS -> R.string.permission_notifications_description
    AppPermission.EXACT_ALARMS -> R.string.permission_exact_alarms_description
    AppPermission.DISPLAY_OVER_OTHER_APPS -> R.string.permission_overlay_description
    AppPermission.FULL_SCREEN_ALERTS -> R.string.permission_full_screen_description
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> R.string.permission_battery_description
    AppPermission.BIOMETRIC -> R.string.permission_biometric_description
}
