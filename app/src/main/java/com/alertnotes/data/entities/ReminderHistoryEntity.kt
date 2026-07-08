package com.alertnotes.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.HistoryEntry
import java.time.Instant

@Entity(
    tableName = "reminder_history",
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
        Index("triggered_at"),
    ],
)
data class ReminderHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "reminder_id")
    val reminderId: Long,
    val title: String,
    @ColumnInfo(name = "triggered_at")
    val triggeredAtMillis: Long,
    @ColumnInfo(name = "dismissed_at")
    val dismissedAtMillis: Long?,
    val method: String?,
    @ColumnInfo(name = "snoozed_minutes")
    val snoozedMinutes: Long?,
    /** Signature vector JSON when acknowledged by signature. */
    val signature: String?,
)

fun ReminderHistoryEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    reminderId = reminderId,
    title = title,
    triggeredAt = Instant.ofEpochMilli(triggeredAtMillis),
    dismissedAt = dismissedAtMillis?.let(Instant::ofEpochMilli),
    method = method?.let { name -> AcknowledgeMethod.entries.firstOrNull { it.name == name } },
    snoozedMinutes = snoozedMinutes,
    signature = signature?.toReminderDrawingOrNull(),
)
