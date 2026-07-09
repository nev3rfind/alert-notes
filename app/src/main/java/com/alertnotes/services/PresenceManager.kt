package com.alertnotes.services

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the public profile's `online`/`lastSeen` fields in step with the
 * app's foreground state. Deliberately simple: a force-killed process skips
 * ON_STOP and leaves a stale "online" — accepted for now; the future
 * presence feature can graduate to Realtime Database onDisconnect hooks
 * without touching callers. Does nothing in offline mode or signed out, so
 * the offline edition never opens a connection.
 */
@Singleton
class PresenceManager @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val logger: AppLogger,
) {

    /** Called once from Application.onCreate. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> publish(online = true)
                    Lifecycle.Event.ON_STOP -> publish(online = false)
                    else -> Unit
                }
            },
        )
    }

    private fun publish(online: Boolean) {
        applicationScope.launch {
            runCatching {
                val preferences = settingsRepository.preferences.first()
                if (preferences.appMode == AppMode.ONLINE && authRepository.currentUser != null) {
                    userProfileRepository.setPresence(online)
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
    }
}
