package com.alertnotes.data.remote

import android.os.Build
import com.alertnotes.BuildConfig
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.model.PresenceState
import com.alertnotes.domain.model.PrivacySettings
import com.alertnotes.domain.repository.SettingsRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * The sectioned Firestore user profile plus the `usernames/{username}`
 * reservation collection that keeps usernames unique. Only the data layer
 * talks to Firestore directly; domain code goes through
 * [com.alertnotes.domain.repository.AuthRepository].
 *
 * Layout (see [FirestoreSchema] for why sections are separate documents):
 * ```
 * users/{uid}                     anchor: { uid }
 *   ├── public/data              displayName, username, photoUrl,
 *   │                            statusMessage, online, lastSeen
 *   ├── private/data             email, deviceModel, androidVersion,
 *   │                            memberSince, firebaseUid
 *   ├── preferences/data         theme, language, notificationEnabled,
 *   │                            timezone, applicationMode
 *   ├── security/data            emailVerified, multiFactorEnabled,
 *   │                            lastPasswordChange, loginProvider
 *   ├── statistics/data          friendCount, familyCount, …
 *   └── metadata/data            createdAt, lastLogin, lastProfileUpdate,
 *                                appVersion
 * ```
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
        val reservation = firestore.collection(FirestoreSchema.USERNAMES)
            .document(username.lowercase())
            .get(Source.SERVER)
            .await()
        return !reservation.exists()
    }

    /**
     * Creates every profile section and the username reservation in one
     * atomic batch. The stored username keeps the user's casing; the
     * reservation id is the lowercase form so lookups are case-insensitive.
     */
    suspend fun createProfile(
        uid: String,
        displayName: String,
        username: String,
        email: String,
    ) {
        val preferences = settingsRepository.preferences.first()

        val publicProfile = mapOf(
            "displayName" to displayName,
            "username" to username,
            "photoUrl" to null,
            "statusMessage" to "",
            "online" to false,
            "presenceState" to PresenceState.OFFLINE.name,
            "lastSeen" to FieldValue.serverTimestamp(),
            // Privacy ships with the profile itself: the security rule that
            // guards a profile read consults this map on the document it is
            // already reading, so no extra lookup is billed on that path.
            "privacy" to PrivacySettings.DEFAULT.toMap(),
            // Denormalised from profileVisibility because user search is a
            // collection-group query, and a rule cannot evaluate a per-result
            // condition on a query — only on a field the query filters by.
            "discoverable" to PrivacySettings.DEFAULT.isDiscoverable,
        )
        val privateProfile = mapOf(
            "email" to email,
            "deviceModel" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "memberSince" to FieldValue.serverTimestamp(),
            "firebaseUid" to uid,
        )
        val prefs = mapOf(
            "theme" to preferences.themeMode.name,
            "language" to Locale.getDefault().toLanguageTag(),
            "notificationEnabled" to preferences.remindersNotificationsEnabled,
            "timezone" to ZoneId.systemDefault().id,
            // Written during online registration, so the mode is ONLINE by
            // definition; kept for future cross-device sync.
            "applicationMode" to AppMode.ONLINE.name,
        )
        val security = mapOf(
            // New email/password accounts start unverified.
            "emailVerified" to false,
            "multiFactorEnabled" to false,
            // The password was just chosen at registration.
            "lastPasswordChange" to FieldValue.serverTimestamp(),
            "loginProvider" to LOGIN_PROVIDER_PASSWORD,
        )
        val statistics = mapOf(
            "friendCount" to 0,
            "familyCount" to 0,
            "sharedReminderCount" to 0,
            "receivedReminderCount" to 0,
            "acknowledgedReminderCount" to 0,
            "chatCount" to 0,
        )
        val metadata = mapOf(
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLogin" to FieldValue.serverTimestamp(),
            "lastProfileUpdate" to FieldValue.serverTimestamp(),
            "appVersion" to BuildConfig.VERSION_NAME,
        )
        val reservation = mapOf("uid" to uid)

        firestore.runBatch { batch ->
            batch.set(userDocument(uid), mapOf("uid" to uid))
            batch.set(section(uid, FirestoreSchema.SECTION_PUBLIC), publicProfile)
            batch.set(section(uid, FirestoreSchema.SECTION_PRIVATE), privateProfile)
            batch.set(section(uid, FirestoreSchema.SECTION_PREFERENCES), prefs)
            batch.set(section(uid, FirestoreSchema.SECTION_SECURITY), security)
            batch.set(section(uid, FirestoreSchema.SECTION_STATISTICS), statistics)
            batch.set(section(uid, FirestoreSchema.SECTION_METADATA), metadata)
            batch.set(
                firestore.collection(FirestoreSchema.USERNAMES)
                    .document(username.lowercase()),
                reservation,
            )
        }.await()
    }

    /**
     * Stamps a successful sign-in on the metadata section. Merge write so a
     * profile that predates a field (or failed to finish writing) is healed
     * rather than rejected.
     */
    suspend fun touchLastLogin(uid: String) {
        val update = mapOf(
            "lastLogin" to FieldValue.serverTimestamp(),
            "appVersion" to BuildConfig.VERSION_NAME,
        )
        section(uid, FirestoreSchema.SECTION_METADATA)
            .set(update, SetOptions.merge())
            .await()
    }

    /**
     * Live view of one profile section. Emits the current document
     * immediately, then again on every change from any device; null while
     * the document does not exist yet.
     */
    fun observeSection(uid: String, name: String): Flow<DocumentSnapshot?> = callbackFlow {
        val registration = section(uid, name).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Surface "no data yet" rather than killing the combined
                // profile flow — transient rule/network errors self-heal on
                // the next snapshot.
                trySend(null)
            } else {
                trySend(snapshot?.takeIf { it.exists() })
            }
        }
        awaitClose { registration.remove() }
    }

    /** Merge-updates public fields and stamps `metadata.lastProfileUpdate`. */
    suspend fun updatePublicFields(uid: String, fields: Map<String, Any?>) {
        firestore.runBatch { batch ->
            batch.set(section(uid, FirestoreSchema.SECTION_PUBLIC), fields, SetOptions.merge())
            batch.set(
                section(uid, FirestoreSchema.SECTION_METADATA),
                mapOf("lastProfileUpdate" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
        }.await()
    }

    /**
     * Renames the account. Case-only changes keep the existing reservation;
     * a real rename atomically releases the old name and claims the new one.
     * The availability check and the batch are not one transaction — the
     * same tiny race registration accepts, with the reservation write as the
     * authoritative record.
     */
    suspend fun changeUsername(uid: String, newUsername: String) {
        val current = section(uid, FirestoreSchema.SECTION_PUBLIC)
            .get()
            .await()
            .getString("username")
            .orEmpty()
        if (current == newUsername) return
        val isRename = !current.equals(newUsername, ignoreCase = true)
        if (isRename && !isUsernameAvailable(newUsername)) {
            throw AuthException(AuthError.USERNAME_TAKEN)
        }
        firestore.runBatch { batch ->
            if (isRename) {
                if (current.isNotBlank()) {
                    batch.delete(
                        firestore.collection(FirestoreSchema.USERNAMES)
                            .document(current.lowercase()),
                    )
                }
                batch.set(
                    firestore.collection(FirestoreSchema.USERNAMES)
                        .document(newUsername.lowercase()),
                    mapOf("uid" to uid),
                )
            }
            batch.set(
                section(uid, FirestoreSchema.SECTION_PUBLIC),
                mapOf("username" to newUsername),
                SetOptions.merge(),
            )
            batch.set(
                section(uid, FirestoreSchema.SECTION_METADATA),
                mapOf("lastProfileUpdate" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
        }.await()
    }

    /**
     * Frees this account's username reservation, called just before the
     * account itself is deleted.
     *
     * The security rules let only the holder delete their own reservation, so
     * this cannot be left to the server-side cascade running after the auth
     * user is gone — it has to happen while the session is still valid.
     */
    suspend fun releaseUsername(uid: String) {
        val username = section(uid, FirestoreSchema.SECTION_PUBLIC)
            .get()
            .await()
            .getString("username")
            .orEmpty()
        if (username.isBlank()) return
        firestore.collection(FirestoreSchema.USERNAMES)
            .document(username.lowercase())
            .delete()
            .await()
    }

    /**
     * Presence transition or heartbeat; lastSeen is stamped every write.
     *
     * [hidePresence] and [hideLastSeen] come from the owner's privacy
     * settings. When an audience is NOBODY the value is not merely filtered on
     * the way out — it is never written, so there is nothing in the document
     * for a modified client to read. Readers apply the narrower audiences (see
     * `toPublicProfile`), which is sound because those viewers are already
     * entitled to open the profile.
     */
    suspend fun setPresence(
        uid: String,
        state: com.alertnotes.domain.model.PresenceState,
        hidePresence: Boolean = false,
        hideLastSeen: Boolean = false,
    ) {
        val effective = if (hidePresence) PresenceState.OFFLINE else state
        val update = buildMap<String, Any?> {
            // Legacy boolean kept for older readers; presenceState is the
            // richer truth (ONLINE / AWAY / OFFLINE).
            put("online", effective == PresenceState.ONLINE)
            put("presenceState", effective.name)
            if (!hideLastSeen) put("lastSeen", FieldValue.serverTimestamp())
        }
        section(uid, FirestoreSchema.SECTION_PUBLIC)
            .set(update, SetOptions.merge())
            .await()
    }

    /**
     * Persists the privacy configuration, keeping the denormalised
     * `discoverable` flag in step. Both live on the public document, so this
     * is a single merge write and the two can never drift apart.
     */
    suspend fun updatePrivacy(uid: String, settings: PrivacySettings) {
        section(uid, FirestoreSchema.SECTION_PUBLIC)
            .set(
                mapOf(
                    "privacy" to settings.toMap(),
                    "discoverable" to settings.isDiscoverable,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun setEmailVerified(uid: String, verified: Boolean) {
        section(uid, FirestoreSchema.SECTION_SECURITY)
            .set(mapOf("emailVerified" to verified), SetOptions.merge())
            .await()
    }

    suspend fun touchPasswordChange(uid: String) {
        section(uid, FirestoreSchema.SECTION_SECURITY)
            .set(
                mapOf("lastPasswordChange" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    /** Mirrors one locally-changed preference for future cross-device sync. */
    suspend fun syncPreference(uid: String, key: String, value: Any) {
        section(uid, FirestoreSchema.SECTION_PREFERENCES)
            .set(mapOf(key to value), SetOptions.merge())
            .await()
    }

    private fun userDocument(uid: String): DocumentReference =
        firestore.collection(FirestoreSchema.USERS).document(uid)

    private fun section(uid: String, name: String): DocumentReference =
        userDocument(uid).collection(name).document(FirestoreSchema.SECTION_DOC)

    private companion object {
        const val LOGIN_PROVIDER_PASSWORD = "password"
    }
}
