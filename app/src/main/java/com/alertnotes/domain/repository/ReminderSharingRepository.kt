package com.alertnotes.domain.repository

import com.alertnotes.domain.model.ReminderShareWithProfile
import kotlinx.coroutines.flow.Flow

/**
 * The reminder-sharing edge layer — the cloud relationship between a local
 * reminder and the friend/family it's shared with. This session lays the
 * foundation only: shares can be created, observed, and responded to, but
 * nothing is delivered or scheduled on the recipient's device yet (that is a
 * later session, built on [com.alertnotes.domain.model.ShareStatus] and the
 * family permission flags). Backed by Firestore; the reminder body itself
 * never leaves the owner's local database here.
 */
interface ReminderSharingRepository {

    /** Shares the signed-in user has sent, newest first. */
    val outgoingShares: Flow<List<ReminderShareWithProfile>>

    /** Shares addressed to the signed-in user, newest first. */
    val incomingShares: Flow<List<ReminderShareWithProfile>>

    /**
     * Creates a share edge for a local reminder. [approvalRequired] is
     * derived by the caller from the relationship (family with
     * auto-delivery vs. friend), and recorded so a future Cloud Function can
     * enforce it. No delivery happens yet.
     */
    suspend fun shareReminder(
        reminderId: Long,
        reminderTitle: String,
        recipientUid: String,
    )

    suspend fun acceptShare(shareId: String)

    suspend fun declineShare(shareId: String)

    /** Owner withdraws a share; the recipient stops seeing it. */
    suspend fun revokeShare(shareId: String)
}
