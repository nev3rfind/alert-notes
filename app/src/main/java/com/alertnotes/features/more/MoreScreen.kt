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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
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
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTopBar
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
    onOpenDevices: () -> Unit,
    onOpenShareReminder: () -> Unit,
    onOpenSharedReminders: () -> Unit,
    onExit: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val showAccountActions by viewModel.showAccountActions
        .collectAsStateWithLifecycle()
    var showLogOutDialog by rememberSaveable { mutableStateOf(false) }
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
    settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val showAccountActions: StateFlow<Boolean> = combine(
        settingsRepository.preferences,
        authRepository.authState,
    ) { preferences, user ->
        preferences.appMode == AppMode.ONLINE && user != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun logOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
