package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.DeviceRemoteDataSource
import com.alertnotes.data.remote.UserProfileRemoteDataSource
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.model.AuthUser
import com.alertnotes.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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
        auth.currentUser?.let { user ->
            runCatching {
                profileDataSource.setPresence(
                    user.uid,
                    com.alertnotes.domain.model.PresenceState.OFFLINE,
                )
            }.onFailure { logger.w(TAG, "Offline presence write failed", it) }
            runCatching { deviceDataSource.clearPushToken(user.uid) }
                .onFailure { logger.w(TAG, "Push token clear failed", it) }
        }
        auth.signOut()
        logger.i(TAG, "Signed out")
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
    )

    private companion object {
        const val TAG = "AuthRepository"
    }
}
