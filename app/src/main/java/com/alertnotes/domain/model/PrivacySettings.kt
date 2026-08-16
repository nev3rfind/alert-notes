package com.alertnotes.domain.model

/**
 * Who may do a thing to, or see a thing about, this account.
 *
 * The order is deliberately most-open to most-closed: the UI renders the
 * options in declaration order, and [isAtLeastAsOpenAs] relies on the ordinal.
 */
enum class PrivacyAudience {
    /** Any signed-in Alert Notes user. */
    EVERYONE,

    /**
     * Users who share at least one friend with this account. Verified
     * server-side: the sender names the mutual friend and the security rules
     * check both legs of the chain, so it cannot be forged.
     */
    FRIENDS_OF_FRIENDS,

    /** Accepted friends only. */
    FRIENDS_ONLY,

    /** Members of the family circle only — the narrowest sharing tier. */
    FAMILY_ONLY,

    /** Nobody at all, including existing friends and family. */
    NOBODY,
    ;

    fun isAtLeastAsOpenAs(other: PrivacyAudience): Boolean = ordinal <= other.ordinal

    companion object {
        /** Unknown or missing values read as [EVERYONE] so older accounts keep working. */
        fun from(name: String?): PrivacyAudience =
            entries.firstOrNull { it.name == name } ?: EVERYONE
    }
}

/**
 * One privacy control: what it governs, and which audiences may be chosen.
 *
 * Not every audience is meaningful everywhere — a family invitation already
 * requires an accepted friendship, so offering "friends of friends" there
 * would be a setting that cannot do anything. Each entry therefore carries its
 * own [options], and the settings screen renders exactly those.
 */
enum class PrivacyControl(
    val key: String,
    val options: List<PrivacyAudience>,
    val default: PrivacyAudience,
) {
    /** Who may send a friend request. */
    FRIEND_REQUESTS(
        key = "friendRequests",
        options = listOf(
            PrivacyAudience.EVERYONE,
            PrivacyAudience.FRIENDS_OF_FRIENDS,
            PrivacyAudience.NOBODY,
        ),
        default = PrivacyAudience.EVERYONE,
    ),

    /** Who may invite this account into a family circle. Friends always. */
    FAMILY_INVITATIONS(
        key = "familyInvitations",
        options = listOf(PrivacyAudience.EVERYONE, PrivacyAudience.NOBODY),
        default = PrivacyAudience.EVERYONE,
    ),

    /** Who may send or assign a reminder. */
    REMINDER_SHARING(
        key = "reminderSharing",
        options = listOf(
            PrivacyAudience.EVERYONE,
            PrivacyAudience.FRIENDS_ONLY,
            PrivacyAudience.FAMILY_ONLY,
            PrivacyAudience.NOBODY,
        ),
        default = PrivacyAudience.EVERYONE,
    ),

    /** Who may start a conversation. */
    MESSAGE_REQUESTS(
        key = "messageRequests",
        options = listOf(
            PrivacyAudience.EVERYONE,
            PrivacyAudience.FRIENDS_ONLY,
            PrivacyAudience.FAMILY_ONLY,
            PrivacyAudience.NOBODY,
        ),
        default = PrivacyAudience.EVERYONE,
    ),

    /**
     * Who may open this profile. Anything narrower than [PrivacyAudience.EVERYONE]
     * also removes the account from user search — see `discoverable`.
     */
    PROFILE_VISIBILITY(
        key = "profileVisibility",
        options = PrivacyAudience.entries,
        default = PrivacyAudience.EVERYONE,
    ),

    /** Who sees the green "online" dot. */
    ONLINE_STATUS(
        key = "onlineStatus",
        options = listOf(
            PrivacyAudience.EVERYONE,
            PrivacyAudience.FRIENDS_ONLY,
            PrivacyAudience.FAMILY_ONLY,
            PrivacyAudience.NOBODY,
        ),
        default = PrivacyAudience.EVERYONE,
    ),

    /** Who sees "last active 3 minutes ago". */
    LAST_SEEN(
        key = "lastSeen",
        options = listOf(
            PrivacyAudience.EVERYONE,
            PrivacyAudience.FRIENDS_ONLY,
            PrivacyAudience.FAMILY_ONLY,
            PrivacyAudience.NOBODY,
        ),
        default = PrivacyAudience.EVERYONE,
    ),

    /** Who sees the reminder counters on the public profile. */
    ANALYTICS_VISIBILITY(
        key = "analyticsVisibility",
        options = listOf(
            PrivacyAudience.EVERYONE,
            PrivacyAudience.FRIENDS_ONLY,
            PrivacyAudience.FAMILY_ONLY,
            PrivacyAudience.NOBODY,
        ),
        default = PrivacyAudience.FRIENDS_ONLY,
    ),
}

