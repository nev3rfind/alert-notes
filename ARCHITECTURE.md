# Alert Notes — Architecture

This document explains how the codebase is structured, why, and how to extend it.

## Guiding principles

1. **Clean Architecture layering.** UI → ViewModel → Repository interface → data source. Dependencies always point inward; `domain` has no Android UI dependencies.
2. **Single responsibility.** Every class answers one question. Screens render state; ViewModels own state; repositories own persistence.
3. **Unidirectional data flow.** Data flows up as `Flow`/`StateFlow`; events flow down as plain function calls. Screens are stateless functions of their `UiState`.
4. **Offline-first by design.** The reminder engine never needs the network: reminders live in Room and fire from AlarmManager in both modes. Online mode is strictly additive - it adds accounts, friends, family, sharing and chat on top, and is opt-in. Offline mode still opens no connection at all, so that privacy guarantee stays structural rather than aspirational.

## Layers

### `domain` — the contract

Pure Kotlin (unit-testable on the JVM):

- `domain/model` — `Reminder` with the complete long-term feature surface
  (type, priority, display mode, floating-card layout, dismissal & snooze
  behaviour, biometric flag, alerting flags, per-reminder time zone),
  `Recurrence` (sealed: None / OneTime / EveryMinutes / EveryHours /
  CustomInterval / Daily / Weekly / Monthly), `ActiveHours` (incl. windows
  crossing midnight), `QueuedReminder`, `ReminderStats`, `UserPreferences`.
- `domain/repository` — `ReminderRepository`, `ReminderQueueRepository`,
  `SettingsRepository`.
- `domain/scheduling` — the reminder engine (see below).

### `domain/scheduling` — the reminder engine

Three pieces, all platform-free:

1. **`NextTriggerCalculator`** — pure occurrence math. Given a reminder and
   "after" instant, returns the next trigger honouring recurrence, active
   days, active hours, the start/end date range, and the reminder's own time
   zone. Interval recurrences tick on a fixed grid (anchored at last trigger
   → start date → creation) so edits never cause drift. Fully covered by
   unit tests.
2. **`ReminderScheduler`** (interface) — the only door to platform alarms.
   Contract: *at most one alarm per reminder id*; scheduling again replaces.
3. **`ReminderSchedulingCoordinator`** — the single owner of alarm
   bookkeeping. Every scheduling-relevant mutation (save, enable/disable,
   delete, restore, duplicate via save, alarm fired, reschedule-all) flows
   through it, maintaining the invariant: a reminder has exactly one alarm
   iff it has a future occurrence, and `next_trigger_at` in the database
   always mirrors that alarm. ViewModels and receivers never touch the
   scheduler or scheduling columns directly.

When a reminder fires, the coordinator appends the occurrence to the
**reminder queue** (Room table, CASCADE-linked to its reminder). Nothing is
ever discarded — simultaneous reminders all wait there, ordered by priority
rank then due time, until the popup engine (future phase) consumes them.

### `features/alerts` — the presentation engine

Displaying reminders is completely separated from scheduling: the scheduling
side *writes* queue entries (`ReminderSchedulingCoordinator.onReminderDue`),
the presentation side *reads* them — the `reminder_queue` table is the only
bridge, so either side can evolve alone.

- **`AlertPresenterViewModel`** (activity-scoped) is the engine's brain. It
  combines the pending queue, user preferences, and the currently visible
  entry into a single `activeAlert` state: one alert at a time, highest
  priority first then earliest due; the visible alert stays up unless a
  CRITICAL entry arrives and the "critical interrupts" setting is on, in
  which case it is replaced — the replaced entry stays pending and returns
  afterwards. Dismiss/snooze consume the entry; the flow then surfaces the
  next one immediately. Nothing is ever dropped. Auto-dismiss runs as one
  `collectLatest` timer per visible alert, cancelled on any change.
