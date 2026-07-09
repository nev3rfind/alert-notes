package com.alertnotes.data.repository

import com.alertnotes.domain.model.ProfileTheme
import com.alertnotes.domain.model.PublicProfile
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/**
 * The one converter from a `public/data` document to [PublicProfile] —
 * shared by the own-profile and friend repositories so the two can never
 * disagree about defaults. Unknown/missing values degrade like every other
 * persisted mapper in the app.
 */
internal fun DocumentSnapshot?.toPublicProfile(): PublicProfile = PublicProfile(
    displayName = this?.getString("displayName").orEmpty(),
    username = this?.getString("username").orEmpty(),
    photoUrl = this?.getString("photoUrl"),
    statusMessage = this?.getString("statusMessage").orEmpty(),
    online = this?.getBoolean("online") == true,
    lastSeen = this?.getTimestamp("lastSeen")?.toDate()?.toInstant(),
    bannerTheme = ProfileTheme.entries
        .firstOrNull { it.name == this?.getString("bannerTheme") }
        ?: ProfileTheme.PRIMARY_ORANGE,
)

/** Uid of the profile owner: `users/{uid}/public/data` → `{uid}`. */
internal fun DocumentSnapshot.ownerUid(): String? =
    reference.parent.parent?.id

internal fun DocumentSnapshot?.instantField(field: String): Instant? =
    this?.getTimestamp(field)?.toDate()?.toInstant()
