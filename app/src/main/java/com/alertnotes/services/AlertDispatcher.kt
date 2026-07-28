package com.alertnotes.services

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.OverlayPreference
import com.alertnotes.domain.model.Reminder
import com.alertnotes.features.alerts.ActiveAlert
import com.alertnotes.features.alerts.AlertPresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Routes the active alert to the right surface:
 *
 * - **App in foreground** → the in-app host renders it; nothing else fires.
 * - **Background, screen on and unlocked, overlay possible** (permission
 *   granted, preference allows, no biometric requirement) → the overlay
 *   engine draws it over other apps — floating cards *and* full-screen.
 * - **Otherwise** (screen off, device locked, no overlay permission, or a
 *   biometric-protected reminder) → a max-priority notification; wake-screen
 *   and lock-screen reminders attach a full-screen intent to [AlertActivity].
 *
 * The previous version had a real bug here: overlays were attached even
 * while the device was locked — `TYPE_APPLICATION_OVERLAY` windows do not
 * render above the keyguard, so nothing was visible and no notification was
 * posted. Screen/keyguard state is now part of the routing decision and is
 * re-evaluated on every SCREEN_ON / SCREEN_OFF / USER_PRESENT broadcast.
 */
@Singleton
class AlertDispatcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val presenter: AlertPresenter,
    private val notifier: ReminderNotifier,
    private val soundPlayer: AlertSoundPlayer,
    private val overlayEngine: AlertOverlayEngine,
    private val settingsRepository: com.alertnotes.domain.repository.SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: AppLogger,
    private val diagnostics: ReliabilityDiagnostics,
) {

    private val isAppForeground = MutableStateFlow(false)
    private val isScreenUsable = MutableStateFlow(computeScreenUsable())

    /** Called once from Application.onCreate. */
    fun start() {
        notifier.ensureChannels()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> isAppForeground.value = true
                    Lifecycle.Event.ON_STOP -> isAppForeground.value = false
                    else -> Unit
                }
            },
        )
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    isScreenUsable.value = computeScreenUsable()
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        // Routing MUST run on the main thread: the overlay engine talks to
        // WindowManager and hosts a ComposeView, both of which require a
        // Looper thread — attaching from a background dispatcher throws and
        // would leave a due alert with no surface at all.
        scope.launch(Dispatchers.Main) {
            combine(
                presenter.activeAlert,
                isAppForeground,
                isScreenUsable,
                AcknowledgementSession.phase,
                settingsRepository.preferences.map { it.remindersNotificationsEnabled },
            ) { alert, foreground, screenUsable, ackPhase, alertsEnabled ->
                RoutingInputs(alert, foreground, screenUsable, ackPhase, alertsEnabled)
            }.collect { inputs ->
                route(
                    alert = inputs.alert,
                    foreground = inputs.foreground,
                    screenUsable = inputs.screenUsable,
                    ackPhase = inputs.ackPhase,
                    alertsEnabled = inputs.alertsEnabled,
                )
            }
        }
        logger.d(TAG, "Alert dispatcher started")
    }

    private data class RoutingInputs(
        val alert: ActiveAlert?,
        val foreground: Boolean,
        val screenUsable: Boolean,
        val ackPhase: AcknowledgementSession.Phase,
        val alertsEnabled: Boolean,
    )

    private fun route(
        alert: ActiveAlert?,
        foreground: Boolean,
        screenUsable: Boolean,
        ackPhase: AcknowledgementSession.Phase,
        alertsEnabled: Boolean,
    ) {
        // The "Reminder alerts" master switch. It was written to DataStore,
        // mirrored to Firestore and rendered in two settings screens, but no
        // delivery-path code ever read it — turning it off changed nothing.
        //
        // Suppression happens here rather than in the presenter on purpose:
        // the queue entry must stay PENDING, so switching alerts back on
        // surfaces whatever came due while they were off instead of silently
        // discarding it.
        if (!alertsEnabled) {
            soundPlayer.stop()
            overlayEngine.hide()
            notifier.cancel()
            if (alert != null) {
                diagnostics.log(
                    ReliabilityDiagnostics.STAGE_ROUTED,
                    "Entry ${alert.entryId}: suppressed — reminder alerts are switched off",
                )
            }
            return
        }
        // The single audio authority: sound starts with the alert on EVERY
        // surface and stops the moment it is resolved — or while the user is
        // mid proof-capture.
        if (alert != null && ackPhase == AcknowledgementSession.Phase.IDLE) {
            soundPlayer.play(alert)
        } else {
            soundPlayer.stop()
        }
        when {
            // Proof capture in progress: the camera (or the location sheet)
            // owns the screen. Re-fronting the alert here was the camera
            // bounce — stand down until the session resolves.
            alert != null && ackPhase != AcknowledgementSession.Phase.IDLE -> {
                overlayEngine.hide()
                notifier.cancel()
            }

            alert == null || foreground -> {
                overlayEngine.hide()
                notifier.cancel()
            }

            screenUsable && canUseOverlay(alert.reminder) -> {
                val shown = overlayEngine.show(
                    displayMode = alert.reminder.displayMode,
                    position = alert.reminder.floatingCardPosition,
                )
                // Never trade a working surface for a broken one: cancel the
                // notification only once the overlay is actually attached,
                // and fall back to it if attaching failed.
                if (shown) {
                    diagnostics.log(
                        ReliabilityDiagnostics.STAGE_ROUTED,
                        "Entry ${alert.entryId}: overlay over current app",
                    )
                    notifier.cancel()
                } else {
                    logger.w(TAG, "Overlay attach failed — falling back to notification")
                    diagnostics.log(
                        ReliabilityDiagnostics.STAGE_OVERLAY,
                        "Entry ${alert.entryId}: attach FAILED — notification fallback",
                    )
                    showNotificationWithRetry(alert)
                }
            }

            // Device in use, and this reminder can't use a floating card
            // because a biometric prompt is needed (or the overlay permission
            // is missing for an AUTO/PREFER reminder). With the overlay
            // permission granted the app holds Android's documented
            // background-activity-launch exemption — take the user straight
            // to AlertActivity instead of hoping they notice a heads-up.
            //
            // NEVER_OVERLAY is excluded explicitly. It used to fall in here
            // and get a full-screen activity takeover, which is strictly more
            // intrusive than the floating card the user just opted out of; it
            // now falls through to the notification branch below, which is the
            // surface the preference actually promises.
            screenUsable &&
                alert.reminder.overlayPreference != OverlayPreference.NEVER_OVERLAY &&
                Settings.canDrawOverlays(context) -> {
                overlayEngine.hide()
                if (launchAlertActivity(alert)) {
                    notifier.cancel()
                } else {
                    showNotificationWithRetry(alert)
                }
            }

            else -> {
                overlayEngine.hide()
                if (screenUsable) {
                    // In use, but no overlay permission: Android will only
                    // show a heads-up here by design. Record the reason so
                    // the Reliability screen can point at the missing grant.
                    diagnostics.log(
                        ReliabilityDiagnostics.STAGE_PERMISSION,
                        "Display over other apps not granted — " +
                            "interruption degrades to a heads-up notification",
                    )
                }
                diagnostics.log(
                    ReliabilityDiagnostics.STAGE_ROUTED,
                    "Entry ${alert.entryId}: notification path " +
                        "(screenUsable=$screenUsable)",
                )
                showNotificationWithRetry(alert)
            }
        }
    }

    /**
     * Direct full-screen launch while the user is mid-something-else — the
     * phone-call pattern. Only attempted when the overlay permission grants
     * the background-launch exemption, so it either works or we know why.
     */
    private fun launchAlertActivity(alert: ActiveAlert): Boolean = runCatching {
        context.startActivity(
            com.alertnotes.AlertActivity.intent(
                context,
                alert.reminder.wakeScreen,
                alert.reminder.showOnLockScreen,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        diagnostics.log(
            ReliabilityDiagnostics.STAGE_FULL_SCREEN,
            "Entry ${alert.entryId}: AlertActivity launched directly",
        )
        true
    }.getOrElse { throwable ->
        logger.e(TAG, "Direct AlertActivity launch failed", throwable)
        diagnostics.log(
            ReliabilityDiagnostics.STAGE_FULL_SCREEN,
            "Entry ${alert.entryId}: direct launch FAILED (${throwable.message})",
        )
        false
    }

    /**
     * Failsafe: a reminder must never vanish because one notify() call was
     * unlucky. One delayed retry covers transient NotificationManager
     * hiccups; both attempts are recorded.
     */
    private fun showNotificationWithRetry(alert: ActiveAlert) {
        if (notifier.showAlert(alert)) return
        scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(NOTIFY_RETRY_DELAY_MILLIS)
            // Only retry if this alert is still the active one.
            if (presenter.activeAlert.value?.entryId == alert.entryId) {
                diagnostics.log(
                    ReliabilityDiagnostics.STAGE_NOTIFIED,
                    "Entry ${alert.entryId}: retrying notification post",
                )
                notifier.showAlert(alert)
            }
        }
    }

    /** Overlays render only over a live, unlocked session. */
    private fun computeScreenUsable(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return powerManager?.isInteractive == true && keyguardManager?.isKeyguardLocked == false
    }

    private fun canUseOverlay(reminder: Reminder): Boolean {
        if (reminder.overlayPreference == OverlayPreference.NEVER_OVERLAY) return false
        if (!Settings.canDrawOverlays(context)) return false
        // Biometric prompts need an activity; route those through AlertActivity.
        if (reminder.requiresBiometric) return false
        // AUTO and PREFER both overlay when possible — this is what makes
        // background reminders present properly instead of only notifying.
        return true
    }

    private companion object {
        const val TAG = "AlertDispatcher"
        const val NOTIFY_RETRY_DELAY_MILLIS = 1_000L
    }
}
