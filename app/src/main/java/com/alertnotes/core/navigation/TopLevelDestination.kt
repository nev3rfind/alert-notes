package com.alertnotes.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
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
    MESSAGES(
        route = MessagesRoute,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        labelRes = R.string.nav_messages,
    ),
    MORE(
        route = MoreRoute,
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz,
        labelRes = R.string.nav_more,
    ),
}
