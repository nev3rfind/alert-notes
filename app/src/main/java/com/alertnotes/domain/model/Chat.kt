package com.alertnotes.domain.model

import java.time.Instant

/** SENT → READ today; DELIVERED is reserved for future FCM delivery acks. */
enum class MessageStatus { SENT, DELIVERED, READ }

/** SYSTEM messages narrate reminder-sharing events inside the chat. */
enum class MessageType { TEXT, SYSTEM }

/** What a SYSTEM message narrates; text is resolved from resources. */
enum class SystemMessageKind { REMINDER_SHARED, REMINDER_ACCEPTED, REMINDER_REJECTED, NONE }

data class ChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val type: MessageType,
    val systemKind: SystemMessageKind = SystemMessageKind.NONE,
    val status: MessageStatus,
    /** Reply threading arrives later; the field ships now. */
    val replyToId: String? = null,
    val createdAt: Instant?,
)

/** One 1:1 conversation joined with the other party's live profile. */
data class ChatConversation(
    val chatId: String,
    val otherUid: String,
    val profile: PublicProfile,
    val lastMessage: String,
    val lastMessageAt: Instant?,
    val lastSenderUid: String,
    val unreadCount: Int,
    val otherTyping: Boolean,
    /** Pinned architecture reserved; UI ordering support arrives later. */
    val pinned: Boolean = false,
)
