package com.alertnotes

import android.app.Application
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.alertnotes.services.AlertDispatcher
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class AlertNotesApplication : Application() {

    /** Routes due alerts to the in-app host, overlay, or notifications. */
    @Inject
    lateinit var alertDispatcher: AlertDispatcher

    @Inject
    lateinit var coordinator: ReminderSchedulingCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: AppLogger

    override fun onCreate() {
        super.onCreate()
        verifyFirebase()
        alertDispatcher.start()
        // Reconcile alarms on every process start: a force-stop (aggressive
        // battery managers, "Force stop" in app info) silently cancels every
        // AlarmManager alarm, and boot/time receivers never fire for that
        // case. The sweep is idempotent, runs off the main thread, and also
        // recovers occurrences that came due while the app was dead.
        applicationScope.launch {
            runCatching { coordinator.rescheduleAll() }
                .onFailure { logger.e(TAG, "Startup alarm reconciliation failed", it) }
        }
    }

    /**
     * Verifies the Firebase connection at startup. The SDK's
     * FirebaseInitProvider normally auto-initializes the default app from
     * google-services.json before onCreate even runs, so this is a guarded,
     * idempotent check — it initializes manually ONLY if that didn't happen
     * (never a second time) and surfaces the outcome in Logcat either way.
     * No Firebase product (Auth/Firestore/FCM/Functions) is used yet.
     */
    private fun verifyFirebase() {
        try {
            val app = FirebaseApp.getApps(this).firstOrNull()
                ?: FirebaseApp.initializeApp(this)
            if (app != null) {
                logger.i(
                    TAG,
                    "Firebase initialized: app=${app.name}, " +
                        "project=${app.options.projectId}, appId=${app.options.applicationId}",
                )
            } else {
                logger.e(
                    TAG,
                    "Firebase initialization FAILED — google-services.json missing or invalid",
                )
            }
        } catch (throwable: Throwable) {
            // Firebase must never take the reminder engine down with it.
            logger.e(TAG, "Firebase initialization FAILED", throwable)
        }
    }

    private companion object {
        const val TAG = "AlertNotesApplication"
    }
}
