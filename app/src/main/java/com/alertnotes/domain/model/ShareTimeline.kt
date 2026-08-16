package com.alertnotes.domain.model

import java.time.Instant

/**
 * One event on a shared reminder's audit trail. The SHARE DOCUMENT is the
 * single source of truth — every lifecycle step already stamps it — and
 * this event list is a pure derivation of that document. Nothing is stored
 * twice, no second status system exists, and every screen (tracking,
 * details, chat cards) renders the same derivation.
 */
data class ShareTimelineEvent(
    val type: ShareTimelineEventType,
    val at: Instant,
    /** Extra context: acknowledgement method, delay, failure notes. */
    val detail: String = "",
)

enum class ShareTimelineEventType {
    CREATED,
    ACCEPTED,
    REJECTED,
    SCHEDULED,
    UPDATED,
    TRIGGERED,
    ACKNOWLEDGED,
    COMPLETED,
    CANCELLED,
}

/**
 * The derived timeline, oldest first. Terminal responses map from the
 * share's status; acknowledgement carries its method and response delay so
 * renderers never recompute them differently.
 */
fun ReminderShare.timeline(): List<ShareTimelineEvent> {
    val events = mutableListOf<ShareTimelineEvent>()
    createdAt?.let { events += ShareTimelineEvent(ShareTimelineEventType.CREATED, it) }
    respondedAt?.let { at ->
        when (status) {
            ShareStatus.REJECTED ->
                events += ShareTimelineEvent(ShareTimelineEventType.REJECTED, at)

            ShareStatus.CANCELLED ->
                events += ShareTimelineEvent(ShareTimelineEventType.CANCELLED, at)

            else -> events += ShareTimelineEvent(ShareTimelineEventType.ACCEPTED, at)
        }
    }
    scheduledAt?.let { events += ShareTimelineEvent(ShareTimelineEventType.SCHEDULED, it) }
    if (payloadVersion > 1L) {
        // Content revisions: the latest push time is the sync stamp.
        lastSyncAt?.takeIf { hasPendingUpdate || appliedVersion > 1L }?.let {
            events += ShareTimelineEvent(
                ShareTimelineEventType.UPDATED,
                it,
                detail = "v$payloadVersion",
            )
        }
    }
    lastFiredAt?.let { events += ShareTimelineEvent(ShareTimelineEventType.TRIGGERED, it) }
    ackAt?.let { at ->
        events += ShareTimelineEvent(
            ShareTimelineEventType.ACKNOWLEDGED,
            at,
            detail = ackMethod?.name.orEmpty(),
        )
    }
    if (status == ShareStatus.COMPLETED) {
        (ackAt ?: lastFiredAt ?: lastSyncAt)?.let {
            events += ShareTimelineEvent(ShareTimelineEventType.COMPLETED, it)
        }
    }
    return events.sortedBy { it.at }
}
