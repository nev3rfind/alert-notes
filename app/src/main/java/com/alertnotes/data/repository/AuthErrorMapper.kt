package com.alertnotes.data.repository

import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/**
 * Runs [block], translating provider exceptions to [AuthException] so
 * nothing above the data layer ever sees a Firebase type. Shared by the
 * auth and profile repositories.
 */
internal inline fun <T> runAuthOp(block: () -> T): T = try {
    block()
} catch (exception: AuthException) {
    throw exception
} catch (exception: Exception) {
    throw AuthException(exception.toAuthError(), exception)
}

internal fun Exception.toAuthError(): AuthError = when (this) {
    is FirebaseNetworkException -> AuthError.NETWORK
    is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
    is IOException -> AuthError.NETWORK
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }

    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> AuthError.INVALID_EMAIL
        // Firebase reports all three for bad email/password pairs,
        // depending on backend configuration.
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS",
        -> AuthError.INVALID_CREDENTIALS

        "ERROR_USER_NOT_FOUND" -> AuthError.USER_NOT_FOUND
        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EMAIL_ALREADY_IN_USE
        "ERROR_WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
        "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_REQUESTS
        "ERROR_USER_DISABLED" -> AuthError.ACCOUNT_DISABLED
        else -> AuthError.UNKNOWN
    }

    else -> AuthError.UNKNOWN
}
