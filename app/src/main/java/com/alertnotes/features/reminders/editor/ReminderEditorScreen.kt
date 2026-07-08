package com.alertnotes.features.reminders.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.ConfirmationDialog
import com.alertnotes.core.ui.components.DangerButton
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.theme.spacing

private val FormMaxWidth = 640.dp

/**
 * Full-screen editor host used on phones. On tablets the same session is
 * embedded as a side pane via [ReminderEditorPane].
 */
@Composable
fun ReminderEditorScreen(
    reminderId: Long,
    onClose: () -> Unit,
    initialEpochDay: Long = -1,
) {
    val viewModel = rememberEditorViewModel(reminderId, initialEpochDay)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session = rememberEditorSession(viewModel, onClose)
    val editing = uiState as? EditorUiState.Editing

    BackHandler(enabled = editing?.isDirty == true) {
        session.requestClose(isDirty = true)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(editorTitleRes(editing)),
                onNavigateBack = { session.requestClose(isDirty = editing?.isDirty == true) },
                actions = {
                    EditorActions(editing = editing, viewModel = viewModel)
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        EditorBody(
            uiState = uiState,
            viewModel = viewModel,
            onDeleteRequest = { session.showDeleteDialog = true },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        )
    }

    session.Dialogs(isNew = editing?.isNew != false)
}

/**
 * Embedded editor for the tablet two-pane layout: same session, lighter
 * chrome (close button instead of a top app bar).
 */
@Composable
fun ReminderEditorPane(
    reminderId: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberEditorViewModel(reminderId, initialEpochDay = -1)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session = rememberEditorSession(viewModel, onClose)
    val editing = uiState as? EditorUiState.Editing

    BackHandler {
        session.requestClose(isDirty = editing?.isDirty == true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.small,
                    vertical = MaterialTheme.spacing.extraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { session.requestClose(isDirty = editing?.isDirty == true) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_editor),
                )
            }
            Text(
                text = stringResource(editorTitleRes(editing)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            EditorActions(editing = editing, viewModel = viewModel)
        }
        EditorBody(
            uiState = uiState,
            viewModel = viewModel,
            onDeleteRequest = { session.showDeleteDialog = true },
            modifier = Modifier.fillMaxSize(),
        )
    }

    session.Dialogs(isNew = editing?.isNew != false)
}

// region Session plumbing shared by both hosts

@Composable
private fun rememberEditorViewModel(
    reminderId: Long,
    initialEpochDay: Long,
): ReminderEditorViewModel =
    hiltViewModel<ReminderEditorViewModel, ReminderEditorViewModel.Factory>(
        key = "reminder-editor-$reminderId-$initialEpochDay",
        creationCallback = { factory: ReminderEditorViewModel.Factory ->
            factory.create(reminderId, initialEpochDay)
        },
    )

/** Close/discard/delete choreography shared by the screen and pane hosts. */
private class EditorSession(
    private val viewModel: ReminderEditorViewModel,
    private val onClose: () -> Unit,
    showDiscardDialogState: MutableState<Boolean>,
    showDeleteDialogState: MutableState<Boolean>,
) {
    // Backed by saveable state hoisted in [rememberEditorSession]: an open
    // confirmation dialog must survive rotation and process death.
    var showDiscardDialog by showDiscardDialogState
    var showDeleteDialog by showDeleteDialogState

    fun requestClose(isDirty: Boolean) {
        if (isDirty) showDiscardDialog = true else onClose()
    }

    @Composable
    fun Dialogs(isNew: Boolean) {
        if (showDiscardDialog) {
            ConfirmationDialog(
                title = stringResource(R.string.editor_discard_title),
                message = stringResource(R.string.editor_discard_message),
                confirmText = stringResource(R.string.editor_discard_confirm),
                onConfirm = {
                    showDiscardDialog = false
                    onClose()
                },
                onDismiss = { showDiscardDialog = false },
                isDestructive = true,
            )
        }
        if (showDeleteDialog && !isNew) {
            ConfirmationDialog(
                title = stringResource(R.string.editor_delete_title),
                message = stringResource(R.string.editor_delete_message),
                confirmText = stringResource(R.string.action_delete),
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.delete()
                },
                onDismiss = { showDeleteDialog = false },
                isDestructive = true,
            )
        }
    }
}

