package com.alertnotes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.core.navigation.AlertNotesApp
import com.alertnotes.core.ui.components.LocalSecondTicker
import com.alertnotes.core.ui.theme.AlertNotesTheme
import com.alertnotes.core.util.SecondTicker
import com.alertnotes.core.util.AppLocaleManager
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.ThemeMode
import com.alertnotes.features.account.FirstRunModeGate
import com.alertnotes.features.alerts.AlertPresenter
import com.alertnotes.features.alerts.ReminderAlertHost
import com.alertnotes.features.launch.LaunchOverlay
import com.alertnotes.features.onboarding.OnboardingScreen
import com.alertnotes.features.security.AppLockOverlay
import com.alertnotes.features.security.AppLockState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity (rather than plain ComponentActivity) so BiometricPrompt
// can attach for reminders that require authentication.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // The selected app language must be in place before a single resource is
    // resolved, which is what attachBaseContext guarantees. No-op on API 33+,
    // where the platform has already applied the per-app locale.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrap(newBase))
    }

    private val viewModel: MainViewModel by viewModels()

    /** Shared 1Hz heartbeat for every live countdown and clock. */
    @Inject
    lateinit var secondTicker: SecondTicker

    @Inject
    lateinit var presenter: AlertPresenter

    /** Bumps once per widget quick-create request; consumed by the shell. */
    private var createReminderRequestId by mutableIntStateOf(0)

    /** Latest push-notification destination; bumps once per tap. */
    private var deepLink by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var deepLinkRequestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_CREATE_REMINDER, false)) {
            createReminderRequestId++
        }
        consumeDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val darkTheme = themeState.themeMode.shouldUseDarkTheme()

            // Keep system bar icon contrast in sync when the in-app theme
            // overrides the OS setting.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                )
                onDispose {}
            }

            val isInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalSecondTicker provides secondTicker) {
                AlertNotesTheme(
                    darkTheme = darkTheme,
                    dynamicColor = themeState.useDynamicColor,
                ) {
                    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
                    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()
                    val needsModeSelection by viewModel.needsModeSelection
                        .collectAsStateWithLifecycle()
                    val activeAlert by presenter.activeAlert.collectAsStateWithLifecycle()

                    // With the app lock on, reminder content must not appear
                    // in the Recents screenshot or in screen captures.
                    DisposableEffect(appLockEnabled) {
                        if (appLockEnabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                        onDispose {}
                    }

                    // Overlays cover the app visually and block touches, but
                    // TalkBack traversal and keyboard focus would still reach
                    // the content underneath — hide its semantics entirely
                    // while onboarding, the app lock, or a modal full-screen
                    // alert is on top.
                    val fullScreenAlertActive =
                        activeAlert?.reminder?.displayMode == DisplayMode.FULL_SCREEN
                    val blockAppSemantics = needsModeSelection == true ||
                        needsOnboarding == true ||
                        AppLockState.isLocked(appLockEnabled) ||
                        fullScreenAlertActive
                    Box {
                        Box(
                            modifier = if (blockAppSemantics) {
                                Modifier.clearAndSetSemantics {}
                            } else {
                                Modifier
                            },
                        ) {
                            AlertNotesApp(
                                createReminderRequestId = createReminderRequestId,
                                deepLink = deepLink,
                                deepLinkRequestId = deepLinkRequestId,
                            )
                        }
                        // First run, step 1: offline or online. Deliberately
                        // instead of (not on top of) onboarding so the flow
                        // underneath never leaks to TalkBack traversal.
                        if (needsModeSelection == true) {
                            FirstRunModeGate()
                        } else if (needsOnboarding == true) {
                            // First run, step 2: welcome + guided permissions.
                            OnboardingScreen()
                        }
                        // The lock covers app content, never the alert host —
                        // due reminders stay visible and dismissable.
                        AppLockOverlay(lockEnabled = appLockEnabled)
                        ReminderAlertHost()
                        LaunchOverlay(isAppReady = isInitialized)
                    }
                }
            }
        }
    }

    /**
     * Widget quick-create while the app is already running: the launcher
     * delivers the intent here (FLAG_ACTIVITY_SINGLE_TOP) instead of
     * recreating the activity — without this the widget button did nothing
     * when a task already existed.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CREATE_REMINDER, false)) {
            createReminderRequestId++
        }
        consumeDeepLink(intent)
    }

    /** Push-notification taps land here with their in-app destination. */
    private fun consumeDeepLink(intent: Intent) {
        intent.getStringExtra(com.alertnotes.services.SocialNotifier.EXTRA_DEEP_LINK)?.let {
            deepLink = it
            deepLinkRequestId++
        }
    }
}

@Composable
private fun ThemeMode.shouldUseDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Widget entry point: launches straight into the new-reminder editor. */
fun createReminderIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_CREATE_REMINDER, true)

private const val EXTRA_CREATE_REMINDER = "com.alertnotes.extra.CREATE_REMINDER"
