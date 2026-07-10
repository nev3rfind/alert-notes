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
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alertnotes.R
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
    onOpenShareReminder: () -> Unit,
    onOpenSharedReminders: () -> Unit,
    onExit: () -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.nav_more)) },
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
                            title = stringResource(R.string.inbox_title),
                            supportingText = stringResource(R.string.inbox_row_subtitle),
                            leadingIcon = Icons.Outlined.Inbox,
                            onClick = onOpenInbox,
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

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
