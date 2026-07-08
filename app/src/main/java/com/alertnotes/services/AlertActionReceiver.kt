package com.alertnotes.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.features.alerts.AlertPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Handles notification actions: dismissing an alert straight from the shade. */
@AndroidEntryPoint
class AlertActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var presenter: AlertPresenter

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        if (entryId < 0) return
        logger.d(TAG, "Notification dismissed alert entry $entryId")
        // goAsync keeps the process alive until the DB write lands —
        // without it, a broadcast-only process can be killed the moment
        // onReceive returns and the dismissal is silently lost.
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                presenter.dismissEntry(entryId, AcknowledgeMethod.NOTIFICATION).join()
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to dismiss entry $entryId", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlertActionReceiver"
        private const val ACTION_DISMISS = "com.alertnotes.action.DISMISS_ALERT"
        private const val EXTRA_ENTRY_ID = "com.alertnotes.extra.ENTRY_ID"

        fun dismissIntent(context: Context, entryId: Long): Intent =
            Intent(context, AlertActionReceiver::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_ENTRY_ID, entryId)
    }
}