/**
 * The account's complete privacy configuration.
 *
 * Stored as a `privacy` map on `users/{uid}/public/data` rather than a section
 * of its own, for one concrete reason: the security rule that governs reading
 * a profile can then consult the audience straight off the document being
 * read, with no second (billed) lookup on the hottest path in the app.
 *
 * The rules are the enforcement point for every audience that gates a *write*
 * (friend requests, invitations, shares, messages) and for profile reads.
 * [PrivacyControl.ONLINE_STATUS], [PrivacyControl.LAST_SEEN] and
 * [PrivacyControl.ANALYTICS_VISIBILITY] describe fields inside a document the
 * viewer is already permitted to read, so they are applied when the profile is
 * mapped — plus, for [PrivacyAudience.NOBODY], by never writing the value at
 * all. See KNOWN_LIMITATIONS.md.
 */
data class PrivacySettings(
    private val audiences: Map<PrivacyControl, PrivacyAudience> = emptyMap(),
) {

    operator fun get(control: PrivacyControl): PrivacyAudience =
        audiences[control] ?: control.default

    fun with(control: PrivacyControl, audience: PrivacyAudience): PrivacySettings =
        copy(audiences = audiences + (control to audience))

    /**
     * Whether this account may appear in user search. Search is a
     * collection-group query, and a Firestore rule cannot evaluate a per-result
     * condition on a query, so discoverability is denormalised into a single
     * boolean the query itself filters on.
     */
    val isDiscoverable: Boolean
        get() = this[PrivacyControl.PROFILE_VISIBILITY] == PrivacyAudience.EVERYONE

    /** Firestore representation: `{ "friendRequests": "EVERYONE", … }`. */
    fun toMap(): Map<String, String> =
        PrivacyControl.entries.associate { it.key to this[it].name }

    companion object {
        val DEFAULT = PrivacySettings()

        fun fromMap(raw: Map<*, *>?): PrivacySettings {
            if (raw == null) return DEFAULT
            val audiences = PrivacyControl.entries.mapNotNull { control ->
                val stored = raw[control.key] as? String ?: return@mapNotNull null
                control to PrivacyAudience.from(stored)
            }.toMap()
            return PrivacySettings(audiences)
        }
    }
}

/**
 * How the viewer is related to the profile's owner. Supplied by the reader so
 * audience-scoped fields can be filtered when a profile is mapped.
 */
enum class ViewerRelation { SELF, FAMILY, FRIEND, OTHER;

    fun satisfies(audience: PrivacyAudience): Boolean = when (audience) {
        PrivacyAudience.EVERYONE -> true
        // A reader cannot verify a friend-of-friend chain without extra reads,
        // so it degrades to "friends" here. Writes are verified properly by
        // the security rules, which is where forgery actually matters.
        PrivacyAudience.FRIENDS_OF_FRIENDS -> this != OTHER
        PrivacyAudience.FRIENDS_ONLY -> this == SELF || this == FRIEND || this == FAMILY
        PrivacyAudience.FAMILY_ONLY -> this == SELF || this == FAMILY
        PrivacyAudience.NOBODY -> this == SELF
    }
}
