package com.alertnotes.domain.model

import java.time.Instant

/**
 * Realtime presence. ONLINE = app foregrounded (heartbeat fresh); AWAY = app
 * backgrounded but recently active; OFFLINE = signed out or gone. A stale
 * heartbeat reads as OFFLINE on the consumer side — a force-killed process
 * never gets to say goodbye.
 */
enum class PresenceState { ONLINE, AWAY, OFFLINE }

/** The `public` profile section — everything other users may eventually see. */
data class PublicProfile(
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val statusMessage: String = "",
    val online: Boolean = false,
    val presence: PresenceState = PresenceState.OFFLINE,
    val lastSeen: Instant? = null,
    /** Banner + accent personalisation; public so friends see it too. */
    val bannerTheme: ProfileTheme = ProfileTheme.PRIMARY_ORANGE,
    /**
     * The owner's privacy configuration. It lives in this section — not one of
     * its own — so the security rule guarding a profile read can consult it
     * without a second billed lookup. Readers use it to hide audience-scoped
     * fields; the rules use it to refuse audience-scoped writes.
     */
    val privacy: PrivacySettings = PrivacySettings.DEFAULT,
)

/** The `private` section — visible to the account owner only. */
data class PrivateProfile(
    val email: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val memberSince: Instant? = null,
)

/** The `security` section — auth posture, never other users' business. */
data class SecurityInfo(
    val emailVerified: Boolean = false,
    val multiFactorEnabled: Boolean = false,
    val lastPasswordChange: Instant? = null,
    val loginProvider: String = "",
)

/** The `metadata` section — application bookkeeping. */
data class ProfileMetadata(
    val createdAt: Instant? = null,
    val lastLogin: Instant? = null,
    val appVersion: String = "",
)

/**
 * The `statistics` section. Sharing/friend/family/chat counters stay at
 * their registration defaults until those features ship; the dashboard
 * renders them already so the architecture is proven.
 */
data class ProfileStatistics(
    val friendCount: Int = 0,
    val familyCount: Int = 0,
    val sharedReminderCount: Int = 0,
    val receivedReminderCount: Int = 0,
    val acknowledgedReminderCount: Int = 0,
    val chatCount: Int = 0,
)

/** The signed-in user's complete profile, combined from all sections. */
data class UserProfile(
    val uid: String,
    val publicProfile: PublicProfile = PublicProfile(),
    val privateProfile: PrivateProfile = PrivateProfile(),
    val security: SecurityInfo = SecurityInfo(),
    val statistics: ProfileStatistics = ProfileStatistics(),
    val metadata: ProfileMetadata = ProfileMetadata(),
)

/** Progress of an avatar change, surfaced as the hero card's animation. */
sealed interface AvatarUpload {
    /** Fraction is 0..1; bytes are on their way to Storage. */
    data class InProgress(val fraction: Float) : AvatarUpload

    data class Finished(val photoUrl: String?) : AvatarUpload
}
