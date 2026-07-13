package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.model.AppNotification
import com.alertnotes.domain.model.NotificationCategory
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.NotificationCentreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Notification Centre. One snapshot listener per collector
 * feeds both the list and the unread badge; the equality-only query keeps it
 * index-free. Writes into another user's collection happen only through
 * [publish] and only as creates/merges with the sender's own uid stamped —
 * the security rules' enforcement point.
 */
@Singleton
class NotificationCentreRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val logger: AppLogger,
) : NotificationCentreRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val notifications: Flow<List<AppNotification>> =
        authRepository.authState.flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else entriesOf(user.uid)
        }

    override val unreadCount: Flow<Int> =
        notifications.map { entries -> entries.count { !it.read && !it.archived } }

    override suspend fun publish(
        recipientUid: String,
        category: NotificationCategory,
        title: String,
        body: String,
        refId: String,
        dedupeKey: String?,
    ) {
        val me = auth.currentUser?.uid ?: return
        runCatching {
            val collection = centreOf(recipientUid)
            val document = dedupeKey?.let(collection::document) ?: collection.document()
            document.set(
                mapOf(
                    "category" to category.name,
                    "title" to title,
                    "body" to body,
                    "senderUid" to me,
                    "refId" to refId,
                    // A repeated event re-surfaces as unread with a fresh
                    // timestamp — exactly what "new messages from X" needs.
                    "read" to false,
                    "archived" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }.onFailure { logger.d(TAG, "Notification publish skipped: ${it.message}") }
    }

    override suspend fun markRead(id: String) {
        val me = auth.currentUser?.uid ?: return
        runCatching {
            centreOf(me).document(id).set(mapOf("read" to true), SetOptions.merge()).await()
        }.onFailure { logger.d(TAG, "markRead skipped: ${it.message}") }
    }

    override suspend fun markAllRead() {
        val me = auth.currentUser?.uid ?: return
        runCatching {
            val unread = notifications.first().filter { !it.read }
            if (unread.isEmpty()) return
            firestore.runBatch { batch ->
                unread.forEach { entry ->
                    batch.set(
                        centreOf(me).document(entry.id),
                        mapOf("read" to true),
                        SetOptions.merge(),
                    )
                }
            }.await()
        }.onFailure { logger.d(TAG, "markAllRead skipped: ${it.message}") }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val me = auth.currentUser?.uid ?: return
        runCatching {
            centreOf(me).document(id)
                .set(mapOf("archived" to archived, "read" to true), SetOptions.merge())
                .await()
        }.onFailure { logger.d(TAG, "setArchived skipped: ${it.message}") }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        val me = auth.currentUser?.uid ?: return
        runCatching {
            centreOf(me).document(id).set(mapOf("pinned" to pinned), SetOptions.merge()).await()
        }.onFailure { logger.d(TAG, "setPinned skipped: ${it.message}") }
    }

    override suspend fun delete(id: String) {
        val me = auth.currentUser?.uid ?: return
        runCatching { centreOf(me).document(id).delete().await() }
            .onFailure { logger.d(TAG, "delete skipped: ${it.message}") }
    }

    private fun entriesOf(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val registration = centreOf(uid)
            .addSnapshotListener { snapshot, error ->
                trySend(if (error != null) emptyList() else snapshot?.documents.orEmpty())
            }
        awaitClose { registration.remove() }
    }.map { documents ->
        documents.map { it.toNotification() }
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
    }

    private fun centreOf(uid: String): CollectionReference =
        firestore.collection(FirestoreSchema.USERS)
            .document(uid)
            .collection(FirestoreSchema.NOTIFICATIONS)

    private fun DocumentSnapshot.toNotification(): AppNotification = AppNotification(
        id = id,
        category = NotificationCategory.entries
            .firstOrNull { it.name == getString("category") } ?: NotificationCategory.SYSTEM,
        title = getString("title").orEmpty(),
        body = getString("body").orEmpty(),
        senderUid = getString("senderUid").orEmpty(),
        refId = getString("refId").orEmpty(),
        read = getBoolean("read") == true,
        archived = getBoolean("archived") == true,
        pinned = getBoolean("pinned") == true,
        createdAt = instantField("createdAt"),
    )

    private companion object {
        const val TAG = "NotificationCentre"
    }
}
