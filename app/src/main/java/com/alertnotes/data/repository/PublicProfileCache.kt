package com.alertnotes.data.repository

import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.ViewerRelation
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Short-lived cache of `users/{uid}/public/data` documents.
 *
 * Every list in the social half of the app resolves a counterparty profile per
 * row: friends, family, conversations, incoming and outgoing shares, requests
 * and invitations. Each of those was a fresh `get()` issued inside the
 * snapshot mapper, so a single Firestore emission cost one document read per
 * row — and the emission repeats on every change to any row in the list, on
 * every collector, and on every screen that shows the same people. A user with
 * thirty friends opening the friends screen was spending thirty reads, then
 * thirty more the moment anyone's presence heartbeat landed.
 *
 * This collapses that to one read per person per [TTL_MILLIS]. Two properties
 * make it correct rather than merely cheap:
 *
 * * **In-flight de-duplication.** Concurrent callers asking for the same uid
 *   await one shared request instead of racing several. That is the case that
 *   actually mattered: a list maps all its rows at once.
 * * **A deliberately short TTL.** Profiles carry presence and last-seen, which
 *   are meant to look live. Half a minute is long enough to flatten a burst of
 *   list mappings, short enough that a status change still surfaces promptly.
 *
 * The mapping to [PublicProfile] happens per call, not per cache entry,
 * because the same document yields different results for different viewers —
 * the owner's audience settings decide which fields survive.
 */
@Singleton
class PublicProfileCache @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private class Entry(val snapshot: DocumentSnapshot?, val fetchedAtMillis: Long)

    private val entries = HashMap<String, Entry>()
    private val inFlight = HashMap<String, CompletableDeferred<DocumentSnapshot?>>()
    private val mutex = Mutex()

    /**
     * The profile of [uid] as [viewer] is entitled to see it, or null when the
     * account is gone or unreadable.
     */
    suspend fun get(uid: String, viewer: ViewerRelation): PublicProfile? =
        snapshotOf(uid)?.takeIf { it.exists() }?.toPublicProfile(viewer)

    /** Drops [uid] from the cache; the next read goes to the server. */
    suspend fun invalidate(uid: String) {
        mutex.withLock { entries.remove(uid) }
    }

    /** Drops everything — used when the signed-in account changes. */
    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    private suspend fun snapshotOf(uid: String): DocumentSnapshot? {
        val now = System.currentTimeMillis()
        val pending: CompletableDeferred<DocumentSnapshot?>
        mutex.withLock {
            entries[uid]?.let { cached ->
                if (now - cached.fetchedAtMillis < TTL_MILLIS) return cached.snapshot
            }
            // Someone is already fetching this uid: wait for their result
            // rather than issuing a second identical read.
            inFlight[uid]?.let { existing -> return existing.await() }
            pending = CompletableDeferred()
            inFlight[uid] = pending
        }
        val snapshot = runCatching {
            firestore.collection(FirestoreSchema.USERS).document(uid)
                .collection(FirestoreSchema.SECTION_PUBLIC)
                .document(FirestoreSchema.SECTION_DOC)
                .get()
                .await()
        }.getOrNull()
        mutex.withLock {
            // A failed read is deliberately NOT cached: a transient offline
            // moment must not blank a profile for the whole TTL.
            if (snapshot != null) {
                entries[uid] = Entry(snapshot, System.currentTimeMillis())
            }
            inFlight.remove(uid)
        }
        pending.complete(snapshot)
        return snapshot
    }

    private companion object {
        /**
         * Long enough to collapse one screen's worth of row mappings and the
         * re-mappings that follow each snapshot; short enough that presence
         * still reads as live.
         */
        const val TTL_MILLIS = 30_000L
    }
}
