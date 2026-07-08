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
 * Alarms do not survive a reboot (nor an app update), so rebuild all of them
 * from the database on BOOT_COMPLETED and MY_PACKAGE_REPLACED.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: ReminderSchedulingCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        logger.d(TAG, "Received $action — rebuilding alarms")

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                coordinator.rescheduleAll()
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to reschedule after $action", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
