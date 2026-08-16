package com.alertnotes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.alertnotes.core.ui.components.LocalSecondTicker
import com.alertnotes.core.ui.theme.AlertNotesTheme
import com.alertnotes.core.util.SecondTicker
import com.alertnotes.core.util.AppLocaleManager
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.features.alerts.AlertPresenter
import com.alertnotes.features.alerts.ReminderAlertHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dedicated alert window launched by the full-screen-intent notification:
 * shows due reminders over the lock screen and can wake the display, per the
 * reminder's settings. Android security is respected — the keyguard is never
 * dismissed; the alert simply renders above it. Finishes itself the moment
 * the display queue empties.
 */
@AndroidEntryPoint
class AlertActivity : FragmentActivity() {

    // The selected app language must be in place before a single resource is
    // resolved, which is what attachBaseContext guarantees. No-op on API 33+,
    // where the platform has already applied the per-app locale.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

    @Inject
    lateinit var presenter: AlertPresenter

    @Inject
    lateinit var secondTicker: SecondTicker

    @Inject
    lateinit var diagnostics: com.alertnotes.services.ReliabilityDiagnostics

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics.log(
            com.alertnotes.services.ReliabilityDiagnostics.STAGE_FULL_SCREEN,
            "AlertActivity created (fresh launch)",
        )
        applyLockScreenFlags(
            showWhenLocked = intent.getBooleanExtra(EXTRA_SHOW_WHEN_LOCKED, true),
            turnScreenOn = intent.getBooleanExtra(EXTRA_TURN_SCREEN_ON, true),
        )
        enableEdgeToEdge()
        setContent {
            val themeState by mainViewModel.themeState.collectAsStateWithLifecycle()
            val darkTheme = when (themeState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CompositionLocalProvider(LocalSecondTicker provides secondTicker) {
                AlertNotesTheme(
                    darkTheme = darkTheme,
                    dynamicColor = themeState.useDynamicColor,
                ) {
                    ReminderAlertHost()
                    // Finish only after an alert has actually been shown (or
                    // after a grace window on a cold start). The presenter's
                    // StateFlow starts as null before the queue loads from
                    // Room — finishing on that initial null would close the
                    // lock-screen alert before it ever rendered.
                    val alert by presenter.activeAlert.collectAsStateWithLifecycle()
                    var hasShownAlert by remember { mutableStateOf(false) }
                    LaunchedEffect(alert) {
                        if (alert != null) {
                            hasShownAlert = true
                        } else if (hasShownAlert) {
                            finish()
                        } else {
                            // Cold start: give the queue a moment to load;
                            // bail out if nothing is actually pending.
                            delay(COLD_START_GRACE_MILLIS)
                            if (presenter.activeAlert.value == null) finish()
                        }
                    }
                }
            }
        }
    }

    /**
     * singleInstance means a later full-screen intent lands here instead of
     * creating a new activity — re-apply the lock-screen flags so a reminder
     * that must NOT show over the keyguard can't inherit a previous
     * reminder's more permissive window flags.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLockScreenFlags(
            showWhenLocked = intent.getBooleanExtra(EXTRA_SHOW_WHEN_LOCKED, true),
            turnScreenOn = intent.getBooleanExtra(EXTRA_TURN_SCREEN_ON, true),
        )
    }

    private fun applyLockScreenFlags(showWhenLocked: Boolean, turnScreenOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showWhenLocked)
            setTurnScreenOn(turnScreenOn)
        } else {
            // Both set AND clear: flags applied for one reminder must not
            // leak onto the next one routed into this singleInstance window.
            @Suppress("DEPRECATION")
            if (showWhenLocked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
            @Suppress("DEPRECATION")
            if (turnScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    companion object {
        private const val EXTRA_SHOW_WHEN_LOCKED = "com.alertnotes.extra.SHOW_WHEN_LOCKED"
        private const val EXTRA_TURN_SCREEN_ON = "com.alertnotes.extra.TURN_SCREEN_ON"
        private const val COLD_START_GRACE_MILLIS = 4_000L

        fun intent(context: Context, wakeScreen: Boolean, showOnLockScreen: Boolean): Intent =
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_SHOW_WHEN_LOCKED, showOnLockScreen)
                .putExtra(EXTRA_TURN_SCREEN_ON, wakeScreen)
    }
}
