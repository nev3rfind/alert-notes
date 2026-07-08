package com.alertnotes.features.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppOutlinedButton
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import java.time.LocalDate

private val ContentMaxWidth = 640.dp

/**
 * Backup hub: full ZIP backup/restore (settings, reminders, themes,
 * drawings, history) and portable CSV export/import, all through the
 * system file picker — nothing ever leaves the device unasked.
 */
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val successExport = stringResource(R.string.backup_export_done)
    val successImport = stringResource(R.string.backup_import_done)
    val failure = stringResource(R.string.backup_failed)
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                when {
                    !message.isSuccess -> failure
                    message.isImport -> successImport.format(message.count)
                    else -> successExport.format(message.count)
                },
            )
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportZip) }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }
    val importZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importZip) }
    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importCsv) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.backup_title),
                onNavigateBack = onNavigateBack,
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
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item { PrivacyNote() }
                item {
                    SectionCard(title = stringResource(R.string.backup_export_title)) {
                        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                            Text(
                                text = stringResource(R.string.backup_export_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                            SecondaryButton(
                                text = stringResource(R.string.backup_export_zip),
                                onClick = {
                                    exportZipLauncher.launch(suggestedName("zip"))
                                },
                                enabled = !isBusy,
                                icon = Icons.Outlined.FolderZip,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                            SecondaryButton(
                                text = stringResource(R.string.backup_export_csv),
                                onClick = {
                                    exportCsvLauncher.launch(suggestedName("csv"))
                                },
                                enabled = !isBusy,
                                icon = Icons.Outlined.Description,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.backup_import_title)) {
                        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                            Text(
                                text = stringResource(R.string.backup_import_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                            AppOutlinedButton(
                                text = stringResource(R.string.backup_import_zip),
                                onClick = {
                                    importZipLauncher.launch(arrayOf("application/zip"))
                                },
                                enabled = !isBusy,
                                icon = Icons.Outlined.FileDownload,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                            AppOutlinedButton(
                                text = stringResource(R.string.backup_import_csv),
                                onClick = {
                                    importCsvLauncher.launch(
                                        arrayOf("text/csv", "text/comma-separated-values", "text/plain"),
                                    )
                                },
                                enabled = !isBusy,
                                icon = Icons.Outlined.Description,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun suggestedName(extension: String): String =
    "alertnotes_${LocalDate.now()}.$extension"

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.backup_privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
        )
    }
}
