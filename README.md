# Alert Notes

An **offline-first reminder app for Android** designed to interrupt your attention with scheduled visual reminders — without ever touching the network.

**Version 1.0.0 — production release.** Three reminder types (text, drawing, checklist), full-screen and floating alerts over any app and the lock screen, a vector drawing studio with paper styles and pressure sensitivity, signature-archived acknowledgements, an Activity history with CSV export, first-run onboarding, a real app lock, and a trap-proof alert layout.

New here? Start with the [User Guide](USER_GUIDE.md). Also see
[PERMISSIONS.md](PERMISSIONS.md) for what the app asks for and why, and
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for honest fine print.

## Privacy is the product

- **No cloud.** Nothing is uploaded, ever. The app has no internet permission.
- **No accounts.** Open the app and use it.
- **No analytics, no ads.** There is no tracking code of any kind.
- **Everything local.** Reminders live in an on-device Room database; settings live in DataStore.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin (2.2) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository pattern (Clean Architecture layering) |
| Navigation | Navigation Compose (type-safe routes) |
| Database | Room |
| Settings | Preferences DataStore |
| DI | Hilt |
| Async | Coroutines + Flow |
| Min / Target SDK | 26 / 36 |

## Project layout

```
com.alertnotes
├── core/            # UI-agnostic building blocks shared by all features
│   ├── ui/theme     # Colors, typography, shapes, spacing, theme
│   ├── ui/components# Design system (buttons, cards, dialogs, list rows…)
│   ├── navigation   # Routes, nav host, adaptive app shell
│   ├── permissions  # Permission status architecture (no requests yet)
│   ├── extensions   # Small, focused Kotlin extensions
│   └── util         # TimeProvider, AppLogger
├── data/            # Room (v7 + tested migrations), DataStore, repositories, backup
├── domain/          # Models, repository interfaces, scheduling engine (pure Kotlin)
│   └── scheduling   # NextTriggerCalculator, OccurrenceProjector, coordinator
├── services/        # AlarmManager scheduler, overlay engine, notifier, receivers
├── di/              # Hilt modules
├── features/        # One package per screen: home, reminders, calendar, alerts,
│                    # drawing, history, settings, onboarding, security, backup, about
├── widgets/         # Glance home-screen widgets
└── biometric/       # BiometricPrompt gate + capability detection
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full rationale and
[CHANGELOG.md](CHANGELOG.md) for release history.

## Building

Open the project in Android Studio (Otter or newer — AGP 9 requires it) and run the `app` configuration, or:

```
./gradlew :app:assembleDebug
```

The build uses Gradle 9.4, AGP 9.2 with built-in Kotlin, KSP, and a JVM 21 toolchain resolved automatically via Foojay.

## Roadmap

Completed so far:

- **Phase 1 — foundation**: theme, design system, navigation, data layer, settings.
- **Phase 2 — reminder engine**: full reminder model (priority, display, snooze, security, constraints), recurrence engine (one-time / intervals / daily / weekly / monthly), active days & hours, date ranges, per-reminder time zones, exact AlarmManager scheduling behind a `ReminderScheduler` interface, priority queue for due reminders, boot / app-update / time-change rescheduling, and a full list UI (search, sort, filter, duplicate, delete with undo).
- **Phase 3 — reminder editor**: animated launch screen, full create/edit/duplicate/delete editor with live alert preview, every scheduling & behaviour option, friendly validation, settings search, unsaved-change protection, and a true two-pane layout on tablets (list + editor side by side).
- **Phase 4 — presentation engine**: in-app alert display driven by the Phase-2 queue — Apple-inspired full-screen alerts, floating cards at seven anchor positions in three sizes, one-at-a-time presentation with priority ordering and optional critical interruption, snooze, accurate auto-dismiss with an animated countdown, dismiss-lock countdown, tap/swipe acknowledgement gestures, live statuses everywhere (list rows, Home "Happening now"), and an editor preview built from the same visual components as the real alerts.
- **Phase 5 — live monitoring**: a shared drift-free countdown engine turns the whole UI into a live view — ticking per-second countdowns on every visible reminder, a real-time dashboard (clock, hero next-reminder card, display-queue order with waiting timers, richer statistics), seven live statuses (Running / Queued / Upcoming / Waiting / Completed / Expired / Disabled) with matching filters, time-remaining sorting, highlighted search matches with animated results, a live status panel in the editor, and press micro-interactions across the design system.

- **Phase 6 — drawing & identity**: a full vector drawing studio (pen / highlighter / eraser, undo/redo, stroke smoothing, brush sizes, canvas backgrounds — stored as lossless JSON strokes), the signature tick-paint acknowledgement (paint a giant checkmark orange to dismiss), signature and directional-swipe acknowledgements, and eight per-reminder themes — six premium solids that gently breathe plus two slowly animating gradients — applied to alerts, previews, editor chips, and list accents.
- **Phase 7 — calendar hub**: a new Calendar tab with Month / Week / Day / Timeline views over *projected occurrences* (recurring reminders expand onto every applicable date, themed), search + filters, tap-to-edit, long-press-to-create, quick actions, a tablet side panel, drawing layout options in the editor (40–100% size, top/center/bottom position, live preview), and Home gains Today's Schedule + a mini calendar preview.
- **Phase 8 — system integration**: Alert Notes becomes a true system reminder app. Lock-screen alerts (full-screen-intent notifications launching a dedicated wake-screen `AlertActivity`), an overlay engine for reminders over other apps (graceful fallback to notifications when the permission is denied), biometric acknowledgement with device-credential fallback, responsive home-screen widgets (next reminder + countdown, quick create, today's schedule), full ZIP backup/restore and CSV import/export, reminder history with search and method filters, a clickable Permission Center with battery-optimization guidance, near-full-screen drawing editing with a docking toolbar, and true-proportion drawing sizes in alerts.

- **Phase 9 — reliability**: root-cause fixes across the system layer — alarm-clock scheduling (Doze-proof, to-the-second accuracy), screen/keyguard-aware alert routing (overlays no longer swallow lock-screen alerts), full-screen presentation over other apps, Android 14 full-screen-intent handling with a Permission Center entry, a redesigned three-mode scheduler (At / In / Recurring), true-100% drawing display, global pause (presets, until a moment, or indefinite), and biometric-confirmed Clear All.
- **Phase 10 — production (1.0.0)**: the critical trap-bug fix (full-screen alerts now reserve an uncoverable action area — 100% drawings can never hide the controls), a fail-safe presentation pipeline (unloadable entries consumed, crash-looping entries quarantined), checklist reminders (third type, with animated check-off and completion-gated acknowledgement), signature-archived acknowledgements in a new Activity screen with search/filters/CSV export, six drawing paper styles + a twelve-color ink palette + stylus pressure, first-run onboarding with a guided permission flow, a working biometric/PIN app lock, per-permission explanations in the Permission Center, an Exit Application entry that never touches scheduling, and the full documentation set (User Guide, Permissions, Known Limitations).

The roadmap beyond 1.0.0: more locales, optional extras (custom sounds, tiles, Wear).
