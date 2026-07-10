package com.alertnotes.domain.model

import java.time.Instant

/** Lifecycle of a friend request. Unknown persisted values read as EXPIRED. */
enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED,
}

/** One request edge; [id] is the deterministic `{fromUid}_{toUid}`. */
data class FriendRequest(
    val id: String,
    val fromUid: String,
    val toUid: String,
    val status: FriendRequestStatus,
    val createdAt: Instant?,
)

/** A request joined with the other party's public profile for display. */
data class FriendRequestWithProfile(
    val request: FriendRequest,
    val profile: PublicProfile,
)

/** An accepted friend joined with their live public profile. */
data class FriendUser(
    val uid: String,
    val profile: PublicProfile,
    val since: Instant? = null,
)

/** Relationship between the signed-in user and another profile. */
enum class FriendshipState {
    SELF,
    FRIENDS,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    NONE,
}

/** Why a relationship action was refused; mapped to friendly UI messages. */
enum class FriendError {
    SELF_REQUEST,
    ALREADY_FRIENDS,
    ALREADY_PENDING,
    /** Family invitations require an existing friendship. */
    NOT_FRIENDS,
    ALREADY_FAMILY,
    /** Firestore rejected the operation — security rules are out of date. */
    PERMISSION,
    NETWORK,
    UNKNOWN,
}

class FriendException(val error: FriendError, cause: Throwable? = null) :
    Exception("Friend action failed: $error", cause)
