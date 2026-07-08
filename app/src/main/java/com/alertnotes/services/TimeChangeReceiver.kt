package com.alertnotes.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Occurrences are defined in wall-clock terms (dates, times of day, zones),
 * so when the clock, date, or timezone changes, every alarm's epoch mapping
 * may be stale. Recompute and reschedule all of them.
 */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: ReminderSchedulingCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        logger.d(TAG, "Received ${intent.action} — recomputing schedules")

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                coordinator.rescheduleAll()
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to reschedule after ${intent.action}", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )
    }
}
