package com.alertnotes.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.alertnotes.R

/**
 * The five primary destinations shown in the bottom bar (phones) or
 * navigation rail (tablets), in display order. Deliberately capped at five
 * so phone labels never wrap; everything else lives behind [MORE] (Calendar,
 * History, Settings, Reminder Diagnostics, Exit) and is pushed on top.
 */
enum class TopLevelDestination(
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    HOME(
        route = HomeRoute,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        labelRes = R.string.nav_home,
    ),
    REMINDERS(
        route = RemindersRoute,
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
        labelRes = R.string.nav_reminders,
    ),
    FRIENDS(
        route = FriendsRoute,
        selectedIcon = Icons.Filled.Group,
        unselectedIcon = Icons.Outlined.Group,
        labelRes = R.string.nav_friends,
    ),
    SHARE(
        route = ShareReminderRoute(),
        selectedIcon = Icons.Filled.Share,
        unselectedIcon = Icons.Outlined.Share,
        labelRes = R.string.nav_share,
    ),
    MORE(
        route = MoreRoute,
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz,
        labelRes = R.string.nav_more,
    ),
}

/**
 * The expanded destination set for the left navigation rail on tablets and
 * wide windows, in display order. Wide screens have the vertical room for
 * every primary surface one tap away; phones keep the five-item bottom bar
 * with the rest behind More. More itself stays on the rail so the sharing
 * hub (Share a reminder / Shared Reminders) remains reachable everywhere.
 */
enum class RailDestination(
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    HOME(
        route = HomeRoute,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        labelRes = R.string.nav_home,
    ),
    REMINDERS(
        route = RemindersRoute,
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
        labelRes = R.string.nav_reminders,
    ),
    MESSAGES(
        route = MessagesRoute,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        labelRes = R.string.nav_messages,
    ),
    FRIENDS(
        route = FriendsRoute,
        selectedIcon = Icons.Filled.Group,
        unselectedIcon = Icons.Outlined.Group,
        labelRes = R.string.nav_friends,
    ),
    CALENDAR(
        route = CalendarRoute,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        labelRes = R.string.nav_calendar,
    ),
    PROFILE(
        route = ProfileRoute,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        labelRes = R.string.nav_profile,
    ),
    SETTINGS(
        route = SettingsRoute,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        labelRes = R.string.nav_settings,
    ),
    HISTORY(
        route = HistoryRoute,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        labelRes = R.string.nav_history,
    ),
    INBOX(
        route = InboxRoute,
        selectedIcon = Icons.Filled.Inbox,
        unselectedIcon = Icons.Outlined.Inbox,
        labelRes = R.string.inbox_title,
    ),
    MORE(
        route = MoreRoute,
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz,
        labelRes = R.string.nav_more,
    ),
}
