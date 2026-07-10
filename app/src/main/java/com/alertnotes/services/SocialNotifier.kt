package com.alertnotes.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alertnotes.MainActivity
import com.alertnotes.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts social/sharing/chat push notifications — deliberately separate from
 * the reminder-alert channels so users can tune (or silence) the social
 * layer without ever touching alarm reliability. Every notification deep
 * links back into the app; the durable copy lives in the Notification
 * Centre regardless of what happens to the transient banner.
 */
@Singleton
class SocialNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHAT,
                context.getString(R.string.channel_chat),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_chat_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SOCIAL,
                context.getString(R.string.channel_social),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_social_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SHARING,
                context.getString(R.string.channel_sharing),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_sharing_description) },
        )
    }

    /**
     * [deepLink] is the in-app destination token the push carried
     * (`chat:{uid}`, `notifications`, `shared`, `inbox`); [tag] collapses
     * repeats of the same logical event into one banner.
     */
    fun show(channel: String, tag: String, title: String, body: String, deepLink: String) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DEEP_LINK, deepLink)
        val pending = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.toChannelId())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        manager.notify(tag, SOCIAL_NOTIFICATION_ID, notification)
    }

    private fun String.toChannelId(): String = when (this) {
        TYPE_CHAT -> CHANNEL_CHAT
        TYPE_SHARING -> CHANNEL_SHARING
        else -> CHANNEL_SOCIAL
    }

    companion object {
        const val EXTRA_DEEP_LINK = "com.alertnotes.extra.DEEP_LINK"

        const val TYPE_CHAT = "chat"
        const val TYPE_SOCIAL = "social"
        const val TYPE_SHARING = "sharing"
        const val TYPE_SYNC = "sync"

        private const val CHANNEL_CHAT = "social_chat"
        private const val CHANNEL_SOCIAL = "social_relationships"
        private const val CHANNEL_SHARING = "social_sharing"
        private const val SOCIAL_NOTIFICATION_ID = 41_000
    }
}
