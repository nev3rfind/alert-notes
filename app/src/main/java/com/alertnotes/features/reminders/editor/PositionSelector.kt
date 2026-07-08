package com.alertnotes.features.reminders.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alertnotes.domain.model.FloatingCardPosition

private val PositionRows: List<List<FloatingCardPosition>> = listOf(
    listOf(
        FloatingCardPosition.TOP_LEFT,
        FloatingCardPosition.TOP_CENTER,
        FloatingCardPosition.TOP_RIGHT,
    ),
    listOf(FloatingCardPosition.CENTER),
    listOf(
        FloatingCardPosition.BOTTOM_LEFT,
        FloatingCardPosition.BOTTOM_CENTER,
        FloatingCardPosition.BOTTOM_RIGHT,
    ),
)

/**
 * Miniature screen with a dot per anchor position — pick where the floating
 * card appears by tapping the matching spot. Each dot is a 40dp radio target
 * announced with its position name.
 */
@Composable
fun PositionSelector(
    selected: FloatingCardPosition,
    onSelect: (FloatingCardPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            )
            .selectableGroup(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        PositionRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (row.size) {
                    1 -> Arrangement.Center
                    else -> Arrangement.SpaceBetween
                },
            ) {
                row.forEach { position ->
                    PositionDot(
                        position = position,
                        selected = position == selected,
                        onSelect = { onSelect(position) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionDot(
    position: FloatingCardPosition,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val dotColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "positionDot",
    )
    val description = stringResource(position.labelRes())
    Box(
        modifier = Modifier
            .size(40.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 16.dp else 10.dp)
                .background(color = dotColor, shape = CircleShape),
        )
    }
}
