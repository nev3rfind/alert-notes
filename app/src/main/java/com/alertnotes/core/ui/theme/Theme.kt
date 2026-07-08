package com.alertnotes.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Application theme.
 *
 * @param darkTheme whether to render the dark color scheme. Callers resolve this
 *   from the persisted [com.alertnotes.domain.model.ThemeMode] so the choice
 *   applies instantly when the user changes it in Settings.
 * @param dynamicColor when true (and the device runs Android 12+), Material You
 *   wallpaper-derived colors replace the brand palette. Off by default so the
 *   app ships with its own identity.
 */
@Composable
fun AlertNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> AlertNotesDarkColorScheme
        else -> AlertNotesLightColorScheme
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlertNotesTypography,
            shapes = AlertNotesShapes,
            content = content,
        )
    }
}
