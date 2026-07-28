package com.alertnotes.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable

/**
 * One-shot staggered entrance: content fades in while sliding up a sixth of
 * its height. Give successive blocks increasing [delayMillis] so a page
 * builds top-to-bottom instead of popping in at once.
 */
@Composable
fun StaggeredEntrance(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 350, delayMillis = delayMillis),
                initialOffsetY = { it / 6 },
            ),
    ) {
        content()
    }
}
