package com.alertnotes.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.data.dao.ReminderDao
import com.alertnotes.data.dao.ReminderHistoryDao
import com.alertnotes.data.database.AlertNotesDatabase
import com.alertnotes.data.entities.ReminderHistoryEntity
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Outcome of a backup operation, surfaced as a snackbar. */
sealed interface BackupResult {
    data class Success(val reminderCount: Int) : BackupResult
    data object Failure : BackupResult
}

/**
 * ZIP and CSV import/export over the Storage Access Framework. Everything is
 * local files chosen by the user — consistent with the app's no-cloud
 * promise. ZIP carries the complete state (settings, reminders with themes
 * and drawings, history); CSV is a portable reminder list.
 */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AlertNotesDatabase,
    private val reminderDao: ReminderDao,
    private val historyDao: ReminderHistoryDao,
    private val settingsRepository: SettingsRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // region ZIP

    suspend fun exportZip(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val reminders = reminderDao.getAllForBackup()
            val backup = BackupFile(
                exportedAtEpochMillis = timeProvider.now().toEpochMilli(),
                settings = settingsRepository.preferences.first().toBackup(),
                reminders = reminders.map { it.toBackup() },
                history = historyDao.getAllForBackup().map { it.toBackup() },
            )
            context.contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry(BackupFile.ZIP_ENTRY_NAME))
                    zip.write(json.encodeToString(backup).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: error("Cannot open $uri for writing")
            BackupResult.Success(reminders.size)
        }.getOrElse {
            logger.e(TAG, "ZIP export failed", it)
            BackupResult.Failure
        }
    }

    suspend fun importZip(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }
                        .firstOrNull { it.name.endsWith(".json") }
                        // Bounded read: a crafted ZIP entry can decompress to
                        // gigabytes; readBytes() would OOM the process.
                        ?.let { zip.readBounded(MAX_PAYLOAD_BYTES).toString(Charsets.UTF_8) }
                }
            } ?: error("No backup payload found in $uri")
            val backup = json.decodeFromString<BackupFile>(payload)

            applySettings(backup.settings)
            // One transaction for every row: a mid-import failure rolls back
            // to the pre-import state instead of leaving partial data (and
            // duplicates on retry).
            database.withTransaction {
                val idMap = HashMap<Long, Long>()
                backup.reminders.forEach { backupReminder ->
                    val newId = reminderDao.upsert(backupReminder.toEntity())
                    idMap[backupReminder.originalId] = newId
                }
                backup.history.forEach { entry ->
                    val newReminderId = idMap[entry.reminderOriginalId] ?: return@forEach
                    historyDao.insert(
                        ReminderHistoryEntity(
                            reminderId = newReminderId,
                            title = entry.title,
                            triggeredAtMillis = entry.triggeredAtMillis,
                            dismissedAtMillis = entry.dismissedAtMillis,
                            method = entry.method,
                            snoozedMinutes = entry.snoozedMinutes,
                            signature = entry.signature,
                        ),
                    )
                }
            }
            // Alarms only after the data has committed; the sweep computes
            // every imported reminder's next occurrence and refreshes widgets.
            coordinator.rescheduleAll()
            BackupResult.Success(backup.reminders.size)
        }.getOrElse {
            logger.e(TAG, "ZIP import failed", it)
            BackupResult.Failure
        }
    }

    private suspend fun applySettings(settings: BackupSettings) {
        settingsRepository.setThemeMode(settings.themeModeOrDefault())
        settingsRepository.setUseDynamicColor(settings.useDynamicColor)
        settingsRepository.setRemindersNotificationsEnabled(settings.remindersNotificationsEnabled)
        settingsRepository.setCriticalInterruptsEnabled(settings.criticalInterruptsEnabled)
        settingsRepository.setBiometricLockEnabled(settings.biometricLockEnabled)
    }

    // endregion

    // region CSV

    suspend fun exportCsv(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val reminders = reminderDao.getAllForBackup()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.appendLine(CSV_HEADER)
                    reminders.forEach { entity ->
                        writer.appendLine(
                            listOf(
                                entity.title,
                                entity.description,
                                entity.priority,
                                entity.theme,
                                entity.isEnabled.toString(),
                                entity.recurrenceType,
                            ).joinToString(",") { it.csvSanitize().csvEscape() },
                        )
                    }
                }
            } ?: error("Cannot open $uri for writing")
            BackupResult.Success(reminders.size)
        }.getOrElse {
            logger.e(TAG, "CSV export failed", it)
            BackupResult.Failure
        }
    }

    /**
     * CSV import creates unscheduled reminders from the essential columns
     * (title, description, priority, theme, enabled) — schedules are richer
     * than CSV can express; users pick them in the editor afterwards.
     */
    suspend fun importCsv(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Cannot open $uri for reading")
            val now = timeProvider.now()
            var imported = 0
            // Record-based parsing: quoted fields may contain newlines (the
            // app's own export produces them for multi-line notes).
            parseCsvRecords(text).drop(1).forEach { fields ->
                val title = fields.getOrNull(0)?.trim().orEmpty()
                if (title.isEmpty()) return@forEach
                val reminder = Reminder(
                    title = title,
                    description = fields.getOrNull(1)?.trim().orEmpty(),
                    priority = fields.getOrNull(2)?.let { value ->
                        ReminderPriority.entries.firstOrNull { it.name == value }
                    } ?: ReminderPriority.NORMAL,
                    theme = fields.getOrNull(3)?.let { value ->
                        com.alertnotes.domain.model.ReminderTheme.entries
                            .firstOrNull { it.name == value }
                    } ?: com.alertnotes.domain.model.ReminderTheme.PRIMARY_ORANGE,
                    isEnabled = fields.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
                    recurrence = Recurrence.None,
                    timeZone = ZoneId.systemDefault(),
                    createdAt = now,
                    updatedAt = now,
                )
                coordinator.saveAndSchedule(reminder)
                imported++
            }
            BackupResult.Success(imported)
        }.getOrElse {
            logger.e(TAG, "CSV import failed", it)
            BackupResult.Failure
        }
    }

    /** Activity history as a spreadsheet-friendly CSV. Export only. */
    suspend fun exportHistoryCsv(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val entries = historyDao.getAllForBackup()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.appendLine(HISTORY_CSV_HEADER)
                    entries.forEach { entry ->
                        writer.appendLine(
                            listOf(
                                entry.title,
                                Instant.ofEpochMilli(entry.triggeredAtMillis).toString(),
                                entry.dismissedAtMillis
                                    ?.let { Instant.ofEpochMilli(it).toString() }
                                    .orEmpty(),
                                entry.method.orEmpty(),
                                entry.snoozedMinutes?.toString().orEmpty(),
                                (entry.signature != null).toString(),
                            ).joinToString(",") { it.csvSanitize().csvEscape() },
                        )
                    }
                }
            } ?: error("Cannot open $uri for writing")
            BackupResult.Success(entries.size)
        }.getOrElse {
            logger.e(TAG, "History CSV export failed", it)
            BackupResult.Failure
        }
    }

    // endregion

    private companion object {
        const val TAG = "BackupManager"
        const val CSV_HEADER = "title,description,priority,theme,enabled,recurrence"
        const val HISTORY_CSV_HEADER =
            "title,triggered_at,dismissed_at,method,snoozed_minutes,has_signature"
        const val MAX_PAYLOAD_BYTES = 50 * 1024 * 1024
    }
}

