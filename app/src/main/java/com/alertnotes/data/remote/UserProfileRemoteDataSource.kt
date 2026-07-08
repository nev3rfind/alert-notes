package com.alertnotes.data.remote

import com.alertnotes.BuildConfig
import com.alertnotes.domain.repository.SettingsRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Firestore `users/{uid}` profile documents plus the `usernames/{username}`
 * reservation collection that keeps usernames unique. Only the data layer
 * talks to Firestore directly; domain code goes through
 * [com.alertnotes.domain.repository.AuthRepository].
 */
@Singleton
class UserProfileRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * True when no account has reserved [username] (matched lowercase).
     * Always asks the server — a cached miss must not hand out a name that
     * was taken from another device minutes ago.
     */
    suspend fun isUsernameAvailable(username: String): Boolean {
        val reservation = firestore.collection(USERNAMES_COLLECTION)
            .document(username.lowercase())
            .get(Source.SERVER)
            .await()
        return !reservation.exists()
    }

    /**
     * Creates the profile document and the username reservation atomically.
     * The stored username keeps the user's casing; the reservation id is the
     * lowercase form so lookups are case-insensitive.
     */
    suspend fun createProfile(
        uid: String,
        displayName: String,
        username: String,
        email: String,
    ) {
        val preferences = settingsRepository.preferences.first()
        val profile = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "username" to username,
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLogin" to FieldValue.serverTimestamp(),
            "photoUrl" to null,
            "role" to DEFAULT_ROLE,
            "theme" to preferences.themeMode.name,
            "notificationEnabled" to preferences.remindersNotificationsEnabled,
            "friendCount" to 0,
            "familyCount" to 0,
            "status" to DEFAULT_STATUS,
            "appVersion" to BuildConfig.VERSION_NAME,
            "devicePlatform" to DEVICE_PLATFORM,
        )
        val reservation = mapOf("uid" to uid)
        firestore.runBatch { batch ->
            batch.set(firestore.collection(USERS_COLLECTION).document(uid), profile)
            batch.set(
                firestore.collection(USERNAMES_COLLECTION).document(username.lowercase()),
                reservation,
            )
        }.await()
    }

    /**
     * Stamps a successful sign-in. Merge write so a profile that predates a
     * field (or failed to finish writing) is healed rather than rejected.
     */
    suspend fun touchLastLogin(uid: String) {
        val update = mapOf(
            "lastLogin" to FieldValue.serverTimestamp(),
            "appVersion" to BuildConfig.VERSION_NAME,
            "devicePlatform" to DEVICE_PLATFORM,
        )
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .set(update, SetOptions.merge())
            .await()
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val USERNAMES_COLLECTION = "usernames"
        const val DEFAULT_ROLE = "user"
        const val DEFAULT_STATUS = "active"
        const val DEVICE_PLATFORM = "android"
    }
}
