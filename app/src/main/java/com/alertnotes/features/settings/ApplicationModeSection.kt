package com.alertnotes.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppDialog
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.ConfirmationDialog
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.AuthUser
import com.alertnotes.domain.repository.CloudUploadResult
import com.alertnotes.features.account.AccountViewModel
import com.alertnotes.features.account.AuthFlow

/**
 * "Application Mode" settings section: shows the current mode and switches
 * between them at any time. Offline → online runs the full auth flow, then
 * offers a one-time upload of local reminders. Online → offline only signs
 * out — every reminder stays on the device, nothing is deleted anywhere.
 */
@Composable
internal fun ApplicationModeSection(
    mode: AppMode?,
    authUser: AuthUser?,
    viewModel: SettingsViewModel,
) {
    var showAuthFlow by rememberSaveable { mutableStateOf(false) }
    var showOfflineConfirm by rememberSaveable { mutableStateOf(false) }
    val uploadUiState by viewModel.uploadUiState.collectAsStateWithLifecycle()

    SectionCard(title = stringResource(R.string.settings_section_mode)) {
        if (mode == AppMode.ONLINE) {
            AppListItem(
                title = stringResource(R.string.settings_mode_online),
                supportingText = when {
                    authUser == null -> stringResource(R.string.settings_mode_not_signed_in)
                    authUser.displayName.isNotBlank() -> stringResource(
                        R.string.settings_mode_signed_in_as,
                        authUser.displayName,
                    )

                    else -> stringResource(
                        R.string.settings_mode_signed_in_as,
                        authUser.email,
                    )
                },
                leadingIcon = Icons.Outlined.Cloud,
            )
            if (authUser == null) {
                // Mode says online but the session is gone (revoked or
                // deleted account) — offer to restore it.
                AppListItem(
                    title = stringResource(R.string.settings_mode_sign_in),
                    supportingText = stringResource(R.string.settings_mode_sign_in_subtitle),
                    leadingIcon = Icons.AutoMirrored.Outlined.Login,
                    onClick = { showAuthFlow = true },
                )
            }
            if (authUser != null) {
                // Always-available retry of the one-time upload, so a failed
                // or skipped prompt is never a dead end.
                AppListItem(
                    title = stringResource(R.string.settings_mode_upload_row),
                    supportingText = stringResource(R.string.settings_mode_upload_row_subtitle),
                    leadingIcon = Icons.Outlined.CloudUpload,
                    onClick = viewModel::promptCloudUpload,
                )
            }
            AppListItem(
                title = stringResource(R.string.settings_mode_switch_offline),
                supportingText = stringResource(R.string.settings_mode_switch_offline_subtitle),
                leadingIcon = Icons.Outlined.CloudOff,
                onClick = { showOfflineConfirm = true },
            )
        } else {
            AppListItem(
                title = stringResource(R.string.settings_mode_offline),
                supportingText = stringResource(R.string.settings_mode_offline_subtitle),
                leadingIcon = Icons.Outlined.Smartphone,
            )
            AppListItem(
                title = stringResource(R.string.settings_mode_switch_online),
                supportingText = stringResource(R.string.settings_mode_switch_online_subtitle),
                leadingIcon = Icons.Outlined.Cloud,
                onClick = { showAuthFlow = true },
            )
        }
    }

    if (showAuthFlow) {
        ModeSwitchAuthDialog(
            onDismiss = { showAuthFlow = false },
            onAuthenticated = {
                showAuthFlow = false
                viewModel.promptCloudUpload()
            },
        )
    }
    if (showOfflineConfirm) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_mode_offline_confirm_title),
            message = stringResource(R.string.settings_mode_offline_confirm_message),
            confirmText = stringResource(R.string.settings_mode_offline_confirm_button),
            onConfirm = {
                showOfflineConfirm = false
                viewModel.switchToOfflineMode()
            },
            onDismiss = { showOfflineConfirm = false },
        )
    }
    CloudUploadDialog(
        state = uploadUiState,
        onUpload = viewModel::uploadLocalReminders,
        onDismiss = viewModel::dismissUploadDialog,
    )
}

/**
 * Full-screen host for the auth flow, launched from settings. Back
 * navigation is owned by the flow itself (welcome → exit), so the dialog
 * never dismisses out from under an in-flight submission.
 */
@Composable
private fun ModeSwitchAuthDialog(
    onDismiss: () -> Unit,
    onAuthenticated: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val accountViewModel: AccountViewModel = hiltViewModel()
        val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(accountState.isAuthCompleted) {
            if (accountState.isAuthCompleted) {
                accountViewModel.acknowledgeCompletion()
                onAuthenticated()
            }
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
                AuthFlow(
                    viewModel = accountViewModel,
                    onExit = onDismiss,
                )
            }
        }
    }
}

/** Prompt → progress → result, all within one dialog. */
@Composable
private fun CloudUploadDialog(
    state: CloudUploadUiState,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        CloudUploadUiState.Hidden -> Unit

        CloudUploadUiState.Prompt -> AppDialog(
            title = stringResource(R.string.cloud_upload_title),
            onDismiss = onDismiss,
            confirmText = stringResource(R.string.cloud_upload_confirm),
            onConfirm = onUpload,
            dismissText = stringResource(R.string.cloud_upload_skip),
        ) {
            Text(
                text = stringResource(R.string.cloud_upload_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        CloudUploadUiState.Uploading -> AppDialog(
            title = stringResource(R.string.cloud_upload_title),
            onDismiss = {},
            confirmText = stringResource(R.string.cloud_upload_confirm),
            onConfirm = {},
            confirmEnabled = false,
            dismissText = stringResource(R.string.cloud_upload_skip),
        ) {
            Text(
                text = stringResource(R.string.cloud_upload_in_progress),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is CloudUploadUiState.Finished -> {
            val result = state.result
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = MaterialTheme.shapes.extraLarge,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = if (result is CloudUploadResult.Success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
                title = {
                    Text(
                        text = stringResource(
                            if (result is CloudUploadResult.Success) {
                                R.string.cloud_upload_done_title
                            } else {
                                R.string.cloud_upload_failed_title
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Text(
                        text = if (result is CloudUploadResult.Success) {
                            pluralStringResource(
                                R.plurals.cloud_upload_done_message,
                                result.reminderCount,
                                result.reminderCount,
                            )
                        } else {
                            stringResource(R.string.cloud_upload_failed_message)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_done))
                    }
                },
            )
        }
    }
}
