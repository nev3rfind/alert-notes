package com.alertnotes.data.repository

import com.alertnotes.data.datastore.UserPreferencesDataSource
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import com.alertnotes.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : SettingsRepository {

    override val preferences: Flow<UserPreferences> = dataSource.preferences

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataSource.setThemeMode(themeMode)
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        dataSource.setUseDynamicColor(enabled)
    }

    override suspend fun setRemindersNotificationsEnabled(enabled: Boolean) {
        dataSource.setRemindersNotificationsEnabled(enabled)
    }

    override suspend fun setCriticalInterruptsEnabled(enabled: Boolean) {
        dataSource.setCriticalInterruptsEnabled(enabled)
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataSource.setBiometricLockEnabled(enabled)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataSource.setOnboardingCompleted(completed)
    }

    override suspend fun setAppMode(mode: AppMode) {
        dataSource.setAppMode(mode)
    }

    override suspend fun setPausedUntil(until: java.time.Instant?) {
        dataSource.setPausedUntil(until)
    }
}
