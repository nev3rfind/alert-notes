package com.alertnotes.features.profile

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.alertnotes.R
import com.alertnotes.domain.model.ProfileTheme

/**
 * Visual identity of each [ProfileTheme]. The banner is a brush (gradients
 * are first-class); the accent is a single strong colour for icon tints,
 * monogram backgrounds, and stat numbers. Content on top of any banner is
 * white — every palette entry is deep enough to carry it, matching the hero
 * card's printed-membership-card look in both light and dark theme.
 */
internal data class ProfileThemeColors(
    val banner: Brush,
    val accent: Color,
)

internal fun ProfileTheme.colors(): ProfileThemeColors = when (this) {
    ProfileTheme.PRIMARY_ORANGE -> solid(Color(0xFFE4572E))
    ProfileTheme.FOREST_GREEN -> solid(Color(0xFF2F855A))
    ProfileTheme.OCEAN_BLUE -> solid(Color(0xFF2563EB))
    ProfileTheme.MIDNIGHT_PURPLE -> solid(Color(0xFF5E244E))
    ProfileTheme.CRIMSON -> solid(Color(0xFFAA1C41))
    ProfileTheme.WARM_SAND -> ProfileThemeColors(
        // Sand is too light to carry white text on its own; deepen the tail.
        banner = Brush.linearGradient(listOf(Color(0xFFE8B45A), Color(0xFFC98A2D))),
        accent = Color(0xFFC98A2D),
    )
    ProfileTheme.SLATE_GREY -> solid(Color(0xFF4B5563))
    ProfileTheme.SUNSET_GRADIENT -> gradient(Color(0xFFE4572E), Color(0xFFAA1C41))
    ProfileTheme.AURORA_GRADIENT -> gradient(Color(0xFF5E244E), Color(0xFF2563EB))
    ProfileTheme.EMERALD_GRADIENT -> gradient(Color(0xFF2F855A), Color(0xFF14B8A6))
}

@StringRes
internal fun ProfileTheme.labelRes(): Int = when (this) {
    ProfileTheme.PRIMARY_ORANGE -> R.string.profile_theme_primary_orange
    ProfileTheme.FOREST_GREEN -> R.string.profile_theme_forest_green
    ProfileTheme.OCEAN_BLUE -> R.string.profile_theme_ocean_blue
    ProfileTheme.MIDNIGHT_PURPLE -> R.string.profile_theme_midnight_purple
    ProfileTheme.CRIMSON -> R.string.profile_theme_crimson
    ProfileTheme.WARM_SAND -> R.string.profile_theme_warm_sand
    ProfileTheme.SLATE_GREY -> R.string.profile_theme_slate_grey
    ProfileTheme.SUNSET_GRADIENT -> R.string.profile_theme_sunset
    ProfileTheme.AURORA_GRADIENT -> R.string.profile_theme_aurora
    ProfileTheme.EMERALD_GRADIENT -> R.string.profile_theme_emerald
}

private fun solid(color: Color): ProfileThemeColors = ProfileThemeColors(
    // A barely-darkened tail keeps solid banners from looking flat without
    // reading as a gradient theme.
    banner = Brush.linearGradient(listOf(color, color.darken(0.15f))),
    accent = color,
)

private fun gradient(start: Color, end: Color): ProfileThemeColors = ProfileThemeColors(
    banner = Brush.linearGradient(listOf(start, end)),
    accent = start,
)

private fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha,
)
