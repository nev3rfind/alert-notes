/**
 * Alert Notes push delivery.
 *
 * The Android client writes durable Notification Centre entries itself; this
 * layer's only job is the transient push — sent from the one place that can
 * hold FCM credentials safely. Every message is DATA-ONLY so the client's
 * messaging service always runs and stays in charge of channels, deep links,
 * and smart suppression.
 *
 * Smart delivery (server half): a chat push is skipped entirely when the
 * recipient's private profile says that conversation is open on their
 * screen — the Firestore realtime listener already rendered the message.
 *
 * Token hygiene: every send prunes tokens FCM reports as unregistered, so a
 * reinstalled or wiped device stops costing sends after one failure.
 *
 * Deploy: `firebase deploy --only functions` (requires the Blaze plan).
 */

const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const functionsV1 = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

const db = admin.firestore();
const messaging = admin.messaging();
const bucket = admin.storage().bucket();

/** Sends a data-only push to every registered device of one user. */
async function sendToUser(uid, data) {
  const devices = await db.collection(`users/${uid}/devices`).get();
  const tokens = devices.docs
    .map((doc) => ({ ref: doc.ref, token: doc.get("pushToken") }))
    .filter((entry) => typeof entry.token === "string" && entry.token.length > 0);
  if (tokens.length === 0) return;

  const response = await messaging.sendEachForMulticast({
    tokens: tokens.map((entry) => entry.token),
    android: { priority: "high" },
    data: Object.fromEntries(
      Object.entries(data).map(([key, value]) => [key, String(value ?? "")]),
    ),
  });

  // Automatic token removal: one unregistered response and the token is gone.
  const prunes = [];
  response.responses.forEach((result, index) => {
    const code = result.error?.code ?? "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token" ||
      code === "messaging/invalid-argument"
    ) {
      prunes.push(tokens[index].ref.set({ pushToken: null }, { merge: true }));
    }
  });
  await Promise.all(prunes);
}

/** The recipient's realtime UI already shows this conversation? Skip push. */
async function conversationOnScreen(recipientUid, chatId) {
  const privateDoc = await db.doc(`users/${recipientUid}/private/data`).get();
  return privateDoc.exists && privateDoc.get("activeChatId") === chatId;
}

async function displayNameOf(uid) {
  const publicDoc = await db.doc(`users/${uid}/public/data`).get();
  const name = publicDoc.exists ? publicDoc.get("displayName") : "";
  return name || "Someone";
}

/** New chat message → push to the other participant (smart delivery). */
exports.onChatMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message || message.type !== "TEXT") return;
  const chatId = event.params.chatId;
  const sender = message.senderUid;
  const recipient = chatId.split("_").find((uid) => uid !== sender);
  if (!recipient) return;

  if (await conversationOnScreen(recipient, chatId)) return;

  await sendToUser(recipient, {
    type: "chat",
    tag: `chat_${chatId}`,
    title: await displayNameOf(sender),
    body: String(message.text ?? "").slice(0, 120),
    senderUid: sender,
    deepLink: `chat:${sender}`,
  });
});

/** New friend request → push to the addressee. */
exports.onFriendRequest = onDocumentWritten("friendRequests/{requestId}", async (event) => {
  const after = event.data?.after?.data();
  const before = event.data?.before?.data();
  if (!after || after.status !== "PENDING" || before?.status === "PENDING") return;
  await sendToUser(after.toUid, {
    type: "social",
    tag: `friend_${after.fromUid}`,
    title: "New friend request",
    body: `${await displayNameOf(after.fromUid)} wants to be your friend`,
    senderUid: after.fromUid,
    deepLink: "inbox",
  });
});

/** New family invitation → push to the addressee. */
exports.onFamilyInvitation = onDocumentWritten("familyInvitations/{invitationId}", async (event) => {
  const after = event.data?.after?.data();
  const before = event.data?.before?.data();
  if (!after || after.status !== "PENDING" || before?.status === "PENDING") return;
  await sendToUser(after.toUid, {
    type: "social",
    tag: `family_${after.fromUid}`,
    title: "Family invitation",
    body: `${await displayNameOf(after.fromUid)} invited you to join their family circle`,
    senderUid: after.fromUid,
    deepLink: "inbox",
  });
});

/**
 * Reminder share lifecycle → push whichever side the transition concerns,
 * plus a silent sync so recipient devices store and schedule shared
 * reminders long before the app is next opened (the wake-up path).
 */
