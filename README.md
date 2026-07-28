# Alert Notes

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)](https://developer.android.com/tools/releases/platforms)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)](https://developer.android.com/tools/releases/platforms)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-optional-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)

A reminder app for the reminders that actually matter.

Most reminder apps post a notification and hope you look at it. Alert Notes is
built for the cases where that is not good enough: medication, a dialysis
appointment, a court date, a parent checking that a child took their insulin.
When one of those is due it takes over the screen, over the lock screen, over
whatever app you were in, and it does not go away until you prove you dealt
with it.

---

## Contents

- [The problem](#the-problem)
- [Features](#features)
- [Two modes](#two-modes)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [The reminder lifecycle](#the-reminder-lifecycle)
- [Social features](#social-features)
- [Firebase architecture](#firebase-architecture)
- [Privacy](#privacy)
- [Project layout](#project-layout)
- [Building](#building)
- [Environment configuration](#environment-configuration)
- [Testing](#testing)
- [Localisation](#localisation)
- [Versioning](#versioning)
- [Known limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## The problem

A missed reminder has a cost, and the cost is not evenly distributed. Forgetting
to buy milk is an inconvenience. Forgetting a dose is not.

Android makes the hard cases genuinely hard. Doze delays alarms. Vendor battery
managers kill background work. Notifications get swiped away without being read.
A carer has no way to know whether the reminder they set for someone else was
ever seen.

Alert Notes addresses that directly:

- **It interrupts.** Full-screen alerts over the lock screen, driven by exact
  alarms, with a floating-overlay path when the app is in the background.
- **It survives.** Alarms are rebuilt after reboot, app update, time and
  timezone changes, and on every process start, because a force-stop silently
  cancels every alarm the app owns.
- **It asks for proof.** An alert can require a signature, a photo, a location
  fix, a biometric check, or a completed checklist before it will dismiss.
- **It can be shared.** A reminder can be sent to a friend or assigned to a
  family member, and the sender sees exactly what happened to it.

Everything above works with no account and no network. The social half is
optional, and switched off by default.

---

## Features

### Reminders

- Three types: **text**, **drawing**, and **checklist**
- Recurrence: one-time, fixed interval, daily, weekly, monthly
- Active days, active hours, date ranges, per-reminder time zones
- Global pause (presets, until a moment, or indefinite)
- Snooze, duplicate, archive, delete with undo
- Reusable templates
- Search, sort and filter across seven live statuses

### Alerts

- Full-screen alerts over the lock screen, with the screen woken
- Floating overlay cards at seven anchor positions in three sizes
- Notification fallback when a permission is unavailable
- One-at-a-time presentation ordered by priority
- Animated auto-dismiss countdown and dismiss-lock
- Per-reminder themes, including animated gradients
- An uncoverable action area, so a full-bleed drawing can never hide the controls

### Acknowledgements

Every acknowledgement is recorded with its method and time.

| Method | What it proves |
| --- | --- |
| Tap / swipe | Presence |
| Signature | A person, not a pocket |
| Biometric | A specific person |
| Photo | The task, visually |
| Location | You were where you needed to be |
| Checklist | Every item was completed |

### Organisation

- Calendar with Month, Week, Day and Timeline views over projected occurrences
- History with search, method filters and CSV export
- Home dashboard with a live clock, next-reminder hero card and queue order
- Home-screen widgets (next reminder with countdown, quick create, today)
- Full ZIP backup and restore, plus CSV import and export

### Social (online mode)

- Friends, with requests, blocking and search by @username or display name
- A family circle with per-member permissions, built on top of friendship
- Reminder sharing: send as an invitation, or assign so it leaves your list
- One-to-one chat with system messages narrating the sharing lifecycle
- A durable notification centre, mirrored by push
- Tracking: a single derived timeline showing what happened to a shared reminder
- Eight-control privacy system governing who may reach and see you

---

## Two modes

The mode is chosen on first launch and can be changed in Settings.

| | Offline mode | Online mode |
| --- | --- | --- |
| Account | None | Email and password |
| Network | Never contacted | Used for the social features |
| Reminders | Local, on-device | Local, on-device |
| Alarms | Local | Local |
| Friends, family, sharing, chat | Not available | Available |
| Cloud backup | Not available | Optional |
| Data leaving the device | Only the ZIP export you create | Only what you share |

The important line: **online mode is additive.** It does not move the reminder
engine into the cloud. Reminders live in Room and fire from AlarmManager in both
modes, so a shared reminder that has been delivered keeps working with the
network off, on a plane, forever.

---

## Screenshots

> Screenshots are not yet checked in. Place them in `docs/screenshots/` using
> these names and they will render here.

| Home | Reminder editor | Full-screen alert |
| --- | --- | --- |
| ![Home](docs/screenshots/home.png) | ![Editor](docs/screenshots/editor.png) | ![Alert](docs/screenshots/alert.png) |

| Calendar | Shared reminders | Privacy |
| --- | --- | --- |
| ![Calendar](docs/screenshots/calendar.png) | ![Sharing](docs/screenshots/sharing.png) | ![Privacy](docs/screenshots/privacy.png) |

---

## Architecture

MVVM over a Clean Architecture layering, in a single Gradle module.

```
                       ┌─────────────────────────────┐
   Compose UI  ───────▶│  features/  screens + VMs   │
                       └──────────────┬──────────────┘
                                      │ StateFlow up, calls down
                       ┌──────────────▼──────────────┐
                       │  domain/  models, contracts │  no Android UI types
                       │           scheduling engine │  pure Kotlin, unit testable
                       └──────────────┬──────────────┘
                                      │ repository interfaces
                       ┌──────────────▼──────────────┐
                       │  data/  Room, DataStore,    │
                       │         Firestore, Storage  │
                       └─────────────────────────────┘
                                      │
                       ┌──────────────▼──────────────┐
                       │  services/  AlarmManager,   │
                       │   receivers, FCM, notifier  │
                       └─────────────────────────────┘
```

Rules the codebase actually holds to:

1. **Dependencies point inward.** `domain` knows nothing about Room, Firestore
   or Compose. That is what makes the recurrence engine unit-testable without a
   device.
2. **One owner for scheduling.** Every mutation that can affect an alarm goes
   through `ReminderSchedulingCoordinator`, serialised by a mutex, which
   guarantees the core invariant: *a reminder has exactly one alarm if and only
   if it has a future occurrence, and the stored `nextTriggerAt` mirrors it.*
3. **Screens are functions of state.** They receive a `UiState` and lambdas, not
   a `NavController`, which keeps them previewable and testable.
4. **The client is untrusted.** Every authorisation decision is enforced in
   `firestore.rules`. Client-side checks exist to give better error messages,
   never to grant access.

Full rationale in [ARCHITECTURE.md](ARCHITECTURE.md).

---

## The reminder lifecycle

```
  create/edit
       │
       ▼
  ReminderSchedulingCoordinator.saveAndSchedule
       │   NextTriggerCalculator works out the next occurrence
       │   (recurrence, active days/hours, date range, time zone, pause)
       ▼
  AlarmManager.setAlarmClock          ── survives Doze, exact to the second
       │
       ▼
  ReminderAlarmReceiver               ── the alarm fires
       │
       ▼
  coordinator.onReminderDue           ── enqueue, record history, schedule next
       │
       ▼
  AlertDispatcher                     ── route by screen and keyguard state
       ├── in-app        ReminderAlertHost
       ├── background    AlertActivity (full-screen intent, wakes the screen)
       ├── over apps     overlay window
       └── fallback      notification
       │
       ▼
  acknowledgement                     ── tap / swipe / signature / biometric
       │                                  / photo / location / checklist
       ▼
  history + (if shared) mirrored back to the owner
```

Rebuild triggers, all funnelling into `rescheduleAll()`:

| Event | Why it matters |
| --- | --- |
| `BOOT_COMPLETED` | Alarms do not survive a reboot |
| `MY_PACKAGE_REPLACED` | Alarms do not survive an app update |
| `TIME_SET` / `TIMEZONE_CHANGED` / `DATE_CHANGED` | Wall-clock mapping moved |
| Every process start | A force-stop silently cancels every alarm |

Occurrences that came due while the device was off are delivered late rather
than dropped, and a snooze is preserved across a rebuild rather than recomputed
away.

---

## Social features

### Friends

Deterministic request ids (`{fromUid}_{toUid}`) make a duplicate request
structurally impossible. Accepting writes both edges and both counters in one
atomic batch, so a half-friendship cannot exist. Blocking is symmetric and
terminal: the rules refuse writes in both directions.

### Family

Family builds on friendship - you can only invite an existing friend. Each edge
carries its own permission map, so a one-way and a two-way relationship are just
different values on the two sides. The permission that matters most is
`autoReceiveReminders`: with it on, a shared reminder is released immediately
instead of waiting for approval.

### Reminder sharing

A share is an edge document plus a snapshot of the reminder, carried as the same
versioned JSON the ZIP export uses, so both formats evolve together. Delivery
reconstructs the reminder locally and schedules it through the normal
coordinator - after that it is an ordinary local reminder and works fully
offline.

Two ownership models:

- **Invitation** - both sides keep a copy
- **Assignment** - the sender's copy is archived, so only the recipient is alerted

Idempotency lives in `recipientReminderId`: a share that already carries one is
never delivered again, so listener replays, process restarts and re-opens cannot
double-schedule.

---

## Firebase architecture

Four Firebase products are used, each for one reason.

| Product | Why this and not something else |
| --- | --- |
| **Authentication** | Account identity without this project storing a single password. Email verification, password reset and re-authentication for sensitive operations come with it. The uid it issues is the primary key of the entire data model and the subject of every security rule. |
| **Cloud Firestore** | The social features are inherently realtime - a friend request should appear without a refresh. Firestore's snapshot listeners give that directly, and its offline persistence means the social UI still renders with no connection. Its per-document security rules are what let an untrusted client talk to the database safely with no backend of our own. |
| **Cloud Storage** | Avatars and photo-proof acknowledgements are binary and can be megabytes. Firestore's 1 MiB document ceiling makes it the wrong home for them; Storage has its own rules language and enforces size and content-type limits server-side. |
| **Cloud Messaging** | A reminder shared to a sleeping device has to wake it. FCM is the only sanctioned way to do that on modern Android. Every message is data-only, so the client stays in charge of channels, deep links and suppression. |

Firebase Analytics ships as a transitive dependency with collection disabled in
the manifest and never enabled at runtime.

### Data model

```
users/{uid}                          thin anchor
  ├── public/data                    name, @username, avatar, presence, privacy
  ├── private/data                   email, device, activeChatId
  ├── preferences/data               cross-device settings
  ├── security/data                  auth posture
  ├── statistics/data                friend/family/share counters
  ├── metadata/data                  timestamps, app version
  ├── reminders/{localId}            optional cloud backup
  ├── devices/{deviceId}             sessions and FCM tokens
  ├── blocked/{uid}                  owner-only, both directions enforced
  ├── friends/{uid}                  one thin doc per edge
  ├── family/{uid}                   edge + permission map
  └── notifications/{id}             durable notification centre

usernames/{lowercase}                uniqueness reservation
friendRequests/{from}_{to}           deterministic id
familyInvitations/{from}_{to}        deterministic id
chats/{uidA}_{uidB}/messages/{id}    ids sorted, so membership is provable
reminderShares/{owner}_{rid}_{recipient}
```

The profile is split into single-document subcollections rather than map fields
on one document for a concrete reason: **Firestore rules protect documents, not
fields.** This is the only shape where a rule can expose the public section to a
friend while the private section stays owner-only.

### Security

`firestore.rules` and `storage.rules` are the authorisation boundary, and they
are tested: `firestore-tests/` runs 55 assertions against the emulator covering
forged writes, blocked users, third-party access, immutable messages and every
privacy audience.

```bash
cd firestore-tests && npm install && npm test
```

Cost decisions worth knowing:

- Privacy audiences live on the document a read already fetches, so a
  privacy-aware profile read costs no extra document read.
- User search filters on a denormalised `discoverable` flag, because a rule
  cannot evaluate a condition per query result.
- `payload`, message `text` and notification `body` are exempted from indexing -
  they are never queried and would otherwise cost index entries on every write.

---

## Privacy

Eight controls, each set independently:

| Control | Audiences |
| --- | --- |
| Friend requests | Everyone / Friends of friends / Nobody |
| Family invitations | Everyone / Nobody |
| Reminder sharing | Everyone / Friends / Family / Nobody |
| Messages | Everyone / Friends / Family / Nobody |
| Profile visibility | All five |
| Online status | Everyone / Friends / Family / Nobody |
| Last seen | Everyone / Friends / Family / Nobody |
| Reminder statistics | Everyone / Friends / Family / Nobody |

"Friends of friends" is verified rather than trusted: the sender names the
mutual friend, and the rules check both legs of the chain server-side.

Other guarantees:

- Offline mode never opens a network connection.
- `allowBackup="false"` - the OS never uploads the reminder database to Google.
- Analytics collection is disabled in the manifest.
- Account deletion is available in-app and cascades server-side.

---

## Project layout

```
com.alertnotes
├── core/
│   ├── ui/theme          colour, typography, shape, spacing
│   ├── ui/components     design system: buttons, cards, dialogs, skeletons
│   ├── navigation        type-safe routes, nav host, adaptive shell
│   ├── permissions       permission state model and requests
│   ├── extensions        small focused Kotlin extensions
│   └── util              TimeProvider, AppLogger, AppLocaleManager
├── data/
│   ├── entities dao database   Room v7 with tested migrations
│   ├── datastore               Preferences DataStore
│   ├── remote                  Firestore and Storage data sources
│   ├── repository              repository implementations
│   └── backup                  ZIP and CSV import/export
├── domain/
│   ├── model                   pure Kotlin models
│   ├── repository              contracts the data layer implements
│   └── scheduling              NextTriggerCalculator, OccurrenceProjector,
│                               ReminderSchedulingCoordinator
├── services/                   AlarmManager, receivers, FCM, notifiers,
│                               overlay engine, presence
├── di/                         Hilt modules
├── features/                   one package per screen
├── widgets/                    Glance home-screen widgets
└── biometric/                  BiometricPrompt gate and capability detection

firestore.rules                 authorisation boundary
firestore.indexes.json          every composite index the app needs
storage.rules                   avatar and proof object rules
firestore-tests/                emulator-backed rules tests
functions/                      push delivery and the deletion cascade
```

---

## Building

Requirements:

- Android Studio Otter or newer (AGP 9 requires it)
- JDK 21 (the Android Studio JBR works; the toolchain resolves via Foojay)
- Android SDK 36

```bash
./gradlew :app:assembleDebug
```

Release build:

```bash
./gradlew :app:assembleRelease
```

Without signing credentials this produces an unsigned APK, which is still useful
for verifying R8 output.

### Firebase setup

The committed `app/google-services.json` points at the project's own Firebase
instance. To run against your own:

1. Create a Firebase project and add an Android app with the applicationId
   `com.alertnotes`.
2. Enable Authentication (email/password), Firestore, Storage and Cloud Messaging.
3. Download `google-services.json` into `app/`.
4. Deploy the rules, indexes and functions:

```bash
firebase deploy --only firestore:rules,firestore:indexes,storage
firebase deploy --only functions   # requires the Blaze plan
```

Offline mode needs none of this - the app builds and runs without it.

---

## Environment configuration

Release signing is read from a git-ignored `keystore.properties` in the repo
root, or from environment variables on CI. See
[keystore.properties.example](keystore.properties.example).

| Property | Environment variable |
| --- | --- |
| `storeFile` | `ALERTNOTES_STORE_FILE` |
| `storePassword` | `ALERTNOTES_STORE_PASSWORD` |
| `keyAlias` | `ALERTNOTES_KEY_ALIAS` |
| `keyPassword` | `ALERTNOTES_KEY_PASSWORD` |

---

## Testing

```bash
./gradlew :app:testDebugUnitTest          # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented, needs a device
cd firestore-tests && npm test            # security rules, needs the emulator
```

The rules tests start and stop the Firestore emulator themselves and never touch
a real project.

---

## Localisation

English and Lithuanian, complete. On first launch the device language decides:
a Lithuanian device resolves `values-lt`, everything else falls back to English.
Settings offers an explicit override, and on Android 13+ the app also appears in
the system per-app language screen.

Adding a language means adding `res/values-<tag>/strings.xml` and a `<locale>`
entry in `res/xml/locales_config.xml`. Note that Lithuanian needs four plural
quantity classes (`one`, `few`, `many`, `other`) where English needs two.

---

## Versioning

Semantic versioning. `versionName` is the public version; `versionCode`
increments monotonically on every build submitted anywhere.

Current: **1.0.0** (`versionCode` 10).

---

## Known limitations

The full list is in [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). The ones worth
knowing before you file an issue:

- Some vendor battery managers force-stop apps in ways that cancel alarms. The
  app reconciles on every process start, but a reminder due while force-stopped
  arrives late rather than on time.
- Online status and last seen are filtered when a profile is mapped for a
  viewer. `Nobody` is enforced by never writing the value; the narrower
  audiences rely on the viewer already being entitled to read the profile.
- Reminder sharing carries a snapshot, not a live document. Owner edits
  propagate on the next sweep, not instantly.
- Photo-proof download URLs are Firebase tokenised URLs, which grant access to
  whoever holds the URL. They are only ever written into the share document,
  which is readable by exactly the two parties involved.

---

## Roadmap

**Shipped in 1.0.0** - the reminder engine, three reminder types, six
acknowledgement methods, the calendar hub, system integration (lock screen,
overlays, widgets, biometrics), backup and restore, the full social layer
(friends, family, sharing, chat, notification centre, tracking), the privacy
system, security rules with tests, and English plus Lithuanian.

**Next**

- Cross-device reminder sync, not just backup - needs stable ids, which today
  are autoincrementing Room `Long`s
- Tombstones for deletes, so a delete on one device propagates
- Google sign-in alongside email and password
- Wear OS companion for acknowledgement from the wrist
- Quick Settings tile for global pause
- Custom notification sounds per reminder
- More languages

---

## Contributing

Contributions are welcome.

1. Fork and branch from `main` using `feature/`, `bugfix/` or `refactor/`
   prefixes.
2. Follow the existing conventions. The codebase has a consistent style,
   particularly around comments: they explain *why*, not *what*.
3. Use [Conventional Commits](https://www.conventionalcommits.org/):
   `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `style:`, `build:`, `chore:`.
4. Keep commits focused. One logical change per commit.
5. Run the build and the rules tests before opening a PR.
6. If you change the data model, update `firestore.rules`, the indexes, and the
   rules tests in the same PR.

Bug reports are most useful with the device, Android version, and whether the
app was in the foreground, background or force-stopped at the time.

---

## License

Apache License 2.0 - see [LICENSE](LICENSE).
