package com.alertnotes.data.repository

import com.alertnotes.domain.model.PresenceState
import com.alertnotes.domain.model.ProfileTheme
import com.alertnotes.domain.model.PublicProfile
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Duration
import java.time.Instant

/**
 * The one converter from a `public/data` document to [PublicProfile] —
 * shared by the own-profile and friend repositories so the two can never
 * disagree about defaults. Unknown/missing values degrade like every other
 * persisted mapper in the app.
 */
internal fun DocumentSnapshot?.toPublicProfile(): PublicProfile {
    val lastSeen = this?.getTimestamp("lastSeen")?.toDate()?.toInstant()
    val stored = PresenceState.entries
        .firstOrNull { it.name == this?.getString("presenceState") }
        ?: if (this?.getBoolean("online") == true) PresenceState.ONLINE else PresenceState.OFFLINE
    // A force-killed process never says goodbye: once the heartbeat is
    // stale, ONLINE/AWAY degrade to OFFLINE on the reader's side.
    val heartbeatFresh = lastSeen != null &&
        Duration.between(lastSeen, Instant.now()) < STALE_PRESENCE
    val presence = if (stored != PresenceState.OFFLINE && !heartbeatFresh) {
        PresenceState.OFFLINE
    } else {
        stored
    }
    return PublicProfile(
        displayName = this?.getString("displayName").orEmpty(),
        username = this?.getString("username").orEmpty(),
        photoUrl = this?.getString("photoUrl"),
        statusMessage = this?.getString("statusMessage").orEmpty(),
        online = presence == PresenceState.ONLINE,
        presence = presence,
        lastSeen = lastSeen,
        bannerTheme = ProfileTheme.entries
            .firstOrNull { it.name == this?.getString("bannerTheme") }
            ?: ProfileTheme.PRIMARY_ORANGE,
    )
}

/** Heartbeats land every ~4 min; 10 min of silence means the app is gone. */
private val STALE_PRESENCE: Duration = Duration.ofMinutes(10)

/** Uid of the profile owner: `users/{uid}/public/data` → `{uid}`. */
internal fun DocumentSnapshot.ownerUid(): String? =
    reference.parent.parent?.id

internal fun DocumentSnapshot?.instantField(field: String): Instant? =
    this?.getTimestamp(field)?.toDate()?.toInstant()
