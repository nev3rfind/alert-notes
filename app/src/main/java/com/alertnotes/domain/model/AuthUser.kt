package com.alertnotes.domain.model

/** The signed-in account, as much of it as the auth provider holds locally. */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    /**
     * Straight from the ID token, never from a Firestore field.
     *
     * The profile's `security.emailVerified` is a client-written mirror kept
     * only so the profile screen can render a badge without a token refresh -
     * it is display state and must never gate anything. This is the value the
     * security rules see as `request.auth.token.email_verified`, so gating the
     * UI on it means the UI and the server agree.
     */
    val isEmailVerified: Boolean = false,
)
