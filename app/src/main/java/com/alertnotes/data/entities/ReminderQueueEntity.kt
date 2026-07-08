package com.alertnotes.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alertnotes.domain.model.QueueEntryState
import com.alertnotes.domain.model.QueuedReminder
import com.alertnotes.domain.model.ReminderPriority
import java.time.Instant

/**
 * A due occurrence waiting for the popup engine. The CASCADE foreign key
 * guarantees no orphan entries survive a reminder deletion.
 */
@Entity(
    tableName = "reminder_queue",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("reminder_id"),
        Index("state"),
    ],
)
data class ReminderQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "reminder_id")
    val reminderId: Long,
    @ColumnInfo(name = "due_at")
    val dueAtMillis: Long,
    @ColumnInfo(name = "enqueued_at")
    val enqueuedAtMillis: Long,
    /** Snapshot of [ReminderPriority.rank] for cheap ORDER BY. */
    @ColumnInfo(name = "priority_rank")
    val priorityRank: Int,
    val state: String,
)

fun ReminderQueueEntity.toDomain(): QueuedReminder = QueuedReminder(
    id = id,
    reminderId = reminderId,
    priority = ReminderPriority.entries.firstOrNull { it.rank == priorityRank }
        ?: ReminderPriority.NORMAL,
    dueAt = Instant.ofEpochMilli(dueAtMillis),
    enqueuedAt = Instant.ofEpochMilli(enqueuedAtMillis),
    state = QueueEntryState.entries.firstOrNull { it.name == state }
        ?: QueueEntryState.CONSUMED,
)
