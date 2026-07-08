package com.alertnotes.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Thin, typed facade over Preferences DataStore. Owns the key definitions and
 * the mapping to [UserPreferences]; unreadable data degrades to defaults
 * instead of crashing.
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toUserPreferences() }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setRemindersNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDERS_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setCriticalInterruptsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CRITICAL_INTERRUPTS_ENABLED] = enabled }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setPausedUntil(until: Instant?) {
        dataStore.edit { preferences ->
            if (until == null) {
                preferences.remove(Keys.PAUSED_UNTIL)
            } else {
                preferences[Keys.PAUSED_UNTIL] = until.toEpochMilli()
            }
        }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themeMode = this[Keys.THEME_MODE]?.toThemeMode() ?: defaults.themeMode,
            useDynamicColor = this[Keys.USE_DYNAMIC_COLOR] ?: defaults.useDynamicColor,
            remindersNotificationsEnabled = this[Keys.REMINDERS_NOTIFICATIONS_ENABLED]
                ?: defaults.remindersNotificationsEnabled,
            criticalInterruptsEnabled = this[Keys.CRITICAL_INTERRUPTS_ENABLED]
                ?: defaults.criticalInterruptsEnabled,
            biometricLockEnabled = this[Keys.BIOMETRIC_LOCK_ENABLED]
                ?: defaults.biometricLockEnabled,
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED]
                ?: defaults.onboardingCompleted,
            pausedUntil = this[Keys.PAUSED_UNTIL]?.let(Instant::ofEpochMilli),
        )
    }

    private fun String.toThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val REMINDERS_NOTIFICATIONS_ENABLED = booleanPreferencesKey("reminders_notifications_enabled")
        val CRITICAL_INTERRUPTS_ENABLED = booleanPreferencesKey("critical_interrupts_enabled")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PAUSED_UNTIL = longPreferencesKey("paused_until_epoch_millis")
    }
}
