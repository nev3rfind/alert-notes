package com.alertnotes.features.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.data.backup.BackupManager
import com.alertnotes.data.backup.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Snackbar-able outcome of an import/export run. */
data class BackupMessage(
    val isSuccess: Boolean,
    val count: Int,
    val isImport: Boolean,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<BackupMessage>()
    val messages: SharedFlow<BackupMessage> = _messages.asSharedFlow()

    fun exportZip(uri: Uri) = run(isImport = false) { backupManager.exportZip(uri) }

    fun importZip(uri: Uri) = run(isImport = true) { backupManager.importZip(uri) }

    fun exportCsv(uri: Uri) = run(isImport = false) { backupManager.exportCsv(uri) }

    fun importCsv(uri: Uri) = run(isImport = true) { backupManager.importCsv(uri) }

    private fun run(isImport: Boolean, operation: suspend () -> BackupResult) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = operation()
            _isBusy.value = false
            _messages.emit(
                when (result) {
                    is BackupResult.Success ->
                        BackupMessage(isSuccess = true, count = result.reminderCount, isImport = isImport)

                    is BackupResult.Failure ->
                        BackupMessage(isSuccess = false, count = 0, isImport = isImport)
                },
            )
        }
    }
}
