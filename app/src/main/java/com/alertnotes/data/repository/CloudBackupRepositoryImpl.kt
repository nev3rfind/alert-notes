package com.alertnotes.data.repository

import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.data.backup.BackupFile
import com.alertnotes.data.backup.toBackup
import com.alertnotes.data.dao.ReminderDao
import com.alertnotes.data.remote.FirestoreSchema
import com.alertnotes.domain.repository.CloudBackupRepository
import com.alertnotes.domain.repository.CloudUploadResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Uploads every local reminder to `users/{uid}/reminders/{localId}`. Each
 * document carries the reminder as the same versioned JSON payload the ZIP
 * backup uses, so both backup paths evolve together and the cloud copy
 * degrades exactly like a local import would. Re-running overwrites by local
 * id — uploading twice never duplicates.
 */
@Singleton
class CloudBackupRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val reminderDao: ReminderDao,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) : CloudBackupRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun uploadAllReminders(): CloudUploadResult {
        val uid = auth.currentUser?.uid ?: return CloudUploadResult.Failure
        return runCatching {
            val reminders = reminderDao.getAllForBackup()
            val uploadedAt = timeProvider.now().toEpochMilli()
            val userDocument = firestore.collection(FirestoreSchema.USERS).document(uid)
            val collection = userDocument.collection(FirestoreSchema.REMINDERS)
            // Firestore caps a WriteBatch at 500 operations.
            reminders.chunked(MAX_BATCH_SIZE).forEach { chunk ->
                firestore.runBatch { batch ->
                    chunk.forEach { entity ->
                        val document = mapOf(
                            "formatVersion" to BackupFile.FORMAT_VERSION,
                            "payload" to json.encodeToString(entity.toBackup()),
                            "uploadedAtEpochMillis" to uploadedAt,
                        )
                        batch.set(collection.document(entity.id.toString()), document)
                    }
                }.await()
            }
            val summary = mapOf(
                "reminderUploadCount" to reminders.size,
                "reminderUploadedAt" to FieldValue.serverTimestamp(),
            )
            // Application bookkeeping belongs in the metadata section, not
            // on the anchor document.
            userDocument.collection(FirestoreSchema.SECTION_METADATA)
                .document(FirestoreSchema.SECTION_DOC)
                .set(summary, SetOptions.merge())
                .await()
            logger.i(TAG, "Uploaded ${reminders.size} reminders to cloud backup")
            CloudUploadResult.Success(reminders.size)
        }.getOrElse {
            logger.e(TAG, "Cloud reminder upload failed", it)
            CloudUploadResult.Failure
        }
    }

    private companion object {
        const val TAG = "CloudBackupRepository"
        const val MAX_BATCH_SIZE = 450
    }
}
