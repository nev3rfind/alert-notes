package com.alertnotes.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.extensions.toDisplayString
import com.alertnotes.core.extensions.toRelativeTimeString
import com.alertnotes.core.extensions.weekDaysInLocaleOrder
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.CountdownText
import com.alertnotes.core.ui.components.ElapsedText
import com.alertnotes.core.ui.components.LiveClockText
import com.alertnotes.core.ui.components.PrimaryCard
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.UserPreferences
import com.alertnotes.features.account.ConnectionStatusCard
import com.alertnotes.features.alerts.AlertIconBadge
import com.alertnotes.features.friends.FriendAvatar
import com.alertnotes.features.sharing.StatusChip
import com.alertnotes.features.alerts.alertAccentColor
import com.alertnotes.features.alerts.staticBrush
import com.alertnotes.features.reminders.editor.labelRes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Cards never stretch past this width, so tablets get a tidy grid. */
private val DashboardCellMaxWidth = 420.dp
private val DashboardMaxWidth = 900.dp

@Composable
fun HomeScreen(
    onOpenReminders: () -> Unit,
    onOpenCalendar: () -> Unit,
    onCreateReminder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSharedReminders: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sharingPulse by viewModel.sharingPulse.collectAsStateWithLifecycle()
    val sharedByMe by viewModel.sharedByMe.collectAsStateWithLifecycle()
    val sharedWithMe by viewModel.sharedWithMe.collectAsStateWithLifecycle()
    val recentlyCompleted by viewModel.recentlyCompleted.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier
                    .widthIn(max = DashboardMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DashboardHeader()
                }
                // Shared Activity leads the dashboard — the online edition's
                // core feature is visible before anything else.
                if (!sharingPulse.isEmpty) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SharingPulseRow(
                            pulse = sharingPulse,
                            onOpenInbox = onOpenInbox,
                            onOpenMessages = onOpenMessages,
                            onOpenNotifications = onOpenNotifications,
                        )
                    }
                }
                if (sharedByMe.isNotEmpty()) {
                    item {
                        SharedRemindersCard(
                            titleRes = R.string.home_shared_by_me,
                            shares = sharedByMe,
                            onOpen = onOpenSharedReminders,
                        )
                    }
                }
                if (sharedWithMe.isNotEmpty()) {
                    item {
                        SharedRemindersCard(
                            titleRes = R.string.home_shared_with_me,
                            shares = sharedWithMe,
                            onOpen = onOpenSharedReminders,
                        )
                    }
                }
                if (recentlyCompleted.isNotEmpty()) {
                    item {
                        SharedRemindersCard(
                            titleRes = R.string.home_recently_completed,
                            shares = recentlyCompleted,
                            onOpen = onOpenSharedReminders,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ConnectionStatusCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSettings,
                    )
                }
                uiState.pausedUntil?.let { pausedUntil ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PausedBanner(pausedUntil = pausedUntil, onClick = onOpenSettings)
                    }
                }
                uiState.runningEntry?.let { running ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ActiveNowCard(entry = running, onOpenReminders = onOpenReminders)
                    }
                }
                uiState.nextReminder?.let { next ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NextReminderCard(
                            reminder = next,
                            isGloballyPaused = uiState.pausedUntil != null,
                            onClick = onOpenReminders,
                        )
                    }
                }
                if (uiState.queue.size > 1) {
                    item {
                        QueueCard(queue = uiState.queue)
                    }
                }
                item {
                    TodayScheduleCard(
                        schedule = uiState.schedule,
                        onOpenCalendar = onOpenCalendar,
                    )
                }
                item {
                    CalendarPreviewCard(
                        schedule = uiState.schedule,
                        onOpenCalendar = onOpenCalendar,
                    )
                }
                item {
                    StatisticsCard(uiState = uiState)
                }
                item {
                    UpcomingCard(
                        upcoming = uiState.upcoming,
                        onOpenReminders = onOpenReminders,
                        onCreateReminder = onCreateReminder,
                    )
                }
                item {
                    QuickActionsCard(
                        onCreateReminder = onCreateReminder,
                        onOpenFriends = onOpenFriends,
                        onOpenCalendar = onOpenCalendar,
                        onOpenBackup = onOpenBackup,
                    )
                }
                item {
                    RecentActivityCard(recent = uiState.recentActivity)
                }
            }
        }
    }
}

// region Sharing dashboard cards

/**
 * One glance at everything social: pending invitations, unread messages,
 * unread notifications — each tile a shortcut to its surface. Hidden
 * entirely when all counters are zero (and always in offline mode).
 */
