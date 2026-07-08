package com.alertnotes.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Alert Notes brand palette.
 *
 * The identity color is a warm signal orange ([BrandOrange]). Neutrals are slightly
 * warm-tinted so surfaces feel soft and paper-like rather than clinical.
 */
val BrandOrange = Color(0xFFE4572E)

// Light palette
private val OrangeLight = BrandOrange
private val OnOrangeLight = Color(0xFFFFFFFF)
private val OrangeContainerLight = Color(0xFFFFDBCF)
private val OnOrangeContainerLight = Color(0xFF3B0900)

private val SecondaryLight = Color(0xFF77574C)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFF4DED6)
private val OnSecondaryContainerLight = Color(0xFF2C160E)

private val TertiaryLight = Color(0xFF6B5D2F)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFF5E1A7)
private val OnTertiaryContainerLight = Color(0xFF231B00)

private val BackgroundLight = Color(0xFFF6F3F1)
private val OnBackgroundLight = Color(0xFF201A17)
private val SurfaceLight = Color(0xFFFFFFFF)
private val OnSurfaceLight = Color(0xFF201A17)
private val SurfaceVariantLight = Color(0xFFF0E4DE)
private val OnSurfaceVariantLight = Color(0xFF53433D)
private val OutlineLight = Color(0xFF85736C)
private val OutlineVariantLight = Color(0xFFD8C2BA)

// Dark palette
private val OrangeDark = Color(0xFFFF8E68)
private val OnOrangeDark = Color(0xFF561F03)
private val OrangeContainerDark = Color(0xFF893214)
private val OnOrangeContainerDark = Color(0xFFFFDBCF)

private val SecondaryDark = Color(0xFFE7BEB0)
private val OnSecondaryDark = Color(0xFF442A21)
private val SecondaryContainerDark = Color(0xFF5D4036)
private val OnSecondaryContainerDark = Color(0xFFFFDBCF)

private val TertiaryDark = Color(0xFFD8C58D)
private val OnTertiaryDark = Color(0xFF3A2F05)
private val TertiaryContainerDark = Color(0xFF52451A)
private val OnTertiaryContainerDark = Color(0xFFF5E1A7)

private val BackgroundDark = Color(0xFF16110F)
private val OnBackgroundDark = Color(0xFFEDE0DA)
private val SurfaceDark = Color(0xFF1D1815)
private val OnSurfaceDark = Color(0xFFEDE0DA)
private val SurfaceVariantDark = Color(0xFF3D322D)
private val OnSurfaceVariantDark = Color(0xFFD8C2BA)
private val OutlineDark = Color(0xFFA08D85)
private val OutlineVariantDark = Color(0xFF53433D)

// Shared error palette (Material baseline)
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

val AlertNotesLightColorScheme = lightColorScheme(
    primary = OrangeLight,
    onPrimary = OnOrangeLight,
    primaryContainer = OrangeContainerLight,
    onPrimaryContainer = OnOrangeContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF9F7),
    surfaceContainer = Color(0xFFF9F2EF),
    surfaceContainerHigh = Color(0xFFF3ECE9),
    surfaceContainerHighest = Color(0xFFEDE6E3),
)

val AlertNotesDarkColorScheme = darkColorScheme(
    primary = OrangeDark,
    onPrimary = OnOrangeDark,
    primaryContainer = OrangeContainerDark,
    onPrimaryContainer = OnOrangeContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = Color(0xFF110C0A),
    surfaceContainerLow = Color(0xFF201A17),
    surfaceContainer = Color(0xFF241E1B),
    surfaceContainerHigh = Color(0xFF2F2825),
    surfaceContainerHighest = Color(0xFF3A332F),
)