exports.onReminderShare = onDocumentWritten("reminderShares/{shareId}", async (event) => {
  const after = event.data?.after?.data();
  if (!after) return;
  const before = event.data?.before?.data();
  const shareId = event.params.shareId;
  const title = String(after.title ?? "");
  const assigned = after.ownership === "RECIPIENTS_ONLY";

  // Creation (or release): tell the recipient and wake their device.
  if (!before) {
    await sendToUser(after.recipientUid, {
      type: "sharing",
      tag: `share_${shareId}`,
      title: assigned ? "Reminder assigned to you" : "Reminder invitation",
      body: `${await displayNameOf(after.ownerUid)} sent “${title}”`,
      senderUid: after.ownerUid,
      deepLink: "shared",
    });
    return;
  }

  const statusChanged = before.status !== after.status;
  const versionBumped = (after.payloadVersion ?? 1) > (before.payloadVersion ?? 1);

  if (statusChanged) {
    const toOwner = { ACCEPTED: "accepted", REJECTED: "declined", TRIGGERED: "triggered", COMPLETED: "completed" };
    if (after.status in toOwner) {
      await sendToUser(after.ownerUid, {
        type: "sharing",
        tag: `share_${shareId}_status`,
        title: `Reminder ${toOwner[after.status]}`,
        body: `“${title}” — ${await displayNameOf(after.recipientUid)}`,
        senderUid: after.recipientUid,
        deepLink: "shared",
      });
    } else if (after.status === "CANCELLED") {
      await sendToUser(after.recipientUid, {
        type: "sharing",
        tag: `share_${shareId}_status`,
        title: "Reminder cancelled",
        body: `“${title}” was cancelled by its owner`,
        senderUid: after.ownerUid,
        deepLink: "shared",
      });
    } else if (after.status === "DELIVERED") {
      // Family auto-release: silent wake-up so delivery happens now.
      await sendToUser(after.recipientUid, { type: "sync", tag: `share_${shareId}_sync` });
    }
  }

  if (versionBumped) {
    await sendToUser(after.recipientUid, {
      type: "sharing",
      tag: `share_${shareId}_update`,
      title: "Reminder updated",
      body: `${await displayNameOf(after.ownerUid)} updated “${title}”`,
      senderUid: after.ownerUid,
      deepLink: "shared",
    });
  }

  // Acknowledgement mirrored by the recipient device → tell the owner.
  const ackAdvanced = (after.ackAtMillis ?? 0) > (before.ackAtMillis ?? 0);
  if (ackAdvanced) {
    await sendToUser(after.ownerUid, {
      type: "sharing",
      tag: `share_${shareId}_ack`,
      title: "Reminder acknowledged",
      body: `“${title}” — ${await displayNameOf(after.recipientUid)}`,
      senderUid: after.recipientUid,
      deepLink: "shared",
    });
  }
});

// ===========================================================================
// Account deletion cascade
// ===========================================================================
//
// Google Play requires an in-app way to delete an account AND the data behind
// it. The client can delete its own auth user, but it cannot reach the other
// half of a relationship — the friend edge sitting in someone else's
// subcollection, the share document the counterparty also owns — because the
// security rules (correctly) refuse those writes once the session is gone.
//
// This trigger runs with admin privileges after the auth user disappears, so
// the cascade completes even if the app is killed mid-way. It is idempotent:
// every step is a delete, and re-running it on an already-clean account is a
// no-op. The username reservation is released by the client beforehand, while
// it still holds the session that the rules require.
// ===========================================================================

/** Deletes a query's documents in chunks, respecting the 500-write batch cap. */
async function deleteQuery(query) {
  const BATCH = 400;
  for (;;) {
    const snapshot = await query.limit(BATCH).get();
    if (snapshot.empty) return;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    if (snapshot.size < BATCH) return;
  }
}

/** Removes the mirrored edge this account holds in other users' documents. */
async function removeMirroredEdges(uid, collection) {
  const own = await db.collection(`users/${uid}/${collection}`).get();
  const peers = own.docs.map((doc) => doc.id);
  // Chunked so a very large graph cannot exceed the batch limit.
  for (let i = 0; i < peers.length; i += 400) {
    const batch = db.batch();
    peers.slice(i, i + 400).forEach((peerUid) => {
      batch.delete(db.doc(`users/${peerUid}/${collection}/${uid}`));
      // Keep the peer's public counter honest rather than leaving it inflated.
      const field = collection === "friends" ? "friendCount" : "familyCount";
      batch.set(
        db.doc(`users/${peerUid}/statistics/data`),
        { [field]: admin.firestore.FieldValue.increment(-1) },
        { merge: true },
      );
    });
    await batch.commit();
  }
}

exports.onAccountDeleted = functionsV1
  .region("europe-west1")
  .auth.user()
  .onDelete(async (user) => {
    const uid = user.uid;

    // 1. Mirrored relationship edges, before the local copies are removed.
    await removeMirroredEdges(uid, "friends").catch(() => {});
    await removeMirroredEdges(uid, "family").catch(() => {});

    // 2. Everything under users/{uid}, subcollections included.
    await db.recursiveDelete(db.doc(`users/${uid}`));

    // 3. Top-level documents that name this account on either side.
    for (const [collection, fields] of [
      ["friendRequests", ["fromUid", "toUid"]],
      ["familyInvitations", ["fromUid", "toUid"]],
      ["reminderShares", ["ownerUid", "recipientUid"]],
    ]) {
      for (const field of fields) {
        await deleteQuery(db.collection(collection).where(field, "==", uid));
      }
    }

    // 4. Conversations. The document id is the two uids sorted and joined, so
    //    membership is decidable without reading anything.
    const chats = await db.collection("chats")
      .where("participants", "array-contains", uid)
      .get();
    for (const chat of chats.docs) {
      await db.recursiveDelete(chat.ref);
    }

    // 5. Storage: the avatar and every acknowledgement proof.
    await Promise.all([
      bucket.file(`avatars/${uid}.jpg`).delete().catch(() => {}),
      bucket.deleteFiles({ prefix: `ackProofs/${uid}/` }).catch(() => {}),
    ]);

    console.log(`Account cascade complete for ${uid}`);
  });
