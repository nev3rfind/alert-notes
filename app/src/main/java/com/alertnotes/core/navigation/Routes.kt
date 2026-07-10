package com.alertnotes.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Adding a screen means adding a route here and
 * a `composable<Route>` entry in [AlertNotesNavHost] — nothing else.
 */
@Serializable
data object HomeRoute

@Serializable
data object CalendarRoute

@Serializable
data object RemindersRoute

@Serializable
data object ProfileRoute

@Serializable
data object FriendsRoute

@Serializable
data object FamilyRoute

@Serializable
data object ShareReminderRoute

@Serializable
data object SharedRemindersRoute

/** Another user's public profile. */
@Serializable
data class PublicProfileRoute(val uid: String)

@Serializable
data object MoreRoute

@Serializable
data object SettingsRoute

@Serializable
data object BackupRoute

@Serializable
data object AboutRoute

@Serializable
data object HistoryRoute

@Serializable
data object ReliabilityRoute

/**
 * Full-screen reminder editor; [reminderId] 0 (= Reminder.NEW_ID) creates.
 * [initialEpochDay] pre-fills a new reminder's date (-1 = none) — used by
 * calendar long-press.
 */
@Serializable
data class ReminderEditorRoute(
    val reminderId: Long,
    val initialEpochDay: Long = -1,
)
