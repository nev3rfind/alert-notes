package com.alertnotes.domain.model

import java.time.Instant

/**
 * All persisted user settings. Defaults here are the app's out-of-box
 * experience; the data layer falls back to them for unset keys.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You wallpaper colors instead of the brand palette (Android 12+). */
    val useDynamicColor: Boolean = false,
    /** Master preference for reminder alerts; consumed by the scheduling phase. */
    val remindersNotificationsEnabled: Boolean = true,
    /** Let a critical reminder replace a lower-priority alert already on screen. */
    val criticalInterruptsEnabled: Boolean = true,
    /** Require biometric unlock to open the app. */
    val biometricLockEnabled: Boolean = false,
    /** True once the first-run welcome and permission flow has been finished or skipped. */
    val onboardingCompleted: Boolean = false,
    /**
     * Global pause: no reminder triggers before this instant. Null = not
     * paused; [PAUSE_INDEFINITE] = paused until manually resumed.
     */
    val pausedUntil: Instant? = null,
) {
    fun isPaused(now: Instant): Boolean = pausedUntil != null && now < pausedUntil

    companion object {
        /** Sentinel for "paused until manually resumed". */
        val PAUSE_INDEFINITE: Instant = Instant.ofEpochMilli(Long.MAX_VALUE)
    }
}
