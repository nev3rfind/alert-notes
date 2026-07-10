package com.alertnotes.data.repository

import com.alertnotes.data.dao.ReminderHistoryDao
import com.alertnotes.data.entities.ReminderHistoryEntity
import com.alertnotes.data.entities.toDomain
import com.alertnotes.data.entities.toJson
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.HistoryEntry
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.repository.ReminderHistoryRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val HISTORY_LIMIT = 500

@Singleton
class ReminderHistoryRepositoryImpl @Inject constructor(
    private val historyDao: ReminderHistoryDao,
) : ReminderHistoryRepository {

    // Up to 500 rows, each potentially decoding a signature vector from
    // JSON — must never run in the collector's (main) context.
    override fun observeHistory(): Flow<List<HistoryEntry>> =
        historyDao.observeRecent(HISTORY_LIMIT)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override suspend fun latestResolved(reminderId: Long): HistoryEntry? =
        historyDao.latestResolved(reminderId)?.toDomain()

    override fun observeLatest(): Flow<HistoryEntry?> =
        historyDao.observeRecent(1)
            .map { entities -> entities.firstOrNull()?.toDomain() }
            .flowOn(Dispatchers.Default)

    override suspend fun recordTriggered(reminder: Reminder, at: Instant) {
        if (!reminder.historyEnabled) return
        historyDao.insert(
            ReminderHistoryEntity(
                reminderId = reminder.id,
                title = reminder.title,
                triggeredAtMillis = at.toEpochMilli(),
                dismissedAtMillis = null,
                method = null,
                snoozedMinutes = null,
                signature = null,
            ),
        )
    }

    override suspend fun recordDismissed(
        reminder: Reminder,
        at: Instant,
        method: AcknowledgeMethod,
        signature: ReminderDrawing?,
    ) {
        if (!reminder.historyEnabled) return
        historyDao.resolveLatestOpen(
            reminderId = reminder.id,
            dismissedAtMillis = at.toEpochMilli(),
            method = method.name,
            snoozedMinutes = null,
            signature = signature?.takeUnless { it.isEmpty }?.toJson(),
        )
    }

    override suspend fun recordSnoozed(reminder: Reminder, at: Instant, snoozedFor: Duration) {
        if (!reminder.historyEnabled) return
        historyDao.resolveLatestOpen(
            reminderId = reminder.id,
            dismissedAtMillis = at.toEpochMilli(),
            method = AcknowledgeMethod.SNOOZE.name,
            snoozedMinutes = snoozedFor.toMinutes(),
            signature = null,
        )
    }
}
