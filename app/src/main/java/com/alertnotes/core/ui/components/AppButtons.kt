package com.alertnotes.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alertnotes.core.ui.theme.AlertNotesTheme

private val ButtonHeight = 48.dp
private val ButtonIconSize = 20.dp

/** High-emphasis action. Use at most one per screen region. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonHeight)
            .pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

/** Medium-emphasis action rendered as a soft tonal fill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonHeight)
            .pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

/** Low-emphasis action with a hairline border. */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonHeight)
            .pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

/** Destructive action such as delete or reset. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonHeight)
            .pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        interactionSource = interactionSource,
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

@Preview(showBackground = true)
@Composable
private fun ButtonsPreview() {
    AlertNotesTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(text = "Primary", onClick = {})
            SecondaryButton(text = "Secondary", onClick = {})
            AppOutlinedButton(text = "Outlined", onClick = {})
            DangerButton(text = "Danger", onClick = {})
        }
    }
}
