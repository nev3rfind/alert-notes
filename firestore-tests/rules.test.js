/**
 * Security-rules tests for Alert Notes.
 *
 * These run entirely against the Firestore emulator — `npm test` starts it,
 * loads ../firestore.rules, and tears it down again. No production project is
 * involved and no credentials are needed.
 *
 * The rules are the app's only real authorisation boundary (the Android client
 * is untrusted), so each test states an attack or a legitimate flow and asserts
 * the rule's verdict rather than the app's behaviour.
 */

import { readFileSync } from 'node:fs'
import { after, before, beforeEach, describe, it } from 'node:test'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  startAt,
  updateDoc,
  where,
} from 'firebase/firestore'

const ALICE = 'aliceUid'
const BOB = 'bobUid'
const CAROL = 'carolUid'
const MALLORY = 'malloryUid'

let testEnv

/** Signed-in Firestore handle for `uid`, with rules enforced. */
const as = (uid) => testEnv.authenticatedContext(uid).firestore()

/** Seeds a document with rules disabled — arranging state, not exercising it. */
const seed = (fn) => testEnv.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()))

const publicDoc = (uid) => `users/${uid}/public/data`
const friendEdge = (owner, friend) => `users/${owner}/friends/${friend}`
const familyEdge = (owner, member) => `users/${owner}/family/${member}`

/** Writes a profile with an optional privacy map. */
async function seedProfile(uid, privacy = {}, discoverable = true) {
  await seed((db) =>
    setDoc(doc(db, publicDoc(uid)), {
      displayName: uid,
      username: uid.toLowerCase(),
      discoverable,
      privacy,
    }),
  )
}

async function seedFriendship(a, b) {
  await seed(async (db) => {
    await setDoc(doc(db, friendEdge(a, b)), { uid: b })
    await setDoc(doc(db, friendEdge(b, a)), { uid: a })
  })
}

async function seedFamily(a, b) {
  await seed(async (db) => {
    await setDoc(doc(db, familyEdge(a, b)), { uid: b, permissions: {} })
    await setDoc(doc(db, familyEdge(b, a)), { uid: a, permissions: {} })
  })
}

