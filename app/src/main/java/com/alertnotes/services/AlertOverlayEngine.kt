package com.alertnotes.services

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alertnotes.core.ui.components.LocalSecondTicker
import com.alertnotes.core.ui.theme.AlertNotesTheme
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.SecondTicker
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.FloatingCardPosition
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.features.alerts.AlertPresenter
import com.alertnotes.features.alerts.FloatingAlertCard
import com.alertnotes.features.alerts.FullScreenAlert
import androidx.compose.runtime.CompositionLocalProvider
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws active alerts over other applications through a
 * TYPE_APPLICATION_OVERLAY window hosting Compose. Floating cards get a
 * wrap-content window anchored by gravity so touches outside pass through
 * to the app underneath; full-screen alerts own the display.
 *
 * The engine is only invoked when the overlay permission is granted — the
 * dispatcher falls back to notifications otherwise, so denial degrades
 * gracefully rather than failing.
 */
@Singleton
class AlertOverlayEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val presenter: Lazy<AlertPresenter>,
    private val secondTicker: SecondTicker,
    private val logger: AppLogger,
) {

    private var overlayView: ComposeView? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null
    private var shownFullScreen = false
    private var shownPosition: FloatingCardPosition? = null

    private val windowManager: WindowManager?
        get() = context.getSystemService(WindowManager::class.java)

    /**
     * Shows (or repositions) the overlay for the given presentation shape.
     * Main thread only. Returns false when the window could not be attached
     * so the dispatcher can fall back to a notification instead of leaving
     * the alert without any surface.
     */
    fun show(displayMode: DisplayMode, position: FloatingCardPosition): Boolean {
        val fullScreen = displayMode == DisplayMode.FULL_SCREEN
        val existing = overlayView
        if (existing != null) {
            if (fullScreen != shownFullScreen || (!fullScreen && position != shownPosition)) {
                runCatching {
                    windowManager?.updateViewLayout(existing, layoutParams(fullScreen, position))
                }.onFailure { logger.e(TAG, "Failed to update overlay layout", it) }
                shownFullScreen = fullScreen
                shownPosition = position
            }
            return true
        }

        val lifecycleOwner = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { OverlayContent() }
        }
        return runCatching {
            windowManager?.addView(view, layoutParams(fullScreen, position))
                ?: error("WindowManager unavailable")
        }.map {
            overlayView = view
            overlayLifecycle = lifecycleOwner
            shownFullScreen = fullScreen
            shownPosition = position
            lifecycleOwner.resume()
            logger.d(TAG, "Overlay attached (fullScreen=$fullScreen, position=$position)")
            true
        }.getOrElse {
            logger.e(TAG, "Failed to attach overlay window", it)
            // The lifecycle owner was created but never resumed; tear it down
            // so the ComposeView cannot leak a composition.
            lifecycleOwner.destroy()
            false
        }
    }

    fun hide() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { logger.e(TAG, "Failed to detach overlay window", it) }
        overlayLifecycle?.destroy()
        overlayView = null
        overlayLifecycle = null
        logger.d(TAG, "Overlay detached")
    }

    @androidx.compose.runtime.Composable
    private fun OverlayContent() {
        CompositionLocalProvider(LocalSecondTicker provides secondTicker) {
            AlertNotesTheme {
                val alert by presenter.get().activeAlert.collectAsStateWithLifecycle()
                val current = alert ?: return@AlertNotesTheme
                val dismiss: (AcknowledgeMethod, ReminderDrawing?) -> Unit = { method, signature ->
                    presenter.get().dismiss(current, method, signature)
                }
                when (current.reminder.displayMode) {
                    DisplayMode.FULL_SCREEN -> FullScreenAlert(
                        alert = current,
                        onDismiss = dismiss,
                        onSnooze = { presenter.get().snooze(current, it) },
                    )

                    DisplayMode.FLOATING_CARD -> FloatingAlertCard(
                        alert = current,
                        onDismiss = dismiss,
                        onSnooze = { presenter.get().snooze(current, it) },
                    )
                }
            }
        }
    }

    private fun layoutParams(
        fullScreen: Boolean,
        position: FloatingCardPosition,
    ): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            if (fullScreen) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            },
            if (fullScreen) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            },
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (fullScreen) {
                // Full-screen alerts are modal by design and keep focus.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                // Floating cards must NOT grab key/IME focus: a focusable
                // overlay closes the keyboard and swallows the back button
                // of whatever app the user is typing in underneath.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            },
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (fullScreen) Gravity.CENTER else position.toGravity()
        }

    private fun FloatingCardPosition.toGravity(): Int {
        val vertical = when (verticalBias) {
            -1f -> Gravity.TOP
            1f -> Gravity.BOTTOM
            else -> Gravity.CENTER_VERTICAL
        }
        val horizontal = when (horizontalBias) {
            -1f -> Gravity.START
            1f -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        return vertical or horizontal
    }

    private companion object {
        const val TAG = "AlertOverlayEngine"
    }
}

/** Minimal lifecycle plumbing for a Compose view living in a raw window. */
private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
