package com.alertnotes.features.alerts

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.domain.model.FloatingCardSize
import com.alertnotes.domain.model.ReminderPriority
import java.time.Duration

/**
 * Visual vocabulary shared by real alerts and the editor's live preview, so
 * the preview is the real thing at a smaller scale — never a lookalike.
 */
object AlertDefaults {
    /** Base card width per size; the renderer caps it to the window. */
    fun floatingCardWidth(size: FloatingCardSize): Dp = when (size) {
        FloatingCardSize.SMALL -> 264.dp
        FloatingCardSize.MEDIUM -> 328.dp
        FloatingCardSize.LARGE -> 392.dp
    }

    /** Distance a swipe must travel before it dismisses. */
    val SwipeDismissThreshold: Dp = 120.dp
}

/**
 * Alert accent: muted for low, brand orange by default, error red for
 * critical — the priority's visual identity everywhere it appears.
 */
@Composable
fun alertAccentColor(priority: ReminderPriority): Color {
    val accent by animateColorAsState(
        targetValue = when (priority) {
            ReminderPriority.CRITICAL -> MaterialTheme.colorScheme.error
            ReminderPriority.LOW -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.primary
        },
        label = "alertAccent",
    )
    return accent
}

/**
 * Critical reminders demand attention: a pulsing red border and a subtle
 * full-surface flash. Both effects respect the system accessibility
 * "remove animations" setting (animator scale 0) — with animations off,
 * critical alerts keep a strong static red border and nothing pulses.
 */
@Composable
fun Modifier.criticalAttentionEffects(priority: ReminderPriority): Modifier {
    if (priority != ReminderPriority.CRITICAL) return this
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val error = MaterialTheme.colorScheme.error
    if (!animationsEnabled) {
        return this.border(CriticalBorderWidth, error, MaterialTheme.shapes.extraLarge)
    }
    val transition = rememberInfiniteTransition(label = "criticalAttention")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CRITICAL_PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "criticalPulse",
    )
    return this
        .border(CriticalBorderWidth, error.copy(alpha = pulse), MaterialTheme.shapes.extraLarge)
        .drawWithContent {
            drawContent()
            // Soft flashing wash — deliberately gentle, never strobing.
            drawRect(color = error.copy(alpha = CRITICAL_FLASH_MAX_ALPHA * pulse))
        }
}

private val CriticalBorderWidth = 4.dp
private const val CRITICAL_PULSE_MILLIS = 900
private const val CRITICAL_FLASH_MAX_ALPHA = 0.05f

/** Circular tinted icon badge used by every alert presentation. */
@Composable
fun AlertIconBadge(
    priority: ReminderPriority,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val accent = alertAccentColor(priority)
    Box(
        modifier = modifier
            .size(size)
            .background(color = accent.copy(alpha = 0.14f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Row of snooze duration chips. Pass a null [onSnooze] for the
 * non-interactive preview variant; [accentColor] adapts the chips to themed
 * alert surfaces (defaults to the app primary).
 */
@Composable
fun SnoozeChipRow(
    durations: List<Duration>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onSnooze: ((Duration) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        durations.take(if (compact) 3 else 4).forEach { duration ->
            SnoozeChip(
                duration = duration,
                compact = compact,
                accentColor = accentColor,
                onSnooze = onSnooze,
            )
        }
    }
}

@Composable
private fun SnoozeChip(
    duration: Duration,
    compact: Boolean,
    accentColor: Color,
    onSnooze: ((Duration) -> Unit)?,
) {
    val shape = RoundedCornerShape(if (compact) 6.dp else 10.dp)
    val label = if (compact) {
        duration.toCompactLabel()
    } else {
        pluralStringResource(
            R.plurals.duration_minutes,
            duration.toMinutes().toInt(),
            duration.toMinutes().toInt(),
        )
    }
    val snoozeLabel = stringResource(R.string.cd_snooze_for, label)
    var chipModifier = Modifier.clip(shape)
    if (onSnooze != null) {
        chipModifier = chipModifier
            // Visuals stay compact; the tappable/focusable area is expanded
            // to the 48dp accessibility minimum.
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = snoozeLabel) { onSnooze(duration) }
    }
    Box(
        modifier = chipModifier.background(
            color = accentColor.copy(alpha = 0.16f),
            shape = shape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = accentColor,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 14.dp,
                vertical = if (compact) 2.dp else 8.dp,
            ),
        )
    }
}

/** Compact duration label such as "5m" or "30s" for tight layouts. */
@Composable
fun Duration.toCompactLabel(): String = if (seconds < 60) {
    stringResource(R.string.duration_short_seconds, seconds)
} else {
    stringResource(R.string.duration_short_minutes, toMinutes())
}
