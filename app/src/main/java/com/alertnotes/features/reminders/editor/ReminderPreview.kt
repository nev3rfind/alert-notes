package com.alertnotes.features.reminders.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.alertnotes.R
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.DisplayMode
import com.alertnotes.domain.model.DrawingPosition
import com.alertnotes.domain.model.FloatingCardSize
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.features.alerts.SnoozeChipRow
import com.alertnotes.features.alerts.animatedThemeBackground
import com.alertnotes.features.alerts.spec
import com.alertnotes.features.alerts.toCompactLabel
import com.alertnotes.features.drawing.DrawingView

/**
 * Live preview of how the alert will look — built from the same visual
 * vocabulary (theme brushes, snooze chips, drawing renderer) as the real
 * presentation, so what you configure is what fires. Theme, display mode,
 * position, size, priority, snooze, and drawing all update instantly.
 */
@Composable
fun ReminderPreview(
    reminder: Reminder,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 11f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(MaterialTheme.spacing.medium),
    ) {
        when (reminder.displayMode) {
            DisplayMode.FULL_SCREEN -> FullScreenPreview(reminder)
            DisplayMode.FLOATING_CARD -> FloatingCardPreview(reminder)
        }
    }
}

@Composable
private fun FullScreenPreview(reminder: Reminder) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.background)
            .padding(MaterialTheme.spacing.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .animatedThemeBackground(reminder.theme),
        ) {
            PreviewAlertBody(
                reminder = reminder,
                showDescription = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(MaterialTheme.spacing.medium),
            )
        }
    }
}

@Composable
private fun FloatingCardPreview(reminder: Reminder) {
    val widthFraction by animateFloatAsState(
        targetValue = when (reminder.floatingCardSize) {
            FloatingCardSize.SMALL -> 0.5f
            FloatingCardSize.MEDIUM -> 0.65f
            FloatingCardSize.LARGE -> 0.8f
        },
        animationSpec = tween(250),
        label = "cardWidth",
    )
    val horizontalBias by animateFloatAsState(
        targetValue = reminder.floatingCardPosition.horizontalBias,
        animationSpec = tween(250),
        label = "cardHorizontalBias",
    )
    val verticalBias by animateFloatAsState(
        targetValue = reminder.floatingCardPosition.verticalBias,
        animationSpec = tween(250),
        label = "cardVerticalBias",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .align(BiasAlignment(horizontalBias = horizontalBias, verticalBias = verticalBias))
                .shadow(elevation = 3.dp, shape = MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .animatedThemeBackground(reminder.theme),
        ) {
            PreviewAlertBody(
                reminder = reminder,
                showDescription = reminder.floatingCardSize != FloatingCardSize.SMALL,
                modifier = Modifier.padding(MaterialTheme.spacing.medium),
            )
        }
    }
}

/** Miniature of the shared alert layout on the theme surface. */
@Composable
private fun PreviewAlertBody(
    reminder: Reminder,
    showDescription: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = reminder.theme.spec.contentColor
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (reminder.drawingPosition == DrawingPosition.TOP) {
            PreviewDrawing(reminder)
        }
        Text(
            text = reminder.title.ifBlank { stringResource(R.string.editor_preview_placeholder_title) },
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showDescription && reminder.description.isNotBlank()) {
            Text(
                text = reminder.description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (reminder.drawingPosition != DrawingPosition.TOP) {
            PreviewDrawing(reminder)
        }
        PreviewChecklist(reminder)
        if (reminder.snoozeEnabled && reminder.allowedSnoozeDurations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            SnoozeChipRow(
                durations = reminder.allowedSnoozeDurations,
                compact = true,
                accentColor = contentColor,
            )
        }
        val autoDismiss = reminder.autoDismissAfter
        if (autoDismiss != null) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
            Text(
                text = stringResource(
                    R.string.editor_preview_auto_dismiss,
                    autoDismiss.toCompactLabel(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

/** Drawing miniature at its configured width fraction, matching the alert. */
@Composable
private fun PreviewDrawing(reminder: Reminder) {
    val drawing = reminder.drawing ?: return
    if (reminder.type != ReminderType.DRAWING || drawing.isEmpty) return
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
    DrawingView(
        drawing = drawing,
        modifier = Modifier
            .fillMaxWidth(reminder.drawingSize.fraction)
            .clip(MaterialTheme.shapes.small),
    )
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
}

/** First few checklist rows as unchecked miniatures, matching the alert. */
@Composable
private fun PreviewChecklist(reminder: Reminder) {
    if (reminder.type != ReminderType.CHECKLIST) return
    val items = reminder.checklist.filter { it.text.isNotBlank() }.take(3)
    if (items.isEmpty()) return
    val contentColor = reminder.theme.spec.contentColor
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(contentColor.copy(alpha = 0.14f))
                    .padding(
                        horizontal = MaterialTheme.spacing.small,
                        vertical = MaterialTheme.spacing.extraSmall,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .border(
                            width = 1.5.dp,
                            color = contentColor.copy(alpha = 0.9f),
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val more = reminder.checklist.count { it.text.isNotBlank() } - items.size
        if (more > 0) {
            Text(
                text = stringResource(R.string.editor_preview_more_items, more),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}
