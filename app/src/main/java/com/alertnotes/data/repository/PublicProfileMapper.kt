package com.alertnotes.data.repository

import com.alertnotes.domain.model.PresenceState
import com.alertnotes.domain.model.PrivacyControl
import com.alertnotes.domain.model.PrivacySettings
import com.alertnotes.domain.model.ProfileTheme
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.ViewerRelation
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Duration
import java.time.Instant

/**
 * The one converter from a `public/data` document to [PublicProfile] —
 * shared by the own-profile and friend repositories so the two can never
 * disagree about defaults. Unknown/missing values degrade like every other
 * persisted mapper in the app.
 *
 * [viewer] decides which audience-scoped fields survive the mapping. Presence,
 * last-seen and the public counters live in the same document as the name and
 * avatar, so a reader who is allowed to open the profile at all receives them;
 * this is where they are dropped for viewers the owner did not include. The
 * hard guarantee for [com.alertnotes.domain.model.PrivacyAudience.NOBODY] is
 * upstream — presence is never written in the first place.
 */
internal fun DocumentSnapshot?.toPublicProfile(
    // Defaults to the most restrictive relation on purpose: a call site that
    // forgets to say who is looking hides too much rather than too little.
    viewer: ViewerRelation = ViewerRelation.OTHER,
): PublicProfile {
    val privacy = PrivacySettings.fromMap(this?.get("privacy") as? Map<*, *>)

    val rawLastSeen = this?.getTimestamp("lastSeen")?.toDate()?.toInstant()
    val stored = PresenceState.entries
        .firstOrNull { it.name == this?.getString("presenceState") }
        ?: if (this?.getBoolean("online") == true) PresenceState.ONLINE else PresenceState.OFFLINE
    // A force-killed process never says goodbye: once the heartbeat is
    // stale, ONLINE/AWAY degrade to OFFLINE on the reader's side.
    val heartbeatFresh = rawLastSeen != null &&
        Duration.between(rawLastSeen, Instant.now()) < STALE_PRESENCE
    val livePresence = if (stored != PresenceState.OFFLINE && !heartbeatFresh) {
        PresenceState.OFFLINE
    } else {
        stored
    }

    // Presence is evaluated from the heartbeat first and only then filtered,
    // so hiding it cannot accidentally make someone look permanently online.
    val presence = if (viewer.satisfies(privacy[PrivacyControl.ONLINE_STATUS])) {
        livePresence
    } else {
        PresenceState.OFFLINE
    }
    val lastSeen = rawLastSeen.takeIf { viewer.satisfies(privacy[PrivacyControl.LAST_SEEN]) }

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
        privacy = privacy,
    )
}

/** Heartbeats land every ~4 min; 10 min of silence means the app is gone. */
private val STALE_PRESENCE: Duration = Duration.ofMinutes(10)

/** Uid of the profile owner: `users/{uid}/public/data` → `{uid}`. */
internal fun DocumentSnapshot.ownerUid(): String? =
    reference.parent.parent?.id

internal fun DocumentSnapshot?.instantField(field: String): Instant? =
    this?.getTimestamp(field)?.toDate()?.toInstant()
