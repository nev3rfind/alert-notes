package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.DeviceRemoteDataSource
import com.alertnotes.data.remote.UserProfileRemoteDataSource
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.model.AuthUser
import com.alertnotes.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val profileDataSource: UserProfileRemoteDataSource,
    private val deviceDataSource: DeviceRemoteDataSource,
    private val logger: AppLogger,
) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AuthUser?
        get() = auth.currentUser?.toAuthUser()

    override suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): AuthUser {
        // Checked before the account exists so a taken name costs nothing.
        // The reservation batch below is the authoritative guard; this is
        // the fast, user-friendly path.
        if (!runAuthOp { profileDataSource.isUsernameAvailable(username) }) {
            throw AuthException(AuthError.USERNAME_TAKEN)
        }
        val user = runAuthOp {
            auth.createUserWithEmailAndPassword(email, password).await().user
        } ?: throw AuthException(AuthError.UNKNOWN)
        // Everything after account creation must succeed or the account is
        // rolled back — a half-registered user (no profile document) would
        // be locked out of their username forever.
        try {
            runAuthOp {
                user.updateProfile(
                    userProfileChangeRequest { this.displayName = displayName },
                ).await()
                profileDataSource.createProfile(
                    uid = user.uid,
                    displayName = displayName,
                    username = username,
                    email = email,
                )
            }
        } catch (exception: AuthException) {
            rollbackRegistration(user)
            throw exception
        }
        // Send the verification mail at registration, which is the only moment
        // the user is expecting it. Previously nothing was ever sent unless
        // the user found the Verify action buried in the profile screen, so
        // essentially every account stayed unverified - which made the
        // verified flag meaningless as an authorization input. Best-effort:
        // a mail-send failure must not undo a good registration.
        runCatching { user.sendEmailVerification().await() }
            .onFailure { logger.w(TAG, "Verification mail could not be sent", it) }
        registerDevice(user)
        markOnline(user)
        // i-level logs ship in release builds and must stay free of user
        // data, so no uid/email here.
        logger.i(TAG, "Account registered")
        return user.toAuthUser()
    }

    override suspend fun signIn(email: String, password: String): AuthUser {
        val user = runAuthOp {
            auth.signInWithEmailAndPassword(email, password).await().user
        } ?: throw AuthException(AuthError.UNKNOWN)
        // Bookkeeping only — a failed timestamp write must not fail login.
        runCatching { profileDataSource.touchLastLogin(user.uid) }
            .onFailure { logger.w(TAG, "lastLogin update failed", it) }
        registerDevice(user)
        markOnline(user)
        logger.i(TAG, "Signed in")
        return user.toAuthUser()
    }

    override suspend fun sendPasswordReset(email: String) {
        runAuthOp { auth.sendPasswordResetEmail(email).await() }
    }

    override suspend fun signOut() {
        // Best-effort farewell while the rules still allow the writes: mark
        // OFFLINE and drop this device's push token so a signed-out device
        // can never receive another notification for the account.
        //
        // Time-boxed, because a Firestore write Task only completes when the
        // BACKEND acknowledges it. With no connection the write is queued
        // locally and the Task never completes, so awaiting it here used to
        // hang signOut() forever — the button appeared to do nothing. The
        // queued writes still flush on reconnect; the timeout only stops the
        // session teardown waiting for them.
        auth.currentUser?.let { user ->
            withTimeoutOrNull(FAREWELL_TIMEOUT_MILLIS) {
                runCatching {
                    profileDataSource.setPresence(
                        user.uid,
                        com.alertnotes.domain.model.PresenceState.OFFLINE,
                    )
                }.onFailure { logger.w(TAG, "Offline presence write failed", it) }
                runCatching { deviceDataSource.clearPushToken(user.uid) }
                    .onFailure { logger.w(TAG, "Push token clear failed", it) }
            } ?: logger.w(TAG, "Farewell writes timed out — signing out anyway")
        }
        auth.signOut()
        logger.i(TAG, "Signed out")
    }

    override suspend fun deleteAccount(currentPassword: String) {
        val user = auth.currentUser ?: throw AuthException(AuthError.UNKNOWN)
        val email = user.email ?: throw AuthException(AuthError.UNKNOWN)
        // Firebase refuses deletion on a session older than a few minutes, so
        // re-authenticate first. This doubles as the confirmation step: the
        // person holding the phone must know the password.
        runAuthOp {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        }
        // Release the username immediately. It is the one document another
        // account can be blocked by, and the client is the only party the
        // rules let delete it. Time-boxed for the same reason as signOut.
        withTimeoutOrNull(FAREWELL_TIMEOUT_MILLIS) {
            runCatching { profileDataSource.releaseUsername(user.uid) }
                .onFailure { logger.w(TAG, "Username release failed", it) }
            runCatching { deviceDataSource.clearPushToken(user.uid) }
                .onFailure { logger.w(TAG, "Push token clear failed", it) }
        }
        // Deleting the auth user fires the server-side cascade that removes
        // every remaining document and Storage object. Doing it last means a
        // failure here leaves the account intact rather than orphaned.
        runAuthOp { user.delete().await() }
        logger.i(TAG, "Account deleted")
    }

    private suspend fun rollbackRegistration(user: FirebaseUser) {
        runCatching { user.delete().await() }
            .onFailure { logger.e(TAG, "Registration rollback failed", it) }
    }

    /**
     * Adds this device to the account's device registry. Bookkeeping like
     * lastLogin: a failure is logged, never surfaced — the session is
     * already established and must not be torn down over it.
     */
    private suspend fun registerDevice(user: FirebaseUser) {
        runCatching { deviceDataSource.registerCurrentDevice(user.uid) }
            .onFailure { logger.w(TAG, "Device registration failed", it) }
    }

    /** Presence bookkeeping — the app is in the foreground when this runs. */
    private suspend fun markOnline(user: FirebaseUser) {
        runCatching {
            profileDataSource.setPresence(
                user.uid,
                com.alertnotes.domain.model.PresenceState.ONLINE,
            )
        }.onFailure { logger.w(TAG, "Online presence write failed", it) }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        isEmailVerified = isEmailVerified,
    )

    private companion object {
        const val TAG = "AuthRepository"

        /**
         * Ceiling on the best-effort writes that precede ending a session.
         * Long enough to land on a working connection, short enough that a
         * user on a plane still gets signed out promptly.
         */
        const val FAREWELL_TIMEOUT_MILLIS = 3_000L
    }
}
