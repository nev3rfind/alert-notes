package com.alertnotes.domain.model

/**
 * Every way an auth operation can fail, provider-agnostic. The UI maps each
 * value to a friendly message; raw provider exceptions never reach a screen.
 */
enum class AuthError {
    INVALID_EMAIL,

    /** Wrong email/password combination (deliberately not more specific). */
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    EMAIL_ALREADY_IN_USE,
    USERNAME_TAKEN,
    WEAK_PASSWORD,
    TOO_MANY_REQUESTS,
    ACCOUNT_DISABLED,

    /** No connection, or the auth backend was unreachable. */
    NETWORK,
    UNKNOWN,
}

/** Thrown by auth operations; [error] drives the user-facing message. */
class AuthException(
    val error: AuthError,
    cause: Throwable? = null,
) : Exception("Auth operation failed: $error", cause)
