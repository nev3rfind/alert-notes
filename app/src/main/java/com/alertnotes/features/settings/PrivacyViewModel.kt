package com.alertnotes.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.core.util.AppLogger
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.PrivacyAudience
import com.alertnotes.domain.model.PrivacyControl
import com.alertnotes.domain.model.PrivacySettings
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the privacy screen.
 *
 * [settings] is optimistic: a tap updates it immediately and the Firestore
 * write follows. If that write fails the previous value is restored and
 * [error] is raised — a privacy control that silently kept a value the server
 * never accepted would be worse than useless.
 */
data class PrivacyUiState(
    val loading: Boolean = true,
    val isOnline: Boolean = false,
    val settings: PrivacySettings = PrivacySettings.DEFAULT,
    val savingControl: PrivacyControl? = null,
    val saved: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
    settingsRepository: SettingsRepository,
    private val logger: AppLogger,
) : ViewModel() {

    /** Set while a write is in flight so the remote echo cannot flicker it back. */
    private val pending = MutableStateFlow<PrivacySettings?>(null)
    private val savingControl = MutableStateFlow<PrivacyControl?>(null)
    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(val saved: Boolean = false, val error: Boolean = false)

    val uiState: StateFlow<PrivacyUiState> = combine(
        profileRepository.profile,
        settingsRepository.preferences.map { it.appMode },
        pending,
        savingControl,
        transient,
    ) { profile, mode, pendingSettings, saving, flags ->
        PrivacyUiState(
            // Offline mode has no profile to load, so it is never "loading".
            loading = mode == AppMode.ONLINE && profile == null,
            isOnline = mode == AppMode.ONLINE,
            settings = pendingSettings
                ?: profile?.publicProfile?.privacy
                ?: PrivacySettings.DEFAULT,
            savingControl = saving,
            saved = flags.saved,
            error = flags.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PrivacyUiState(),
    )

    fun setAudience(control: PrivacyControl, audience: PrivacyAudience) {
        val current = uiState.value.settings
        if (current[control] == audience) return
        val updated = current.with(control, audience)
        pending.value = updated
        savingControl.value = control
        transient.value = TransientState()
        viewModelScope.launch {
            runCatching { profileRepository.updatePrivacy(updated) }
                .onSuccess {
                    transient.value = TransientState(saved = true)
                    logger.d(TAG, "Privacy control ${control.key} set to $audience")
                }
                .onFailure { throwable ->
                    // Roll back to whatever the server actually holds.
                    transient.value = TransientState(error = true)
                    logger.e(TAG, "Privacy update failed for ${control.key}", throwable)
                }
            pending.value = null
            savingControl.value = null
        }
    }

    fun consumeFeedback() {
        transient.value = TransientState()
    }

    private companion object {
        const val TAG = "PrivacyViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Label for an audience option. */
fun PrivacyAudience.labelRes(): Int = when (this) {
    PrivacyAudience.EVERYONE -> com.alertnotes.R.string.privacy_audience_everyone
    PrivacyAudience.FRIENDS_OF_FRIENDS -> com.alertnotes.R.string.privacy_audience_friends_of_friends
    PrivacyAudience.FRIENDS_ONLY -> com.alertnotes.R.string.privacy_audience_friends_only
    PrivacyAudience.FAMILY_ONLY -> com.alertnotes.R.string.privacy_audience_family_only
    PrivacyAudience.NOBODY -> com.alertnotes.R.string.privacy_audience_nobody
}

/** One-line explanation of who an audience actually includes. */
fun PrivacyAudience.hintRes(): Int = when (this) {
    PrivacyAudience.EVERYONE -> com.alertnotes.R.string.privacy_audience_everyone_hint
    PrivacyAudience.FRIENDS_OF_FRIENDS ->
        com.alertnotes.R.string.privacy_audience_friends_of_friends_hint
    PrivacyAudience.FRIENDS_ONLY -> com.alertnotes.R.string.privacy_audience_friends_only_hint
    PrivacyAudience.FAMILY_ONLY -> com.alertnotes.R.string.privacy_audience_family_only_hint
    PrivacyAudience.NOBODY -> com.alertnotes.R.string.privacy_audience_nobody_hint
}

fun PrivacyControl.titleRes(): Int = when (this) {
    PrivacyControl.FRIEND_REQUESTS -> com.alertnotes.R.string.privacy_control_friend_requests
    PrivacyControl.FAMILY_INVITATIONS -> com.alertnotes.R.string.privacy_control_family_invitations
    PrivacyControl.REMINDER_SHARING -> com.alertnotes.R.string.privacy_control_reminder_sharing
    PrivacyControl.MESSAGE_REQUESTS -> com.alertnotes.R.string.privacy_control_message_requests
    PrivacyControl.PROFILE_VISIBILITY -> com.alertnotes.R.string.privacy_control_profile_visibility
    PrivacyControl.ONLINE_STATUS -> com.alertnotes.R.string.privacy_control_online_status
    PrivacyControl.LAST_SEEN -> com.alertnotes.R.string.privacy_control_last_seen
    PrivacyControl.ANALYTICS_VISIBILITY ->
        com.alertnotes.R.string.privacy_control_analytics_visibility
}

fun PrivacyControl.summaryRes(): Int = when (this) {
    PrivacyControl.FRIEND_REQUESTS ->
        com.alertnotes.R.string.privacy_control_friend_requests_summary
    PrivacyControl.FAMILY_INVITATIONS ->
        com.alertnotes.R.string.privacy_control_family_invitations_summary
    PrivacyControl.REMINDER_SHARING ->
        com.alertnotes.R.string.privacy_control_reminder_sharing_summary
    PrivacyControl.MESSAGE_REQUESTS ->
        com.alertnotes.R.string.privacy_control_message_requests_summary
    PrivacyControl.PROFILE_VISIBILITY ->
        com.alertnotes.R.string.privacy_control_profile_visibility_summary
    PrivacyControl.ONLINE_STATUS ->
        com.alertnotes.R.string.privacy_control_online_status_summary
    PrivacyControl.LAST_SEEN -> com.alertnotes.R.string.privacy_control_last_seen_summary
    PrivacyControl.ANALYTICS_VISIBILITY ->
        com.alertnotes.R.string.privacy_control_analytics_visibility_summary
}
