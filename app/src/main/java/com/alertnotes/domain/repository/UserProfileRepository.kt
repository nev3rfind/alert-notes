package com.alertnotes.domain.repository

import com.alertnotes.domain.model.AvatarUpload
import com.alertnotes.domain.model.ProfileTheme
import com.alertnotes.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's own profile: live view plus every self-service edit.
 * Backed by the sectioned Firestore documents and Firebase Storage in the
 * data layer. Every suspend function throws
 * [com.alertnotes.domain.model.AuthException] on failure so screens can show
 * the same friendly messages as the auth flow.
 */
interface UserProfileRepository {

    /**
     * The current user's profile, updating live as any section changes on
     * any device. Emits null while signed out.
     */
    val profile: Flow<UserProfile?>

    /** Live count of devices signed in to the account; 0 while signed out. */
    val deviceCount: Flow<Int>

    suspend fun updateDisplayName(displayName: String)

    /** Persists the banner/accent personalisation to the public section. */
    suspend fun updateBannerTheme(theme: ProfileTheme)

    suspend fun updateStatusMessage(statusMessage: String)

    /** Atomically re-reserves the username; fails if the new name is taken. */
    suspend fun changeUsername(username: String)

    /**
     * Uploads already-processed JPEG bytes as the avatar, then points the
     * public profile (and the auth account) at the new URL. Emits progress
     * along the way; the flow completes after [AvatarUpload.Finished].
     */
    fun uploadAvatar(imageBytes: ByteArray): Flow<AvatarUpload>

    suspend fun removeAvatar()

    suspend fun sendEmailVerification()

    /**
     * Re-reads the auth account and mirrors the verification flag into the
     * security section. Returns the fresh value.
     */
    suspend fun refreshEmailVerified(): Boolean

    /** Requires the current password (re-authentication) before changing. */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    /** Mirrors a locally-changed preference into the preferences section. */
    suspend fun syncPreference(key: String, value: Any)

    /** Presence transition or heartbeat; every write stamps lastSeen. */
    suspend fun setPresence(state: com.alertnotes.domain.model.PresenceState)
}
