package com.alertnotes.core.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.consumeWindowInsets
import com.alertnotes.domain.model.Reminder
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alertnotes.core.ui.WindowWidthClass
import com.alertnotes.core.ui.components.AppBottomNavigationBar
import com.alertnotes.core.ui.components.AppNavigationRail
import com.alertnotes.core.ui.rememberWindowWidthClass

/**
 * Root shell: places the navigation chrome adaptively — a bottom bar on
 * phones, a navigation rail on tablets and wide windows — around the nav host.
 */
@Composable
fun AlertNotesApp(createReminderRequestId: Int = 0) {
    val windowWidthClass = rememberWindowWidthClass()
    val navController = rememberNavController()

    // Widget quick-create: jump into the editor exactly once per request.
    // The id increments on every widget tap (including via onNewIntent while
    // the app is already running); the consumed marker survives rotation and
    // process death so a stale intent never re-opens the editor.
    var consumedEditorRequestId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(createReminderRequestId) {
        if (createReminderRequestId > consumedEditorRequestId) {
            consumedEditorRequestId = createReminderRequestId
            navController.navigate(ReminderEditorRoute(Reminder.NEW_ID))
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevelScreen = TopLevelDestination.entries.any { destination ->
        currentDestination.isDestinationSelected(destination)
    }
    val useNavigationRail = windowWidthClass != WindowWidthClass.Compact
    // Exit closes only the UI — AlarmManager schedules and reminder data are
    // untouched. It lives in the More menu now, not on the bar itself.
    val exitActivity = LocalActivity.current
    val onExit: () -> Unit = { exitActivity?.finishAndRemoveTask() }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useNavigationRail) {
            AppNavigationRail(
                destinations = TopLevelDestination.entries,
                isSelected = { currentDestination.isDestinationSelected(it) },
                onNavigate = { navController.navigateToTopLevel(it.route) },
            )
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useNavigationRail) {
                    AnimatedVisibility(
                        visible = isTopLevelScreen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        AppBottomNavigationBar(
                            destinations = TopLevelDestination.entries,
                            isSelected = { currentDestination.isDestinationSelected(it) },
                            onNavigate = { navController.navigateToTopLevel(it.route) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            AlertNotesNavHost(
                navController = navController,
                onExit = onExit,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

private fun NavDestination?.isDestinationSelected(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
