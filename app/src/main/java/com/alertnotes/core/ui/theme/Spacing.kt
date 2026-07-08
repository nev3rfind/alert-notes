package com.alertnotes.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single source of truth for whitespace. Using the scale instead of ad-hoc
 * dp values keeps rhythm consistent across every screen.
 *
 * Access it through [MaterialTheme.spacing].
 */
@Immutable
data class Spacing(
    /** 4dp – hairline gaps, icon-to-text nudges. */
    val extraSmall: Dp = 4.dp,
    /** 8dp – gaps between related elements. */
    val small: Dp = 8.dp,
    /** 12dp – gaps between rows inside a card. */
    val medium: Dp = 12.dp,
    /** 16dp – screen edge padding, gaps between cards. */
    val large: Dp = 16.dp,
    /** 24dp – gaps between sections. */
    val extraLarge: Dp = 24.dp,
    /** 32dp – top-of-screen breathing room, empty-state padding. */
    val huge: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
