package com.alertnotes.data.remote

/**
 * Every Firestore collection and document id the app touches, in one place.
 *
 * The user profile is deliberately split into one-document subcollections
 * (`users/{uid}/public/data`, `users/{uid}/private/data`, …) rather than
 * map fields on a single document: Firestore security rules protect whole
 * documents, never individual fields, so this is the only shape that lets
 * future rules give friends read access to the public section while private
 * data stays owner-only. Separate `public` subcollections also enable user
 * search later via a collection-group query over `public` docs without ever
 * matching a private one.
 */
object FirestoreSchema {

    /** Root collection; `users/{uid}` itself is a thin anchor document. */
    const val USERS = "users"

    // Profile sections — each a subcollection holding one [SECTION_DOC].
    /** What other users may eventually see (search, friends, chat). */
    const val SECTION_PUBLIC = "public"

    /** Owner-only account data; never exposed to other users. */
    const val SECTION_PRIVATE = "private"

    /** Settings that will synchronise across the user's devices. */
    const val SECTION_PREFERENCES = "preferences"

    /** Auth posture; leaves room for MFA without restructuring. */
    const val SECTION_SECURITY = "security"

    /** Counters for the future friend/family/sharing/chat features. */
    const val SECTION_STATISTICS = "statistics"

    /** Application bookkeeping (timestamps, app version). */
    const val SECTION_METADATA = "metadata"

    /** Fixed id of the single document inside every section subcollection. */
    const val SECTION_DOC = "data"

    /** One-time reminder backup: `users/{uid}/reminders/{localId}`. */
    const val REMINDERS = "reminders"

    /**
     * Devices signed in to the account: `users/{uid}/devices/{deviceId}`.
     * Registered on every login; the future push layer fills `pushToken`
     * per device, and future device management flips `active`.
     */
    const val DEVICES = "devices"

    /** Lowercase username reservations keeping usernames unique. */
    const val USERNAMES = "usernames"

    /**
     * Users the owner has blocked: `users/{uid}/blocked/{blockedUid}`.
     * Owner-only readable; rules consult it to refuse the blocked party's
     * messages, requests, shares, and profile reads.
     */
    const val BLOCKED = "blocked"

    /**
     * 1:1 conversations, doc id = the two uids sorted and joined with `_`
     * (deterministic — one conversation per pair). Messages live in the
     * [CHAT_MESSAGES] subcollection.
     */
    const val CHATS = "chats"
    const val CHAT_MESSAGES = "messages"

    /**
     * Top-level friend requests, doc id `{fromUid}_{toUid}` — deterministic
     * so a duplicate request is structurally impossible. Future Cloud
     * Functions validate transitions server-side on this collection.
     */
    const val FRIEND_REQUESTS = "friendRequests"

    /**
     * Accepted friendships: `users/{uid}/friends/{friendUid}`, one thin doc
     * per edge per user. Subcollection (not an array field) so thousands of
     * friends stay queryable and each edge carries its own metadata.
     */
    const val FRIENDS = "friends"

    /** Family invitations, mirroring [FRIEND_REQUESTS]: `{from}_{to}` ids. */
    const val FAMILY_INVITATIONS = "familyInvitations"

    /**
     * Family edges: `users/{uid}/family/{memberUid}`, each embedding the
     * per-direction permission map — one-way and two-way relationships are
     * just different values on the two sides.
     */
    const val FAMILY = "family"

    /**
     * Durable Notification Centre entries: `users/{uid}/notifications/{id}`.
     * Event producers create into the RECIPIENT's collection (rules pin the
     * sender's uid); reading and managing them stays owner-only. Push
     * notifications are the transient mirror of these documents.
     */
    const val NOTIFICATIONS = "notifications"

    /**
     * Reminder shares, top-level `reminderShares/{ownerUid}_{reminderId}_{recipientUid}`.
     * Owner-writable, readable by owner and recipient. The reminder body
     * stays in the owner's local database; this is only the sharing edge
     * plus a title snapshot. Foundation for the sharing/delivery session.
     */
    const val REMINDER_SHARES = "reminderShares"
}
