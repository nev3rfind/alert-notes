package com.alertnotes.domain.repository

/** Outcome of a cloud upload, surfaced in the settings dialog. */
sealed interface CloudUploadResult {
    data class Success(val reminderCount: Int) : CloudUploadResult
    data object Failure : CloudUploadResult
}

/**
 * One-time copy of local reminders to the signed-in account, offered when
 * switching from offline to online mode. Strictly one-way and additive:
 * local rows are read, never modified, and nothing is downloaded.
 */
interface CloudBackupRepository {

    suspend fun uploadAllReminders(): CloudUploadResult
}
