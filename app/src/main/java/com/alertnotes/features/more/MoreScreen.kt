package com.alertnotes.features.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.features.account.messageRes
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing

/**
 * The "More" hub: everything the five-item bottom bar can't hold — Calendar,
 * History, Settings, Reminder Diagnostics — plus Exit. Keeping these one tap
 * deeper is what lets the primary bar stay at five items and never wrap on a
 * phone.
 */
@Composable
fun MoreScreen(
    onOpenProfile: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenShareReminder: () -> Unit,
    onOpenSharedReminders: () -> Unit,
    onExit: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val showAccountActions by viewModel.showAccountActions
        .collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    var showLogOutDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            state = deletion,
            onConfirm = viewModel::deleteAccount,
            onDismiss = {
                showDeleteDialog = false
                viewModel.dismissDeletionError()
            },
        )
    }
    if (showLogOutDialog) {
        AlertDialog(
            onDismissRequest = { showLogOutDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(text = stringResource(R.string.more_logout_confirm_title)) },
            text = { Text(text = stringResource(R.string.more_logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logOut()
                        showLogOutDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.more_logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogOutDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_more),
                actions = {
                    com.alertnotes.core.ui.components.NotificationBellAction(
                        onOpen = onOpenNotifications,
                    )
                },
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
                    SectionCard(title = stringResource(R.string.more_section_app)) {
                        AppListItem(
                            title = stringResource(R.string.nav_profile),
                            supportingText = stringResource(R.string.more_profile_subtitle),
                            leadingIcon = Icons.Outlined.Person,
                            onClick = onOpenProfile,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.nav_calendar),
                            supportingText = stringResource(R.string.more_calendar_subtitle),
                            leadingIcon = Icons.Outlined.CalendarMonth,
                            onClick = onOpenCalendar,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.nav_history),
                            supportingText = stringResource(R.string.more_history_subtitle),
                            leadingIcon = Icons.Outlined.History,
                            onClick = onOpenHistory,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.templates_title),
                            supportingText = stringResource(R.string.templates_row_subtitle),
                            leadingIcon = Icons.Outlined.Bookmark,
                            onClick = onOpenTemplates,
                            trailingContent = { Chevron() },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.sharing_section_title)) {
                        AppListItem(
                            title = stringResource(R.string.nav_messages),
                            supportingText = stringResource(R.string.more_messages_subtitle),
                            leadingIcon = Icons.Outlined.ChatBubbleOutline,
                            onClick = onOpenMessages,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.inbox_title),
                            supportingText = stringResource(R.string.inbox_row_subtitle),
                            leadingIcon = Icons.Outlined.Inbox,
                            onClick = onOpenInbox,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.notifications_title),
                            supportingText = stringResource(R.string.notifications_row_subtitle),
                            leadingIcon = Icons.Outlined.NotificationsNone,
                            onClick = onOpenNotifications,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.sharing_share_row),
                            supportingText = stringResource(R.string.sharing_share_row_subtitle),
                            leadingIcon = Icons.Outlined.Share,
                            onClick = onOpenShareReminder,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.sharing_dashboard_row),
                            supportingText = stringResource(R.string.sharing_dashboard_row_subtitle),
                            leadingIcon = Icons.Outlined.CloudSync,
                            onClick = onOpenSharedReminders,
                            trailingContent = { Chevron() },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.more_section_system)) {
                        AppListItem(
                            title = stringResource(R.string.nav_settings),
                            supportingText = stringResource(R.string.more_settings_subtitle),
                            leadingIcon = Icons.Outlined.Settings,
                            onClick = onOpenSettings,
                            trailingContent = { Chevron() },
                        )
                        AppListItem(
                            title = stringResource(R.string.settings_reliability_row),
                            supportingText = stringResource(R.string.settings_reliability_row_subtitle),
                            leadingIcon = Icons.Outlined.Verified,
                            onClick = onOpenDiagnostics,
                            trailingContent = { Chevron() },
                        )
                    }
                }
                if (showAccountActions) {
                    item {
                        SectionCard(title = stringResource(R.string.more_section_account)) {
                            AppListItem(
                                title = stringResource(R.string.more_devices_title),
                                supportingText = stringResource(R.string.more_devices_subtitle),
                                leadingIcon = Icons.Outlined.PhoneAndroid,
                                onClick = onOpenDevices,
                                trailingContent = { Chevron() },
                            )
                            AppListItem(
                                title = stringResource(R.string.more_logout),
                                supportingText = stringResource(R.string.more_logout_subtitle),
                                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                                leadingIconTint = MaterialTheme.colorScheme.error,
                                onClick = { showLogOutDialog = true },
                            )
                            // Google Play requires an in-app route to account
                            // deletion for any app that offers sign-up.
                            AppListItem(
                                title = stringResource(R.string.account_delete_title),
                                supportingText = stringResource(R.string.account_delete_subtitle),
                                leadingIcon = Icons.Outlined.DeleteForever,
                                leadingIconTint = MaterialTheme.colorScheme.error,
                                onClick = { showDeleteDialog = true },
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.more_section_exit)) {
                        AppListItem(
                            title = stringResource(R.string.settings_exit_app),
                            supportingText = stringResource(R.string.settings_exit_app_subtitle),
                            leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                            leadingIconTint = MaterialTheme.colorScheme.error,
                            onClick = onExit,
                        )
                    }
                }
            }
        }
    }
}

