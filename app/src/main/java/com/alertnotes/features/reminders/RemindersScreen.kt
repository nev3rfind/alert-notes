package com.alertnotes.features.reminders

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.ui.WindowWidthClass
import com.alertnotes.core.ui.components.AppFloatingActionButton
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.CountdownText
import com.alertnotes.core.ui.components.EmptyState
import com.alertnotes.core.ui.components.HighlightedText
import com.alertnotes.core.ui.components.PrimaryCard
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.rememberWindowWidthClass
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.features.alerts.staticBrush
import com.alertnotes.features.reminders.editor.ReminderEditorPane

private val ListMaxWidth = 640.dp

/** Sentinel for "no editor open" in the two-pane layout. */
private const val NO_EDITOR = -1L

@Composable
fun RemindersScreen(
    onOpenEditor: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentlyDeleted by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val isTwoPane = rememberWindowWidthClass() == WindowWidthClass.Expanded
    var paneEditorId by rememberSaveable { mutableLongStateOf(NO_EDITOR) }
    // Bumped on every open so a fresh editor session (and ViewModel) starts.
    var paneEditorSession by rememberSaveable { mutableLongStateOf(0L) }
    val openEditor: (Long) -> Unit = { id ->
        if (isTwoPane) {
            paneEditorId = id
            paneEditorSession++
        } else {
            onOpenEditor(id)
        }
    }

    val deletedMessage = stringResource(R.string.reminders_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(recentlyDeleted) {
        if (recentlyDeleted != null) {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.onUndoExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_reminders),
                actions = {
                    com.alertnotes.core.ui.components.NotificationBellAction(
                        onOpen = onOpenNotifications,
                    )
                    val content = uiState as? RemindersUiState.Content
                    if (content != null) {
                        SortMenuAction(
                            selected = content.sort,
                            onSortChange = viewModel::onSortChange,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                icon = Icons.Filled.Add,
                contentDescription = null,
                text = stringResource(R.string.reminders_new),
                onClick = { openEditor(Reminder.NEW_ID) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (isTwoPane) {
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                Box(modifier = Modifier.weight(0.45f)) {
                    ListPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenReminder = openEditor,
                    )
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Box(modifier = Modifier.weight(0.55f)) {
                    if (paneEditorId == NO_EDITOR) {
                        EmptyState(
                            icon = Icons.Outlined.EditNote,
                            title = stringResource(R.string.editor_pane_placeholder_title),
                            message = stringResource(R.string.editor_pane_placeholder_message),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        key(paneEditorId, paneEditorSession) {
                            ReminderEditorPane(
                                reminderId = paneEditorId,
                                onClose = { paneEditorId = NO_EDITOR },
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                ListPane(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenReminder = openEditor,
                    modifier = Modifier
                        .widthIn(max = ListMaxWidth)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun ListPane(
    uiState: RemindersUiState,
    viewModel: RemindersViewModel,
    onOpenReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is RemindersUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is RemindersUiState.Error -> {
                EmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = stringResource(R.string.reminders_error_title),
                    message = stringResource(uiState.messageRes),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is RemindersUiState.Content -> {
                RemindersContent(
                    state = uiState,
                    onQueryChange = viewModel::onQueryChange,
                    onFilterToggle = viewModel::onFilterToggle,
                    onOpenReminder = onOpenReminder,
                    onSetEnabled = viewModel::setEnabled,
                    onDuplicate = viewModel::duplicate,
                    onDelete = viewModel::delete,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RemindersContent(
    state: RemindersUiState.Content,
    onQueryChange: (String) -> Unit,
    onFilterToggle: (ReminderFilter) -> Unit,
    onOpenReminder: (Long) -> Unit,
    onSetEnabled: (Reminder, Boolean) -> Unit,
    onDuplicate: (Reminder, String) -> Unit,
    onDelete: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.reminders_search_hint),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
        )
        FilterChipsRow(
            activeFilters = state.activeFilters,
            onFilterToggle = onFilterToggle,
        )
        when {
            state.hasNoReminders -> {
                EmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = stringResource(R.string.reminders_empty_title),
                    message = stringResource(R.string.reminders_empty_message),
                )
            }

            state.reminders.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.reminders_no_results_title),
                    message = stringResource(R.string.reminders_no_results_message),
                )
            }

            else -> {
                ReminderList(
                    state = state,
                    onOpenReminder = onOpenReminder,
                    onSetEnabled = onSetEnabled,
                    onDuplicate = onDuplicate,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SortMenuAction(
    selected: ReminderSort,
    onSortChange: (ReminderSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = stringResource(R.string.cd_sort_reminders),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReminderSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(sort.labelRes)) },
                    trailingIcon = {
                        if (sort == selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    activeFilters: Set<ReminderFilter>,
    onFilterToggle: (ReminderFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(ReminderFilter.entries) { filter ->
            FilterChip(
                selected = filter in activeFilters,
                onClick = { onFilterToggle(filter) },
                label = { Text(text = stringResource(filter.labelRes)) },
            )
        }
    }
}

@Composable
private fun ReminderList(
    state: RemindersUiState.Content,
    onOpenReminder: (Long) -> Unit,
    onSetEnabled: (Reminder, Boolean) -> Unit,
    onDuplicate: (Reminder, String) -> Unit,
    onDelete: (Reminder) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.large,
            end = MaterialTheme.spacing.large,
            top = MaterialTheme.spacing.small,
            // Room for the FAB so the last row is never covered.
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(items = state.reminders, key = { it.id }) { reminder ->
            ReminderRow(
                reminder = reminder,
                status = state.statusOf(reminder),
                query = state.query,
                onClick = { onOpenReminder(reminder.id) },
                onSetEnabled = { onSetEnabled(reminder, it) },
                onDuplicate = onDuplicate,
                onDelete = { onDelete(reminder) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    status: ReminderDisplayStatus,
    query: String,
    onClick: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDuplicate: (Reminder, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Theme accent: a thin indicator hugging the card's right edge.
            Box(
                modifier = Modifier
                    .matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(5.dp)
                        .background(reminder.theme.staticBrush()),
                )
            }
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.small,
                    top = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = MaterialTheme.spacing.small),
            ) {
                HighlightedText(
                    text = reminder.title,
                    query = query,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reminder.description.isNotBlank()) {
                    HighlightedText(
                        text = reminder.description,
                        query = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ReminderStatusLine(reminder = reminder, status = status)
            }
            val toggleDescription = stringResource(R.string.cd_toggle_reminder, reminder.title)
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
            ReminderRowMenu(
                reminder = reminder,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
            )
            }
        }
    }
}

@Composable
private fun ReminderStatusLine(reminder: Reminder, status: ReminderDisplayStatus) {
    val staticText = buildList {
        add(stringResource(status.labelRes))
        when (reminder.priority) {
            ReminderPriority.LOW -> add(stringResource(R.string.priority_low))
            ReminderPriority.HIGH -> add(stringResource(R.string.priority_high))
            ReminderPriority.CRITICAL -> add(stringResource(R.string.priority_critical))
            ReminderPriority.NORMAL -> Unit
        }
    }.joinToString(separator = " · ")

    val statusColor by animateColorAsState(
        targetValue = when {
            status == ReminderDisplayStatus.RUNNING ||
                status == ReminderDisplayStatus.QUEUED ->
                MaterialTheme.colorScheme.error

            reminder.priority == ReminderPriority.CRITICAL &&
                status == ReminderDisplayStatus.UPCOMING ->
                MaterialTheme.colorScheme.error

            status == ReminderDisplayStatus.UPCOMING ||
                status == ReminderDisplayStatus.WAITING ->
                MaterialTheme.colorScheme.primary

            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "statusColor",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = staticText,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val nextTrigger = reminder.nextTriggerAt
        if (nextTrigger != null &&
            (status == ReminderDisplayStatus.UPCOMING || status == ReminderDisplayStatus.WAITING)
        ) {
            Text(
                text = " · ",
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
            // Leaf composable: only this text recomposes on each tick.
            CountdownText(
                target = nextTrigger,
                fallback = stringResource(R.string.countdown_due_now),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun ReminderRowMenu(
    reminder: Reminder,
    onDuplicate: (Reminder, String) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val copyTitle = stringResource(R.string.reminder_copy_title, reminder.title)
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_reminder_actions, reminder.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.action_duplicate)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onDuplicate(reminder, copyTitle)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
