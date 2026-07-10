package com.alertnotes.domain.model

import java.time.Instant

/**
 * Kinds of trusted relationships. New types (e.g. COLLEAGUE) slot in as new
 * values plus their own edge subcollection — nothing pair-specific is
 * hardcoded anywhere.
 */
enum class RelationshipType {
    FRIEND,
    FAMILY,
}

/**
 * What ONE side of a family edge is allowed to do toward the edge's owner.
 * Stored per direction, so parent→child (one-way automatic reminders) and
 * partners (two-way) are both just different flag combinations. Defaults are
 * the trusting two-way setup; the future sharing/chat features read these.
 */
data class FamilyPermissions(
    /** Their reminders can land on my device without per-reminder approval. */
    val autoReceiveReminders: Boolean = true,
    val canSendWithoutApproval: Boolean = true,
    val canSendWithApproval: Boolean = true,
    val canViewOnlineStatus: Boolean = true,
    val canViewLastSeen: Boolean = true,
    val canStartChat: Boolean = true,
    val canRemoveRelationship: Boolean = true,
    val canInviteBack: Boolean = true,
)

/** Family invitation lifecycle; unknown persisted values read as EXPIRED. */
enum class FamilyInvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED,
    EXPIRED,
}

/** One invitation edge; [id] is the deterministic `{fromUid}_{toUid}`. */
data class FamilyInvitation(
    val id: String,
    val fromUid: String,
    val toUid: String,
    val message: String,
    val status: FamilyInvitationStatus,
    val createdAt: Instant?,
)

data class FamilyInvitationWithProfile(
    val invitation: FamilyInvitation,
    val profile: PublicProfile,
)

/** An accepted family member with their live public profile. */
data class FamilyMember(
    val uid: String,
    val profile: PublicProfile,
    val since: Instant? = null,
    /** What this member may do toward me — editable in Family settings. */
    val permissions: FamilyPermissions = FamilyPermissions(),
)

/** Relationship between the signed-in user and another profile, per type. */
enum class FamilyState {
    NONE,
    INVITE_SENT,
    INVITE_RECEIVED,
    FAMILY,
}

/** Another user's shareable counters, shown on their public profile. */
data class PublicStatistics(
    val friendCount: Int = 0,
    val familyCount: Int = 0,
)