// region CSV / stream primitives

internal fun String.csvEscape(): String =
    if (contains(',') || contains('"') || contains('\n')) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

/**
 * Spreadsheet formula-injection guard (CWE-1236): a cell starting with
 * `=`, `+`, `-`, `@`, or a tab would execute as a formula when the export
 * is opened in Excel/Sheets. The standard mitigation prefixes it with an
 * apostrophe, which spreadsheets treat as "literal text".
 */
internal fun String.csvSanitize(): String =
    if (firstOrNull() in FORMULA_TRIGGERS) "'$this" else this

private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')

private operator fun CharArray.contains(char: Char?): Boolean =
    char != null && any { it == char }

/**
 * RFC-4180-style record parsing over the whole document: quoted fields may
 * contain commas AND newlines — line-by-line parsing broke round-tripping
 * the app's own export of multi-line notes.
 */
internal fun parseCsvRecords(text: String): List<List<String>> {
    val records = ArrayList<List<String>>()
    val fields = ArrayList<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0

    fun endField() {
        fields += current.toString()
        current.clear()
    }

    fun endRecord() {
        endField()
        records += ArrayList(fields)
        fields.clear()
    }

    while (index < text.length) {
        val char = text[index]
        when {
            inQuotes && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                current.append('"')
                index++
            }

            char == '"' -> inQuotes = !inQuotes

            char == ',' && !inQuotes -> endField()

            (char == '\n' || char == '\r') && !inQuotes -> {
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                endRecord()
            }

            else -> current.append(char)
        }
        index++
    }
    if (current.isNotEmpty() || fields.isNotEmpty()) endRecord()
    return records.filter { record -> record.any { it.isNotBlank() } }
}

/** Reads at most [maxBytes]; anything larger is a malformed/hostile payload. */
internal fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val read = read(chunk)
        if (read == -1) break
        total += read
        require(total <= maxBytes) { "Payload exceeds $maxBytes bytes" }
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

// endregion
