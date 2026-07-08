package com.alertnotes.data.repository

import com.alertnotes.data.dao.ReminderQueueDao
import com.alertnotes.data.entities.ReminderQueueEntity
import com.alertnotes.data.entities.toDomain
import com.alertnotes.domain.model.QueueEntryState
import com.alertnotes.domain.model.QueuedReminder
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.repository.ReminderQueueRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ReminderQueueRepositoryImpl @Inject constructor(
    private val queueDao: ReminderQueueDao,
) : ReminderQueueRepository {

    override fun observePending(): Flow<List<QueuedReminder>> =
        queueDao.observeByState(QueueEntryState.PENDING.name)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueue(reminder: Reminder, dueAt: Instant, enqueuedAt: Instant): Long =
        queueDao.insert(
            ReminderQueueEntity(
                reminderId = reminder.id,
                dueAtMillis = dueAt.toEpochMilli(),
                enqueuedAtMillis = enqueuedAt.toEpochMilli(),
                priorityRank = reminder.priority.rank,
                state = QueueEntryState.PENDING.name,
            ),
        )

    override suspend fun markConsumed(entryId: Long) {
        queueDao.setState(entryId, QueueEntryState.CONSUMED.name)
    }

    override suspend fun purgeConsumedBefore(cutoff: Instant) {
        queueDao.deleteByStateBefore(QueueEntryState.CONSUMED.name, cutoff.toEpochMilli())
    }
}
