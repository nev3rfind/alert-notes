package com.alertnotes.features.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.ConfirmationDialog
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderTemplate
import com.alertnotes.domain.repository.TemplateRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.alertnotes.features.alerts.spec
import com.alertnotes.features.reminders.editor.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val templates: StateFlow<List<ReminderTemplate>> = templateRepository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openEditor = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openEditor: SharedFlow<Long> = _openEditor.asSharedFlow()

    /**
     * Using a template creates a real reminder (an hour out by default) and
     * opens the editor on it — the schedule is always the user's decision.
     */
    fun use(template: ReminderTemplate) {
        viewModelScope.launch {
            runCatching {
                val now = timeProvider.now()
                val id = coordinator.saveAndSchedule(
                    Reminder(
                        title = template.title,
                        description = template.description,
                        type = template.type,
                        priority = template.priority,
                        acknowledgement = template.acknowledgement,
                        theme = template.theme,
                        recurrence = Recurrence.OneTime(now.plus(DEFAULT_LEAD)),
                        timeZone = ZoneId.systemDefault(),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _openEditor.tryEmit(id)
            }
        }
    }

    fun duplicate(template: ReminderTemplate) {
        viewModelScope.launch { runCatching { templateRepository.duplicate(template) } }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { templateRepository.delete(id) } }
    }

    private companion object {
        val DEFAULT_LEAD: Duration = Duration.ofHours(1)
    }
}

/**
 * Reminder Templates: the shipped set plus the user's own captures from the
 * editor's "Save as template". Using one creates the reminder and opens the
 * editor to set its schedule; built-ins can only be copied.
 */
@Composable
fun TemplatesScreen(
    onNavigateBack: () -> Unit,
    onOpenEditor: (Long) -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.openEditor.collect(onOpenEditor)
    }
    val builtIns = templates.filter { it.builtIn }
    val custom = templates.filterNot { it.builtIn }

    // Deleting a template was instant and irreversible - one mis-tap next to
    // Use and Duplicate destroyed a capture the user had built by hand, with
    // no confirmation, no undo, and no error if the write failed.
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        ConfirmationDialog(
            title = stringResource(R.string.templates_delete_confirm_title),
            message = stringResource(R.string.templates_delete_confirm_message),
            confirmText = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.delete(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.templates_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.templates_custom_section)) {
                        if (custom.isEmpty()) {
                            Text(
                                text = stringResource(R.string.templates_custom_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.large),
                            )
                        } else {
                            custom.forEach { template ->
                                TemplateRow(
                                    template = template,
                                    onUse = { viewModel.use(template) },
                                    onDuplicate = { viewModel.duplicate(template) },
                                    onDelete = { pendingDelete = template.id },
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.templates_builtin_section)) {
                        builtIns.forEach { template ->
                            TemplateRow(
                                template = template,
                                onUse = { viewModel.use(template) },
                                onDuplicate = { viewModel.duplicate(template) },
                                onDelete = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: ReminderTemplate,
    onUse: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Bookmark,
                contentDescription = null,
                tint = template.theme.spec.accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.spacing.medium),
            )
        }
        Text(
            text = listOf(
                stringResource(template.priority.labelRes()),
                stringResource(template.type.labelRes()),
                stringResource(template.acknowledgement.labelRes()),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = onUse) {
                Text(text = stringResource(R.string.templates_use))
            }
            TextButton(onClick = onDuplicate) {
                Text(text = stringResource(R.string.templates_duplicate))
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