@Composable
private fun rememberEditorSession(
    viewModel: ReminderEditorViewModel,
    onClose: () -> Unit,
): EditorSession {
    val showDiscardDialog = rememberSaveable { mutableStateOf(false) }
    val showDeleteDialog = rememberSaveable { mutableStateOf(false) }
    val session = remember(viewModel) {
        EditorSession(viewModel, onClose, showDiscardDialog, showDeleteDialog)
    }
    val isFinished by viewModel.isFinished.collectAsStateWithLifecycle()
    LaunchedEffect(isFinished) {
        if (isFinished) onClose()
    }
    return session
}

@StringRes
private fun editorTitleRes(editing: EditorUiState.Editing?): Int =
    if (editing?.isNew == false) R.string.editor_title_edit else R.string.editor_title_new

@Composable
private fun EditorActions(
    editing: EditorUiState.Editing?,
    viewModel: ReminderEditorViewModel,
) {
    val draftTitle = editing?.draft?.title.orEmpty()
    val copyTitle = stringResource(R.string.reminder_copy_title, draftTitle)
    TextButton(
        onClick = viewModel::save,
        enabled = editing != null && editing.validation.isValid && (editing.isDirty || editing.isNew),
    ) {
        Text(text = stringResource(R.string.action_save))
    }
    if (editing?.isNew == false) {
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_editor_more_actions),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.action_duplicate)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        viewModel.duplicate(copyTitle)
                    },
                )
            }
        }
    }
}

// endregion

// region Body: searchable section list

/** Editor sections in display order, each with its searchable keywords. */
private enum class EditorSectionId(
    @param:StringRes val titleRes: Int,
    val keywordRes: List<Int>,
) {
    STATUS(
        R.string.editor_section_status,
        listOf(
            R.string.editor_status_next,
            R.string.editor_status_last,
            R.string.editor_status_schedule,
        ),
    ),
    GENERAL(
        R.string.editor_section_general,
        listOf(
            R.string.reminder_field_title,
            R.string.reminder_field_notes,
            R.string.editor_type,
            R.string.editor_priority,
            R.string.editor_enabled,
        ),
    ),
    DISPLAY(
        R.string.editor_section_display,
        listOf(
            R.string.editor_display_mode,
            R.string.display_full_screen,
            R.string.display_floating_card,
            R.string.editor_floating_position,
            R.string.editor_floating_size,
        ),
    ),
    DISMISSAL(
        R.string.editor_section_dismissal,
        listOf(
            R.string.editor_auto_dismiss,
            R.string.editor_acknowledgement,
        ),
    ),
    SNOOZE(
        R.string.editor_section_snooze,
        listOf(
            R.string.editor_snooze_enabled,
            R.string.editor_snooze_options,
        ),
    ),
    SECURITY(
        R.string.editor_section_security,
        listOf(
            R.string.editor_require_fingerprint,
            R.string.editor_pin_fallback,
        ),
    ),
    ALERTS(
        R.string.editor_section_alerts,
        listOf(
            R.string.editor_wake_screen,
            R.string.editor_show_lock_screen,
            R.string.editor_vibration,
            R.string.editor_sound,
            R.string.editor_history,
            R.string.editor_overlay,
        ),
    ),
    SCHEDULE(
        R.string.editor_section_schedule,
        listOf(
            R.string.editor_recurrence,
            R.string.editor_time_of_day,
            R.string.editor_active_days,
            R.string.editor_active_hours,
            R.string.editor_start_date,
            R.string.editor_end_date,
        ),
    ),
}

