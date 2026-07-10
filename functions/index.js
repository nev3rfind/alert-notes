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
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

const db = admin.firestore();
const messaging = admin.messaging();

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
