package com.alertnotes.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alertnotes.core.ui.theme.spacing

/**
 * The app's one skeleton vocabulary: content-shaped placeholders with a
 * subtle synchronized pulse, replacing spinners wherever the final layout
 * is known. Deliberately a shared alpha pulse rather than a sweeping
 * shimmer — cheaper to draw, calmer to look at, and consistent across
 * every screen. Composables using these should switch to real content the
 * moment data arrives (the nullable-StateFlow loading pattern).
 */
@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

/** A pulsing placeholder line; the building block of every skeleton. */
@Composable
fun SkeletonLine(
    width: Dp,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = skeletonAlpha() * 0.25f),
                MaterialTheme.shapes.extraLarge,
            ),
    )
}

/** A pulsing circular placeholder — avatars and icon badges. */
@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = skeletonAlpha() * 0.25f),
                CircleShape,
            ),
    )
}

/**
 * The standard list-row skeleton: leading avatar circle, a title line and
 * a shorter supporting line — matches PersonRow/AppListItem/notification
 * card proportions so loading never shifts the layout.
 */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(size = 40.dp)
        Column(modifier = Modifier.padding(start = MaterialTheme.spacing.medium)) {
            SkeletonLine(width = 160.dp, height = 14.dp)
            SkeletonLine(
                width = 96.dp,
                height = 10.dp,
                modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
            )
        }
    }
}

/** A short stack of row skeletons — the default list loading body. */
@Composable
fun SkeletonList(rows: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) {
            SkeletonListItem()
        }
    }
}

private const val SKELETON_PULSE_MILLIS = 800
