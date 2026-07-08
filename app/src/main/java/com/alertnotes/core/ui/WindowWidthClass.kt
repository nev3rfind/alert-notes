package com.alertnotes.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Coarse window-width buckets following the Material 3 breakpoints
 * (600dp and 840dp). Screens use this to pick single- or multi-pane layouts
 * instead of stretching phone UI across tablets.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Current window width class, recomputed automatically on resize,
 * rotation, and window-mode changes.
 */
@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { containerWidthPx.toDp() }
    return when {
        widthDp < 600.dp -> WindowWidthClass.Compact
        widthDp < 840.dp -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
}
