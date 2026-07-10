package com.alertnotes.services

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.PresenceState
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the public profile's presence in step with the app's real state:
 *
 * - foreground → ONLINE, plus a low-frequency heartbeat (one tiny write
 *   every few minutes) so readers can distinguish "really here" from a
 *   process that died without saying goodbye,
 * - background → AWAY with a fresh lastSeen,
 * - sign-out → OFFLINE (written by the auth repository),
 * - force-kill / connection lost → no write happens, so consumers treat a
 *   stale heartbeat as OFFLINE (see the public-profile mapper).
 *
 * The heartbeat runs ONLY while the app is foregrounded — zero background
 * wakeups, zero background battery cost. Does nothing in offline mode or
 * signed out, so the offline edition never opens a connection.
 */
@Singleton
class PresenceManager @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val logger: AppLogger,
) {

    private var heartbeatJob: Job? = null

    /** Called once from Application.onCreate. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        publish(PresenceState.ONLINE)
                        startHeartbeat()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        stopHeartbeat()
                        publish(PresenceState.AWAY)
                    }

                    else -> Unit
                }
            },
        )
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = applicationScope.launch {
            while (isActive) {
                delay(HEARTBEAT_MILLIS)
                publish(PresenceState.ONLINE)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun publish(state: PresenceState) {
        applicationScope.launch {
            runCatching {
                val preferences = settingsRepository.preferences.first()
                if (preferences.appMode == AppMode.ONLINE && authRepository.currentUser != null) {
                    userProfileRepository.setPresence(state)
                }
            }.onFailure {
                // Routine when the network is down; d-level keeps release
                // logs quiet.
                logger.d(TAG, "Presence update failed: ${it.message}")
            }
        }
    }

    private companion object {
        const val TAG = "PresenceManager"

        /** Fresh enough for a 10-minute staleness window, cheap on battery. */
        const val HEARTBEAT_MILLIS = 4L * 60L * 1000L
    }
}