@Composable
private fun EditorBody(
    uiState: EditorUiState,
    viewModel: ReminderEditorViewModel,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (uiState) {
            is EditorUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is EditorUiState.Editing -> {
                EditorForm(
                    state = uiState,
                    onUpdate = viewModel::update,
                    onChangeRecurrenceKind = viewModel::changeRecurrenceKind,
                    onScheduleModeChange = viewModel::setScheduleMode,
                    onTriggerInChange = viewModel::setTriggerIn,
                    onDeleteRequest = onDeleteRequest,
                    modifier = Modifier
                        .widthIn(max = FormMaxWidth)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun EditorForm(
    state: EditorUiState.Editing,
    onUpdate: DraftUpdate,
    onChangeRecurrenceKind: (RecurrenceKind) -> Unit,
    onScheduleModeChange: (ScheduleMode) -> Unit,
    onTriggerInChange: (java.time.Duration) -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    // The live status panel only makes sense for persisted reminders.
    val visibleSections = visibleSections(settingsQuery)
        .filter { it != EditorSectionId.STATUS || !state.isNew }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.large,
            end = MaterialTheme.spacing.large,
            top = MaterialTheme.spacing.small,
            bottom = MaterialTheme.spacing.huge,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
    ) {
        item(key = "search") {
            SearchField(
                query = settingsQuery,
                onQueryChange = { settingsQuery = it },
                placeholder = stringResource(R.string.editor_search_hint),
            )
        }
        visibleSections.forEach { section ->
            item(key = section.name) {
                when (section) {
                    EditorSectionId.STATUS -> StatusSection(
                        draft = state.draft,
                    )

                    EditorSectionId.GENERAL -> GeneralSection(
                        draft = state.draft,
                        validation = state.validation,
                        onUpdate = onUpdate,
                    )

                    EditorSectionId.DISPLAY -> DisplaySection(
                        draft = state.draft,
                        onUpdate = onUpdate,
                    )

                    EditorSectionId.DISMISSAL -> DismissalSection(
                        draft = state.draft,
                        onUpdate = onUpdate,
                    )

                    EditorSectionId.SNOOZE -> SnoozeSection(
                        draft = state.draft,
                        validation = state.validation,
                        onUpdate = onUpdate,
                    )

                    EditorSectionId.SECURITY -> SecuritySection(
                        draft = state.draft,
                        onUpdate = onUpdate,
                    )

                    EditorSectionId.ALERTS -> AlertsSection(
                        draft = state.draft,
                        onUpdate = onUpdate,
                    )

                    EditorSectionId.SCHEDULE -> ScheduleSection(
                        draft = state.draft,
                        validation = state.validation,
                        scheduleMode = state.scheduleMode,
                        triggerIn = state.triggerIn,
                        onUpdate = onUpdate,
                        onChangeRecurrenceKind = onChangeRecurrenceKind,
                        onScheduleModeChange = onScheduleModeChange,
                        onTriggerInChange = onTriggerInChange,
                    )
                }
            }
        }
        if (visibleSections.isEmpty()) {
            item(key = "no-matches") {
                Text(
                    text = stringResource(R.string.editor_search_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                )
            }
        }
        if (!state.isNew && settingsQuery.isBlank()) {
            item(key = "delete") {
                DangerButton(
                    text = stringResource(R.string.editor_delete_button),
                    onClick = onDeleteRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Sections whose title or any setting label matches the search query. */
@Composable
private fun visibleSections(query: String): List<EditorSectionId> {
    if (query.isBlank()) return EditorSectionId.entries.toList()
    return EditorSectionId.entries.filter { section ->
        stringResource(section.titleRes).contains(query, ignoreCase = true) ||
            section.keywordRes.any { stringResource(it).contains(query, ignoreCase = true) }
    }
}

// endregion
