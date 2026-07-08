# Alert Notes — Permissions

Alert Notes requests only what a reminder app needs to make alerts reliable
and visible. Everything is optional: denying a permission degrades the
experience gracefully (usually to a notification) — it never breaks the app
or loses data. All statuses are visible in **Settings → Permissions**, each
row deep-links to the exact system screen, and statuses refresh when you
return.

| Capability | System permission | Why it helps | If denied |
| --- | --- | --- | --- |
| **Notifications** | `POST_NOTIFICATIONS` (runtime, Android 13+) | Shows alerts for due reminders while the app is in the background; the notification carries the full-screen intent that opens alerts over the lock screen. | Background reminders can't surface a notification; alerts still appear when the app is open, and over other apps if the overlay permission is granted. |
| **Exact alarms** | `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` (Android 12+) | Reminders fire at the exact minute. Alert Notes schedules with `AlarmManager.setAlarmClock`, which is Doze-exempt and shows the system alarm indicator. | The system may batch or delay triggers by minutes. |
| **Display over other apps** | `SYSTEM_ALERT_WINDOW` (special access) | Full-screen alerts and floating cards render on top of whatever app is open, without a notification round-trip. | Background alerts fall back to a high-priority notification with a full-screen intent. |
| **Full-screen alerts** | `USE_FULL_SCREEN_INTENT` (user-revocable on Android 14+) | Lets the alarm notification take over the screen from the lock screen or Do Not Disturb. | The notification appears but cannot take over the screen by itself. |
| **Ignore battery optimizations** | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (special access, recommended) | Stops aggressive vendor battery managers from freezing the app between alarms. | On most devices nothing changes (`setAlarmClock` is already exempt from Doze); on aggressive OEM builds reminders may be delayed. |
| **Biometric** | `USE_BIOMETRIC` (normal) | Fingerprint / face confirmation for protected reminder dismissal, the app lock, and Clear All verification. Falls back to the device PIN/pattern/password. | Rows read "Unavailable" on devices without secure hardware; protected actions proceed without authentication rather than locking you out. |
| **Boot / time change** | `RECEIVE_BOOT_COMPLETED`, time/timezone broadcasts (normal) | Re-schedules every reminder after a reboot, app update, or clock/timezone change. | Not user-deniable; granted at install. |
| **Vibrate** | `VIBRATE` (normal) | Haptics on alert arrival for reminders with vibration enabled. | Not user-deniable; granted at install. |

## What Alert Notes never requests

No internet, no location, no contacts, no camera, no microphone, no storage
beyond files you explicitly pick with the system file picker for backup
import/export. The app has no network permission at all — your data cannot
leave the device even in principle.
