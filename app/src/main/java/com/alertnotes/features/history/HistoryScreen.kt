package com.alertnotes.features.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.extensions.toRelativeTimeString
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.EmptyState
import com.alertnotes.core.ui.components.HighlightedText
import com.alertnotes.core.ui.components.PrimaryCard
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.data.backup.BackupManager
import com.alertnotes.data.backup.BackupResult
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.HistoryEntry
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.repository.ReminderHistoryRepository
import com.alertnotes.features.alerts.SIGNATURE_ASPECT_RATIO
import com.alertnotes.features.drawing.drawReminderStrokes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val ContentMaxWidth = 640.dp

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val query: String = "",
    val methodFilter: AcknowledgeMethod? = null,
    val hasNoHistory: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: ReminderHistoryRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val methodFilter = MutableStateFlow<AcknowledgeMethod?>(null)

    /** True while a CSV export runs; false again when it lands. */
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /** One event per finished export: true on success. */
    private val _exportResults = MutableSharedFlow<Boolean>()
    val exportResults: SharedFlow<Boolean> = _exportResults.asSharedFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.observeHistory(),
        query,
        methodFilter,
    ) { entries, query, method ->
        HistoryUiState(
            entries = entries
                .filter { method == null || it.method == method }
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) },
            query = query,
            methodFilter = method,
            hasNoHistory = entries.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onMethodFilterChange(method: AcknowledgeMethod?) {
        methodFilter.value = if (methodFilter.value == method) null else method
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            val result = backupManager.exportHistoryCsv(uri)
            _isExporting.value = false
            _exportResults.emit(result is BackupResult.Success)
        }
    }
}

/** Reminder history: when things fired, when and how they were resolved. */
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val exportSuccessText = stringResource(R.string.history_export_success)
    val exportFailureText = stringResource(R.string.history_export_failure)
    LaunchedEffect(Unit) {
        viewModel.exportResults.collect { success ->
            snackbarHostState.showSnackbar(
                if (success) exportSuccessText else exportFailureText,
            )
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.history_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { exportLauncher.launch("alertnotes_activity.csv") },
                        enabled = !isExporting && !uiState.hasNoHistory,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = stringResource(R.string.history_export),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    placeholder = stringResource(R.string.history_search_hint),
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.small,
                    ),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    items(AcknowledgeMethod.entries) { method ->
                        FilterChip(
                            selected = uiState.methodFilter == method,
                            onClick = { viewModel.onMethodFilterChange(method) },
                            label = { Text(text = stringResource(method.labelRes())) },
                        )
                    }
                }
                when {
                    uiState.hasNoHistory -> EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(R.string.history_empty_message),
                    )

                    uiState.entries.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.reminders_no_results_title),
                        message = stringResource(R.string.reminders_no_results_message),
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.spacing.large,
                            vertical = MaterialTheme.spacing.small,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        items(uiState.entries, key = { it.id }) { entry ->
                            HistoryRow(
                                entry = entry,
                                query = uiState.query,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    query: String,
    modifier: Modifier = Modifier,
) {
    PrimaryCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            HighlightedText(
                text = entry.title,
                query = query,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    R.string.history_triggered_at,
                    entry.triggeredAt.toRelativeTimeString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val method = entry.method
            val dismissedAt = entry.dismissedAt
            Text(
                text = when {
                    method == AcknowledgeMethod.SNOOZE && entry.snoozedMinutes != null ->
                        stringResource(R.string.history_snoozed_for, entry.snoozedMinutes)

                    method != null && dismissedAt != null ->
                        stringResource(
                            R.string.history_dismissed_via,
                            dismissedAt.toRelativeTimeString(),
                            stringResource(method.labelRes()),
                        )

                    else -> stringResource(R.string.history_still_open)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (method == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            entry.signature?.let { signature ->
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                SignatureThumb(signature)
            }
        }
    }
}

/**
 * The archived acknowledgement signature, re-rendered from its vector at the
 * pad's aspect ratio so it looks exactly as it was drawn.
 */
@Composable
private fun SignatureThumb(signature: ReminderDrawing) {
    val description = stringResource(R.string.cd_archived_signature)
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .aspectRatio(SIGNATURE_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = description },
    ) {
        drawReminderStrokes(signature.strokes)
    }
}

@StringRes
fun AcknowledgeMethod.labelRes(): Int = when (this) {
    AcknowledgeMethod.BUTTON -> R.string.method_button
    AcknowledgeMethod.TAP -> R.string.ack_tap
    AcknowledgeMethod.SWIPE -> R.string.ack_swipe
    AcknowledgeMethod.TICK_GESTURE -> R.string.ack_tick
    AcknowledgeMethod.SIGNATURE -> R.string.ack_signature
    AcknowledgeMethod.AUTO -> R.string.method_auto
    AcknowledgeMethod.NOTIFICATION -> R.string.method_notification
    AcknowledgeMethod.SNOOZE -> R.string.method_snooze
    AcknowledgeMethod.CHECKLIST -> R.string.method_checklist
}
