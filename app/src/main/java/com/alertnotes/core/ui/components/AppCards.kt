package com.alertnotes.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alertnotes.core.ui.theme.spacing

/**
 * The base surface for grouped content. Flat (no shadow) with a large corner
 * radius, echoing grouped lists on iOS. Pass [onClick] to make the whole card
 * tappable.
 */
@Composable
fun PrimaryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            // Flat at rest, gently lifting while touched.
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 3.dp,
            ),
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}

/**
 * A [PrimaryCard] preceded by an uppercase section label — the standard
 * building block of every list-style screen in the app.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        SectionLabel(text = title)
        PrimaryCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * The heading [SectionCard] draws above its card, on its own.
 *
 * A list long enough to need virtualising cannot use [SectionCard] - that
 * would put every row inside one lazy item and compose them all at once. Such
 * a list emits this label as one item and its rows as their own, so the
 * heading still looks identical to every other section in the app.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.small,
        ),
    )
}
