package com.alertnotes.features.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.model.AvatarUpload
import com.alertnotes.domain.model.ProfileTheme
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.model.UserPreferences
import com.alertnotes.domain.model.UserProfile
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.ReminderHistoryRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.repository.UserProfileRepository
import com.alertnotes.features.account.AccountViewModel
import com.alertnotes.features.account.FieldError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Page-level state: gate, spinner, or the live profile. */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** Offline mode or signed out — the page shows how to go online. */
    data object Unavailable : ProfileUiState

    data class Ready(
        val profile: UserProfile,
        val preferences: UserPreferences,
    ) : ProfileUiState
}

/** Avatar pipeline progress, drawn over the hero avatar. */
sealed interface AvatarUiState {
    data object Idle : AvatarUiState

    /** Decoding/cropping/compressing — indeterminate. */
    data object Processing : AvatarUiState

    data class Uploading(val fraction: Float) : AvatarUiState
}

/** State of whichever edit dialog is currently open (one at a time). */
data class ProfileEditState(
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    val fieldError: FieldError? = null,
    /** Bumps when a save lands so the open dialog closes itself. */
    val completedAt: Long = 0L,
)

/** One-shot notices surfaced as a small dialog. */
enum class ProfileNotice {
    AVATAR_FAILED,
    VERIFICATION_SENT,
    PASSWORD_CHANGED,
    GENERIC_ERROR,
    NETWORK_ERROR,
}

/**
 * Dashboard counters. Reminder numbers are live local data; sharing,
 * friend, and family counts come from the statistics section and stay at
 * zero until those features ship.
 */
