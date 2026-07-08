# Changelog

All notable changes to Alert Notes are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] — 2026-07-08

Production audit hardening (pre-release stability pass on top of the Phase 10 build below).

### Fixed — reliability of alert delivery
- **Background alerts over other apps never rendered** (root cause: the dispatcher routed on a background dispatcher, so `WindowManager.addView` for the overlay threw off the main thread and was silently swallowed). Routing now runs on the main thread; overlay attach reports success and **falls back to the notification** if it fails, so a due alert always has a surface.
- **Lock-screen full-screen alerts could close before rendering** (cold-start race: `AlertActivity` finished on the presenter's initial `null` before the queue loaded from Room). It now finishes only after an alert was actually shown, with a grace window on cold start; `onNewIntent` re-applies per-reminder lock-screen flags so one reminder's `showWhenLocked` can never leak onto the next.
- **Reminders due while the device was off were silently lost** — the reboot/time-change rebuild now queues missed occurrences for late delivery (never drops them), and **alarms are reconciled on every app start**, recovering from force-stops that wipe AlarmManager without a reboot.
- **Snoozes were destroyed by every alarm rebuild** (reboot, update, time change, pause toggle); a stored future trigger earlier than the natural next occurrence is now preserved as the snooze it is.
- Coordinator entry points are serialized by a mutex (a rebuild racing a delete could re-create a phantom alarm); interval grids anchor on the scheduled due time instead of broadcast processing time (no more per-fire drift); a spurious-fire guard prevents double alerts when recovery races a live alarm.
- Notification actions and the alarm receiver hold `goAsync()` until their work (and the alert's presentation) actually lands — a broadcast-only process can no longer die in the gap and lose a dismissal or a due alert.
- A new alert arriving while a previous notification was showing is audible again (alert-once now applies per entry, not per notification id); the audible channel plays the **alarm sound on the alarm stream**.
- Dismiss/snooze are idempotent per queue entry — an auto-dismiss racing a manual dismiss can no longer resolve the wrong history entry.
- Floating overlay cards no longer steal keyboard/back focus from the app underneath; the queue purges consumed rows; the app coroutine scope survives collector exceptions; a corrupt settings file resets to defaults instead of crashing every write.

### Fixed — alert UI correctness
- The **dismiss-lock countdown now gates every acknowledgement path** (tap-anywhere, swipe, checkmark, signature — not just the button), and both it and the auto-dismiss indicator anchor to the alert's real deadline, so rotation can't reset or desync them.
- A cancelled biometric prompt no longer permanently disarms the tick/signature gestures (they re-arm), and the floating card got the same trap-proof bounded-scroll layout as full-screen alerts.
- Swipe threshold reads the synchronously-tracked travel (no stale-value misses).

### Fixed — security & privacy
- `allowBackup` is off: the Room database and settings no longer upload to Google cloud backup — the explicit local ZIP export is the only way data leaves the device.
- App lock: no longer bypassable after process death (unlock state is process-scoped, not saved-instance state), blocks Recents thumbnails/screenshots via `FLAG_SECURE`, and TalkBack can no longer traverse app content behind the lock, onboarding, or a modal full-screen alert. A lock restored onto a device with no screen lock unlocks instead of bricking the app; transient biometric sensor errors no longer bypass protected-reminder authentication.
- Debug logs (reminder ids, trigger times) are compiled out of release builds; release builds are R8-minified; CSV exports are sanitized against spreadsheet formula injection; ZIP import is bounded against decompression bombs and fully transactional (a failed import rolls back instead of leaving partial data).

### Fixed — accessibility & UX
- TalkBack can now acknowledge swipe alerts (custom action), tap alerts announce a labelled action instead of a silent whole-screen click, snooze chips / theme chips / drawing swatches meet the 48dp target minimum and expose selection state, the brush slider and archived signatures are labelled, and the active drawing tool announces as selected.
- Widget "New reminder" now works while the app is already running; tapping a bottom-bar tab always opens that tab's root screen (Settings no longer reopens a previous sub-screen); Exit Application is a standalone section at the bottom of Settings; editor confirmation dialogs survive rotation.

Phase 10: production release — the trap-bug fix, a third reminder type, and commercial-grade polish.

### Fixed
- **CRITICAL — fullscreen drawing at 100% trapped the app** (root cause: the whole alert lived in one centered, unbounded column, so a large drawing pushed the acknowledgement controls off-screen with no way to scroll or dismiss). Full-screen alerts now use a trap-proof two-zone layout: a scrollable, weight-bounded content area and a **reserved action area pinned below it** that content can never cover or displace. 100% means "maximum usable content area" — the dismiss/snooze/acknowledge controls are always laid out and always tappable.
- **App lock did nothing** (root cause: the `biometricLockEnabled` preference was stored and toggled but never enforced anywhere). A real `AppLockOverlay` now locks the app on launch, re-locks when it leaves the foreground, and unlocks by fingerprint / face / device credential. Reminder alerts render above the lock — a lock protects your notes, never your alarms.
- History method labels, calendar filters, and the editor type selector all handle the new checklist type.

### Added
- **Checklist reminders** — the third reminder type: a title plus unlimited items, each checked off with a springy animation when the reminder fires; acknowledgement stays locked (a disabled pill counts the remaining items) until every item is complete. Recorded in history as "Checklist completed". Schema v7 (additive migration).
- **Fail-safe presentation**: a queue entry that cannot load is consumed instead of blocking the queue; a crash-loop guard (persisted render marker) quarantines an entry that repeatedly kills the process mid-render. Reminder data is never touched, and nothing can soft-lock the alert pipeline.
- **First-run onboarding**: a welcome page and a guided permission page with live statuses, why-it-helps explanations, and Grant buttons; skippable at every step and resumable (statuses refresh when returning from system settings).
- **Signature history**: signature acknowledgements store the drawn signature as a normalized vector alongside the history entry, re-rendered pixel-perfect in the Activity screen.
- **Activity screen** (replaces History): every event type — triggered, acknowledged (button/tap/swipe/tick/signature/checklist), snoozed, auto-dismissed, notification-dismissed — searchable and filterable, with signature thumbnails and one-tap **CSV export**.
- **Drawing studio upgrades**: six paper styles (White, Light Grid, Dark Grid, Dots, Notebook, Plain Dark) rendered identically in the editor, thumbnails, and alerts; a twelve-color ink palette plus the theme accent; and stylus pressure sensitivity where the hardware reports it.
- **Settings**: an **Exit Application** entry that closes only the UI — AlarmManager schedules and reminder data are untouched, so alerts keep firing; per-permission "why it helps" descriptions in the Permission Center.

### Changed
- Version 1.0.0 (versionCode 10). Database schema v7 with a tested additive migration.
- Backup format carries checklists, paper styles, and signatures (older backups import unchanged).

## [0.9.0] — 2026-07-07

Phase 9: reliability & correctness — root-cause fixes across the system layer.

### Fixed
- **Late triggers** (root cause: `setExactAndAllowWhileIdle` is throttled under Doze): the scheduler now uses `AlarmManager.setAlarmClock` — Doze-exempt, second-accurate, no exact-alarm permission required, with the standard system alarm indicator.
- **Lock-screen alerts never appearing** (root cause: with the overlay permission granted, the dispatcher attached a `TYPE_APPLICATION_OVERLAY` window even while locked — such windows never render above the keyguard, and no notification was posted). Routing is now screen/keyguard-aware and re-evaluates on screen-state broadcasts; locked or dark devices always take the notification + full-screen-intent path.
- **Background reminders reduced to a small notification**: full-screen reminders now present properly over other apps through the overlay engine (AUTO preference covers both display modes), and full-screen intents are validated against Android 14's `canUseFullScreenIntent()` with a new Permission Center entry deep-linking to the system toggle.
- **Drawing at 100%** (root cause: the size fraction measured a width-capped, padded text column): the drawing is now a direct child of the surface-wide layout with 8dp safe margins; text and actions keep their readable width caps. 40/60/80% are now true surface proportions.
- Drawing editor: canvas margins reduced further; toolbar never steals vertical space beyond a single row unless expanded.

### Added
- **Three-mode scheduling UI**: *At* (date + time), *In* (1 min – 24 h presets plus custom hours/minutes, re-anchored to the moment you save), *Recurring* (every X minutes/hours, daily, weekly, monthly, custom interval). Only the selected mode's controls are visible; day/hour/date-range constraints appear only for recurring schedules.
- **Global Reminder Controls** in Settings: Pause All Reminders (1h / 8h / 24h presets, until a picked date & time, or indefinitely) — while paused nothing fires, the dashboard shows a paused banner and the hero countdown reads "Paused", and scheduling resumes automatically when the pause expires; plus **Clear All Reminders** with confirmation *and* biometric/device-credential verification, wiping reminders, schedules, drawings, queue, and history, then refreshing widgets.
- Full-screen-alert permission row (Android 14+) in the Permission Center.

## [0.8.0] — 2026-07-07

Phase 8: system integration — Alert Notes becomes a true Android reminder application.

### Added
- **Lock-screen & wake-screen alerts**: due reminders in the background post a max-priority alarm notification; wake/lock-screen reminders attach a full-screen intent launching a dedicated `AlertActivity` shown over the keyguard (`setShowWhenLocked`/`setTurnScreenOn`), which hosts the same alert UI and finishes when the queue drains. Android security is respected — the keyguard is never dismissed.
- **Overlay engine**: floating reminders over other apps via a `TYPE_APPLICATION_OVERLAY` Compose window — wrap-content, gravity-anchored to the reminder's position, outside touches pass through; full-screen overlays supported for Prefer-overlay reminders. Permission denial degrades gracefully to notifications.
- **Biometric acknowledgement**: fingerprint/face confirmation (with optional device PIN/password/pattern fallback) required before dismissing protected reminders, on every surface with an activity; overlays route protected reminders through the notification path.
- **Home-screen widget** (Glance, responsive small/medium/large): next reminder with countdown, quick-create button, open-app, and today's schedule — updated event-driven on every schedule change, alarm, boot, and time change; no polling service.
- **ZIP backup/restore**: versioned JSON (settings, all reminders including themes, drawings, recurrence, and history) inside a ZIP via the system file picker; imports insert as new reminders and schedule immediately. **CSV export/import** for portable lists.
- **Reminder history** (schema v6): triggered time, dismissed time, acknowledgement method, and snooze duration per firing (respecting each reminder's history setting), with a searchable, method-filterable History screen.
- **Permission Center**: every capability row is now clickable and deep-links to the exact system screen; statuses read Granted / Missing / Recommended / Unavailable; battery optimization shows a friendly explanation first (recommended, never forced); Android 13+ notification permission is requested in-place.
- Notification "Dismiss" action resolves the same queue entry as every other surface.

### Fixed
- **Drawing sizes**: the alert content margin no longer eats the drawing area — 100% now truly spans the usable themed surface (safe 16dp margins), and 40/60/80% match their proportions in both alerts and the editor preview.
- Drawing editor: the canvas now expands to nearly the full screen; the toolbar collapses behind a Tune toggle on phones and docks beside the canvas on wide screens.

### Changed
- Alert presentation logic moved from a ViewModel into the app-scoped `AlertPresenter`, shared by the in-app host, `AlertActivity`, the overlay engine, and notification actions.
- `MainActivity` is now a `FragmentActivity` (required for BiometricPrompt).

## [0.7.0] — 2026-07-07

Phase 7: the calendar hub — Alert Notes becomes a planning application.

### Added
- **Calendar tab** (new top-level destination) with Month / Week / Day / Timeline views switched by a segmented control with smooth crossfade-scale transitions.
- **Occurrence projection** (`OccurrenceProjector`): recurring reminders (daily, weekly, monthly, every-X) expand onto every applicable date using the exact same engine that schedules alarms; capped per window for every-minute-style reminders; unit-tested.
- Month view: theme-colored dots per day (stacking, "+N" overflow), an elegantly pulsing today marker, tap to open the day, long-press to create a reminder pre-filled for that date.
- Week view: horizontally scrolling day columns with themed reminder blocks.
- Day view: a chronological timeline showing time, title, priority, and display mode on themed rows.
- Timeline view: the next 30 days bucketed under Today / Tomorrow / This week / Later headers.
- Quick actions on any occurrence (long-press): edit, pause/resume, delete (confirmed).
- Calendar search plus filters: active/disabled, recurring/one-time, text/drawing, high/critical priority, and a theme picker chip.
- Tablet layout: the selected day's timeline docks as a permanent side panel next to month/week views.
- **Drawing layout editor**: drawing size (40 / 60 / 80 / 100% of the alert width) and position (top / centered / bottom), persisted per reminder (schema v5, additive migration), rendered identically in the live preview and real alerts.
- Home dashboard: **Today's Schedule** card (remaining occurrences with times and theme dots, plus a this-week count) and a **mini calendar preview** that opens the Calendar tab.

## [0.6.0] — 2026-07-07

Phase 6: drawing reminders, gesture acknowledgements, and reminder themes — the Alert Notes identity.

### Added
- **Drawing studio**: full-screen canvas with pen, highlighter (translucent wide ink), and true eraser (offscreen-layer `BlendMode.Clear` — erases ink, keeps the background), brush-size slider, four canvas backgrounds, undo/redo (bounded history), clear, and stroke smoothing via midpoint quadratic curves. Drawings are stored as normalized vector strokes in JSON — zero bitmap loss at any scale — and survive rotation through a saver.
- Drawing reminders render their artwork in the editor (thumbnail + preview) and inside real alerts; ink color comes from the reminder's theme.
- **Tick gesture acknowledgement**: paint a very large grey checkmark with your finger — painted regions fill brand orange (#E4572E), and at ~95% coverage the check pulses and the reminder dismisses. Includes a TalkBack custom action and painted-progress semantics.
- **Signature acknowledgement**: sign on a baseline pad; enough ink + finger lift dismisses.
- **Directional swipe acknowledgement**: configurable up/down/left/right; the alert follows the finger with fade and spring-back.
- **Eight reminder themes**: Primary Orange, Forest Green, Golden Yellow, Midnight Purple (#5E244E), Berry Red (#AA1C41), Soft Cream (#FFE8B4), and two animated gradients (orange tones; berry→midnight purple). Solids breathe subtly; gradients drift very slowly, Apple-style.
- Themed alert surfaces: ~80% of the alert is the theme (inner card on full screen; the card itself when floating) on a neutral outer background; all alert content, snooze chips, buttons, and countdowns adapt to the theme's contrast color.
- Editor theme picker: eight oval color chips with soft-spring enlargement and an in-chip checkmark on selection; the live preview updates instantly.
- Reminder list rows carry a 5dp theme accent bar on the right edge.
- Room schema v4 (additive migration): `theme`, `swipe_direction`, `drawing` columns.

## [0.5.0] — 2026-07-07

Phase 5: live monitoring — the UI now reflects reminder state in real time.

### Added
- **Countdown engine**: a single drift-free 1Hz heartbeat (`SecondTicker`) aligned to wall-clock second boundaries, shared by every countdown, stopped automatically when nothing is visible. Leaf composables (`CountdownText`, `ElapsedText`, `LiveClockText`) mean only the ticking text recomposes — never rows, cards, or screens.
- Reminder list rows show a live per-second countdown ("in 19m 58s") instead of a static relative time; rows animate on reorder/appear/disappear.
- Home dashboard: live clock in the header, a hero **Next reminder** card (big ticking countdown, title, priority, display mode, absolute time), a **Display queue** panel showing presentation order with live waiting timers (`00:00:52`), queue length and due-today statistics, and the running-alert banner now shows elapsed time.
- Seven live statuses — Running, Queued, Upcoming (≤24h), Waiting, Completed, Expired, Disabled — computed against the display queue and shown with smoothly animating status colors.
- Status filters (Running / Queued / Upcoming / Waiting / Completed / Expired / Disabled) alongside the existing type and priority filters.
- "Time remaining" sort (soonest first, unscheduled last) — now the default.
- Search matches are highlighted in the primary color in titles and descriptions.
- Editor gains a live **Status** panel for existing reminders: ticking next-trigger countdown with the absolute time, last-triggered, a human schedule summary ("Daily at 9:00 AM"), priority, and history state.
- Micro-interactions: all buttons compress slightly while pressed (spring back on release), clickable cards lift while touched.
- Unit tests for duration formatting and the status model (30 tests total).

### Changed
- Tabular figures on all live numbers so ticking digits never cause layout jitter.
- "Due 24h" home statistic became "Due today" (counts up to local midnight).

## [0.4.0] — 2026-07-06

Phase 4: the reminder presentation engine.

### Added
- In-app alert display driven by the Phase-2 queue: one alert at a time, priority-ordered (critical → high → normal, then earliest due), next alert appearing immediately after dismissal — occurrences are never lost.
- Full-screen alerts: accent-tinted background, large centered typography, rounded primary action, minimal chrome; content capped at 480dp so tablets don't stretch.
- Floating card alerts at **seven anchor positions** (corners, edges, center) in three sizes, inset from screen edges, overlaying the app without blocking it.
- Presentation animations: fade + soft-spring scale in, fade + slight downward slide out; swipe-away follows the finger and springs back below the threshold.
- Accurate auto-dismiss (1s–1min) with an animated hairline countdown bar and per-second remaining label.
- Dismiss-lock countdown: the dismiss action stays disabled with a ticking label; now also configurable in the editor (off/3/5/10/15s).
- Acknowledgement gestures: tap (button or tap-anywhere) and swipe; tick-gesture and signature fall back to the button until the drawing phase.
- Snooze from the alert via the reminder's configured durations — postpones just the next occurrence through the scheduling coordinator.
- Critical interruption: a critical reminder may replace a lower-priority alert on screen (new setting, on by default); the replaced alert returns afterwards.
- Reminder list rows now show live status — Running / Upcoming / Paused / Expired.
- Home shows a "Happening now" banner while an alert is running.
- Editor: floating position picked on a miniature 7-dot screen selector; the live preview is now built from the same visual components as real alerts.

### Changed
- `FloatingCardPosition` expanded from 3 to 7 anchors; legacy stored values map to their centered equivalents (no schema change needed).

## [0.3.0] — 2026-07-06

Phase 3: the reminder editor and launch experience.

### Added
- Launch screen on every start: brand logo fading in and scaling 96%→100%, hairline progress indicator, light/dark aware, dismissed the moment initialization and the intro finish (~0.9s, no artificial delay). The window background now matches the theme so cold starts never flash.
- Full reminder editor (create / edit / duplicate / delete) hosted two ways: a full-screen modal route on phones (slides up/down) and an embedded right-hand pane in the two-pane tablet layout.
- Editor sections: General (title, description, text/drawing type, priority, enabled), Display (mode, floating position & size) with a **live animated preview**, Dismissal (auto-dismiss 1s–1min or manual; acknowledgement: none/tap/swipe/tick/signature), Snooze (toggle + 1/5/10/20-minute button selection), Security (fingerprint requirement + device-PIN fallback with capability explanation), Alert behavior (wake screen, lock screen, vibration, sound, history, overlay preference), Schedule (all seven recurrence kinds with parameter carry-over, active days selector, active hours, start/end dates).
- Friendly inline validation (empty title, past one-time trigger, invalid intervals, empty snooze/day selections, inverted date ranges, reminder time outside active hours); Save stays disabled until the draft is valid.
- Settings search inside the editor filters sections by any control label.
- Unsaved-change protection: system back and close both ask before discarding a dirty draft.
- New design-system components: segmented control, time & date picker dialogs, weekday selector, setting-value row, generic option-picker dialog (theme picker now reuses it).
- Locale-aware weekday ordering and 12/24-hour time formatting throughout.

### Changed
- Acknowledgement is now a gesture type instead of a boolean; biometric security gained a PIN-fallback flag (Room schema v3 with tested migration — old `true` values map to Tap).
- Default snooze buttons are now 5 and 10 minutes, from the 1/5/10/20 set.
- The Reminders quick-add dialog was removed; the FAB, list rows, and Home quick action all open the full editor.

## [0.2.0] — 2026-07-06

Phase 2: the reminder engine. Scheduling works end to end internally; nothing is shown to the user when a reminder fires yet.

### Added
- Complete reminder domain model: type (text/drawing), priority (normal/high/critical), display mode (full screen / floating card) with position & size, auto-dismiss, acknowledgement requirement, dismiss countdown, biometric requirement, snooze with allowed durations, history/vibration/sound/wake/lock-screen/overlay flags, active days, active hours (incl. overnight windows), start/end dates, per-reminder time zone, archive flag.
- Recurrence engine: one-time, every X minutes, every X hours, custom interval, daily, weekly, monthly (day-of-month clamped in short months) — extensible without schema redesign (discriminator + parameter columns).
- `NextTriggerCalculator`: pure, unit-tested occurrence math honouring all constraints; interval recurrences tick on a fixed anchor grid.
- Scheduling abstraction (`ReminderScheduler`) with an exact-alarm `AlarmManager` implementation that degrades to inexact when `SCHEDULE_EXACT_ALARM` is not granted; one PendingIntent identity per reminder prevents duplicate alarms.
- `ReminderSchedulingCoordinator`: single owner of alarm bookkeeping (save/enable/delete/restore/fire/reschedule-all) keeping the alarm ↔ `next_trigger_at` invariant.
- Reminder queue (Room table with CASCADE FK): due occurrences are queued — never discarded — ordered by priority then due time, ready for the popup engine.
- Boot receiver (BOOT_COMPLETED + MY_PACKAGE_REPLACED) and time-change receiver (TIME_SET, TIMEZONE_CHANGED, DATE_CHANGED) rebuilding all alarms.
- Room schema v2 with tested `MIGRATION_1_2` (table rebuild, minSdk-26-safe) and indexes on `is_enabled`, `next_trigger_at`, `updated_at`.
- Reminders screen: instant search, sorting (newest/oldest/alphabetical/priority/recently modified), AND-combined filters (active/paused/one-time/recurring/high/critical), duplicate, delete with snackbar undo, sealed Loading/Error/Content UI state, priority & next-occurrence status line.
- Home dashboard now shows real statistics (total/active/paused/due-in-24h via one aggregate query) and real upcoming occurrences with relative times.
- Injectable `AppLogger`; broadcast receivers and ViewModels log failures instead of crashing.
- Unit tests: 18 scheduling-path tests for `NextTriggerCalculator`, lossless entity-mapping round-trip tests, active-day bitmask tests.

### Changed
- `Reminder.notes` renamed to `description`; quick-add dialog now creates a real one-time occurrence an hour ahead.
- Delete confirmation dialog replaced by immediate delete + snackbar undo.

### Removed
- Template `ExampleUnitTest` (superseded by real engine tests).

## [0.1.0] — 2026-07-06

Phase 1: production foundation. No reminder scheduling yet.

### Added
- Clean architecture skeleton: `domain` (models + repository interfaces), `data` (Room, DataStore, repository implementations), `core` (theme, design system, navigation, permissions, utilities), `features` (one package per screen), `di` (Hilt modules).
- Apple-inspired Material 3 theme around brand orange `#E4572E`: hand-tuned light & dark color schemes, editorial type scale, rounded shape scale, spacing system (`MaterialTheme.spacing`), optional Material You dynamic color.
- Design system: primary/secondary/outlined/danger buttons, primary & section cards, top app bar, bottom navigation + navigation rail, dialogs (general + confirmation), list item with icon badge, toggle row, radio item, text field, search field, FAB, empty state.
- Type-safe Navigation Compose graph with subtle fade/slide transitions; adaptive shell (bottom bar on phones, rail on tablets); per-tab state preservation.
- Home dashboard: greeting, upcoming reminders, live statistics, quick actions, recent activity — all derived from the database in real time.
- Reminders: create, edit, search, pause/resume, and delete reminder notes backed by Room (scheduling intentionally deferred).
- Settings persisted in DataStore: theme mode (Light/Dark/System, applied instantly), Material You toggle, reminder-alert preference, biometric-lock preference, live permission status display, privacy statement, developer info.
- Permissions architecture reporting status of notifications, exact alarms, overlay, battery optimization, and biometrics — checks only, no requests.
- About screen: version, developer, license, privacy statement, open-source libraries.
- Backup screen UI for ZIP/CSV export & import (engines arrive in a later phase; actions disabled and labelled).
- Adaptive launcher icon: white bell on an orange gradient, with themed (monochrome) icon support.
- Accessibility groundwork: dynamic font scaling, TalkBack semantics on all controls, ≥48dp touch targets.
- Documentation: README.md, ARCHITECTURE.md, CHANGELOG.md.

### Changed
- Migrated template project to Hilt 2.60, KSP 2.3.6, Room 2.7.2, Navigation 2.9.3, DataStore 1.1.7, Kotlinx Serialization 1.9.0 on AGP 9.2.1 with built-in Kotlin.
