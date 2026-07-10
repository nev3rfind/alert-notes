package com.alertnotes.services

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.DeviceRemoteDataSource
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Owns the FCM registration token's journey into Firestore. On every login
 * (and app start while signed in, online mode only) the current token lands
 * in this device's `users/{uid}/devices/{deviceId}.pushToken`; when FCM
 * rotates the token, [onTokenRefreshed] re-uploads it. Sign-out clears it
 * (auth repository), and the send layer prunes tokens FCM reports invalid —
 * the full token lifecycle with no manual steps.
 */
@Singleton
class PushTokenManager @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val deviceDataSource: DeviceRemoteDataSource,
    private val logger: AppLogger,
) {

    /** Called once from Application.onCreate. */
    fun start() {
        scope.launch {
            combine(
                settingsRepository.preferences.map { it.appMode }.distinctUntilChanged(),
                authRepository.authState,
            ) { mode, user -> if (mode == AppMode.ONLINE) user?.uid else null }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid != null) registerCurrentToken(uid)
                }
        }
    }

    /** Called by the messaging service when FCM rotates the token. */
    fun onTokenRefreshed(token: String) {
        scope.launch {
            runCatching {
                val online = settingsRepository.preferences.first().appMode == AppMode.ONLINE
                val uid = authRepository.currentUser?.uid
                if (online && uid != null) {
                    deviceDataSource.updatePushToken(uid, token)
                    logger.i(TAG, "Refreshed FCM token stored")
                }
            }.onFailure { logger.w(TAG, "Token refresh upload failed", it) }
        }
    }

    private suspend fun registerCurrentToken(uid: String) {
        runCatching {
            // Deprecated in the current SDK with no replacement API yet;
            // still the only way to read this installation's token.
            @Suppress("DEPRECATION")
            val token = FirebaseMessaging.getInstance().token.await()
            deviceDataSource.updatePushToken(uid, token)
            logger.i(TAG, "FCM token registered for this device")
        }.onFailure { logger.w(TAG, "FCM token registration failed", it) }
    }

    private companion object {
        const val TAG = "PushTokenManager"
    }
}
