package com.alertnotes.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alertnotes.AlertActivity
import com.alertnotes.R
import com.alertnotes.core.util.AppLogger
import com.alertnotes.domain.model.AcknowledgementType
import com.alertnotes.features.alerts.ActiveAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the system notification for a due reminder when the app is in the
 * background. Wake-screen/lock-screen reminders attach a full-screen intent
 * (the Android-sanctioned alarm pattern) launching [AlertActivity]; others
 * arrive as a high-priority heads-up. Per-reminder sound/vibration choose
 * between an audible and a silent channel; lock-screen visibility follows
 * the reminder's setting.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AppLogger,
    private val diagnostics: ReliabilityDiagnostics,
) {

    /** Entry that last made noise; re-posts of it stay silent. */
    private var lastAlertedEntryId: Long? = null

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // The legacy single-sound channel is superseded by the per-priority
        // channels below; removing it keeps system settings tidy.
        manager.deleteNotificationChannel(CHANNEL_ALERTS_LEGACY)
        val alarmAttributes = AudioAttributes.Builder()
            // Alarm stream: reminders must ring like alarms (and respect the
            // alarm volume), not like chat pings.
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_description)
                enableVibration(true)
                // The bundled Alert Notes signature sound.
                setSound(rawSoundUri(R.raw.sound_noti), alarmAttributes)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRITICAL,
                context.getString(R.string.notification_channel_critical),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_critical_description)
                enableVibration(true)
                // Critical reminders always carry the dedicated alarm sound.
                setSound(rawSoundUri(R.raw.alert_critical), alarmAttributes)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT,
                context.getString(R.string.notification_channel_silent),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun rawSoundUri(resId: Int): android.net.Uri =
        android.net.Uri.parse("android.resource://${context.packageName}/$resId")

    /** Returns true when the notification was actually handed to the system. */
    fun showAlert(alert: ActiveAlert): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            logger.w(TAG, "Notifications disabled — cannot surface alert ${alert.entryId}")
            diagnostics.log(
                ReliabilityDiagnostics.STAGE_PERMISSION,
                "Notifications DISABLED — entry ${alert.entryId} has no surface",
            )
            return false
        }
        val reminder = alert.reminder
        val channel = when {
            !reminder.soundEnabled && !reminder.vibrationEnabled -> CHANNEL_SILENT
            reminder.priority == com.alertnotes.domain.model.ReminderPriority.CRITICAL ->
                CHANNEL_CRITICAL

            else -> CHANNEL_ALERTS
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            AlertActivity.intent(context, reminder.wakeScreen, reminder.showOnLockScreen),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISS,
            AlertActionReceiver.dismissIntent(context, alert.entryId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.description.ifBlank { null })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Low-priority reminders arrive quietly; everything else demands
            // maximum prominence.
            .setPriority(
                if (reminder.priority == com.alertnotes.domain.model.ReminderPriority.LOW) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_MAX
                },
            )
            .setVisibility(
                if (reminder.showOnLockScreen) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_SECRET
                },
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            // Re-posts of the SAME alert (screen on/off, routing changes)
            // stay silent; a NEW alert on the reused id must ring again.
            .setOnlyAlertOnce(alert.entryId == lastAlertedEntryId)
            .addAction(0, context.getString(R.string.alert_dismiss), dismissIntent)
        if (!reminder.requiresBiometric && reminder.acknowledgement == AcknowledgementType.NONE) {
            // NONE acknowledgement: allow direct dismissal from the shade.
            builder.setDeleteIntent(dismissIntent)
        }
        if (reminder.wakeScreen || reminder.showOnLockScreen) {
            if (canUseFullScreenIntent()) {
                builder.setFullScreenIntent(contentIntent, true)
                diagnostics.log(
                    ReliabilityDiagnostics.STAGE_FULL_SCREEN,
                    "Entry ${alert.entryId}: full-screen intent attached",
                )
            } else {
                diagnostics.log(
                    ReliabilityDiagnostics.STAGE_PERMISSION,
                    "Full-screen intents NOT permitted — entry ${alert.entryId} " +
                        "degrades to heads-up (enable in Reminder Reliability)",
                )
            }
        }

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            lastAlertedEntryId = alert.entryId
            diagnostics.log(
                ReliabilityDiagnostics.STAGE_NOTIFIED,
                "Entry ${alert.entryId}: notification posted",
            )
            true
        }.getOrElse {
            logger.e(TAG, "Failed to post alert notification", it)
            diagnostics.log(
                ReliabilityDiagnostics.STAGE_NOTIFIED,
                "Entry ${alert.entryId}: post FAILED (${it.message})",
            )
            false
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Android 14+ gates full-screen intents behind a user-visible setting. */
    private fun canUseFullScreenIntent(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val allowed = manager.canUseFullScreenIntent()
        if (!allowed) {
            logger.w(TAG, "Full-screen intents not permitted — lock-screen alert degrades to heads-up")
        }
        return allowed
    }

    private companion object {
        const val TAG = "ReminderNotifier"
        const val CHANNEL_ALERTS_LEGACY = "reminder_alerts"
        const val CHANNEL_ALERTS = "reminder_alerts_noti"
        const val CHANNEL_CRITICAL = "reminder_alerts_critical"
        const val CHANNEL_SILENT = "reminder_alerts_silent"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_OPEN = 10
        const val REQUEST_DISMISS = 11
    }
}
