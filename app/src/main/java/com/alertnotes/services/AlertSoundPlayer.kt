package com.alertnotes.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.alertnotes.R
import com.alertnotes.core.util.AppLogger
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.features.alerts.ActiveAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single audio authority for due reminders. Notification channels no
 * longer carry sound — three of the four alert surfaces (in-app host,
 * overlay, direct full-screen launch) never post a notification at all, so
 * channel-based audio was structurally silent everywhere except the pure
 * notification path. This player is driven by the dispatcher, which runs in
 * the app process for every surface, so sound starts the moment the alert
 * appears — foreground, background, locked, screen off, or cold-started by
 * an alarm after the process was killed.
 *
 * Critical reminders loop their dedicated alarm until acknowledged or
 * snoozed; every other priority plays the signature sound once. Audio runs
 * on the ALARM stream: it respects the alarm volume and sounds through Do
 * Not Disturb exactly like a clock alarm.
 */
@Singleton
class AlertSoundPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {

    private var player: MediaPlayer? = null
    private var playingEntryId: Long? = null

    /** Idempotent per queue entry — re-routing the same alert never restarts audio. */
    fun play(alert: ActiveAlert) {
        if (!alert.reminder.soundEnabled) {
            stop()
            return
        }
        if (playingEntryId == alert.entryId && player != null) return
        stop()
        val critical = alert.reminder.priority == ReminderPriority.CRITICAL
        val soundRes = if (critical) R.raw.alert_critical else R.raw.sound_noti
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        player = runCatching {
            MediaPlayer.create(context, soundRes, attributes, 0)?.apply {
                isLooping = critical
                if (!critical) {
                    setOnCompletionListener { completed ->
                        runCatching { completed.release() }
                        if (player == completed) {
                            player = null
                        }
                    }
                }
                start()
            }
        }.onFailure { logger.e(TAG, "Alert sound failed to start", it) }.getOrNull()
        playingEntryId = alert.entryId
        logger.d(TAG, "Alert sound started for entry ${alert.entryId} (critical=$critical)")
    }

    fun stop() {
        player?.let { active ->
            runCatching {
                if (active.isPlaying) active.stop()
                active.release()
            }
        }
        player = null
        playingEntryId = null
    }

    private companion object {
        const val TAG = "AlertSoundPlayer"
    }
}
