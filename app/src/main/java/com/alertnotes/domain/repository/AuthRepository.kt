package com.alertnotes.domain.repository

import com.alertnotes.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the online-mode account. Backed by Firebase Authentication in
 * the data layer; the session persists across process restarts until
 * [signOut]. Every suspend function throws
 * [com.alertnotes.domain.model.AuthException] on failure.
 */
interface AuthRepository {

    /** The signed-in user, or null. Emits on every session change. */
    val authState: Flow<AuthUser?>

    /** Snapshot of the current session without waiting for the flow. */
    val currentUser: AuthUser?

    /**
     * Creates the account and its cloud profile. The username is reserved
     * case-insensitively; if any step after account creation fails the
     * account is rolled back so registration is all-or-nothing.
     */
    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): AuthUser

    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun sendPasswordReset(email: String)

    /**
     * Ends the session, marking the public profile offline first (while the
     * write is still authorized). Local data is never touched.
     *
     * The farewell writes are strictly best-effort and time-boxed: signing out
     * must succeed even with no connection.
     */
    suspend fun signOut()

    /**
     * Permanently deletes the account and everything the cloud holds about it.
     *
     * Google Play requires an in-app path to this for any app that lets users
     * create an account. [currentPassword] is required because Firebase treats
     * deletion as a sensitive operation and rejects it on a stale session.
     *
     * Local reminders are deliberately left alone — deleting the cloud account
     * returns the app to offline mode, it does not wipe the user's data off
     * their own device. The cloud cascade (profile sections, username
     * reservation, relationship edges on both sides, shares, chats, avatars and
     * acknowledgement proofs) runs server-side in response to the deletion, so
     * it completes even if the app is killed mid-way.
     */
    suspend fun deleteAccount(currentPassword: String)
}
