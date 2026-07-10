package com.alertnotes.domain.model

import java.time.Instant

/**
 * Lifecycle of a shared reminder. Delivery/scheduling integration lands in a
 * later session; these states already model the full flow so the Firestore
 * shape and rules are stable now. Unknown persisted values read as REVOKED.
 */
enum class ShareStatus {
    /** Sent, awaiting the recipient's approval (approval-required shares). */
    PENDING,

    /** Recipient accepted; eligible for delivery to their device. */
    ACCEPTED,
    DECLINED,

    /** Family auto-delivery: no approval step, delivered straight through. */
    AUTO_ACCEPTED,

    /** Owner withdrew the share, or it lapsed. */
    REVOKED,
}

/**
 * One shared-reminder edge, owner → recipient. The reminder itself stays in
 * the owner's local Room database (the source of truth); this cloud record
 * carries only the sharing relationship and a lightweight snapshot for the
 * recipient to preview before the delivery layer exists.
 *
 * [relationship] ties a share to the FRIEND/FAMILY graph so a future Cloud
 * Function can enforce "family may auto-deliver, friends need approval"
 * server-side. [approvalRequired] captures that decision at share time.
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
    /** Human-readable snapshot so the recipient sees something pre-delivery. */
    val title: String,
    val createdAt: Instant?,
)

/** A share joined with the other party's public profile, for lists. */
data class ReminderShareWithProfile(
    val share: ReminderShare,
    val profile: PublicProfile,
)
