package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.model.ChatConversation
import com.alertnotes.domain.model.ChatMessage
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.MessageStatus
import com.alertnotes.domain.model.MessageType
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.SystemMessageKind
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.ChatRepository
import com.alertnotes.domain.repository.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore-backed 1:1 chat. One conversation document per pair
 * (deterministic sorted-uid id) carrying denormalised list metadata
 * (last message, per-user unread counters, per-user typing flags) so the
 * chat list renders from ONE query with no per-row message reads. Messages
 * are a subcollection ordered by server time; delete-for-me is a per-user
 * hide (`deletedFor` array), never a destructive delete.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    authRepository: AuthRepository,
    private val friendRepository: FriendRepository,
    private val firestore: FirebaseFirestore,
    private val identity: OwnIdentityCache,
    private val notificationCentre: com.alertnotes.domain.repository.NotificationCentreRepository,
    private val chatSessionTracker: com.alertnotes.services.ChatSessionTracker,
    private val logger: AppLogger,
) : ChatRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val conversations: Flow<List<ChatConversation>> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else conversationDocs(user.uid)
        }

    override val totalUnread: Flow<Int> =
        conversations.map { list -> list.sumOf { it.unreadCount } }

    override fun observeMessages(otherUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val registration = chatDocument(me, otherUid)
            .collection(FirestoreSchema.CHAT_MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGE_PAGE)
            .addSnapshotListener { snapshot, error ->
                trySend(
                    if (error != null) {
                        emptyList()
                    } else {
                        snapshot?.documents.orEmpty()
                            .filterNot { doc ->
                                (doc.get("deletedFor") as? List<*>)?.contains(me) == true
                            }
                            .map { it.toMessage() }
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(otherUid: String, text: String) = runChatOp {
        val body = text.trim()
        if (body.isEmpty()) return@runChatOp
        postMessage(otherUid, body, MessageType.TEXT, SystemMessageKind.NONE)
    }

    override suspend fun postSystemMessage(
        otherUid: String,
        kind: SystemMessageKind,
        shareId: String?,
        shareTitle: String,
        shareSchedule: String,
    ) = runChatOp {
        postMessage(otherUid, "", MessageType.SYSTEM, kind, shareId, shareTitle, shareSchedule)
    }

    override suspend fun markRead(otherUid: String) = runChatOp {
        val me = requireUid()
        val chat = chatDocument(me, otherUid)
        // Zero my counter first — cheap and what the list shows.
        chat.set(mapOf("unread_$me" to 0), SetOptions.merge()).await()
        // Then receipt the sender's messages.
        val unread = chat.collection(FirestoreSchema.CHAT_MESSAGES)
            .whereEqualTo("senderUid", otherUid)
            .whereEqualTo("status", MessageStatus.SENT.name)
            .get()
            .await()
        if (!unread.isEmpty) {
            firestore.runBatch { batch ->
                unread.documents.forEach { doc ->
                    batch.update(doc.reference, "status", MessageStatus.READ.name)
                }
            }.await()
        }
        // Opening the conversation resolves its centre entry too.
        notificationCentre.markRead("chat_" + listOf(me, otherUid).sorted().joinToString("_"))
        Unit
    }

    override suspend fun setTyping(otherUid: String, typing: Boolean) {
        val me = auth.currentUser?.uid ?: return
        runCatching {
            chatDocument(me, otherUid)
                .set(mapOf("typing_$me" to typing), SetOptions.merge())
                .await()
        }
    }

    override suspend fun setActiveConversation(otherUid: String?) {
        val me = auth.currentUser?.uid ?: return
        // Must survive caller cancellation: the clearing call fires from
        // ON_STOP/leave-composition moments before the ViewModel scope is
        // torn down, and a cancelled clear would suppress this partner's
        // pushes indefinitely.
        withContext(NonCancellable) {
            // Local half first — the messaging service reads it synchronously.
            if (otherUid != null) {
                chatSessionTracker.onConversationVisible(otherUid)
            } else {
                chatSessionTracker.activePartnerUid.value
                    ?.let(chatSessionTracker::onConversationHidden)
            }
            // Server half: the Cloud Function checks this before pushing.
            runCatching {
                val chatId = otherUid?.let { listOf(me, it).sorted().joinToString("_") }
                firestore.collection(FirestoreSchema.USERS).document(me)
                    .collection(FirestoreSchema.SECTION_PRIVATE)
                    .document(FirestoreSchema.SECTION_DOC)
                    .set(mapOf("activeChatId" to chatId), SetOptions.merge())
                    .await()
            }.onFailure { logger.d(TAG, "Active-conversation mirror failed: ${it.message}") }
        }
    }

    override suspend fun deleteForMe(otherUid: String, messageId: String) = runChatOp {
        val me = requireUid()
        chatDocument(me, otherUid)
            .collection(FirestoreSchema.CHAT_MESSAGES)
            .document(messageId)
            .update("deletedFor", FieldValue.arrayUnion(me))
            .await()
        Unit
    }

    private suspend fun postMessage(
        otherUid: String,
        text: String,
        type: MessageType,
        kind: SystemMessageKind,
        shareId: String? = null,
        shareTitle: String = "",
        shareSchedule: String = "",
    ) {
        val me = requireUid()
        // No chatting with strangers: friendship or family is required.
        val allowed = friendRepository.friends.first().any { it.uid == otherUid } ||
            friendRepository.family.first().any { it.uid == otherUid }
        if (!allowed) throw FriendException(FriendError.NOT_FRIENDS)
        val chat = chatDocument(me, otherUid)
        val message = chat.collection(FirestoreSchema.CHAT_MESSAGES).document()
        firestore.runBatch { batch ->
            batch.set(
                message,
                mapOf(
                    "senderUid" to me,
                    "text" to text,
                    "type" to type.name,
                    "systemKind" to kind.name,
                    "status" to MessageStatus.SENT.name,
                    "replyToId" to null,
                    "shareId" to shareId,
                    "shareTitle" to shareTitle,
                    "shareSchedule" to shareSchedule,
                    "deletedFor" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                chat,
                mapOf(
                    "participants" to listOf(me, otherUid).sorted(),
                    "lastMessage" to text,
                    "lastSystemKind" to kind.name,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderUid" to me,
                    "unread_$otherUid" to FieldValue.increment(1),
                    "typing_$me" to false,
                ),
                SetOptions.merge(),
            )
        }.await()
        // One durable centre entry per conversation, re-surfacing as unread
        // on each new message — never one entry per message. System events
        // publish their own share-lifecycle entries.
        if (type == MessageType.TEXT) {
            notificationCentre.publish(
                recipientUid = otherUid,
                category = com.alertnotes.domain.model.NotificationCategory.CHAT_MESSAGE,
                title = identity.displayName(),
                body = text.take(CHAT_PREVIEW_LENGTH),
                refId = me,
                dedupeKey = "chat_" + listOf(me, otherUid).sorted().joinToString("_"),
            )
        }
    }

    private fun conversationDocs(me: String): Flow<List<ChatConversation>> = callbackFlow {
        val registration = firestore.collection(FirestoreSchema.CHATS)
            .whereArrayContains("participants", me)
            .addSnapshotListener { snapshot, error ->
                trySend(if (error != null) emptyList() else snapshot?.documents.orEmpty())
            }
        awaitClose { registration.remove() }
    }.map { documents ->
        documents.mapNotNull { doc ->
            val participants = (doc.get("participants") as? List<*>).orEmpty()
            val otherUid = participants.filterIsInstance<String>().firstOrNull { it != me }
                ?: return@mapNotNull null
            publicProfileOf(otherUid)?.let { profile ->
                ChatConversation(
                    chatId = doc.id,
                    otherUid = otherUid,
                    profile = profile,
                    lastMessage = doc.getString("lastMessage").orEmpty(),
                    lastMessageAt = doc.instantField("lastMessageAt"),
                    lastSenderUid = doc.getString("lastSenderUid").orEmpty(),
                    unreadCount = doc.getLong("unread_$me")?.toInt() ?: 0,
                    otherTyping = doc.getBoolean("typing_$otherUid") == true,
                    pinned = doc.getBoolean("pinned_$me") == true,
                )
            }
        }.sortedWith(
            compareByDescending<ChatConversation> { it.unreadCount > 0 }
                .thenByDescending { it.lastMessageAt ?: Instant.EPOCH },
        )
    }

    private suspend fun publicProfileOf(uid: String): PublicProfile? = runCatching {
        firestore.collection(FirestoreSchema.USERS).document(uid)
            .collection(FirestoreSchema.SECTION_PUBLIC).document(FirestoreSchema.SECTION_DOC)
            .get().await().takeIf { it.exists() }
            ?.toPublicProfile(friendRepository.viewerRelation(uid))
    }.getOrNull()

    private fun DocumentSnapshot.toMessage(): ChatMessage = ChatMessage(
        id = id,
        senderUid = getString("senderUid").orEmpty(),
        text = getString("text").orEmpty(),
        type = MessageType.entries.firstOrNull { it.name == getString("type") }
            ?: MessageType.TEXT,
        systemKind = SystemMessageKind.entries.firstOrNull { it.name == getString("systemKind") }
            ?: SystemMessageKind.NONE,
        status = MessageStatus.entries.firstOrNull { it.name == getString("status") }
            ?: MessageStatus.SENT,
        replyToId = getString("replyToId"),
        shareId = getString("shareId"),
        shareTitle = getString("shareTitle").orEmpty(),
        shareSchedule = getString("shareSchedule").orEmpty(),
        createdAt = instantField("createdAt"),
    )

    private fun chatDocument(a: String, b: String): DocumentReference =
        firestore.collection(FirestoreSchema.CHATS)
            .document(listOf(a, b).sorted().joinToString("_"))

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw FriendException(FriendError.UNKNOWN)

    private suspend fun <T> runChatOp(block: suspend () -> T): T = try {
        block()
    } catch (exception: FriendException) {
        throw exception
    } catch (exception: Exception) {
        logger.e(TAG, "Chat op failed: ${exception::class.simpleName}: ${exception.message}", exception)
        throw FriendException(
            when {
                exception is IOException -> FriendError.NETWORK
                exception is FirebaseFirestoreException &&
                    exception.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                    FriendError.NETWORK

                exception is FirebaseFirestoreException &&
                    exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    FriendError.PERMISSION

                else -> FriendError.UNKNOWN
            },
            exception,
        )
    }

    private companion object {
        const val TAG = "ChatRepository"
        const val MESSAGE_PAGE = 100L
        const val CHAT_PREVIEW_LENGTH = 80
    }
}