data class ProfileDashboard(
    val activeReminders: Int = 0,
    val completed: Int = 0,
    val shared: Int = 0,
    val friends: Int = 0,
    val family: Int = 0,
    val devices: Int = 0,
    val accountAgeDays: Long? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    reminderRepository: ReminderRepository,
    historyRepository: ReminderHistoryRepository,
    private val timeProvider: TimeProvider,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UserProfileRepository,
    private val imageProcessor: AvatarImageProcessor,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.profile,
        settingsRepository.preferences,
        authRepository.authState,
    ) { profile, preferences, user ->
        when {
            user == null || preferences.appMode != AppMode.ONLINE -> ProfileUiState.Unavailable
            profile == null -> ProfileUiState.Loading
            else -> ProfileUiState.Ready(profile = profile, preferences = preferences)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState.Loading,
    )

    /** Live dashboard numbers; recomputed whenever any source changes. */
    val dashboard: StateFlow<ProfileDashboard> = combine(
        reminderRepository.observeStats(timeProvider.now().plus(STATS_DUE_HORIZON)),
        historyRepository.observeHistory(),
        profileRepository.profile,
        profileRepository.deviceCount,
    ) { stats, history, profile, devices ->
        ProfileDashboard(
            activeReminders = stats.enabled,
            // The history flow is bounded to recent entries, so this is a
            // floor rather than an all-time total — honest enough until a
            // dedicated counter lands with the statistics work.
            completed = history.count { it.dismissedAt != null },
            shared = profile?.statistics?.sharedReminderCount ?: 0,
            friends = profile?.statistics?.friendCount ?: 0,
            family = profile?.statistics?.familyCount ?: 0,
            devices = devices,
            accountAgeDays = profile?.privateProfile?.memberSince?.let { since ->
                Duration.between(since, timeProvider.now()).toDays().coerceAtLeast(0)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileDashboard(),
    )

    private val _avatarState = MutableStateFlow<AvatarUiState>(AvatarUiState.Idle)
    val avatarState: StateFlow<AvatarUiState> = _avatarState.asStateFlow()

    /** Bitmap being cropped in the editor dialog; null when closed. */
    private val _editorImage = MutableStateFlow<Bitmap?>(null)
    val editorImage: StateFlow<Bitmap?> = _editorImage.asStateFlow()

    private val _editState = MutableStateFlow(ProfileEditState())
    val editState: StateFlow<ProfileEditState> = _editState.asStateFlow()

    private val _notice = MutableStateFlow<ProfileNotice?>(null)
    val notice: StateFlow<ProfileNotice?> = _notice.asStateFlow()

    // region Avatar

    /** Decodes the picked/captured image and opens the crop editor. */
    fun beginAvatarEdit(uri: Uri) {
        if (_avatarState.value != AvatarUiState.Idle) return
        _avatarState.value = AvatarUiState.Processing
        viewModelScope.launch {
            try {
                _editorImage.value = imageProcessor.decodeForEditing(uri)
                _avatarState.value = AvatarUiState.Idle
            } catch (exception: Exception) {
                _avatarState.value = AvatarUiState.Idle
                _notice.value = ProfileNotice.AVATAR_FAILED
            }
        }
    }

    fun cancelAvatarEdit() {
        _editorImage.value = null
    }

    /** Renders the previewed crop and uploads it with live progress. */
    fun confirmAvatarCrop(crop: AvatarCrop) {
        val source = _editorImage.value ?: return
        if (_avatarState.value != AvatarUiState.Idle) return
        _editorImage.value = null
        _avatarState.value = AvatarUiState.Processing
        viewModelScope.launch {
            try {
                val bytes = imageProcessor.renderAvatar(
                    source = source,
                    rotationSteps = crop.rotationSteps,
                    zoom = crop.zoom,
                    offsetX = crop.offsetX,
                    offsetY = crop.offsetY,
                    viewportPx = crop.viewportPx,
                )
                _avatarState.value = AvatarUiState.Uploading(0f)
                profileRepository.uploadAvatar(bytes).collect { step ->
                    if (step is AvatarUpload.InProgress) {
                        _avatarState.value = AvatarUiState.Uploading(step.fraction)
                    }
                }
                _avatarState.value = AvatarUiState.Idle
            } catch (exception: Exception) {
                _avatarState.value = AvatarUiState.Idle
                _notice.value = if ((exception as? AuthException)?.error == AuthError.NETWORK) {
                    ProfileNotice.NETWORK_ERROR
                } else {
                    ProfileNotice.AVATAR_FAILED
                }
            }
        }
    }

    fun removeAvatar() {
        if (_avatarState.value != AvatarUiState.Idle) return
        _avatarState.value = AvatarUiState.Processing
        viewModelScope.launch {
            try {
                profileRepository.removeAvatar()
                _avatarState.value = AvatarUiState.Idle
            } catch (exception: AuthException) {
                _avatarState.value = AvatarUiState.Idle
                _notice.value = exception.toNotice(ProfileNotice.AVATAR_FAILED)
            }
        }
    }

    // endregion

    // region Personal information

    fun saveDisplayName(displayName: String) {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) {
            _editState.update { it.copy(fieldError = FieldError.REQUIRED) }
            return
        }
        submitEdit { profileRepository.updateDisplayName(trimmed) }
    }

    fun saveUsername(username: String) {
        val trimmed = username.trim()
        if (!AccountViewModel.USERNAME_PATTERN.matches(trimmed)) {
            _editState.update {
                it.copy(
                    fieldError = if (trimmed.isBlank()) {
                        FieldError.REQUIRED
                    } else {
                        FieldError.USERNAME_INVALID
                    },
                )
            }
            return
        }
        submitEdit { profileRepository.changeUsername(trimmed) }
    }

    fun saveStatusMessage(statusMessage: String) {
        // Blank is allowed — it clears the status.
        submitEdit { profileRepository.updateStatusMessage(statusMessage.trim()) }
    }

    /** Called when an edit dialog opens so stale errors never greet it. */
    fun resetEditState() {
        _editState.value = ProfileEditState(completedAt = _editState.value.completedAt)
    }

    // endregion

    // region Security

    fun changePassword(current: String, new: String, confirm: String) {
        val fieldError = when {
            current.isBlank() || new.isBlank() || confirm.isBlank() -> FieldError.REQUIRED
            new.length < AccountViewModel.MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
            new != confirm -> FieldError.PASSWORDS_DO_NOT_MATCH
            else -> null
        }
        if (fieldError != null) {
            _editState.update { it.copy(fieldError = fieldError) }
            return
        }
        submitEdit(onSuccess = { _notice.value = ProfileNotice.PASSWORD_CHANGED }) {
            profileRepository.changePassword(currentPassword = current, newPassword = new)
        }
    }

    fun sendEmailVerification() {
        viewModelScope.launch {
            try {
                profileRepository.sendEmailVerification()
                _notice.value = ProfileNotice.VERIFICATION_SENT
            } catch (exception: AuthException) {
                _notice.value = exception.toNotice(ProfileNotice.GENERIC_ERROR)
            }
        }
    }

    /** On resume: the user may have just clicked the verification link. */
    fun refreshEmailVerification() {
        viewModelScope.launch {
            runCatching { profileRepository.refreshEmailVerified() }
        }
    }

    // endregion

    // region Personalisation & preferences

    /** Persists the banner/accent theme; visible to friends later. */
    fun setBannerTheme(theme: ProfileTheme) {
        viewModelScope.launch {
            runCatching { profileRepository.updateBannerTheme(theme) }
                .onFailure { _notice.value = ProfileNotice.NETWORK_ERROR }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
            mirrorPreference("theme", themeMode.name)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRemindersNotificationsEnabled(enabled)
            mirrorPreference("notificationEnabled", enabled)
        }
    }

    private suspend fun mirrorPreference(key: String, value: Any) {
        // Local DataStore stays the source of truth; the cloud copy is
        // best-effort for future cross-device sync.
        runCatching { profileRepository.syncPreference(key, value) }
    }

    // endregion

    fun dismissNotice() {
        _notice.value = null
    }

    private fun submitEdit(
        onSuccess: (() -> Unit)? = null,
        operation: suspend () -> Unit,
    ) {
        if (_editState.value.isSubmitting) return
        _editState.update { it.copy(isSubmitting = true, error = null, fieldError = null) }
        viewModelScope.launch {
            try {
                operation()
                _editState.update {
                    it.copy(isSubmitting = false, completedAt = it.completedAt + 1)
                }
                onSuccess?.invoke()
            } catch (exception: AuthException) {
                _editState.update { it.copy(isSubmitting = false, error = exception.error) }
            }
        }
    }

    private fun AuthException.toNotice(fallback: ProfileNotice): ProfileNotice =
        if (error == AuthError.NETWORK) ProfileNotice.NETWORK_ERROR else fallback

    private companion object {
        /** Same "due soon" window the home dashboard uses. */
        val STATS_DUE_HORIZON: Duration = Duration.ofHours(24)
    }
}
