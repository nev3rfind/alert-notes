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

    /** Ends the session. Local data is never touched. */
    fun signOut()
}