/** Online-account state + sign-out; the More page itself stays stateless. */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val showAccountActions: StateFlow<Boolean> = combine(
        settingsRepository.preferences,
        authRepository.authState,
    ) { preferences, user ->
        preferences.appMode == AppMode.ONLINE && user != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _deletion = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    val deletion: StateFlow<AccountDeletionState> = _deletion.asStateFlow()

    fun logOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            // Back to the welcome chooser: Offline Mode or Online Mode.
            runCatching { settingsRepository.clearAppMode() }
        }
    }

    /**
     * Deletes the cloud account. Local reminders are untouched — the app
     * simply returns to the offline mode chooser, which is what a user
     * deleting their *account* (not their data) expects.
     */
    fun deleteAccount(password: String) {
        if (_deletion.value == AccountDeletionState.Working) return
        _deletion.value = AccountDeletionState.Working
        viewModelScope.launch {
            runCatching { authRepository.deleteAccount(password) }
                .onSuccess {
                    runCatching { settingsRepository.clearAppMode() }
                    _deletion.value = AccountDeletionState.Idle
                }
                .onFailure { throwable ->
                    val error = (throwable as? AuthException)?.error ?: AuthError.UNKNOWN
                    _deletion.value = AccountDeletionState.Failed(error)
                }
        }
    }

    fun dismissDeletionError() {
        _deletion.value = AccountDeletionState.Idle
    }
}

/** Progress of the irreversible account deletion. */
sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState
    data object Working : AccountDeletionState
    data class Failed(val error: AuthError) : AccountDeletionState
}

/**
 * Irreversible-action dialog for account deletion.
 *
 * Two deliberate guards: the consequences are spelled out in full, and the
 * password must be typed. The password is not theatre — Firebase rejects
 * deletion on a stale session, so re-authentication is required anyway; asking
 * for it here turns a technical requirement into the confirmation step.
 */
@Composable
private fun DeleteAccountDialog(
    state: AccountDeletionState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    val working = state == AccountDeletionState.Working
    AlertDialog(
        // A dismiss mid-delete would leave the user staring at a screen whose
        // account no longer exists.
        onDismissRequest = { if (!working) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = stringResource(R.string.account_delete_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Text(
                    text = stringResource(R.string.account_delete_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.auth_field_password),
                    isError = state is AccountDeletionState.Failed,
                    errorText = (state as? AccountDeletionState.Failed)
                        ?.let { stringResource(it.error.messageRes()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (working) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank() && !working,
            ) {
                Text(
                    text = stringResource(R.string.account_delete_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
