package com.alertnotes.features.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayString
import com.alertnotes.core.ui.WindowWidthClass
import com.alertnotes.core.ui.components.AppSegmentedControl
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.ConfirmationDialog
import com.alertnotes.core.ui.components.OptionPickerDialog
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.rememberWindowWidthClass
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderTheme
import com.alertnotes.features.alerts.spec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ContentMaxWidth = 980.dp

/**
 * The calendar hub: Month / Week / Day / Timeline views over projected
 * reminder occurrences, with search, filters, and quick actions. On expanded
 * windows the selected day's timeline docks as a side panel.
 */
@Composable
fun CalendarScreen(
    onOpenEditor: (Long) -> Unit,
    onCreateOn: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTwoPane = rememberWindowWidthClass() == WindowWidthClass.Expanded
    var deleteTarget by remember { mutableStateOf<Reminder?>(null) }
    var showThemeFilter by remember { mutableStateOf(false) }

    val actions = CalendarActions(
        onOpenDay = viewModel::onOpenDay,
        onCreateOn = onCreateOn,
        onEdit = { onOpenEditor(it.id) },
        onSetEnabled = viewModel::setEnabled,
        onDeleteRequest = { deleteTarget = it },
    )

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.nav_calendar)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
            ) {
                SearchField(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.reminders_search_hint),
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.small,
                    ),
                )
                CalendarFilterRow(
                    state = uiState,
                    onFilterToggle = viewModel::onFilterToggle,
                    onThemeFilterClick = { showThemeFilter = true },
                )
                Column(
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    AppSegmentedControl(
                        options = CalendarMode.entries.toList(),
                        selected = uiState.mode,
                        onSelect = viewModel::onModeChange,
                        label = { stringResource(it.labelRes) },
                    )
                    if (uiState.mode != CalendarMode.TIMELINE) {
                        PeriodNavigator(
                            state = uiState,
                            onStep = viewModel::onStep,
                            onToday = viewModel::onGoToToday,
                        )
                    }
                }
                if (isTwoPane && uiState.mode != CalendarMode.DAY) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        CalendarBody(
                            state = uiState,
                            actions = actions,
                            modifier = Modifier
                                .weight(0.62f)
                                .padding(MaterialTheme.spacing.small),
                        )
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        SidePanel(
                            state = uiState,
                            actions = actions,
                            modifier = Modifier.weight(0.38f),
                        )
                    }
                } else {
                    CalendarBody(
                        state = uiState,
                        actions = actions,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(MaterialTheme.spacing.small),
                    )
                }
            }
        }
    }

    deleteTarget?.let { reminder ->
        ConfirmationDialog(
            title = stringResource(R.string.editor_delete_title),
            message = stringResource(R.string.editor_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.delete(reminder)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            isDestructive = true,
        )
    }
    if (showThemeFilter) {
        OptionPickerDialog(
            title = stringResource(R.string.calendar_filter_theme),
            options = listOf<ReminderTheme?>(null) + ReminderTheme.entries,
            selected = uiState.themeFilter,
            optionLabel = { theme ->
                theme?.let { stringResource(it.spec.labelRes) }
                    ?: stringResource(R.string.calendar_filter_any_theme)
            },
            onSelect = viewModel::onThemeFilterChange,
            onDismiss = { showThemeFilter = false },
        )
    }
}

@Composable
private fun CalendarBody(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.mode,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                .togetherWith(fadeOut(tween(150)))
        },
        label = "calendarMode",
        modifier = modifier,
    ) { mode ->
        when (mode) {
            CalendarMode.MONTH -> MonthView(state = state, actions = actions)
            CalendarMode.WEEK -> WeekView(state = state, actions = actions)
            CalendarMode.DAY -> DayTimeline(
                date = state.anchor,
                state = state,
                actions = actions,
            )

            CalendarMode.TIMELINE -> TimelineView(state = state, actions = actions)
        }
    }
}

/** Tablet side panel: the selected day's timeline, always in view. */
@Composable
private fun SidePanel(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = state.selectedDate.toDisplayString(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
        )
        DayTimeline(
            date = state.selectedDate,
            state = state,
            actions = actions,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PeriodNavigator(
    state: CalendarUiState,
    onStep: (forward: Boolean) -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStep(false) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_calendar_previous),
            )
        }
        Text(
            text = periodTitle(state),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onStep(true) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_calendar_next),
            )
        }
        TextButton(onClick = onToday) {
            Text(text = stringResource(R.string.calendar_today))
        }
    }
}

private fun periodTitle(state: CalendarUiState): String {
    val locale = Locale.getDefault()
    return when (state.mode) {
        CalendarMode.MONTH ->
            state.anchor.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) +
                " " + state.anchor.year

        CalendarMode.WEEK -> {
            val start = state.anchor.with(
                java.time.temporal.WeekFields.of(locale).dayOfWeek(),
                1,
            )
            val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
            start.format(formatter) + " – " + start.plusDays(6).format(formatter)
        }

        CalendarMode.DAY -> state.anchor.toDisplayString()
        CalendarMode.TIMELINE -> ""
    }
}

@Composable
private fun CalendarFilterRow(
    state: CalendarUiState,
    onFilterToggle: (CalendarFilter) -> Unit,
    onThemeFilterClick: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        item(key = "theme-filter") {
            FilterChip(
                selected = state.themeFilter != null,
                onClick = onThemeFilterClick,
                label = {
                    Text(
                        text = state.themeFilter?.let { stringResource(it.spec.labelRes) }
                            ?: stringResource(R.string.calendar_filter_theme),
                    )
                },
            )
        }
        items(CalendarFilter.entries) { filter ->
            FilterChip(
                selected = filter in state.filters,
                onClick = { onFilterToggle(filter) },
                label = { Text(text = stringResource(filter.labelRes)) },
            )
        }
    }
}
