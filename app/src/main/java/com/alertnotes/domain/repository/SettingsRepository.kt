package com.alertnotes.domain.repository

import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user settings. Backed by Preferences DataStore in the data
 * layer; every write is applied and observed reactively.
 */
interface SettingsRepository {

    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setUseDynamicColor(enabled: Boolean)

    suspend fun setRemindersNotificationsEnabled(enabled: Boolean)

    suspend fun setCriticalInterruptsEnabled(enabled: Boolean)

    suspend fun setBiometricLockEnabled(enabled: Boolean)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setAppMode(mode: AppMode)

    /** Null resumes; [UserPreferences.PAUSE_INDEFINITE] pauses until resumed. */
    suspend fun setPausedUntil(until: java.time.Instant?)
}