- **`ReminderAlertHost`** sits above the whole app in `MainActivity` and
  renders the active alert through `AnimatedContent` (fade + soft-spring
  scale in, fade + slight downward slide out). Full-screen alerts own the
  display and the back button; floating cards overlay the UI at the
  reminder's anchor position (7 positions × 3 sizes, inset so they never
  leave the screen) without blocking the app underneath.
- **Gestures & countdowns** — the primary button adapts to the
  acknowledgement type (tap-anywhere for TAP, swipe-away with spring-back
  for SWIPE; tick/signature fall back to the button until the drawing
  phase). The dismiss-lock countdown disables the button with a ticking
  label; auto-dismiss shows a linearly animated hairline progress bar plus
  a once-per-second remaining label.
- **`AlertVisuals`** is the shared vocabulary (icon badge, accent colors,
  snooze chips, size/position mappings) used by both the real alerts and
  the editor's live preview — the preview *is* the alert at miniature
  scale, not a lookalike.
- Snoozing calls `ReminderSchedulingCoordinator.snooze`, which postpones
  only the next occurrence; the recurrence itself is untouched.

### The calendar (`features/calendar` + `domain/scheduling/OccurrenceProjector`)

The calendar never guesses: `OccurrenceProjector` expands each reminder's
recurrence into concrete instants inside a window by iterating the same
`NextTriggerCalculator` the alarm engine uses — calendar rendering therefore
cannot disagree with actual scheduling. Dense interval reminders are capped
per window (surfaced as "+N"), disabled reminders still project (filters
narrow), archived ones never do.

`CalendarViewModel` folds one selection snapshot (mode, anchor date,
selected date, query, filters, theme filter) with the reminders flow, projects
on `Dispatchers.Default` under `mapLatest` (stale projections cancel), and
emits a `Map<LocalDate, List<CalendarOccurrence>>` for exactly the visible
window. Views are thin: **Month** (7-column grid, theme dots + "+N", pulsing
today, tap→day, long-press→create), **Week** (horizontally scrolling themed
block columns), **Day** (chronological timeline rows), **Timeline**
(Today / Tomorrow / This week / Later buckets over the next 30 days). All
occurrence rows share tap-to-edit and long-press quick actions
(edit / pause / delete). On expanded windows the selected day's timeline
docks as a permanent side panel. Home reuses the same projector for Today's
Schedule and the mini month preview.

### Drawing (`features/drawing` + `domain/model/ReminderDrawing`)

Drawings are **pure vectors**: strokes of normalized (0..1) points with tool,
width fraction, and ink color, serialized to JSON in a nullable `drawing`
column — re-rendered losslessly at any size, from list thumbnails to
full-screen alerts. `DrawingRenderer` is the single rendering path
(midpoint-quadratic smoothing, round caps, highlighter alpha, eraser via
`BlendMode.Clear` inside an offscreen layer so only ink is erased);
`DrawingCanvasDialog` is the studio: pen/highlighter/eraser, brush-size
slider, a twelve-color ink palette (defaulting to the reminder theme's
accent), six **paper styles** (White, Light Grid, Dark Grid, Dots, Notebook,
Plain Dark — the pattern draws in a layer *under* the ink so erasing never
cuts the paper), bounded undo/redo history, and a JSON saver so work
survives rotation. Stylus **pressure** scales the committed stroke width
when the hardware reports varying pressure; constant-pressure touch input
is left untouched. Paper style is stored on `ReminderDrawing` (taking
precedence over the legacy flat background color) so it rides through
backups for free.

### Reminder themes (`features/alerts/ReminderThemes`)

Eight `ReminderTheme`s (six solids + two gradients) resolve through one
`ReminderThemeSpec` (palette, content-contrast color, label). Motion comes
from `animatedBrush()` — gradients drift by slowly lerping their color phase
over ~9s; solids breathe with a ±6% brightness swell — while `staticBrush()`
serves list accents and editor chips. Alerts put the theme on ~80% of the
visible surface (inner card on full screen, the card itself when floating);
every text, chip, button, and countdown on it uses the spec's content color.

### Gesture acknowledgements (`features/alerts/AcknowledgeGestures`)

