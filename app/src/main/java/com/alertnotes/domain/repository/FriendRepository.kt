package com.alertnotes.domain.repository

import com.alertnotes.domain.model.FamilyInvitationWithProfile
import com.alertnotes.domain.model.FamilyMember
import com.alertnotes.domain.model.FamilyPermissions
import com.alertnotes.domain.model.FamilyState
import com.alertnotes.domain.model.FriendRequestWithProfile
import com.alertnotes.domain.model.FriendUser
import com.alertnotes.domain.model.FriendshipState
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.PublicStatistics
import kotlinx.coroutines.flow.Flow

/**
 * The friend graph, backed by Firestore snapshot listeners so both sides of
 * every action update live. Action functions throw
 * [com.alertnotes.domain.model.FriendException] with a user-mappable reason.
 * All state transitions are also the validation points a future Cloud
 * Function enforces server-side; the client never assumes more authority
 * than the security rules grant it.
 */
interface FriendRepository {

    /** Accepted friends with live public profiles, newest friendship first. */
    val friends: Flow<List<FriendUser>>

    /** Pending requests addressed to the signed-in user, newest first. */
    val incomingRequests: Flow<List<FriendRequestWithProfile>>

    /** Pending requests the signed-in user sent, newest first. */
    val outgoingRequests: Flow<List<FriendRequestWithProfile>>

    /** Prefix search by @username (primary) and display name (secondary). */
    suspend fun search(query: String): List<FriendUser>

    /** Live public profile of any user; null while missing/deleted. */
    fun observePublicProfile(uid: String): Flow<PublicProfile?>

    /** Live relationship with [uid]; drives the profile action button. */
    fun observeFriendshipState(uid: String): Flow<FriendshipState>

    suspend fun sendRequest(toUid: String)

    suspend fun acceptRequest(requestId: String)

    suspend fun rejectRequest(requestId: String)

    suspend fun cancelRequest(requestId: String)

    suspend fun removeFriend(friendUid: String)

    // region Family

    /** Family members with live profiles and their per-edge permissions. */
    val family: Flow<List<FamilyMember>>

    val incomingFamilyInvitations: Flow<List<FamilyInvitationWithProfile>>

    val outgoingFamilyInvitations: Flow<List<FamilyInvitationWithProfile>>

    /** Live family relationship with [uid]; drives profile/search badges. */
    fun observeFamilyState(uid: String): Flow<FamilyState>

    /** Another user's shareable counters for their public profile. */
    suspend fun publicStatistics(uid: String): PublicStatistics

    /** Only existing friends can be invited; optional personal [message]. */
    suspend fun inviteToFamily(toUid: String, message: String)

    suspend fun acceptFamilyInvitation(invitationId: String)

    suspend fun declineFamilyInvitation(invitationId: String)

    suspend fun cancelFamilyInvitation(invitationId: String)

    suspend fun removeFamilyMember(memberUid: String)

    /** Rewrites what [memberUid] may do toward the signed-in user. */
    suspend fun setFamilyPermissions(memberUid: String, permissions: FamilyPermissions)

    // endregion

    // region Blocking

    /** Users the signed-in user has blocked, with live profiles. */
    val blockedUsers: Flow<List<FriendUser>>

    /**
     * Blocks [uid]: severs any friendship and family relationship, then
     * records the block. Blocked users can't message, share reminders, or
     * invite; search results hide them. Reminders already received stay.
     */
    suspend fun blockUser(uid: String)

    suspend fun unblockUser(uid: String)

    /** Cheap membership check for chat/sharing guards. */
    suspend fun isBlocked(uid: String): Boolean

    // endregion
}