async function seedBlock(owner, blocked) {
  await seed((db) => setDoc(doc(db, `users/${owner}/blocked/${blocked}`), { at: 1 }))
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'alert-notes-test',
    firestore: {
      rules: readFileSync('../firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  })
})

after(async () => {
  await testEnv?.cleanup()
})

beforeEach(async () => {
  await testEnv.clearFirestore()
})

// ---------------------------------------------------------------------------

describe('profile sections', () => {
  it('keeps the private section owner-only', async () => {
    await seed((db) => setDoc(doc(db, `users/${ALICE}/private/data`), { email: 'a@b.c' }))
    await assertSucceeds(getDoc(doc(as(ALICE), `users/${ALICE}/private/data`)))
    await assertFails(getDoc(doc(as(BOB), `users/${ALICE}/private/data`)))
  })

  it('keeps preferences, security and metadata owner-only', async () => {
    for (const section of ['preferences', 'security', 'metadata']) {
      await seed((db) => setDoc(doc(db, `users/${ALICE}/${section}/data`), { x: 1 }))
      await assertFails(getDoc(doc(as(BOB), `users/${ALICE}/${section}/data`)))
    }
  })

  it('never exposes the local reminder backup to anyone else', async () => {
    await seed((db) => setDoc(doc(db, `users/${ALICE}/reminders/7`), { title: 'Pills' }))
    await assertSucceeds(getDoc(doc(as(ALICE), `users/${ALICE}/reminders/7`)))
    await assertFails(getDoc(doc(as(BOB), `users/${ALICE}/reminders/7`)))
  })

  it('never exposes FCM device tokens to anyone else', async () => {
    await seed((db) => setDoc(doc(db, `users/${ALICE}/devices/d1`), { pushToken: 'secret' }))
    await assertFails(getDoc(doc(as(MALLORY), `users/${ALICE}/devices/d1`)))
  })

  it('hides the block list from the blocked account', async () => {
    await seedBlock(ALICE, MALLORY)
    await assertFails(getDoc(doc(as(MALLORY), `users/${ALICE}/blocked/${MALLORY}`)))
  })

  it('refuses to let one user rewrite another public profile', async () => {
    await seedProfile(ALICE)
    await assertFails(
      updateDoc(doc(as(MALLORY), publicDoc(ALICE)), { displayName: 'hacked' }),
    )
  })
})

describe('profile visibility (privacy)', () => {
  it('EVERYONE is readable by any signed-in user', async () => {
    await seedProfile(ALICE, { profileVisibility: 'EVERYONE' })
    await assertSucceeds(getDoc(doc(as(BOB), publicDoc(ALICE))))
  })

  it('defaults to readable when no privacy map exists (legacy accounts)', async () => {
    await seed((db) => setDoc(doc(db, publicDoc(ALICE)), { displayName: 'Alice' }))
    await assertSucceeds(getDoc(doc(as(BOB), publicDoc(ALICE))))
  })

  it('FRIENDS_ONLY hides the profile from a stranger but not from a friend', async () => {
    await seedProfile(ALICE, { profileVisibility: 'FRIENDS_ONLY' })
    await assertFails(getDoc(doc(as(MALLORY), publicDoc(ALICE))))
    await seedFriendship(ALICE, BOB)
    await assertSucceeds(getDoc(doc(as(BOB), publicDoc(ALICE))))
  })

  it('FAMILY_ONLY excludes a mere friend', async () => {
    await seedProfile(ALICE, { profileVisibility: 'FAMILY_ONLY' })
    await seedFriendship(ALICE, BOB)
    await assertFails(getDoc(doc(as(BOB), publicDoc(ALICE))))
    await seedFamily(ALICE, CAROL)
    await assertSucceeds(getDoc(doc(as(CAROL), publicDoc(ALICE))))
  })

  it('NOBODY hides the profile even from a friend', async () => {
    await seedProfile(ALICE, { profileVisibility: 'NOBODY' })
    await seedFriendship(ALICE, BOB)
    await assertFails(getDoc(doc(as(BOB), publicDoc(ALICE))))
  })

  it('always lets the owner read their own profile whatever the setting', async () => {
    await seedProfile(ALICE, { profileVisibility: 'NOBODY' })
    await assertSucceeds(getDoc(doc(as(ALICE), publicDoc(ALICE))))
  })

  it('blocks profile reads in both directions once either side blocks', async () => {
    await seedProfile(ALICE, { profileVisibility: 'EVERYONE' })
    await seedProfile(MALLORY, { profileVisibility: 'EVERYONE' })
    await seedBlock(ALICE, MALLORY)
    await assertFails(getDoc(doc(as(MALLORY), publicDoc(ALICE))))
    await assertFails(getDoc(doc(as(ALICE), publicDoc(MALLORY))))
  })
})

describe('user search', () => {
  /** Exactly the query FriendRepositoryImpl.search issues. */
  const searchQuery = (db, term) =>
    query(
      collectionGroup(db, 'public'),
      where('discoverable', '==', true),
      orderBy('displayName'),
      startAt(term),
      limit(20),
    )

  it('allows the discoverable-filtered collection-group search', async () => {
    await seedProfile(ALICE, {}, true)
    await assertSucceeds(getDocs(searchQuery(as(BOB), 'a')))
  })

  it('rejects an unfiltered collection-group listing of every profile', async () => {
    await seedProfile(ALICE, {}, true)
    await assertFails(getDocs(collectionGroup(as(MALLORY), 'public')))
  })

  it('rejects a search that asks for non-discoverable profiles', async () => {
    await seedProfile(ALICE, { profileVisibility: 'FRIENDS_ONLY' }, false)
    await assertFails(
      getDocs(query(collectionGroup(as(MALLORY), 'public'),
        where('discoverable', '==', false))),
    )
  })
})

describe('usernames', () => {
  it('lets a user claim a free name for themselves only', async () => {
    await assertSucceeds(setDoc(doc(as(ALICE), 'usernames/alice'), { uid: ALICE }))
    await assertFails(setDoc(doc(as(MALLORY), 'usernames/mallory'), { uid: ALICE }))
  })

  it('refuses to overwrite someone else’s reservation', async () => {
    await seed((db) => setDoc(doc(db, 'usernames/alice'), { uid: ALICE }))
    await assertFails(setDoc(doc(as(MALLORY), 'usernames/alice'), { uid: MALLORY }))
    await assertFails(deleteDoc(doc(as(MALLORY), 'usernames/alice')))
    await assertSucceeds(deleteDoc(doc(as(ALICE), 'usernames/alice')))
  })

  it('refuses a mixed-case reservation id', async () => {
    await assertFails(setDoc(doc(as(ALICE), 'usernames/Alice'), { uid: ALICE }))
  })
})

describe('friend requests', () => {
  const req = (from, to) => `friendRequests/${from}_${to}`

  beforeEach(async () => {
    await seedProfile(ALICE)
    await seedProfile(BOB)
    await seedProfile(MALLORY)
  })

  const pending = (from, to) => ({
    fromUid: from,
    toUid: to,
    status: 'PENDING',
    createdAt: serverTimestamp(),
    respondedAt: null,
  })

  it('allows a normal request', async () => {
    await assertSucceeds(setDoc(doc(as(ALICE), req(ALICE, BOB)), pending(ALICE, BOB)))
  })

  it('refuses a request forged in someone else’s name', async () => {
    await assertFails(setDoc(doc(as(MALLORY), req(ALICE, BOB)), pending(ALICE, BOB)))
  })

  it('refuses a request whose id does not match its contents', async () => {
    await assertFails(setDoc(doc(as(ALICE), req(ALICE, CAROL)), pending(ALICE, BOB)))
  })

  it('refuses a request that starts already accepted', async () => {
    await assertFails(
      setDoc(doc(as(ALICE), req(ALICE, BOB)), { ...pending(ALICE, BOB), status: 'ACCEPTED' }),
    )
  })

  it('refuses a request to someone who blocked you', async () => {
    await seedBlock(BOB, ALICE)
    await assertFails(setDoc(doc(as(ALICE), req(ALICE, BOB)), pending(ALICE, BOB)))
  })

  it('honours friendRequests = NOBODY', async () => {
    await seedProfile(BOB, { friendRequests: 'NOBODY' })
    await assertFails(setDoc(doc(as(ALICE), req(ALICE, BOB)), pending(ALICE, BOB)))
  })

  it('honours friendRequests = FRIENDS_OF_FRIENDS with a verified mutual friend', async () => {
    await seedProfile(BOB, { friendRequests: 'FRIENDS_OF_FRIENDS' })
    // No mutual friend yet.
    await assertFails(
      setDoc(doc(as(ALICE), req(ALICE, BOB)), { ...pending(ALICE, BOB), viaUid: CAROL }),
    )
    // Carol is a friend of both — now the chain verifies.
    await seedFriendship(ALICE, CAROL)
    await seedFriendship(CAROL, BOB)
    await assertSucceeds(
      setDoc(doc(as(ALICE), req(ALICE, BOB)), { ...pending(ALICE, BOB), viaUid: CAROL }),
    )
  })

  it('refuses a FRIENDS_OF_FRIENDS request with a fabricated viaUid', async () => {
    await seedProfile(BOB, { friendRequests: 'FRIENDS_OF_FRIENDS' })
    await seedFriendship(ALICE, CAROL) // Carol is not Bob's friend.
    await assertFails(
      setDoc(doc(as(ALICE), req(ALICE, BOB)), { ...pending(ALICE, BOB), viaUid: CAROL }),
    )
  })

  it('lets only the addressee accept, and only the sender cancel', async () => {
    await seed((db) => setDoc(doc(db, req(ALICE, BOB)), pending(ALICE, BOB)))
    await assertFails(updateDoc(doc(as(ALICE), req(ALICE, BOB)), { status: 'ACCEPTED' }))
    await assertFails(updateDoc(doc(as(BOB), req(ALICE, BOB)), { status: 'CANCELLED' }))
    await assertSucceeds(updateDoc(doc(as(BOB), req(ALICE, BOB)), { status: 'ACCEPTED' }))
  })

  it('refuses an unrelated third party touching the request', async () => {
    await seed((db) => setDoc(doc(db, req(ALICE, BOB)), pending(ALICE, BOB)))
    await assertFails(updateDoc(doc(as(MALLORY), req(ALICE, BOB)), { status: 'ACCEPTED' }))
    await assertFails(getDoc(doc(as(MALLORY), req(ALICE, BOB))))
  })

  it('refuses re-opening a settled request', async () => {
    await seed((db) =>
      setDoc(doc(db, req(ALICE, BOB)), { ...pending(ALICE, BOB), status: 'REJECTED' }),
    )
    await assertFails(updateDoc(doc(as(BOB), req(ALICE, BOB)), { status: 'ACCEPTED' }))
  })
})

describe('family invitations', () => {
  const inv = (from, to) => `familyInvitations/${from}_${to}`
  const pending = (from, to) => ({
    fromUid: from,
    toUid: to,
    message: '',
    status: 'PENDING',
    createdAt: serverTimestamp(),
    respondedAt: null,
  })

  beforeEach(async () => {
    await seedProfile(ALICE)
    await seedProfile(BOB)
  })

  it('requires an existing friendship', async () => {
    await assertFails(setDoc(doc(as(ALICE), inv(ALICE, BOB)), pending(ALICE, BOB)))
    await seedFriendship(ALICE, BOB)
    await assertSucceeds(setDoc(doc(as(ALICE), inv(ALICE, BOB)), pending(ALICE, BOB)))
  })

  it('honours familyInvitations = NOBODY even between friends', async () => {
    await seedFriendship(ALICE, BOB)
    await seedProfile(BOB, { familyInvitations: 'NOBODY' })
    await assertFails(setDoc(doc(as(ALICE), inv(ALICE, BOB)), pending(ALICE, BOB)))
  })
})

describe('chat', () => {
  const chatId = [ALICE, BOB].sort().join('_')
  const header = {
    participants: [ALICE, BOB].sort(),
    lastMessage: 'hi',
    lastSenderUid: ALICE,
  }
  const message = {
    senderUid: ALICE,
    text: 'hi',
    type: 'TEXT',
    status: 'SENT',
    deletedFor: [],
    createdAt: serverTimestamp(),
  }

  beforeEach(async () => {
    await seedProfile(ALICE)
    await seedProfile(BOB)
  })

  it('refuses chatting with a stranger', async () => {
    await assertFails(setDoc(doc(as(ALICE), `chats/${chatId}`), header))
  })

  it('allows friends to chat', async () => {
    await seedFriendship(ALICE, BOB)
    await assertSucceeds(setDoc(doc(as(ALICE), `chats/${chatId}`), header))
    await assertSucceeds(
      setDoc(doc(as(ALICE), `chats/${chatId}/messages/m1`), message),
    )
  })

  it('refuses an outsider reading or writing the conversation', async () => {
    await seedFriendship(ALICE, BOB)
    await seed((db) => setDoc(doc(db, `chats/${chatId}/messages/m1`), message))
    await assertFails(getDoc(doc(as(MALLORY), `chats/${chatId}/messages/m1`)))
    await assertFails(setDoc(doc(as(MALLORY), `chats/${chatId}/messages/m2`), message))
  })

  it('refuses sending a message in someone else’s name', async () => {
    await seedFriendship(ALICE, BOB)
    await assertFails(
      setDoc(doc(as(BOB), `chats/${chatId}/messages/m1`), { ...message, senderUid: ALICE }),
    )
  })

  it('makes a sent message immutable', async () => {
    await seedFriendship(ALICE, BOB)
    await seed((db) => setDoc(doc(db, `chats/${chatId}/messages/m1`), message))
    await assertFails(updateDoc(doc(as(ALICE), `chats/${chatId}/messages/m1`), { text: 'edited' }))
  })

  it('lets the recipient mark read but not the sender', async () => {
    await seedFriendship(ALICE, BOB)
    await seed((db) => setDoc(doc(db, `chats/${chatId}/messages/m1`), message))
    await assertFails(updateDoc(doc(as(ALICE), `chats/${chatId}/messages/m1`), { status: 'READ' }))
    await assertSucceeds(updateDoc(doc(as(BOB), `chats/${chatId}/messages/m1`), { status: 'READ' }))
  })

  it('stops messaging once blocked', async () => {
    await seedFriendship(ALICE, BOB)
    await seedBlock(BOB, ALICE)
    await assertFails(setDoc(doc(as(ALICE), `chats/${chatId}/messages/m2`), message))
  })

  it('rejects an oversized message', async () => {
    await seedFriendship(ALICE, BOB)
    await assertFails(
      setDoc(doc(as(ALICE), `chats/${chatId}/messages/big`), {
        ...message,
        text: 'x'.repeat(4001),
      }),
    )
  })
})

describe('reminder shares', () => {
  const shareId = `${ALICE}_42_${BOB}`
  const share = {
    reminderId: 42,
    ownerUid: ALICE,
    recipientUid: BOB,
    status: 'PENDING',
    title: 'Take pills',
    payload: '{}',
    payloadVersion: 1,
    appliedVersion: 0,
  }

  beforeEach(async () => {
    await seedProfile(ALICE)
    await seedProfile(BOB)
    await seedFriendship(ALICE, BOB)
  })

  it('allows sharing with a friend', async () => {
    await assertSucceeds(setDoc(doc(as(ALICE), `reminderShares/${shareId}`), share))
  })

  it('refuses sharing with a stranger', async () => {
    await seedProfile(CAROL)
    await assertFails(
      setDoc(doc(as(ALICE), `reminderShares/${ALICE}_42_${CAROL}`), {
        ...share,
        recipientUid: CAROL,
      }),
    )
  })

  it('honours reminderSharing = FAMILY_ONLY', async () => {
    await seedProfile(BOB, { reminderSharing: 'FAMILY_ONLY' })
    await assertFails(setDoc(doc(as(ALICE), `reminderShares/${shareId}`), share))
    await seedFamily(ALICE, BOB)
    await assertSucceeds(setDoc(doc(as(ALICE), `reminderShares/${shareId}`), share))
  })

  it('refuses a share forged on someone else’s behalf', async () => {
    await assertFails(setDoc(doc(as(MALLORY), `reminderShares/${shareId}`), share))
  })

  it('refuses an id that disagrees with the document', async () => {
    await assertFails(
      setDoc(doc(as(ALICE), `reminderShares/${ALICE}_99_${BOB}`), share),
    )
  })

  it('stops the recipient rewriting the payload', async () => {
    await seed((db) => setDoc(doc(db, `reminderShares/${shareId}`), share))
    await assertFails(
      updateDoc(doc(as(BOB), `reminderShares/${shareId}`), { payload: '{"evil":true}' }),
    )
  })

  it('stops the owner forging an acknowledgement', async () => {
    await seed((db) => setDoc(doc(db, `reminderShares/${shareId}`), share))
    await assertFails(
      updateDoc(doc(as(ALICE), `reminderShares/${shareId}`), { ackAtMillis: 1 }),
    )
  })

  it('lets the recipient accept and the owner cancel', async () => {
    await seed((db) => setDoc(doc(db, `reminderShares/${shareId}`), share))
    await assertSucceeds(
      updateDoc(doc(as(BOB), `reminderShares/${shareId}`), { status: 'ACCEPTED' }),
    )
    await assertSucceeds(
      updateDoc(doc(as(ALICE), `reminderShares/${shareId}`), { status: 'CANCELLED' }),
    )
  })

  it('refuses a third party reading or cancelling the share', async () => {
    await seed((db) => setDoc(doc(db, `reminderShares/${shareId}`), share))
    await assertFails(getDoc(doc(as(MALLORY), `reminderShares/${shareId}`)))
    await assertFails(
      updateDoc(doc(as(MALLORY), `reminderShares/${shareId}`), { status: 'CANCELLED' }),
    )
  })
})

describe('notification centre', () => {
  const entry = (sender) => ({
    category: 'FRIEND_REQUEST',
    title: 'New friend request',
    body: 'someone wants to be your friend',
    senderUid: sender,
    refId: '',
    read: false,
    archived: false,
    pinned: false,
    createdAt: serverTimestamp(),
  })

  it('lets a sender publish into the recipient’s centre under their own uid', async () => {
    await assertSucceeds(
      setDoc(doc(as(ALICE), `users/${BOB}/notifications/friend_${ALICE}`), entry(ALICE)),
    )
  })

  it('refuses an entry attributed to someone else', async () => {
    await assertFails(
      setDoc(doc(as(MALLORY), `users/${BOB}/notifications/x`), entry(ALICE)),
    )
  })

  it('refuses publishing to someone who blocked you', async () => {
    await seedBlock(BOB, MALLORY)
    await assertFails(
      setDoc(doc(as(MALLORY), `users/${BOB}/notifications/y`), entry(MALLORY)),
    )
  })

  it('keeps the centre readable only by its owner', async () => {
    await seed((db) => setDoc(doc(db, `users/${BOB}/notifications/n1`), entry(ALICE)))
    await assertSucceeds(getDoc(doc(as(BOB), `users/${BOB}/notifications/n1`)))
    await assertFails(getDoc(doc(as(ALICE), `users/${BOB}/notifications/n1`)))
  })

  it('refuses an entry carrying unexpected fields', async () => {
    await assertFails(
      setDoc(doc(as(ALICE), `users/${BOB}/notifications/z`), {
        ...entry(ALICE),
        injected: 'payload',
      }),
    )
  })
})

describe('default deny', () => {
  it('closes any collection without an explicit rule', async () => {
    await assertFails(setDoc(doc(as(ALICE), 'somethingNew/doc'), { a: 1 }))
    await assertFails(getDoc(doc(as(ALICE), 'somethingNew/doc')))
  })
})
