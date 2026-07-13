package com.alertnotes.domain.model

import java.time.Instant

/**
 * Who a reminder alerts. ONLY_ME never leaves the device; ME_AND_RECIPIENTS
 * keeps the creator's local alarm and additionally shares copies;
 * RECIPIENTS_ONLY assigns the reminder away — the creator's master copy is
 * archived (never scheduled, hidden from reminder lists) and lives on purely
 * as the editable source of truth tracked from Shared Reminders.
 */
enum class ReminderOwnership {
    ONLY_ME,
    ME_AND_RECIPIENTS,
    RECIPIENTS_ONLY,
}

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

    /** The reminder fired on the recipient's device. */
    TRIGGERED,

    /** A one-time reminder fired with no further occurrence left. */
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
 *
 * Edit propagation: every owner edit re-uploads [payload] and bumps
 * [payloadVersion]; [contentUpdatedAt] mirrors the owner's local
 * `updatedAt` so the sync sweep knows when the document is stale. Recipients
 * record what they run as [appliedVersion] — a gap means an update exists,
 * applied automatically under family auto-delivery or surfaced as an
 * approval card when [updateRequested] is set.
 */
data class ReminderShare(
    val id: String,
    /** Local reminder id in the owner's database (stable per owner). */
    val reminderId: Long,
    val ownerUid: String,
    val recipientUid: String,
    val relationship: RelationshipType,
    val approvalRequired: Boolean,
    val ownership: ReminderOwnership,
    val status: ShareStatus,
    /** Title snapshot for previews before acceptance. */
    val title: String,
    /** Human-readable schedule preview shown on the invitation. */
    val scheduleSummary: String,
    /** Full reminder body (backup-DTO JSON); empty only on legacy docs. */
    val payload: String,
    /** Monotonic content revision; bumped on every owner edit push. */
    val payloadVersion: Long,
    /** Revision the recipient's device currently runs. */
    val appliedVersion: Long,
    /** True when an edit awaits the recipient's approval. */
    val updateRequested: Boolean,
    /** Owner's local `updatedAt` (epoch millis) captured at last push. */
    val contentUpdatedAt: Long,
    /** Recipient's local reminder id once delivered; the dedup guard. */
    val recipientReminderId: Long?,
    /** Most recent recipient-side fire mirrored back for owner tracking. */
    val lastFiredAt: Instant?,
    /** How the recipient acknowledged the latest fire (null = not yet). */
    val ackMethod: AcknowledgeMethod? = null,
    val ackAt: Instant? = null,
    /** Seconds between the alert firing and the acknowledgement. */
    val ackDelaySeconds: Long? = null,
    /** Signature vector JSON when the acknowledgement was a signature. */
    val ackSignature: String = "",
    /** Storage URL of the live camera proof when the method was PHOTO. */
    val ackPhotoUrl: String = "",
    /** Location proof when the method was LOCATION. */
    val ackLat: Double? = null,
    val ackLng: Double? = null,
    val ackAccuracyM: Double? = null,
    val ackAddress: String = "",
    /** True when the reminder was closed without an obtainable location. */
    val ackLocationUnavailable: Boolean = false,
    val ackLocationNote: String = "",
    val createdAt: Instant?,
    val respondedAt: Instant?,
    val scheduledAt: Instant?,
    val lastSyncAt: Instant?,
) {
    /** An edit exists that the recipient's device has not applied yet. */
    val hasPendingUpdate: Boolean
        get() = recipientReminderId != null && payloadVersion > appliedVersion
}

/** A share joined with the other party's public profile, for lists. */
data class ReminderShareWithProfile(
    val share: ReminderShare,
    val profile: PublicProfile,
)
