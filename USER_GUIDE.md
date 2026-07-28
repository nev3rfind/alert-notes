# Alert Notes — User Guide

Alert Notes works two ways, and you choose on first launch.

**Offline mode** is the whole reminder app with nothing attached: no account,
no analytics, no cloud, and no network connection of any kind. Nothing you
create ever leaves your device.

**Online mode** adds an account and the social features - friends, a family
circle, sharing a reminder with someone, chat, and optional cloud backup. Your
reminders still live on your device and still fire without a connection; the
account only adds what you choose to share. You can change mode later in
Settings, and delete your account from More at any time.

Either way there are no advertisements and no tracking.

## First launch

On first run you'll see a short welcome and a guided permission page. Every
permission is optional — the app works without any of them, just less
visibly. You can skip the whole flow and grant anything later in
**Settings → Permissions**. See [PERMISSIONS.md](PERMISSIONS.md) for what
each one does.

## Creating a reminder

Tap the **+** button on the Home screen (or long-press a calendar day).

**Reminder types**

- **Text** — a title and optional notes.
- **Drawing** — a hand-drawn note shown inside the alert. Open the drawing
  studio to sketch with pen, highlighter, and eraser, pick from twelve ink
  colors, choose a paper style (white, grids, dots, notebook, dark), and set
  how large the drawing appears (40–100% of the alert) and where (top,
  centered, bottom). A stylus draws pressure-sensitive strokes.
- **Checklist** — a title plus as many items as you need. When the reminder
  fires you check items off one by one; the alert can only be acknowledged
  once every item is complete.

**Scheduling** comes in three modes:

- **At** — a specific date and time.
- **In** — a duration from now ("in 10 minutes"); the countdown starts the
  moment you save.
- **Recurring** — every X minutes/hours, daily, weekly, monthly, or a custom
  interval, optionally restricted to active days, active hours, and a date
  range.

**Alert appearance**: full-screen or floating card (with position and size),
one of eight animated themes, priority (Normal / High / Critical), snooze
presets, auto-dismiss, and a dismiss countdown lock.

**Acknowledgement**: choose how the alert is dismissed — a button, a tap
anywhere, a directional swipe, painting a checkmark, or signing the
signature pad. Signature acknowledgements are archived in the Activity
screen. Protected reminders can additionally require fingerprint / face /
device PIN.

## When a reminder fires

Alerts appear over whatever you're doing — the launcher, other apps, games,
video — and on the lock screen (waking the display if the reminder says so).
The app does not need to be open.

- **Full-screen alerts** always keep their controls visible. Long content
  scrolls; the dismiss / snooze / acknowledge area is reserved at the bottom
  and can never be covered.
- **Floating cards** appear at the position you chose and let you keep using
  the app underneath.
- If nothing can draw over the current screen, a high-priority notification
  with a full-screen intent takes over instead.
- Snoozing postpones the reminder by the chosen duration; nothing is ever
  lost — pending alerts queue and appear one at a time, highest priority
  first.

## Calendar

The Calendar tab shows every upcoming occurrence — including recurring
projections — in Month, Week, Day, and Timeline views. Tap a day to inspect
it, long-press to create a reminder pre-filled for that date.

## Activity

**Settings → Activity history** lists everything that happened: when each
reminder fired, when and how it was resolved (button, tap, swipe, checkmark,
signature, checklist completion, snooze, auto-dismiss), archived signatures,
search, method filters, and a CSV export button in the top bar.

Reminders record history only while their **History** toggle is on.

## Global controls

**Settings → Reminder controls**:

- **Pause all reminders** — for 1/8/24 hours, until a picked date & time, or
  indefinitely. Nothing fires while paused; scheduling resumes automatically.
- **Clear all reminders** — deletes everything after a confirmation *and*
  biometric/PIN verification.

## Security

- **Biometric lock** (Settings → Security) locks the whole app behind
  fingerprint / face / device credential. It re-locks whenever the app
  leaves the foreground. Reminder alerts still appear while locked — the
  lock protects your notes, not your alarms.
- Individual reminders can require authentication before they can be
  dismissed.

## Backup

**Settings → Backup & restore** exports everything (settings, reminders with
drawings, checklists, themes, schedules, and history including signatures)
as a ZIP you place anywhere with the system file picker, and imports it
back. A simpler CSV export/import carries reminder lists.

## Exiting the app

**Settings → Exit application** closes the app screen only. Scheduled
reminders live in Android's alarm system and keep firing; your data is
untouched. Simply pressing Home or swiping the app away works the same way —
reminders never depend on the app being open.
