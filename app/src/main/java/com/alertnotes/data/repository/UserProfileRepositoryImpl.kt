package com.alertnotes.data.repository

import android.net.Uri
import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.AvatarStorageDataSource
import com.alertnotes.data.remote.DeviceRemoteDataSource
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.data.remote.UserProfileRemoteDataSource
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.model.AvatarUpload
import com.alertnotes.domain.model.PrivacyAudience
import com.alertnotes.domain.model.PrivacyControl
import com.alertnotes.domain.model.PrivacySettings
import com.alertnotes.domain.model.PrivateProfile
import com.alertnotes.domain.model.ProfileMetadata
import com.alertnotes.domain.model.ProfileStatistics
import com.alertnotes.domain.model.ProfileTheme
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.SecurityInfo
import com.alertnotes.domain.model.UserProfile
import com.alertnotes.domain.model.ViewerRelation
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.UserProfileRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository,
    private val profileDataSource: UserProfileRemoteDataSource,
    private val avatarDataSource: AvatarStorageDataSource,
    private val deviceDataSource: DeviceRemoteDataSource,
    private val logger: AppLogger,
) : UserProfileRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val profile: Flow<UserProfile?> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                combine(
                    profileDataSource.observeSection(user.uid, FirestoreSchema.SECTION_PUBLIC),
                    profileDataSource.observeSection(user.uid, FirestoreSchema.SECTION_PRIVATE),
                    profileDataSource.observeSection(user.uid, FirestoreSchema.SECTION_SECURITY),
                    profileDataSource.observeSection(user.uid, FirestoreSchema.SECTION_STATISTICS),
                    profileDataSource.observeSection(user.uid, FirestoreSchema.SECTION_METADATA),
                ) { publicDoc, privateDoc, securityDoc, statisticsDoc, metadataDoc ->
                    UserProfile(
                        uid = user.uid,
                        // The owner always sees their own profile in full,
                        // whatever audiences they have configured for others.
                        publicProfile = publicDoc.toPublicProfile(ViewerRelation.SELF),
                        privateProfile = privateDoc.toPrivateProfile(),
                        security = securityDoc.toSecurityInfo(),
                        statistics = statisticsDoc.toProfileStatistics(),
                        metadata = metadataDoc.toProfileMetadata(),
                    )
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val deviceCount: Flow<Int> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) flowOf(0) else deviceDataSource.observeDeviceCount(user.uid)
        }

    override suspend fun updateDisplayName(displayName: String) {
        val user = requireUser()
        runAuthOp {
            user.updateProfile(
                userProfileChangeRequest { this.displayName = displayName },
            ).await()
            profileDataSource.updatePublicFields(
                user.uid,
                mapOf("displayName" to displayName),
            )
        }
    }

    override suspend fun updateStatusMessage(statusMessage: String) {
        val user = requireUser()
        runAuthOp {
            profileDataSource.updatePublicFields(
                user.uid,
                mapOf("statusMessage" to statusMessage),
            )
        }
    }

    override suspend fun updateBannerTheme(theme: ProfileTheme) {
        val user = requireUser()
        runAuthOp {
            profileDataSource.updatePublicFields(
                user.uid,
                mapOf("bannerTheme" to theme.name),
            )
        }
    }

    override suspend fun changeUsername(username: String) {
        val user = requireUser()
        runAuthOp { profileDataSource.changeUsername(user.uid, username) }
    }

    override fun uploadAvatar(imageBytes: ByteArray): Flow<AvatarUpload> = channelFlow {
        val user = requireUser()
        // Progress arrives on a Storage listener thread; channelFlow's
        // trySend is the safe bridge back into the flow.
        val url = runAuthOp {
            avatarDataSource.upload(user.uid, imageBytes) { fraction ->
                trySend(AvatarUpload.InProgress(fraction))
            }
        }
        runAuthOp {
            user.updateProfile(
                userProfileChangeRequest { photoUri = Uri.parse(url) },
            ).await()
            profileDataSource.updatePublicFields(user.uid, mapOf("photoUrl" to url))
        }
        logger.i(TAG, "Avatar uploaded")
        send(AvatarUpload.Finished(url))
    }

    override suspend fun removeAvatar() {
        val user = requireUser()
        runAuthOp {
            avatarDataSource.delete(user.uid)
            user.updateProfile(userProfileChangeRequest { photoUri = null }).await()
            profileDataSource.updatePublicFields(user.uid, mapOf("photoUrl" to null))
        }
    }

    override suspend fun sendEmailVerification() {
        val user = requireUser()
        runAuthOp { user.sendEmailVerification().await() }
    }

    override suspend fun refreshEmailVerified(): Boolean {
        val user = requireUser()
        runAuthOp { user.reload().await() }
        val verified = user.isEmailVerified
        // Mirror only — the auth account is the source of truth here.
        runCatching { profileDataSource.setEmailVerified(user.uid, verified) }
            .onFailure { logger.w(TAG, "emailVerified mirror failed", it) }
        return verified
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = requireUser()
        val email = user.email
            ?: throw AuthException(AuthError.UNKNOWN)
        runAuthOp {
            // Recent authentication is required for password changes; the
            // current password doubles as the user's confirmation.
            user.reauthenticate(
                EmailAuthProvider.getCredential(email, currentPassword),
            ).await()
            user.updatePassword(newPassword).await()
        }
        runCatching { profileDataSource.touchPasswordChange(user.uid) }
            .onFailure { logger.w(TAG, "lastPasswordChange stamp failed", it) }
        logger.i(TAG, "Password changed")
    }

    override suspend fun syncPreference(key: String, value: Any) {
        val user = auth.currentUser ?: return
        profileDataSource.syncPreference(user.uid, key, value)
    }

    override suspend fun setPresence(state: com.alertnotes.domain.model.PresenceState) {
        val user = auth.currentUser ?: return
        // NOBODY is enforced by not writing the value at all, so there is
        // nothing in the document for a modified client to read. The narrower
        // audiences are applied when a profile is mapped for a viewer.
        val privacy = currentPrivacy()
        profileDataSource.setPresence(
            uid = user.uid,
            state = state,
            hidePresence = privacy[PrivacyControl.ONLINE_STATUS] == PrivacyAudience.NOBODY,
            hideLastSeen = privacy[PrivacyControl.LAST_SEEN] == PrivacyAudience.NOBODY,
        )
    }

    override suspend fun updatePrivacy(settings: PrivacySettings) {
        val user = requireUser()
        runAuthOp {
            profileDataSource.updatePrivacy(user.uid, settings)
            // Presence is republished immediately so switching a status to (or
            // away from) "nobody" takes effect now rather than at the next
            // heartbeat, which can be minutes away.
            profileDataSource.setPresence(
                uid = user.uid,
                state = com.alertnotes.domain.model.PresenceState.ONLINE,
                hidePresence = settings[PrivacyControl.ONLINE_STATUS] == PrivacyAudience.NOBODY,
                hideLastSeen = settings[PrivacyControl.LAST_SEEN] == PrivacyAudience.NOBODY,
            )
        }
        logger.i(TAG, "Privacy settings updated")
    }

    /** The live privacy map, falling back to defaults when unavailable. */
    private suspend fun currentPrivacy(): PrivacySettings =
        runCatching { profile.first()?.publicProfile?.privacy }
            .getOrNull() ?: PrivacySettings.DEFAULT

    private fun requireUser(): FirebaseUser =
        auth.currentUser ?: throw AuthException(AuthError.UNKNOWN)

    private fun DocumentSnapshot?.toProfileStatistics(): ProfileStatistics = ProfileStatistics(
        friendCount = intOf("friendCount"),
        familyCount = intOf("familyCount"),
        sharedReminderCount = intOf("sharedReminderCount"),
        receivedReminderCount = intOf("receivedReminderCount"),
        acknowledgedReminderCount = intOf("acknowledgedReminderCount"),
        chatCount = intOf("chatCount"),
    )

    private fun DocumentSnapshot?.intOf(field: String): Int =
        this?.getLong(field)?.toInt() ?: 0

    private fun DocumentSnapshot?.toPrivateProfile(): PrivateProfile = PrivateProfile(
        email = this?.getString("email").orEmpty(),
        deviceModel = this?.getString("deviceModel").orEmpty(),
        androidVersion = this?.getString("androidVersion").orEmpty(),
        memberSince = instantOf("memberSince"),
    )

    private fun DocumentSnapshot?.toSecurityInfo(): SecurityInfo = SecurityInfo(
        emailVerified = this?.getBoolean("emailVerified") == true,
        multiFactorEnabled = this?.getBoolean("multiFactorEnabled") == true,
        lastPasswordChange = instantOf("lastPasswordChange"),
        loginProvider = this?.getString("loginProvider").orEmpty(),
    )

    private fun DocumentSnapshot?.toProfileMetadata(): ProfileMetadata = ProfileMetadata(
        createdAt = instantOf("createdAt"),
        lastLogin = instantOf("lastLogin"),
        appVersion = this?.getString("appVersion").orEmpty(),
    )

    private fun DocumentSnapshot?.instantOf(field: String): Instant? =
        this?.getTimestamp(field)?.toDate()?.toInstant()

    private companion object {
        const val TAG = "UserProfileRepository"
    }
}
