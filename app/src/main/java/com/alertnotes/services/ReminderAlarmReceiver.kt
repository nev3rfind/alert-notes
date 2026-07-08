package com.alertnotes.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.alertnotes.features.alerts.AlertPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives the exact alarm for a due reminder and hands it to the
 * coordinator, which queues the occurrence and schedules the next one.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: ReminderSchedulingCoordinator

    @Inject
    lateinit var presenter: AlertPresenter

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER_DUE) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, INVALID_ID)
        if (reminderId == INVALID_ID) {
            logger.w(TAG, "Alarm broadcast without a reminder id — ignoring")
            return
        }

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                coordinator.onReminderDue(reminderId)
                // Keep the process alive (goAsync grants ~10s) until the
                // presenter has surfaced the entry and the dispatcher had a
                // beat to post its notification/overlay — otherwise a
                // broadcast-only process can be killed in the gap and the
                // due alert stays invisible until the next app launch.
                withTimeoutOrNull(PRESENTATION_WAIT_MILLIS) {
                    presenter.activeAlert.first { it != null }
                    delay(ROUTE_SETTLE_MILLIS)
                }
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to process due reminder $reminderId", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER_DUE = "com.alertnotes.action.REMINDER_DUE"
        const val EXTRA_REMINDER_ID = "com.alertnotes.extra.REMINDER_ID"
        private const val INVALID_ID = -1L
        private const val PRESENTATION_WAIT_MILLIS = 5_000L
        private const val ROUTE_SETTLE_MILLIS = 300L
        private const val TAG = "ReminderAlarmReceiver"
    }
}
