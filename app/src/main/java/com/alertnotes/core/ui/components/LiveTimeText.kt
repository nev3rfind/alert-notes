package com.alertnotes.core.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.core.extensions.toClockString
import com.alertnotes.core.extensions.toCountdownString
import com.alertnotes.core.util.SecondTicker
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * App-wide heartbeat, provided once by MainActivity. Null (e.g. previews)
 * degrades every live text to a static snapshot instead of crashing.
 */
val LocalSecondTicker = staticCompositionLocalOf<SecondTicker?> { null }

/**
 * The current instant, refreshed each second while this composition is
 * visible and the lifecycle is at least STARTED. Read it in a **leaf**
 * composable: only that leaf recomposes on tick.
 */
@Composable
fun rememberNow(): Instant {
    val ticker = LocalSecondTicker.current ?: return Instant.now()
    val now by ticker.now.collectAsStateWithLifecycle(initialValue = remember { Instant.now() })
    // The shared flow replays its last tick to new collectors; returning to
    // the foreground after hours would render that stale instant for one
    // frame (e.g. "overdue by 3h" flashes). Clamp anything old to the clock.
    return if (Duration.between(now, Instant.now()).seconds >= STALE_TICK_SECONDS) {
        Instant.now()
    } else {
        now
    }
}

private const val STALE_TICK_SECONDS = 2L

/**
 * Live "time remaining until [target]" label. Uses tabular figures so digits
 * change without the text shifting sideways.
 *
 * @param fallback shown once the target has passed (e.g. "Due now").
 * @param clockFormat true for fixed `HH:MM:SS`, false for compact `19m 58s`.
 */
@Composable
fun CountdownText(
    target: Instant,
    fallback: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    clockFormat: Boolean = false,
) {
    val now = rememberNow()
    val remaining = Duration.between(now, target)
    val text = when {
        remaining.isNegative || remaining.isZero -> fallback
        clockFormat -> remaining.toClockString()
        else -> remaining.toCountdownString()
    }
    Text(
        text = text,
        modifier = modifier,
        style = style.withTabularFigures(),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/** Live "time since [since]" label in `HH:MM:SS`, counting up. */
@Composable
fun ElapsedText(
    since: Instant,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val now = rememberNow()
    Text(
        text = Duration.between(since, now).toClockString(),
        modifier = modifier,
        style = style.withTabularFigures(),
        color = color,
        maxLines = 1,
    )
}

/** Live wall clock (localized, with seconds). */
@Composable
fun LiveClockText(
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val now = rememberNow()
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM) }
    Text(
        text = now.atZone(ZoneId.systemDefault()).toLocalTime().format(formatter),
        modifier = modifier,
        style = style.withTabularFigures(),
        color = color,
        maxLines = 1,
    )
}

/** Tabular (monospaced-width) digits: ticking numbers never jitter. */
private fun TextStyle.withTabularFigures(): TextStyle =
    copy(fontFeatureSettings = "tnum")