- **Tick gesture** — a large grey checkmark path is sampled into 72 points
  (`PathMeasure`); finger movement marks samples within the brush radius as
  painted, rendering as brand-orange strokes intersected with the check
  shape (`DstIn` layer). At ≥95% coverage the check pulses fully orange and
  acknowledges. TalkBack users get a custom "Acknowledge" action instead of
  painting; progress is exposed as a range-info semantic.
- **Signature** — free strokes on a baseline pad; when total inked length
  passes a width-relative threshold, lifting the finger acknowledges. The
  strokes are normalized into a `ReminderDrawing` and threaded through the
  dismiss path into the history entry, so the Activity screen re-renders
  the exact signature that acknowledged the reminder.
- **Checklist** — checklist reminders render their items as toggle rows
  (springy check animation, strike-through, progress line). While any item
  is unchecked, *every* acknowledgement affordance is replaced by a
  disabled pill counting the remaining items; tap/swipe/back are gated the
  same way. Completion dismisses with the `CHECKLIST` method. Checked state
  is keyed to the queue entry, so a re-trigger starts fresh.
- **Swipe** — the alert follows the finger along its configured direction
  (up/down/left/right; opposite movement ignored), springing back below the
  threshold. Tap acknowledges via button or tap-anywhere.

Both drawing-style pads sit on a neutral inset panel so they stay readable
on any theme surface.

### The countdown engine (`core/util/SecondTicker` + `core/ui/components/LiveTimeText`)

All live time in the app flows from **one** heartbeat: `SecondTicker`, a
singleton `SharedFlow<Instant>` that sleeps until the next wall-clock second
boundary (drift-free by construction) and stops entirely under
`WhileSubscribed` when nothing observes it — backgrounded or idle, the app
burns zero timer cycles.

The UI consumes it exclusively through **leaf composables** —
`CountdownText`, `ElapsedText`, `LiveClockText` — which read `rememberNow()`
(the ticker via a CompositionLocal, collected with
`collectAsStateWithLifecycle`). Because the tick is read inside the leaf,
each second recomposes only those Text nodes: rows, cards, and screens
around them are untouched. Lazy lists compose only visible items, so only
visible countdowns subscribe; scrolled-away and off-screen ones cost
nothing. Tabular figures (`tnum`) keep ticking digits from shifting layout.
ViewModels never embed the tick in their state — statuses and lists
recompute only when *data* changes.

### System alert surfaces (`AlertDispatcher` + `AlertActivity` + overlay + notifications)

`AlertPresenter` (formerly ViewModel logic, now an app-scoped singleton) is
the single source of truth for "what alert is on screen"; four surfaces
consume it and `AlertDispatcher` routes among them:

- **App foreground** → the in-app `ReminderAlertHost` (unchanged behavior).
- **Background, overlay possible** (permission granted, overlay preference
  allows, no biometric requirement) → `AlertOverlayEngine` attaches a
  `TYPE_APPLICATION_OVERLAY` Compose window: floating cards are wrap-content
  windows gravity-anchored to the reminder's position (touches outside pass
  through); full-screen alerts own the display.
- **Background otherwise** → `ReminderNotifier` posts a max-priority alarm
  notification (audible or silent channel per reminder; lock-screen
  visibility per reminder). Wake-screen/lock-screen reminders attach a
  full-screen intent launching **`AlertActivity`** — shown over the keyguard
  with `setShowWhenLocked`/`setTurnScreenOn`, never dismissing it. The
  activity hosts the same `ReminderAlertHost` and finishes when the queue
  drains.
- Notification "Dismiss" actions land in `AlertActionReceiver` and consume
  the same queue entry — every path converges.

Dismissal is method-aware: biometric-protected reminders route through
`BiometricGate` (BiometricPrompt on the hosting FragmentActivity, optional
device-credential fallback) before the entry is consumed; overlays never
host biometric reminders (prompts need an activity) — those go the
notification → AlertActivity route.

### The trap-proof alert layout & fail-safe pipeline (Phase 10)