@Composable
private fun SharingPulseRow(
    pulse: SharingPulse,
    onOpenInbox: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        PulseTile(
            count = pulse.pendingInvitations,
            labelRes = R.string.home_pulse_invitations,
            icon = Icons.Outlined.Inbox,
            onClick = onOpenInbox,
            modifier = Modifier.weight(1f),
        )
        PulseTile(
            count = pulse.unreadMessages,
            labelRes = R.string.home_pulse_messages,
            icon = Icons.Outlined.ChatBubbleOutline,
            onClick = onOpenMessages,
            modifier = Modifier.weight(1f),
        )
        PulseTile(
            count = pulse.unreadNotifications,
            labelRes = R.string.home_pulse_notifications,
            icon = Icons.Outlined.NotificationsNone,
            onClick = onOpenNotifications,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PulseTile(
    count: Int,
    labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = count > 0
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Live shared reminders (sent or received) with avatars and status. */
@Composable
private fun SharedRemindersCard(
    titleRes: Int,
    shares: List<com.alertnotes.domain.model.ReminderShareWithProfile>,
    onOpen: () -> Unit,
) {
    SectionCard(title = stringResource(titleRes)) {
        shares.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.small,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FriendAvatar(profile = item.profile, size = 32.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    Text(
                        text = item.share.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(item.profile.displayName, item.share.scheduleSummary)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(status = item.share.status)
            }
        }
        SecondaryButton(
            text = stringResource(R.string.home_shared_open_all),
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
        )
    }
}

// endregion

// region Header & hero cards

@Composable
private fun DashboardHeader() {
    val greetingRes = remember { greetingForHour(LocalTime.now().hour) }
    val today = remember {
        LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greetingRes),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = today,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Live clock: a leaf composable — only this text ticks each second.
        LiveClockText(
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun greetingForHour(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..17 -> R.string.home_greeting_afternoon
    else -> R.string.home_greeting_evening
}

/** Highlighted banner shown while a reminder's alert is running. */
@Composable
private fun ActiveNowCard(entry: QueuePanelItem, onOpenReminders: () -> Unit) {
    PrimaryCard(onClick = onOpenReminders, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlertIconBadge(priority = entry.priority, size = 40.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.home_active_now),
                    style = MaterialTheme.typography.labelMedium,
                    color = alertAccentColor(entry.priority),
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ElapsedText(
                since = entry.dueAt,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Banner shown while the global pause is active; countdowns pause with it. */
@Composable
private fun PausedBanner(pausedUntil: java.time.Instant, onClick: () -> Unit) {
    PrimaryCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = stringResource(R.string.home_paused_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = if (pausedUntil == UserPreferences.PAUSE_INDEFINITE) {
                    stringResource(R.string.settings_pause_indefinite)
                } else {
                    stringResource(
                        R.string.settings_pause_until,
                        pausedUntil.toDisplayDateTime(ZoneId.systemDefault()),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The dashboard hero: what fires next and exactly how long remains. */
@Composable
private fun NextReminderCard(
    reminder: Reminder,
    isGloballyPaused: Boolean,
    onClick: () -> Unit,
) {
    PrimaryCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_next_reminder).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            val nextTrigger = reminder.nextTriggerAt
            if (isGloballyPaused) {
                Text(
                    text = stringResource(R.string.home_paused_countdown),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (nextTrigger != null) {
                CountdownText(
                    target = nextTrigger,
                    fallback = stringResource(R.string.countdown_due_now),
                    style = MaterialTheme.typography.displaySmall,
                    color = alertAccentColor(reminder.priority),
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
            val meta = buildList {
                add(stringResource(reminder.priority.labelRes()))
                add(stringResource(reminder.displayMode.labelRes()))
                nextTrigger?.let { add(it.toDisplayDateTime(ZoneId.systemDefault())) }
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Display-queue order with live "waiting for" timers. */
@Composable
private fun QueueCard(queue: List<QueuePanelItem>) {
    SectionCard(
        title = stringResource(R.string.home_queue_title),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        queue.forEachIndexed { index, item ->
            if (index > 0) CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.medium,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ElapsedText(
                    since = item.dueAt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Today's remaining occurrences with times, plus a this-week footer. */
@Composable
private fun TodayScheduleCard(
    schedule: HomeSchedule,
    onOpenCalendar: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_today_schedule),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        if (schedule.today.isEmpty()) {
            CardMessage(message = stringResource(R.string.home_today_empty))
        } else {
            schedule.today.forEachIndexed { index, occurrence ->
                if (index > 0) CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.spacing.large,
                            vertical = MaterialTheme.spacing.medium,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                brush = occurrence.reminder.theme.staticBrush(),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = occurrence.time.toDisplayString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = MaterialTheme.spacing.medium)
                            .width(64.dp),
                    )
                    Text(
                        text = occurrence.reminder.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (schedule.weekCount > schedule.today.size) {
            CardDivider()
            AppListItem(
                title = stringResource(R.string.home_week_more, schedule.weekCount),
                onClick = onOpenCalendar,
            )
        }
    }
}

/** Miniature month: dots mark days with reminders; opens the Calendar tab. */
@Composable
private fun CalendarPreviewCard(
    schedule: HomeSchedule,
    onOpenCalendar: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_calendar_preview),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        PrimaryCard(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                val locale = remember { Locale.getDefault() }
                val orderedDays = remember(locale) { weekDaysInLocaleOrder(locale) }
                val firstOfMonth = schedule.month.atDay(1)
                val leadingBlanks = orderedDays.indexOf(firstOfMonth.dayOfWeek)
                val daysInMonth = schedule.month.lengthOfMonth()
                val today = remember { LocalDate.now() }
                Row(modifier = Modifier.fillMaxWidth()) {
                    orderedDays.forEach { day ->
                        Text(
                            text = day.getDisplayName(JavaTextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val rows = (leadingBlanks + daysInMonth + 6) / 7
                repeat(rows) { rowIndex ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { columnIndex ->
                            val dayOfMonth = rowIndex * 7 + columnIndex - leadingBlanks + 1
                            if (dayOfMonth in 1..daysInMonth) {
                                val date = schedule.month.atDay(dayOfMonth)
                                MiniDayCell(
                                    day = dayOfMonth,
                                    isToday = date == today,
                                    hasReminders = date in schedule.monthDaysWithReminders,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDayCell(
    day: Int,
    isToday: Boolean,
    hasReminders: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .then(
                    if (isToday) {
                        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Box(
            modifier = Modifier
                .size(3.dp)
                .background(
                    color = if (hasReminders) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape,
                ),
        )
    }
}

// endregion

// region Info panels

@Composable
private fun UpcomingCard(
    upcoming: List<Reminder>,
    onOpenReminders: () -> Unit,
    onCreateReminder: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_upcoming_title),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        if (upcoming.isEmpty()) {
            CardMessage(
                message = stringResource(R.string.home_upcoming_empty),
                actionText = stringResource(R.string.home_action_new_reminder),
                onAction = onCreateReminder,
            )
        } else {
            upcoming.forEachIndexed { index, reminder ->
                if (index > 0) CardDivider()
                AppListItem(
                    title = reminder.title,
                    supportingText = reminder.nextTriggerAt
                        ?.toDisplayDateTime(ZoneId.systemDefault()),
                    leadingIcon = Icons.Outlined.Notifications,
                    onClick = onOpenReminders,
                    trailingContent = {
                        val nextTrigger = reminder.nextTriggerAt
                        if (nextTrigger != null) {
                            CountdownText(
                                target = nextTrigger,
                                fallback = stringResource(R.string.countdown_due_now),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatisticsCard(uiState: HomeUiState) {
    SectionCard(
        title = stringResource(R.string.home_stats_title),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatTile(value = uiState.stats.total, label = stringResource(R.string.home_stat_total))
            StatTile(value = uiState.stats.enabled, label = stringResource(R.string.home_stat_active))
            StatTile(value = uiState.stats.disabled, label = stringResource(R.string.home_stat_paused))
            StatTile(value = uiState.stats.dueSoon, label = stringResource(R.string.home_stat_due_today))
            StatTile(value = uiState.queue.size, label = stringResource(R.string.home_stat_queued))
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuickActionsCard(
    onCreateReminder: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_quick_actions_title),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        QuickActionRow(
            icon = Icons.Filled.Add,
            text = stringResource(R.string.home_action_new_reminder),
            onClick = onCreateReminder,
        )
        CardDivider()
        QuickActionRow(
            icon = Icons.Outlined.Group,
            text = stringResource(R.string.home_action_find_friends),
            onClick = onOpenFriends,
        )
        CardDivider()
        QuickActionRow(
            icon = Icons.Outlined.Diversity3,
            text = stringResource(R.string.home_action_invite_family),
            onClick = onOpenFriends,
        )
        CardDivider()
        QuickActionRow(
            icon = Icons.Outlined.CalendarMonth,
            text = stringResource(R.string.home_calendar_preview),
            onClick = onOpenCalendar,
        )
        CardDivider()
        QuickActionRow(
            icon = Icons.Outlined.FolderZip,
            text = stringResource(R.string.home_action_backup),
            onClick = onOpenBackup,
        )
    }
}

@Composable
private fun QuickActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    AppListItem(
        title = text,
        leadingIcon = icon,
        onClick = onClick,
    )
}

@Composable
private fun RecentActivityCard(recent: List<Reminder>) {
    SectionCard(
        title = stringResource(R.string.home_recent_title),
        modifier = Modifier.widthIn(max = DashboardCellMaxWidth),
    ) {
        if (recent.isEmpty()) {
            CardMessage(message = stringResource(R.string.home_recent_empty))
        } else {
            recent.forEachIndexed { index, reminder ->
                if (index > 0) CardDivider()
                AppListItem(
                    title = reminder.title,
                    supportingText = reminder.updatedAt.toRelativeTimeString(),
                    leadingIcon = Icons.Outlined.History,
                    leadingIconTint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

// endregion

// region Small shared pieces

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** Short inline message (with optional action) shown inside dashboard cards. */
@Composable
private fun CardMessage(
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            SecondaryButton(text = actionText, onClick = onAction)
        }
    }
}

// endregion
