package com.alertnotes.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alertnotes.R
import com.alertnotes.core.ui.components.OptionPickerDialog
import com.alertnotes.domain.model.ThemeMode

/**
 * Light / Dark / Follow system chooser. Selection applies instantly — the
 * dialog only offers a close action.
 */
@Composable
fun ThemePickerDialog(
    currentThemeMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    OptionPickerDialog(
        title = stringResource(R.string.settings_theme_dialog_title),
        options = ThemeMode.entries.toList(),
        selected = currentThemeMode,
        optionLabel = { stringResource(it.labelRes()) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
