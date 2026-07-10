package com.alertnotes.data.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.alertnotes.BuildConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The account's device registry, `users/{uid}/devices/{deviceId}` — one
 * document per phone or tablet signed in to the account. Registration runs
 * after every login so `lastSeen`, OS and app versions stay current; the
 * future push layer stores each device's FCM token in `pushToken`, and
 * future device management revokes access by flipping `active`.
 */
@Singleton
class DeviceRemoteDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
) {

    /**
     * Creates or refreshes this device's document. `pushToken` is written
     * (null) only when the document is first created — later logins must
     * never wipe a token the push layer has stored.
     */
    suspend fun registerCurrentDevice(uid: String) {
        val deviceId = currentDeviceId()
        val document = firestore.collection(FirestoreSchema.USERS)
            .document(uid)
            .collection(FirestoreSchema.DEVICES)
            .document(deviceId)
        val refresh = mapOf(
            "deviceName" to deviceName(),
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "appVersion" to BuildConfig.VERSION_NAME,
            "lastSeen" to FieldValue.serverTimestamp(),
            "active" to true,
        )
        val snapshot = document.get().await()
        if (snapshot.exists()) {
            document.set(refresh, SetOptions.merge()).await()
        } else {
            document.set(
                refresh + mapOf(
                    "deviceId" to deviceId,
                    "pushToken" to null,
                ),
            ).await()
        }
    }

    /**
     * Stores this device's current FCM registration token. Multi-device is
     * structural — one token per device document. `pushTokenUpdatedAt` lets
     * the send layer prune tokens that have gone stale.
     */
    suspend fun updatePushToken(uid: String, token: String) {
        firestore.collection(FirestoreSchema.USERS)
            .document(uid)
            .collection(FirestoreSchema.DEVICES)
            .document(currentDeviceId())
            .set(
                mapOf(
                    "deviceId" to currentDeviceId(),
                    "pushToken" to token,
                    "pushTokenUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    /** Sign-out hygiene: a signed-out device must never be pushed to. */
    suspend fun clearPushToken(uid: String) {
        firestore.collection(FirestoreSchema.USERS)
            .document(uid)
            .collection(FirestoreSchema.DEVICES)
            .document(currentDeviceId())
            .set(mapOf("pushToken" to null), SetOptions.merge())
            .await()
    }

    /** Live count of registered devices; drives the profile dashboard. */
    fun observeDeviceCount(uid: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection(FirestoreSchema.USERS)
            .document(uid)
            .collection(FirestoreSchema.DEVICES)
            .addSnapshotListener { snapshot, error ->
                // Transient failures read as "no devices yet" and self-heal
                // on the next snapshot instead of killing the flow.
                trySend(if (error != null) 0 else snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Stable per-device identity. ANDROID_ID survives app reinstalls and is
     * unique per device + signing key, so the same phone maps to the same
     * document across sessions. The blank fallback covers the rare devices
     * that misreport it.
     */
    private fun currentDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .ifBlank { FALLBACK_DEVICE_ID }

    /** The user's own name for the device, falling back to the model. */
    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            .orEmpty()
            .ifBlank { Build.MODEL }

    private companion object {
        const val FALLBACK_DEVICE_ID = "unknown-device"
    }
}
