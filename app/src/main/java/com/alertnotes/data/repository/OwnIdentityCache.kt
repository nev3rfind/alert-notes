package com.alertnotes.data.repository

import com.alertnotes.data.remote.FirestoreSchema
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * The signed-in user's own display name, fetched once and cached per uid —
 * every notification an event producer publishes carries it, and paying a
 * profile read per event would be pure waste. Falls back to a neutral
 * placeholder so a failed read never blocks the event itself.
 */
@Singleton
class OwnIdentityCache @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {

    @Volatile
    private var cached: Pair<String, String>? = null

    suspend fun displayName(): String {
        val uid = auth.currentUser?.uid ?: return FALLBACK
        cached?.takeIf { it.first == uid }?.let { return it.second }
        val name = runCatching {
            firestore.collection(FirestoreSchema.USERS).document(uid)
                .collection(FirestoreSchema.SECTION_PUBLIC).document(FirestoreSchema.SECTION_DOC)
                .get().await().getString("displayName")
        }.getOrNull().orEmpty().ifBlank { FALLBACK }
        cached = uid to name
        return name
    }

    private companion object {
        const val FALLBACK = "Someone"
    }
}
