package com.alertnotes.features.alerts

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.alertnotes.R
import com.alertnotes.domain.model.ReminderTheme

/**
 * Rendering vocabulary for the eight reminder themes. One place decides the
 * palette, content contrast, and motion: alerts, previews, list accents, and
 * the editor's chips all read from here.
 */
data class ReminderThemeSpec(
    /** Base and (for gradients) end colors. Single entry for solids. */
    val colors: List<Color>,
    /** Text/icon color that stays readable on the theme surface. */
    val contentColor: Color,
    @param:StringRes val labelRes: Int,
) {
    val isGradient: Boolean get() = colors.size > 1
    val accent: Color get() = colors.first()
}

private val DarkContent = Color(0xFF3A2A14)
private val LightContent = Color(0xFFFFFFFF)

val ReminderTheme.spec: ReminderThemeSpec
    get() = when (this) {
        ReminderTheme.PRIMARY_ORANGE -> ReminderThemeSpec(
            colors = listOf(Color(0xFFE4572E)),
            contentColor = LightContent,
            labelRes = R.string.theme_primary_orange,
        )

        ReminderTheme.FOREST_GREEN -> ReminderThemeSpec(
            colors = listOf(Color(0xFF3F6B52)),
            contentColor = LightContent,
            labelRes = R.string.theme_forest_green,
        )

        ReminderTheme.GOLDEN_YELLOW -> ReminderThemeSpec(
            colors = listOf(Color(0xFFE8A83E)),
            contentColor = DarkContent,
            labelRes = R.string.theme_golden_yellow,
        )

        ReminderTheme.MIDNIGHT_PURPLE -> ReminderThemeSpec(
            colors = listOf(Color(0xFF5E244E)),
            contentColor = LightContent,
            labelRes = R.string.theme_midnight_purple,
        )

        ReminderTheme.BERRY_RED -> ReminderThemeSpec(
            colors = listOf(Color(0xFFAA1C41)),
            contentColor = LightContent,
            labelRes = R.string.theme_berry_red,
        )

        ReminderTheme.SOFT_CREAM -> ReminderThemeSpec(
            colors = listOf(Color(0xFFFFE8B4)),
            contentColor = DarkContent,
            labelRes = R.string.theme_soft_cream,
        )

        ReminderTheme.ORANGE_GRADIENT -> ReminderThemeSpec(
            colors = listOf(Color(0xFFE4572E), Color(0xFFF2953F)),
            contentColor = LightContent,
            labelRes = R.string.theme_orange_gradient,
        )

        ReminderTheme.PURPLE_GRADIENT -> ReminderThemeSpec(
            colors = listOf(Color(0xFFAA1C41), Color(0xFF5E244E)),
            contentColor = LightContent,
            labelRes = R.string.theme_purple_gradient,
        )
    }

/** Static brush for list accents, chips, and other non-animated surfaces. */
fun ReminderTheme.staticBrush(): Brush {
    val spec = spec
    return if (spec.isGradient) {
        Brush.verticalGradient(spec.colors)
    } else {
        SolidColor(spec.accent)
    }
}

/**
 * The living theme surface. Gradients drift very slowly by shifting their
 * color phase; solids "breathe" with a barely-visible brightness swell —
 * both far below attention-grabbing thresholds.
 *
 * The animated value is read inside the DRAW phase (`drawBehind`), never in
 * composition: reading an infinite-transition state during composition
 * recomposed the entire alert (or preview) tree on every animation frame.
 * With the deferred read, each frame only re-executes this draw lambda.
 */
@Composable
fun Modifier.animatedThemeBackground(theme: ReminderTheme): Modifier {
    val spec = theme.spec
    val transition = rememberInfiniteTransition(label = "themeBrush")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "themePhase",
    )
    return this.drawBehind {
        if (spec.isGradient) {
            val start = spec.colors[0]
            val end = spec.colors[1]
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        lerp(start, end, phase * 0.35f),
                        lerp(end, start, phase * 0.35f),
                    ),
                ),
            )
        } else {
            val base = spec.accent
            drawRect(color = lerp(base, lerp(base, Color.White, 0.06f), phase))
        }
    }
}
