package com.alertnotes

import android.app.Application
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.alertnotes.services.AlertDispatcher
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

    private companion object {
        const val TAG = "AlertNotesApplication"
    }
}
