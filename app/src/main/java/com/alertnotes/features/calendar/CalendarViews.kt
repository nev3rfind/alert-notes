package com.alertnotes.features.calendar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayString
import com.alertnotes.core.extensions.weekDaysInLocaleOrder
import com.alertnotes.core.ui.components.EmptyState
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.Reminder
import com.alertnotes.features.alerts.spec
import com.alertnotes.features.alerts.staticBrush
import com.alertnotes.features.reminders.editor.labelRes
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Callbacks shared by all calendar views. */
data class CalendarActions(
    val onOpenDay: (LocalDate) -> Unit,
    val onCreateOn: (LocalDate) -> Unit,
    val onEdit: (Reminder) -> Unit,
    val onSetEnabled: (Reminder, Boolean) -> Unit,
    val onDeleteRequest: (Reminder) -> Unit,
)

// region Month

private const val MAX_MONTH_DOTS = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthView(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val orderedDays = remember(locale) { weekDaysInLocaleOrder(locale) }
    val firstOfMonth = state.anchor.withDayOfMonth(1)
    val leadingBlanks = orderedDays.indexOf(firstOfMonth.dayOfWeek)
    val daysInMonth = firstOfMonth.lengthOfMonth()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            orderedDays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7
        repeat(rows) { rowIndex ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { columnIndex ->
                    val cellIndex = rowIndex * 7 + columnIndex
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        MonthDayCell(
                            date = firstOfMonth.withDayOfMonth(dayOfMonth),
                            state = state,
                            actions = actions,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthDayCell(
    date: LocalDate,
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val occurrences = state.occurrencesOn(date)
    val isToday = date == state.today
    val isSelected = date == state.selectedDate
    val cellDescription = stringResource(
        R.string.cd_calendar_day,
        date.toDisplayString(),
        occurrences.size,
    )
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = { actions.onOpenDay(date) },
                onLongClick = { actions.onCreateOn(date) },
            )
            .padding(vertical = MaterialTheme.spacing.extraSmall)
            .semantics { contentDescription = cellDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DayNumber(day = date.dayOfMonth, isToday = isToday, isSelected = isSelected)
        Row(
            modifier = Modifier.height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            occurrences.take(MAX_MONTH_DOTS).forEach { occurrence ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            brush = occurrence.reminder.theme.staticBrush(),
                            shape = CircleShape,
                        ),
                )
            }
        }
        Text(
            text = if (occurrences.size > MAX_MONTH_DOTS) {
                stringResource(R.string.calendar_more_count, occurrences.size - MAX_MONTH_DOTS)
            } else {
                ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Day number; today breathes inside a soft primary circle. */
@Composable
private fun DayNumber(day: Int, isToday: Boolean, isSelected: Boolean) {
    val todayPulse = if (isToday) {
        val transition = rememberInfiniteTransition(label = "todayPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_200), RepeatMode.Reverse),
            label = "todayAlpha",
        )
        alpha
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .then(
                when {
                    isToday -> Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = todayPulse),
                        CircleShape,
                    )

                    isSelected -> Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )

                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isToday) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

// endregion

// region Week

private const val MAX_WEEK_BLOCKS = 12

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekView(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val weekStart = remember(state.anchor, locale) {
        state.anchor.with(WeekFields.of(locale).dayOfWeek(), 1)
    }
    LazyRow(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            WeekDayColumn(date = date, state = state, actions = actions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekDayColumn(
    date: LocalDate,
    state: CalendarUiState,
    actions: CalendarActions,
) {
    val occurrences = state.occurrencesOn(date)
    val isToday = date == state.today
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isToday) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .combinedClickable(
                onClick = { actions.onOpenDay(date) },
                onLongClick = { actions.onCreateOn(date) },
            )
            .padding(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                " " + date.dayOfMonth,
            style = MaterialTheme.typography.titleSmall,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        occurrences.take(MAX_WEEK_BLOCKS).forEach { occurrence ->
            WeekBlock(occurrence = occurrence, actions = actions)
        }
        if (occurrences.size > MAX_WEEK_BLOCKS) {
            Text(
                text = stringResource(
                    R.string.calendar_more_count,
                    occurrences.size - MAX_WEEK_BLOCKS,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekBlock(occurrence: CalendarOccurrence, actions: CalendarActions) {
    val spec = occurrence.reminder.theme.spec
    OccurrenceMenuHost(occurrence = occurrence, actions = actions) { openMenu ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(occurrence.reminder.theme.staticBrush())
                .occurrenceClicks(
                    onClick = { actions.onEdit(occurrence.reminder) },
                    onLongClick = openMenu,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = occurrence.time.toDisplayString(),
                style = MaterialTheme.typography.labelSmall,
                color = spec.contentColor.copy(alpha = 0.85f),
            )
            Text(
                text = occurrence.reminder.title,
                style = MaterialTheme.typography.labelMedium,
                color = spec.contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// endregion

// region Day timeline & shared rows

@Composable
fun DayTimeline(
    date: LocalDate,
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val occurrences = state.occurrencesOn(date)
    if (occurrences.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.calendar_day_empty_title),
                message = stringResource(R.string.calendar_day_empty_message),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(occurrences, key = { "${it.reminder.id}-${it.at.toEpochMilli()}" }) { occurrence ->
            OccurrenceRow(
                occurrence = occurrence,
                actions = actions,
                showDate = false,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun TimelineView(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val allOccurrences = remember(state.occurrencesByDay) {
        state.occurrencesByDay.values.flatten().sortedBy { it.at }
    }
    if (allOccurrences.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.calendar_timeline_empty_title),
                message = stringResource(R.string.calendar_timeline_empty_message),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    val grouped = remember(allOccurrences, state.today) {
        allOccurrences.groupBy { timelineBucketFor(it.date, state.today) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        TimelineBucket.entries.forEach { bucket ->
            val bucketOccurrences = grouped[bucket].orEmpty()
            if (bucketOccurrences.isEmpty()) return@forEach
            item(key = "header-${bucket.name}") {
                Text(
                    text = stringResource(bucket.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        top = MaterialTheme.spacing.medium,
                        start = MaterialTheme.spacing.extraSmall,
                    ),
                )
            }
            items(
                bucketOccurrences,
                key = { "${it.reminder.id}-${it.at.toEpochMilli()}" },
            ) { occurrence ->
                OccurrenceRow(
                    occurrence = occurrence,
                    actions = actions,
                    showDate = bucket != TimelineBucket.TODAY &&
                        bucket != TimelineBucket.TOMORROW,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * A single occurrence: time gutter + themed block with title and meta.
 * Tap opens the editor; long-press opens quick actions.
 */
@Composable
fun OccurrenceRow(
    occurrence: CalendarOccurrence,
    actions: CalendarActions,
    showDate: Boolean,
    modifier: Modifier = Modifier,
) {
    val reminder = occurrence.reminder
    val spec = reminder.theme.spec
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(64.dp)) {
            Text(
                text = occurrence.time.toDisplayString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showDate) {
                Text(
                    text = occurrence.date.toDisplayString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OccurrenceMenuHost(occurrence = occurrence, actions = actions) { openMenu ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(reminder.theme.staticBrush())
                    .occurrenceClicks(
                        onClick = { actions.onEdit(reminder) },
                        onLongClick = openMenu,
                    )
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    ),
            ) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = spec.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(reminder.priority.labelRes()) + " · " +
                        stringResource(reminder.displayMode.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.contentColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** Hosts the long-press quick-action menu for an occurrence. */
@Composable
private fun OccurrenceMenuHost(
    occurrence: CalendarOccurrence,
    actions: CalendarActions,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val reminder = occurrence.reminder
    Box {
        content { menuExpanded = true }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                onClick = {
                    menuExpanded = false
                    actions.onEdit(reminder)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (reminder.isEnabled) R.string.action_pause else R.string.action_resume,
                        ),
                    )
                },
                onClick = {
                    menuExpanded = false
                    actions.onSetEnabled(reminder, !reminder.isEnabled)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    actions.onDeleteRequest(reminder)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.occurrenceClicks(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(onClick = onClick, onLongClick = onLongClick)

// endregion