The 1.0.0 headline fix. Full-screen alerts previously laid everything in one
centered, unbounded column — a 100% drawing pushed the action buttons off
screen with no scroll, soft-locking the app. The layout is now **two zones**:
a scrollable, `weight(1f)`-bounded content column (drawing, title, notes,
checklist) and a **reserved action column pinned below it** (`AlertActions`
+ auto-dismiss indicator) that content can never cover or displace, on any
screen size, at any drawing size.

Behind the layout sits a fail-safe pipeline in `AlertPresenter`:

- an entry whose reminder fails to load (or vanished) is consumed and
  logged instead of blocking the queue;
- `AlertCrashGuard` persists a render marker (SharedPreferences) while an
  entry is on screen and clears it on any graceful change; an entry seen
  three times without a graceful clear — i.e. one that keeps killing the
  process mid-render — is quarantined (consumed). Reminder data and future
  occurrences are never touched; only the stuck presentation is dropped.

### App lock & onboarding

- **`AppLockOverlay`** enforces the `biometricLockEnabled` preference (which
  previously was stored but enforced nowhere): a full-screen scrim over the
  app content on launch, re-locking on `ON_STOP` (with an `authenticating`
  flag so the credential screen itself doesn't trigger a re-lock), unlocked
  through `BiometricGate` with device-credential fallback. It layers *under*
  `ReminderAlertHost` — the lock protects notes, never alarms.
- **`OnboardingScreen`** shows once (`onboardingCompleted` preference):
  welcome page, then a guided permission page reusing the Permission
  Center's status machinery — live statuses, why-it-helps copy, Grant
  buttons (runtime request for notifications, settings deep-links for the
  rest), refresh-on-resume. Skippable at every step; skipping completes it.

### History, backup, widgets

- **History** (`reminder_history`, schema v7): the coordinator records each
  trigger (when the reminder has history enabled); the presenter resolves
  the latest open entry on dismiss/snooze with the acknowledgement method,
  snooze duration, and (for signature acknowledgements) the signature
  vector. The Activity screen offers search, method filters, signature
  thumbnails, and a CSV export through the Storage Access Framework.
- **Backup** (`data/backup`): ZIP carries a versioned JSON of settings +
  reminders (entity-shaped primitives, including themes and vector drawing
  JSON) + history; imports insert as new rows (never clobber) and schedule
  through the coordinator. CSV is the portable subset. Everything flows
  through the Storage Access Framework — local files only.
- **Widgets** (`widgets/`, Glance): one responsive widget with small
  (next + countdown), medium (+ quick actions), large (+ today's schedule)
  layouts, fed by the same repository + occurrence projector via a Hilt
  entry point. Updates are event-driven through the domain `ScheduleEvents`
  seam (every schedule change, alarm fire, boot, and time change) — no
  polling service.

### `services` — the platform edge

- `AlarmManagerReminderScheduler` — alarms via **`setAlarmClock`**, the
  user-facing alarm API: exempt from Doze batching (the root cause of the
  late triggers `setExactAndAllowWhileIdle` suffered), accurate to the
  second, and requiring no exact-alarm permission. One PendingIntent
  identity per reminder (request code + unique data URI,
  `FLAG_UPDATE_CURRENT`) makes duplicate alarms structurally impossible.
- **Global pause** — the coordinator persists the *true* next occurrence
  but clamps the platform alarm past an active pause window (suspending
  alarms entirely while paused indefinitely); occurrences that slip through
  are swallowed at `onReminderDue`. Pause changes rebuild every alarm, so
  resume is automatic at the chosen moment with no extra machinery.
- **Alert routing is screen/keyguard-aware** (re-evaluated on
  SCREEN_ON/OFF and USER_PRESENT): overlays render only over a live,
  unlocked session, because `TYPE_APPLICATION_OVERLAY` windows never appear
  above the keyguard — locked or dark devices always take the
  notification + full-screen-intent path. Full-screen intents are checked
  against Android 14's `canUseFullScreenIntent()` and surfaced as a
  Permission Center entry.
- `ReminderAlarmReceiver` — receives due alarms, hands off to the coordinator.
- `BootCompletedReceiver` — BOOT_COMPLETED + MY_PACKAGE_REPLACED → rebuild
  all alarms (they don't survive either event).
- `TimeChangeReceiver` — TIME_SET / TIMEZONE_CHANGED / DATE_CHANGED →
  recompute every occurrence (wall-clock definitions may map to new epochs).

All receivers are `@AndroidEntryPoint`, do their work in an injected
application-scoped coroutine scope via `goAsync()`, and log failures instead
of crashing.

### `data` — the implementation

- `data/entities` + `data/dao` + `data/database` — Room, **schema v2**.
  `reminders` stores the full model as SQLite primitives (enums as names,
  recurrence as discriminator + parameter columns, active days as a 7-bit
  mask) with indexes on `is_enabled`, `next_trigger_at`, `updated_at`.
  `reminder_queue` holds due occurrences with a CASCADE foreign key (no
  orphans possible). `MIGRATION_1_2` rebuilds the v1 table (rename-free, so
  it works on minSdk 26) and is the template for future migrations; schema
  history is exported to `app/schemas/`.
- Dashboard statistics come from one aggregate query
  (`ReminderDao.observeStats`) rather than multiple counts.
- `data/datastore/UserPreferencesDataSource` — the only class that knows
  DataStore key names; corrupt data degrades to defaults.
- `data/repository` — maps entities ⇄ domain models and implements the
  domain interfaces. Corrupt enum/recurrence data degrades to safe defaults,
  never crashes.

### `core` — shared building blocks

- `core/ui/theme` — color schemes (light/dark + optional Material You),
  typography, shapes, and a `Spacing` scale exposed as
  `MaterialTheme.spacing`. Use the scale; never hard-code dp for whitespace.
- `core/ui/components` — the design system. **Every screen is built from
  these**: `PrimaryButton` / `SecondaryButton` / `AppOutlinedButton` /
  `DangerButton`, `PrimaryCard` / `SectionCard`, `AppTopBar`,
  `AppBottomNavigationBar` / `AppNavigationRail`, `AppDialog` /
  `ConfirmationDialog`, `AppListItem`, `AppToggleRow`, `AppRadioItem`,
  `AppTextField` / `SearchField`, `AppFloatingActionButton`, `EmptyState`.
- `core/navigation` — type-safe routes (`@Serializable` objects), the
  `AlertNotesNavHost`, and `AlertNotesApp`, the adaptive shell that shows a
  bottom bar on phones and a navigation rail on tablets.
- `core/permissions` — `PermissionsManager` reports the status of every
  capability the app will eventually request (notifications, exact alarms,
  overlay, battery optimization, biometrics). Request flows plug in here in
  a later phase without touching the UI that displays statuses today.
- `core/util/TimeProvider` — injectable clock; production code never calls
  `Instant.now()` directly.

### `features` — one package per screen

Each feature contains a `Screen` composable and (when it has state) a
`ViewModel`. Screens receive navigation callbacks as lambdas — they never see
the `NavController` — which keeps them previewable and unit-testable.

| Feature | Contents |
| --- | --- |
| `launch` | The launch overlay: logo scale/fade intro, hairline progress line, dismissed as soon as the intro and settings load complete — no artificial delay. |
| `home` | Dashboard: greeting, upcoming scheduled occurrences, live statistics (total / active / paused / due in 24h from one aggregate query), quick actions, recent activity. |
| `reminders` | Reminder list with instant search (title + description), five sort orders, six AND-combined filters, enable/disable, duplicate, delete with snackbar undo. Rows open the editor; on expanded windows the screen becomes two-pane (list left, editor right). |
| `reminders/editor` | The full editor (see below). |
| `settings` | DataStore-backed preferences: theme, dynamic color, notification & biometric-lock preferences, live permission statuses, privacy statement, links to Backup/About. |
| `about` | Version info, developer, license, privacy statement, open-source libraries. |
| `backup` | Complete UI for ZIP/CSV export & import; engines arrive in the backup phase, so actions are visibly disabled. |

### The reminder editor (`features/reminders/editor`)

One editor session = one `ReminderEditorViewModel`, created by **Hilt
assisted injection** with the reminder id (`NEW_ID` creates). This makes the
session host-agnostic: `ReminderEditorScreen` (full-screen route, phones) and
`ReminderEditorPane` (embedded pane, tablets) wrap the same body and the same
`EditorSession` choreography (finished-event → close, dirty → discard dialog,
delete → confirmation).

- **Draft model** — the domain `Reminder` itself is the draft; every field
  change flows through `viewModel.update { it.copy(…) }`, which revalidates
  and recomputes dirtiness against the loaded baseline. No parallel form
  model to keep in sync.
- **Validation** — `EditorValidation` holds nullable message ids per field
  group (title, schedule, snooze, active days, date range); Save is enabled
  only when everything is valid, and messages render inline next to the
  offending control.
- **Recurrence editing** — `RecurrenceKind` maps 1:1 onto the sealed
  `Recurrence`; switching kinds carries compatible parameters over (time of
  day between daily/weekly/monthly, interval length between interval kinds).
- **Live preview** — `ReminderPreview` renders a miniature alert that
  animates with display mode, floating position/size, priority accent,
  snooze chips, and the auto-dismiss caption.
- **Settings search** — sections declare their searchable labels; a query
  filters the section list in place.

### `di` — composition root

`DatabaseModule`, `DataStoreModule`, and `BindingsModule` (interface → implementation bindings). Hilt owns all object graphs; nothing is constructed manually.

## Theming

- Brand primary `#E4572E` with warm-tinted neutral surfaces; separate
  hand-tuned light and dark schemes in `core/ui/theme/Color.kt`.
- Theme mode (Light / Dark / Follow system) and Material You dynamic color
  are user settings, applied instantly: `MainViewModel` exposes them as a
  `StateFlow`, `MainActivity` recomposes the theme and re-syncs system-bar
  icon contrast on change.

## Navigation

Three top-level tabs (Home, Reminders, Settings) with per-tab back-stack
preservation, plus pushed detail screens (Backup, About) that hide the bottom
bar. Transitions are subtle fade + short horizontal slide. Adding a screen:

1. Add a `@Serializable` route object in `core/navigation/Routes.kt`.
2. Add a `composable<Route>` block in `AlertNotesNavHost`.
3. (Top-level only) add an entry to `TopLevelDestination`.

## Adaptive layouts

`rememberWindowWidthClass()` buckets the window into Compact / Medium /
Expanded at the Material breakpoints (600dp, 840dp). The shell swaps bottom
bar ↔ navigation rail; the dashboard uses an adaptive grid (two columns on
tablets); list screens cap content width at 640dp so nothing stretches.

## Accessibility

- All text uses `sp` and scales with system font size.
- Toggle and radio rows use `toggleable`/`selectable` with roles so TalkBack
  announces them as single controls; icon-only actions carry content
  descriptions; decorative icons are explicitly `null`.
- Touch targets are ≥ 48dp (rows are 56dp).

## Future expansion (reserved seams)

- **Popup engine** — consumes `ReminderQueueRepository.observePending()`;
  every display/behaviour field it needs is already on `Reminder`.
- **Reminder editor** — a screen over `ReminderSchedulingCoordinator
  .saveAndSchedule`; the model, recurrence types, and persistence are done.
- **Drawing, widgets** — top-level `drawing`/`widgets` packages, consuming the
  same repositories (`ReminderType.DRAWING` is already modelled).
- **Biometric app lock** — the preference, capability check, and
  per-reminder `requiresBiometric` flag exist; the prompt flow plugs into
  `biometric/`.
- **Backup engines** — implement behind the existing Backup UI.
- **Archive UI** — `is_archived` column, repository API, and query filters
  are in place; only the surface is missing.
