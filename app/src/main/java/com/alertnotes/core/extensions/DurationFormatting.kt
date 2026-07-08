package com.alertnotes.core.extensions

import java.time.Duration
import java.util.Locale

/**
 * Compact live-countdown label: `3d 4h`, `1h 04m 12s`, `19m 58s`, `42s`.
 * Negative durations clamp to zero.
 */
fun Duration.toCountdownString(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val secs = totalSeconds % 60
    return when {
        days > 0 -> String.format(Locale.getDefault(), "%dd %dh", days, hours)
        hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, secs)
        minutes > 0 -> String.format(Locale.getDefault(), "%dm %02ds", minutes, secs)
        else -> String.format(Locale.getDefault(), "%ds", secs)
    }
}

/** Fixed-width `HH:MM:SS` label, e.g. `00:00:52`. Negative clamps to zero. */
fun Duration.toClockString(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    return String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        totalSeconds / 3_600,
        (totalSeconds % 3_600) / 60,
        totalSeconds % 60,
    )
}
