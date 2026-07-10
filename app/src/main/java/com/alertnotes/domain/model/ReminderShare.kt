package com.alertnotes.domain.model

import java.time.Instant

/**
 * Lifecycle of a shared reminder. Unknown persisted values read as
 * CANCELLED so a malformed document can never demand action.
 */
enum class ShareStatus {
    /** Sent, awaiting the recipient's approval (friend workflow). */
    PENDING,

    /** Recipient approved; download in progress on their device. */
    ACCEPTED,
    REJECTED,

    /** Family auto-delivery: released to the recipient, no approval step. */
    DELIVERED,

    /** Stored and scheduled on the recipient's device. */
    SCHEDULED,

    /** The reminder fired on the recipient's device (future emission). */
    TRIGGERED,

    /** The recipient completed/acknowledged it (future emission). */
    COMPLETED,

    /** Owner withdrew the share. */
    CANCELLED,
}

/**
 * One shared-reminder edge, owner → recipient, id `{owner}_{rid}_{recipient}`.
 * The owner's copy stays in their local Room database; [payload] carries the
 * full reminder (versioned backup-DTO JSON) uploaded ONLY for explicitly
 * shared reminders, so the recipient can reconstruct it locally and keep it
 * working offline. [approvalRequired] captures the family-permission
 * decision at share time — the future Cloud Function's enforcement point.
 */
data class ReminderShare(
    val id: String,
    /** Local reminder id in the owner's database (stable per owner). */
    val reminderId: Long,
    val ownerUid: String,
    val recipientUid: String,
    val relationship: RelationshipType,
    val approvalRequired: Boolean,
    val status: ShareStatus,
    /** Title snapshot for previews before acceptance. */
    val title: String,
    /** Human-readable schedule preview shown on the invitation. */
    val scheduleSummary: String,
    /** Full reminder body (backup-DTO JSON); empty only on legacy docs. */
    val payload: String,
    /** Recipient's local reminder id once delivered; the dedup guard. */
    val recipientReminderId: Long?,
    val createdAt: Instant?,
    val respondedAt: Instant?,
    val scheduledAt: Instant?,
    val lastSyncAt: Instant?,
)

/** A share joined with the other party's public profile, for lists. */
data class ReminderShareWithProfile(
    val share: ReminderShare,
    val profile: PublicProfile,
)
