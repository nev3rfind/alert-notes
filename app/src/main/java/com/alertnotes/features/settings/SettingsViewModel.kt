package com.alertnotes.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.core.permissions.PermissionState
import com.alertnotes.core.permissions.PermissionStatus
import com.alertnotes.core.permissions.AppPermission
import com.alertnotes.core.permissions.PermissionsManager
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.AuthUser
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.CloudBackupRepository
import com.alertnotes.domain.repository.CloudUploadResult
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Progress of the optional local-to-cloud reminder upload dialog. */
sealed interface CloudUploadUiState {
    data object Hidden : CloudUploadUiState

    /** "Would you like to upload your local reminders?" — Upload / Skip. */
    data object Prompt : CloudUploadUiState
    data object Uploading : CloudUploadUiState
    data class Finished(val result: CloudUploadResult) : CloudUploadUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val permissionsManager: PermissionsManager,
    private val coordinator: ReminderSchedulingCoordinator,
    private val timeProvider: TimeProvider,
    private val authRepository: AuthRepository,
    private val cloudBackupRepository: CloudBackupRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences(),
        )

    private val _permissionStates = MutableStateFlow(permissionsManager.allStatuses())
    val permissionStates: StateFlow<List<PermissionState>> = _permissionStates.asStateFlow()

    /** Biometric toggle is offered only when the device can ever support it. */
    val isBiometricAvailable: Boolean
        get() = permissionsManager.statusOf(AppPermission.BIOMETRIC) != PermissionStatus.UNAVAILABLE

    /** Called on resume so statuses reflect changes made in system settings. */
    fun refreshPermissions() {
        _permissionStates.value = permissionsManager.allStatuses()
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(themeMode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun setRemindersNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRemindersNotificationsEnabled(enabled) }
    }

    fun setCriticalInterruptsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCriticalInterruptsEnabled(enabled) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
    }

    /** Opens the exact system-settings screen for a capability. */
    fun openPermissionSettings(context: Context, permission: AppPermission) {
        val intent = permissionsManager.settingsIntent(permission) ?: return
        runCatching { context.startActivity(intent) }
    }

    // region Global reminder controls

    fun pauseFor(duration: Duration) {
        setPause(timeProvider.now().plus(duration))
    }

    fun pauseUntil(until: Instant) {
        setPause(until)
    }

    fun pauseIndefinitely() {
        setPause(UserPreferences.PAUSE_INDEFINITE)
    }

    fun resumeReminders() {
        setPause(null)
    }

    private fun setPause(until: Instant?) {
        viewModelScope.launch {
            settingsRepository.setPausedUntil(until)
            // Rebuild every alarm so the pause takes effect (or lifts) now.
            coordinator.onPauseChanged()
        }
    }

    /** Wipes every reminder; caller has already confirmed + authenticated. */
    fun clearAllReminders() {
        viewModelScope.launch { coordinator.clearAllReminders() }
    }

    // endregion

    // region Application mode

    /** The signed-in account, or null; drives the mode section labels. */
    val authUser: StateFlow<AuthUser?> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = authRepository.currentUser,
        )

    private val _uploadUiState = MutableStateFlow<CloudUploadUiState>(CloudUploadUiState.Hidden)
    val uploadUiState: StateFlow<CloudUploadUiState> = _uploadUiState.asStateFlow()

    /**
     * Online → offline: end the session and stop cloud access. Deliberately
     * touches nothing else — every reminder stays local, and whatever was
     * uploaded stays in the account for when the user returns.
     */
    fun switchToOfflineMode() {
        viewModelScope.launch {
            authRepository.signOut()
            settingsRepository.setAppMode(AppMode.OFFLINE)
        }
    }

    /**
     * Opens the upload prompt — after the settings-hosted auth flow
     * finishes, and from the always-available "Upload reminders" row.
     */
    fun promptCloudUpload() {
        _uploadUiState.value = CloudUploadUiState.Prompt
    }

    fun uploadLocalReminders() {
        if (_uploadUiState.value == CloudUploadUiState.Uploading) return
        _uploadUiState.value = CloudUploadUiState.Uploading
        viewModelScope.launch {
            val result = cloudBackupRepository.uploadAllReminders()
            _uploadUiState.value = CloudUploadUiState.Finished(result)
        }
    }

    fun dismissUploadDialog() {
        _uploadUiState.value = CloudUploadUiState.Hidden
    }

    // endregion
}
