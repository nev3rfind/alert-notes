package com.alertnotes.data.repository

import com.alertnotes.core.util.TimeProvider
import com.alertnotes.data.dao.ReminderDao
import com.alertnotes.data.entities.toDomain
import com.alertnotes.data.entities.toEntity
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderStats
import com.alertnotes.domain.repository.ReminderRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val timeProvider: TimeProvider,
) : ReminderRepository {

    // Entity → domain mapping decodes drawing/checklist JSON per row; flowOn
    // keeps that off the main thread (the collector's context) on every DB
    // invalidation.
    override fun observeReminders(): Flow<List<Reminder>> =
        reminderDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun observeStats(dueHorizon: Instant): Flow<ReminderStats> =
        reminderDao.observeStats(dueHorizon.toEpochMilli()).map { projection ->
            ReminderStats(
                total = projection.total,
                enabled = projection.enabled,
                dueSoon = projection.dueSoon,
            )
        }

    override fun observeUpcoming(limit: Int): Flow<List<Reminder>> =
        reminderDao.observeUpcoming(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun observeRecentlyUpdated(limit: Int): Flow<List<Reminder>> =
        reminderDao.observeRecentlyUpdated(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getReminder(id: Long): Reminder? =
        reminderDao.getById(id)?.toDomain()

    override suspend fun getSchedulableReminders(): List<Reminder> =
        reminderDao.getSchedulable().map { it.toDomain() }

    override suspend fun save(reminder: Reminder): Long {
        val insertedId = reminderDao.upsert(reminder.toEntity())
        return if (insertedId == UPDATED_ROW) reminder.id else insertedId
    }

    override suspend fun setEnabled(id: Long, isEnabled: Boolean) {
        reminderDao.setEnabled(id, isEnabled, timeProvider.now().toEpochMilli())
    }

    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        reminderDao.setArchived(id, isArchived, timeProvider.now().toEpochMilli())
    }

    override suspend fun setNextTrigger(id: Long, nextTriggerAt: Instant?) {
        reminderDao.setNextTrigger(id, nextTriggerAt?.toEpochMilli())
    }

    override suspend fun markTriggered(id: Long, triggeredAt: Instant) {
        reminderDao.markTriggered(id, triggeredAt.toEpochMilli())
    }

    override suspend fun delete(id: Long) {
        reminderDao.deleteById(id)
    }

    override suspend fun getAllReminderIds(): List<Long> = reminderDao.getAllIds()

    override suspend fun deleteAll() {
        reminderDao.deleteAll()
    }

    private companion object {
        /** Room's @Upsert returns -1 when the row already existed. */
        const val UPDATED_ROW = -1L
    }
}
