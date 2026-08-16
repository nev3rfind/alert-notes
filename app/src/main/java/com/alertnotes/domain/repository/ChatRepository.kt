package com.alertnotes.domain.repository

import com.alertnotes.domain.model.ChatConversation
import com.alertnotes.domain.model.ChatMessage
import com.alertnotes.domain.model.SystemMessageKind
import kotlinx.coroutines.flow.Flow

/**
 * 1:1 chat between friends/family. Conversations are lazy — the document
 * appears on first message. All operations throw
 * [com.alertnotes.domain.model.FriendException] with a mappable reason.
 */
interface ChatRepository {

    /** All conversations, unread-and-newest first, live. */
    val conversations: Flow<List<ChatConversation>>

    /** Total unread across conversations (inbox badge). */
    val totalUnread: Flow<Int>

    /** Last messages of one conversation, oldest first, live. */
    fun observeMessages(otherUid: String): Flow<List<ChatMessage>>

    /** Relationship-gated: only friends or family may message. */
    suspend fun sendMessage(otherUid: String, text: String)

    /**
     * Inserts a distinct system message narrating a sharing event. When
     * [shareId] is provided the message renders as an interactive reminder
     * card that lives in the history and tracks the share's live status.
     */
    suspend fun postSystemMessage(
        otherUid: String,
        kind: SystemMessageKind,
        shareId: String? = null,
        shareTitle: String = "",
        shareSchedule: String = "",
    )

    /** Marks the conversation read and zeroes my unread counter. */
    suspend fun markRead(otherUid: String)

    /** Debounced by the caller; merged into the conversation doc. */
    suspend fun setTyping(otherUid: String, typing: Boolean)

    /** Delete-for-me: hides the message on this account only. */
    suspend fun deleteForMe(otherUid: String, messageId: String)

    /**
     * Smart-delivery signal: which conversation is on screen (null = none).
     * Mirrored to the private profile section so the push layer can skip
     * notifying about a conversation the user is already looking at.
     */
    suspend fun setActiveConversation(otherUid: String?)
}
