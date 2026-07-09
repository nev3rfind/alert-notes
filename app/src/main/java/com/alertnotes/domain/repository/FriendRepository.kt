package com.alertnotes.domain.repository

import com.alertnotes.domain.model.FriendRequestWithProfile
import com.alertnotes.domain.model.FriendUser
import com.alertnotes.domain.model.FriendshipState
import com.alertnotes.domain.model.PublicProfile
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
}
