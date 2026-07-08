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
    private val overlayEngine: AlertOverlayEngine,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: AppLogger,
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
            ) { alert, foreground, screenUsable ->
                Triple(alert, foreground, screenUsable)
            }.collect { (alert, foreground, screenUsable) ->
                route(alert, foreground, screenUsable)
            }
        }
        logger.d(TAG, "Alert dispatcher started")
    }

    private fun route(alert: ActiveAlert?, foreground: Boolean, screenUsable: Boolean) {
        when {
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
                    notifier.cancel()
                } else {
                    logger.w(TAG, "Overlay attach failed — falling back to notification")
                    notifier.showAlert(alert)
                }
            }

            else -> {
                overlayEngine.hide()
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
    }
}
