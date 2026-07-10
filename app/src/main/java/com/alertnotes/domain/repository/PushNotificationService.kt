package com.alertnotes.domain.repository

/**
 * Firebase Cloud Messaging preparation — interface only, per scope. The
 * future FCM session implements this against device tokens (already stored
 * per device with a reserved pushToken field) and delivery acknowledgements
 * (the DELIVERED message status). Nothing calls the network today.
 */
interface PushNotificationService {

    /** Refreshes this device's push token in the account's device registry. */
    suspend fun refreshToken()

    /** Acknowledges delivery of a pushed message (sets DELIVERED status). */
    suspend fun acknowledgeDelivery(chatId: String, messageId: String)
}

/** No-op stand-in bound until the FCM session lands. */
class NoOpPushNotificationService : PushNotificationService {
    override suspend fun refreshToken() = Unit
    override suspend fun acknowledgeDelivery(chatId: String, messageId: String) = Unit
}
