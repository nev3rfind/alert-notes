package com.alertnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Theme selection consumed by [MainActivity] before any screen renders. */
data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeState: StateFlow<ThemeState> = settingsRepository.preferences
        .map { ThemeState(themeMode = it.themeMode, useDynamicColor = it.useDynamicColor) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeState(),
        )

    /**
     * True once persisted settings have loaded — the launch overlay stays up
     * until then so the UI never flashes with default theme values.
     */
    val isInitialized: StateFlow<Boolean> = settingsRepository.preferences
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /** App-lock setting; false until preferences load (launch overlay covers). */
    val appLockEnabled: StateFlow<Boolean> = settingsRepository.preferences
        .map { it.biometricLockEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /**
     * True when the first-run flow should show; null until preferences load
     * so returning users never see it flash.
     */
    val needsOnboarding: StateFlow<Boolean?> = settingsRepository.preferences
        .map { !it.onboardingCompleted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /**
     * True until the user has chosen offline or online mode — gates the
     * whole app, ahead of onboarding. Null until preferences load, same as
     * [needsOnboarding], so returning users never see the chooser flash.
     * Users upgrading from the offline-only release (onboarding done, mode
     * never set) are not gated: they implicitly stay offline and can opt
     * into online mode from Settings.
     */
    // Unset mode always gates: first launch (before onboarding) and after
    // Log Out (which clears the mode) both land on the welcome chooser.
    val needsModeSelection: StateFlow<Boolean?> = settingsRepository.preferences
        .map { it.appMode == null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
