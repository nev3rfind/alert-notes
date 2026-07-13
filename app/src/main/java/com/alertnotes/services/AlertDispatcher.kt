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
            ) { alert, foreground, screenUsable, ackPhase ->
                RoutingInputs(alert, foreground, screenUsable, ackPhase)
            }.collect { inputs ->
                route(inputs.alert, inputs.foreground, inputs.screenUsable, inputs.ackPhase)
            }
        }
        logger.d(TAG, "Alert dispatcher started")
    }

    private data class RoutingInputs(
        val alert: ActiveAlert?,
        val foreground: Boolean,
        val screenUsable: Boolean,
        val ackPhase: AcknowledgementSession.Phase,
    )

    private fun route(
        alert: ActiveAlert?,
        foreground: Boolean,
        screenUsable: Boolean,
        ackPhase: AcknowledgementSession.Phase,
    ) {
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

            // Device in use but this reminder can't overlay (never-overlay
            // preference or biometric prompt needed). With the overlay
            // permission granted the app holds Android's documented
            // background-activity-launch exemption — take the user straight
            // to AlertActivity instead of hoping they notice a heads-up.
            screenUsable && Settings.canDrawOverlays(context) -> {
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
