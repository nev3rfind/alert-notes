package com.alertnotes.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alertnotes.data.entities.ReminderEntity
import kotlinx.coroutines.flow.Flow

/** Aggregate projection for the dashboard — one query instead of three. */
data class ReminderStatsProjection(
    val total: Int,
    val enabled: Int,
    val dueSoon: Int,
)

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(is_enabled), 0) AS enabled,
               COALESCE(SUM(
                   CASE WHEN is_enabled = 1
                             AND next_trigger_at IS NOT NULL
                             AND next_trigger_at <= :dueHorizonMillis
                        THEN 1 ELSE 0 END
               ), 0) AS dueSoon
        FROM reminders
        WHERE is_archived = 0
        """,
    )
    fun observeStats(dueHorizonMillis: Long): Flow<ReminderStatsProjection>

    @Query(
        """
        SELECT * FROM reminders
        WHERE is_archived = 0 AND is_enabled = 1 AND next_trigger_at IS NOT NULL
        ORDER BY next_trigger_at ASC
        LIMIT :limit
        """,
    )
    fun observeUpcoming(limit: Int): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE is_archived = 0 ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecentlyUpdated(limit: Int): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE is_enabled = 1 AND is_archived = 0")
    suspend fun getSchedulable(): List<ReminderEntity>

    /** Everything, archived included — backup export only. */
    @Query("SELECT * FROM reminders")
    suspend fun getAllForBackup(): List<ReminderEntity>

    /** Returns the new row id on insert, -1 on update. */
    @Upsert
    suspend fun upsert(entity: ReminderEntity): Long

    @Query("UPDATE reminders SET is_enabled = :isEnabled, updated_at = :updatedAtMillis WHERE id = :id")
    suspend fun setEnabled(id: Long, isEnabled: Boolean, updatedAtMillis: Long)

    @Query("UPDATE reminders SET is_archived = :isArchived, updated_at = :updatedAtMillis WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAtMillis: Long)

    /** Scheduling bookkeeping — deliberately does not bump updated_at. */
    @Query("UPDATE reminders SET next_trigger_at = :nextTriggerAtMillis WHERE id = :id")
    suspend fun setNextTrigger(id: Long, nextTriggerAtMillis: Long?)

    @Query("UPDATE reminders SET last_triggered_at = :triggeredAtMillis WHERE id = :id")
    suspend fun markTriggered(id: Long, triggeredAtMillis: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM reminders")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
