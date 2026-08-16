package com.alertnotes.services

import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The FCM entry point. The Cloud Functions layer sends DATA-ONLY messages so
 * this service always runs (foreground and background alike) and stays in
 * charge of what the user actually sees:
 *
 * - `chat` pushes are suppressed when the sender's conversation is already
 *   on screen — Firestore's realtime listener renders the message first
 *   (the server-side half of smart delivery skips most of these anyway),
 * - `sharing` pushes trigger an immediate share sweep so a shared reminder
 *   is stored and scheduled BEFORE the user even opens the app — the
 *   reminder wake-up path,
 * - `sync` pushes are silent wake-ups with no banner at all,
 * - everything shown deep-links back to the exact destination.
 */
@AndroidEntryPoint
class AlertNotesMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    @Inject
    lateinit var notifier: SocialNotifier

    @Inject
    lateinit var chatSessionTracker: ChatSessionTracker

    @Inject
    lateinit var sharingRepository: ReminderSharingRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var logger: AppLogger

    // The SDK deprecated this hook without shipping a replacement callback;
    // it remains the only delivery path for token rotation.
    @Deprecated("Mirrors the SDK's deprecation; still the token-rotation entry point")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        pushTokenManager.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val senderUid = data["senderUid"].orEmpty()

        // Smart delivery, client half: the open conversation renders its own
        // messages in realtime — a banner would just be noise.
        val suppressed = type == SocialNotifier.TYPE_CHAT &&
            chatSessionTracker.activePartnerUid.value == senderUid
        if (!suppressed && type != SocialNotifier.TYPE_SYNC) {
            notifier.show(
                channel = type,
                tag = data["tag"] ?: type,
                title = data["title"].orEmpty(),
                body = data["body"].orEmpty(),
                deepLink = data["deepLink"] ?: DEEP_LINK_NOTIFICATIONS,
            )
        }

        // Reminder wake-up: sharing events and silent syncs both pull fresh
        // share state NOW, inside FCM's execution window, so a delivered
        // reminder is scheduled long before the app is next opened.
        if (type == SocialNotifier.TYPE_SHARING || type == SocialNotifier.TYPE_SYNC) {
            scope.launch {
                runCatching {
                    withTimeout(SYNC_WINDOW_MILLIS) {
                        sharingRepository.syncIncomingShares()
                        sharingRepository.syncOwnedShares()
                    }
                }.onFailure { logger.w(TAG, "Push-triggered sync failed", it) }
            }
        }
    }

    private companion object {
        const val TAG = "AlertNotesMessaging"
        const val DEEP_LINK_NOTIFICATIONS = "notifications"

        /** FCM grants ~10s of background execution; stay well inside it. */
        const val SYNC_WINDOW_MILLIS = 8_000L
    }
}
