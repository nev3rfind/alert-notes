package com.alertnotes.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.alertnotes.domain.model.Reminder
import com.alertnotes.features.about.AboutScreen
import com.alertnotes.features.backup.BackupScreen
import com.alertnotes.features.calendar.CalendarScreen
import com.alertnotes.features.history.HistoryScreen
import com.alertnotes.features.home.HomeScreen
import com.alertnotes.features.more.MoreScreen
import com.alertnotes.features.profile.ProfileScreen
import com.alertnotes.features.reminders.RemindersScreen
import com.alertnotes.features.reminders.editor.ReminderEditorScreen
import com.alertnotes.features.friends.FamilyScreen
import com.alertnotes.features.friends.FriendsScreen
import com.alertnotes.features.friends.PublicProfileScreen
import com.alertnotes.features.settings.ReliabilityScreen
import com.alertnotes.features.settings.SettingsScreen
import com.alertnotes.features.chat.ChatScreen
import com.alertnotes.features.chat.InboxScreen
import com.alertnotes.features.chat.MessagesScreen
import com.alertnotes.features.sharing.ShareReminderScreen
import com.alertnotes.features.sharing.SharedRemindersScreen

private const val TRANSITION_MILLIS = 260

/**
 * Central navigation graph. Screens receive navigation lambdas instead of the
 * NavController so they stay previewable and testable.
 */
@Composable
fun AlertNotesNavHost(
    navController: NavHostController,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(TRANSITION_MILLIS)) +
                slideInHorizontally(tween(TRANSITION_MILLIS)) { it / 16 }
        },
        exitTransition = { fadeOut(tween(TRANSITION_MILLIS / 2)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
        popExitTransition = {
            fadeOut(tween(TRANSITION_MILLIS)) +
                slideOutHorizontally(tween(TRANSITION_MILLIS)) { it / 16 }
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenReminders = { navController.navigateToTopLevel(RemindersRoute) },
                onOpenCalendar = { navController.navigate(CalendarRoute) },
                onCreateReminder = { navController.navigate(ReminderEditorRoute(Reminder.NEW_ID)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenBackup = { navController.navigate(BackupRoute) },
                onOpenFriends = { navController.navigateToTopLevel(FriendsRoute) },
            )
        }
        composable<CalendarRoute> {
            CalendarScreen(
                onOpenEditor = { reminderId ->
                    navController.navigate(ReminderEditorRoute(reminderId))
                },
                onCreateOn = { date ->
                    navController.navigate(
                        ReminderEditorRoute(Reminder.NEW_ID, initialEpochDay = date.toEpochDay()),
                    )
                },
            )
        }
        composable<RemindersRoute> {
            RemindersScreen(
                onOpenEditor = { reminderId ->
                    navController.navigate(ReminderEditorRoute(reminderId))
                },
            )
        }
        composable<ReminderEditorRoute>(
            // Modal feel: the editor slides up over the list and back down.
            enterTransition = {
                fadeIn(tween(TRANSITION_MILLIS)) +
                    slideInVertically(tween(TRANSITION_MILLIS)) { it / 12 }
            },
            popExitTransition = {
                fadeOut(tween(TRANSITION_MILLIS)) +
                    slideOutVertically(tween(TRANSITION_MILLIS)) { it / 12 }
            },
        ) { entry ->
            val route = entry.toRoute<ReminderEditorRoute>()
            ReminderEditorScreen(
                reminderId = route.reminderId,
                initialEpochDay = route.initialEpochDay,
                onClose = navController::navigateUp,
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<MoreRoute> {
            MoreScreen(
                onOpenProfile = { navController.navigate(ProfileRoute) },
                onOpenCalendar = { navController.navigate(CalendarRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDiagnostics = { navController.navigate(ReliabilityRoute) },
                onOpenInbox = { navController.navigate(InboxRoute) },
                onOpenShareReminder = { navController.navigate(ShareReminderRoute) },
                onOpenSharedReminders = { navController.navigate(SharedRemindersRoute) },
                onExit = onExit,
            )
        }
        composable<FriendsRoute> {
            FriendsScreen(
                onOpenUser = { uid -> navController.navigate(PublicProfileRoute(uid)) },
                onOpenFamily = { navController.navigate(FamilyRoute) },
                onOpenChat = { uid -> navController.navigate(ChatRoute(uid)) },
            )
        }
        composable<FamilyRoute> {
            FamilyScreen(
                onOpenUser = { uid -> navController.navigate(PublicProfileRoute(uid)) },
                onNavigateBack = navController::navigateUp,
                onOpenChat = { uid -> navController.navigate(ChatRoute(uid)) },
            )
        }
        composable<PublicProfileRoute> {
            PublicProfileScreen(
                onNavigateBack = navController::navigateUp,
                onOpenChat = { uid -> navController.navigate(ChatRoute(uid)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onOpenBackup = { navController.navigate(BackupRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenReliability = { navController.navigate(ReliabilityRoute) },
            )
        }
        composable<ReliabilityRoute> {
            ReliabilityScreen(onNavigateBack = navController::navigateUp)
        }
        composable<ShareReminderRoute> {
            ShareReminderScreen(onNavigateBack = navController::navigateUp)
        }
        composable<SharedRemindersRoute> {
            SharedRemindersScreen(onNavigateBack = navController::navigateUp)
        }
        composable<InboxRoute> {
            InboxScreen(
                onOpenSharedReminders = { navController.navigate(SharedRemindersRoute) },
                onOpenFriends = { navController.navigateToTopLevel(FriendsRoute) },
                onOpenMessages = { navController.navigateToTopLevel(MessagesRoute) },
            )
        }
        composable<MessagesRoute> {
            MessagesScreen(
                onOpenChat = { uid -> navController.navigate(ChatRoute(uid)) },
                onOpenUser = { uid -> navController.navigate(PublicProfileRoute(uid)) },
                onOpenFriends = { navController.navigateToTopLevel(FriendsRoute) },
            )
        }
        composable<ChatRoute> {
            ChatScreen(
                onNavigateBack = navController::navigateUp,
                onOpenTracking = { navController.navigate(SharedRemindersRoute) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onNavigateBack = navController::navigateUp)
        }
        composable<BackupRoute> {
            BackupScreen(onNavigateBack = navController::navigateUp)
        }
        composable<AboutRoute> {
            AboutScreen(onNavigateBack = navController::navigateUp)
        }
    }
}

/**
 * Navigates between top-level tabs. A tab tap always lands on that tab's
 * ROOT screen: state is deliberately not saved/restored, because restoring
 * would resurrect sub-screens (e.g. tapping Settings while its saved stack
 * ends in Activity history reopened the sub-screen instead of Settings).
 */
fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
