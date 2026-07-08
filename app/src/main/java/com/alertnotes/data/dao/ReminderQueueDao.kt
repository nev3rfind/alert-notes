package com.alertnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.alertnotes.data.entities.ReminderQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderQueueDao {

    @Query(
        """
        SELECT * FROM reminder_queue
        WHERE state = :state
        ORDER BY priority_rank DESC, due_at ASC
        """,
    )
    fun observeByState(state: String): Flow<List<ReminderQueueEntity>>

    @Insert
    suspend fun insert(entity: ReminderQueueEntity): Long

    @Query("UPDATE reminder_queue SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)

    /** Housekeeping: consumed rows have no further use after a retention window. */
    @Query("DELETE FROM reminder_queue WHERE state = :state AND enqueued_at < :beforeMillis")
    suspend fun deleteByStateBefore(state: String, beforeMillis: Long)
}
