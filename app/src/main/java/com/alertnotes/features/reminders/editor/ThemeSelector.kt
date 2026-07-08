package com.alertnotes.features.reminders.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alertnotes.domain.model.ReminderTheme
import com.alertnotes.features.alerts.spec
import com.alertnotes.features.alerts.staticBrush

private val ChipWidth = 56.dp
private val ChipHeight = 38.dp

/**
 * The eight reminder themes as oval color chips. The selected chip enlarges
 * slightly and shows a subtle checkmark; selection animates with a soft
 * spring. Each chip is announced with its theme name.
 */
@Composable
fun ThemeSelector(
    selected: ReminderTheme,
    onSelect: (ReminderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(ReminderTheme.entries) { theme ->
            ThemeChip(
                theme = theme,
                selected = theme == selected,
                onSelect = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun ThemeChip(
    theme: ReminderTheme,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val spec = theme.spec
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "themeChipScale",
    )
    val label = stringResource(spec.labelRes)
    val ovalShape = RoundedCornerShape(50)
    // The 56x38 chip stays visually compact; the interactive area around it
    // is expanded to the 48dp accessibility minimum.
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = ChipWidth, height = ChipHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(brush = theme.staticBrush(), shape = ovalShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = ovalShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = spec.contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
