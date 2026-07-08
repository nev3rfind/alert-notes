package com.alertnotes.core.permissions

/**
 * Every capability the app will ever need to ask the user for. Alert-style
 * reminders rely on these in later phases; this phase only reports their
 * state, never requests them.
 */
enum class AppPermission {
    /** Post reminder notifications (runtime permission on Android 13+). */
    NOTIFICATIONS,

    /** Schedule exact alarms so reminders fire on time (Android 12+). */
    EXACT_ALARMS,

    /** Draw attention-grabbing reminders over other apps. */
    DISPLAY_OVER_OTHER_APPS,

    /** Take over the screen for due reminders (Android 14+ user setting). */
    FULL_SCREEN_ALERTS,

    /** Exempt the app from battery optimization so alarms are not deferred. */
    IGNORE_BATTERY_OPTIMIZATIONS,

    /** Unlock the app with fingerprint or face. */
    BIOMETRIC,
}
