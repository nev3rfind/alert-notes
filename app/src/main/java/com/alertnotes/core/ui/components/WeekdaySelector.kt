package com.alertnotes.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alertnotes.core.extensions.weekDaysInLocaleOrder
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seven circular day toggles, localized and ordered by the user's locale.
 * Each circle is a 44dp toggle announced to TalkBack with the full day name.
 */
@Composable
fun WeekdaySelector(
    selectedDays: Set<DayOfWeek>,
    onDayToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val orderedDays = remember(locale) { weekDaysInLocaleOrder(locale) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        orderedDays.forEach { day ->
            DayToggle(
                day = day,
                selected = day in selectedDays,
                onToggle = { onDayToggle(day) },
                locale = locale,
            )
        }
    }
}

@Composable
private fun DayToggle(
    day: DayOfWeek,
    selected: Boolean,
    onToggle: () -> Unit,
    locale: Locale,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "dayBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "dayContent",
    )
    val fullName = remember(day, locale) { day.getDisplayName(TextStyle.FULL, locale) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(color = background, shape = CircleShape)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = fullName },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.getDisplayName(TextStyle.NARROW, locale),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

