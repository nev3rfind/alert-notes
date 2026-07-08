package com.alertnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.alertnotes.data.entities.ReminderHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderHistoryDao {

    @Query("SELECT * FROM reminder_history ORDER BY triggered_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReminderHistoryEntity>>

    @Insert
    suspend fun insert(entity: ReminderHistoryEntity): Long

    /** Backup export only. */
    @Query("SELECT * FROM reminder_history ORDER BY triggered_at ASC")
    suspend fun getAllForBackup(): List<ReminderHistoryEntity>

    /** Resolves the newest still-open entry for the reminder. */
    @Query(
        """
        UPDATE reminder_history
        SET dismissed_at = :dismissedAtMillis,
            method = :method,
            snoozed_minutes = :snoozedMinutes,
            signature = :signature
        WHERE id = (
            SELECT id FROM reminder_history
            WHERE reminder_id = :reminderId AND dismissed_at IS NULL
            ORDER BY triggered_at DESC
            LIMIT 1
        )
        """,
    )
    suspend fun resolveLatestOpen(
        reminderId: Long,
        dismissedAtMillis: Long,
        method: String,
        snoozedMinutes: Long?,
        signature: String?,
    )
}
