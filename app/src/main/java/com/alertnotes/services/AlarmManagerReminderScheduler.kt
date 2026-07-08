package com.alertnotes.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService
import com.alertnotes.MainActivity
import com.alertnotes.core.util.AppLogger
import com.alertnotes.domain.scheduling.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-backed [ReminderScheduler] built on **setAlarmClock** — the
 * user-facing alarm API. Root cause of late reminders: the previous
 * `setExactAndAllowWhileIdle` is throttled under Doze (deliveries can slip
 * by many minutes on an idle device). Alarm-clock alarms are exempt from
 * Doze batching and fire to the second. Note: on Android 12+ setAlarmClock
 * still requires the SCHEDULE_EXACT_ALARM permission declared in the
 * manifest; the SecurityException fallback below degrades to an inexact
 * alarm if an OEM build (or the user) revokes it.
 *
 * One PendingIntent identity per reminder — the request code and a
 * reminder-specific data URI make the intent unique per reminder, and
 * FLAG_UPDATE_CURRENT makes re-scheduling replace rather than accumulate.
 */
@Singleton
class AlarmManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : ReminderScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService<AlarmManager>()

    override fun schedule(reminderId: Long, triggerAt: Instant) {
        val alarmManager = alarmManager ?: run {
            logger.e(TAG, "AlarmManager unavailable — cannot schedule reminder $reminderId")
            return
        }
        val pendingIntent = alarmPendingIntent(reminderId)
        val triggerAtMillis = triggerAt.toEpochMilli()
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showAppPendingIntent()),
                pendingIntent,
            )
            logger.d(TAG, "Scheduled alarm-clock alarm for reminder $reminderId at $triggerAt")
        } catch (securityException: SecurityException) {
            // Extremely defensive: some OEM builds gate this — degrade, don't crash.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            logger.w(TAG, "Alarm-clock scheduling denied for $reminderId — fell back to inexact", securityException)
        }
    }

    override fun cancel(reminderId: Long) {
        val alarmManager = alarmManager ?: return
        val pendingIntent = alarmPendingIntent(reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Tapping the system alarm indicator opens the app. */
    private fun showAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            SHOW_APP_REQUEST,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarmPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_REMINDER_DUE)
            .setData(Uri.parse("alertnotes://reminder/$reminderId"))
            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val SHOW_APP_REQUEST = 900
    }
}
