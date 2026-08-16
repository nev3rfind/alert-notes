# Alert Notes — Known Limitations

An honest list of what version 1.0.0 does not (or cannot) do, and why.

## Platform constraints

- **Vendor battery managers** (some Xiaomi/Huawei/Oppo/vivo builds) can
  force-stop apps in ways that also cancel `setAlarmClock` alarms. Alarms are
  rebuilt on the next app or boot broadcast, but a reminder due while the app
  was force-stopped may be missed. The battery-optimization exemption in the
  Permission Center is the best available mitigation.
- **Full-screen intents on Android 14+** are a user-revocable special
  permission. If revoked and the overlay permission is also denied, a locked
  device shows a (still high-priority) notification instead of a full-screen
  takeover.
- **Overlay windows never render above the keyguard** — this is an Android
  security rule. Lock-screen alerts always use the notification +
  full-screen-intent path (`AlertActivity`), which is why both permissions
  exist side by side.
- **The keyguard is never dismissed by the app.** Interacting with an alert
  on a secured lock screen leaves the device locked afterwards, by design.
- **Exact-to-the-second delivery** is subject to `AlarmManager` semantics:
  `setAlarmClock` is minute-accurate in practice and Doze-exempt, but the OS
  provides no hard real-time guarantee.

## App-level trade-offs

- **Snoozed + recurring interplay**: snoozing postpones the reminder's next
  alarm to the snooze time — a natural recurring occurrence that would have
  fallen before the snooze fires is skipped, not queued alongside it (one
  reminder, one pending alarm). Snoozes survive reboots, app updates, and
  time changes. If the snoozed alarm lands inside a global pause window it
  is swallowed by the pause, like any other occurrence.
- **Missed occurrences are delivered late, not silently dropped**: a
  reminder that came due while the device was off (or the app force-stopped)
  is presented on the next boot or app launch, marked with its original due
  time. It does not re-ring at the original moment — that moment has passed.
- **Crash-loop quarantine** (fail-safe): an alert entry whose rendering kills
  the process three times in a row is consumed automatically. The entry is
  logged and the reminder itself (and its schedule) is untouched. Repeatedly
  swipe-killing the app from Recents while the same alert is on screen
  counts toward the same threshold — a deliberate trade-off to guarantee the
  app can never soft-lock.
- **Ink color selection resets** between drawing-studio sessions (strokes
  keep their colors; the palette selection itself isn't persisted).
- **Pressure sensitivity** applies per stroke (the committed stroke width is
  scaled by the stroke's average pressure) rather than varying width within
  a single stroke — a storage-format decision that keeps drawings
  losslessly scalable vectors.
- **CSV** carries the essential reminder fields only (title, notes,
  priority, theme, enabled, recurrence type); schedules richer than CSV can
  express are re-picked in the editor after import. ZIP backups carry
  everything.
- **History is capped** at the most recent 500 entries shown in the Activity
  screen (the export includes everything stored).
- **Checklist progress is per-presentation**: rotating a device mid-checklist
  preserves checked items, but if the process is killed mid-alert the next
  presentation starts unchecked, by design (the reminder was never
  acknowledged).
- **App lock** relies on system biometrics/credentials; on devices with no
  secure lock configured, the toggle is disabled rather than offering a
  weaker custom PIN (and a lock setting restored onto such a device unlocks
  rather than bricking the app). While the lock is enabled the window is
  marked secure — screenshots and the Recents preview are blocked.
- **Editor drafts** live in memory while editing: if Android kills the app
  process while the editor is backgrounded mid-edit, unsaved changes are
  lost. Open confirmation dialogs and the drawing canvas do survive
  rotation and process death.
- **Hardware-keyboard focus** behind the app lock / onboarding overlays is
  not fully trapped (touch and TalkBack are); a paired keyboard user could
  scroll content behind the lock scrim, though not read it — the scrim is
  opaque.

## Online Edition trade-offs

- **Three privacy controls are enforced client-side, not by security
  rules.** `onlineStatus`, `lastSeen` and `analyticsVisibility` describe
  fields inside a profile document the viewer is already permitted to
  read, so they are applied when the profile is mapped rather than by
  Firestore. Choosing `NOBODY` additionally stops the value being written
  at all, which is the only tier that is enforced end-to-end. A modified
  client could therefore read a narrower audience's online status or
  counters from a profile it may otherwise open. The five controls that
  gate a *write* - friend requests, family invitations, reminder sharing,
  message requests - and profile reads themselves are all enforced by
  `firestore.rules` and covered by the rules test suite.
- **Firebase App Check is not configured.** The backend cannot currently
  distinguish the shipped app from any client holding the (necessarily
  public) API key, so `firestore.rules` is the only barrier. The rules are
  written to assume exactly that and deny by default, but App Check should
  be added before a production launch.
- Evidence uploads are best-effort while offline: the acknowledgement is
  recorded locally and the image or signature uploads on reconnect.

## Not implemented (deliberate scope decisions)

- No per-reminder custom sounds (the alarm channel uses the system alarm
  sound); no TTS announcements.
- No wear-OS companion, no quick-settings tile.
- Localization ships English and Lithuanian; all strings are externalized
  and further languages need only a new `values-xx/strings.xml`.
