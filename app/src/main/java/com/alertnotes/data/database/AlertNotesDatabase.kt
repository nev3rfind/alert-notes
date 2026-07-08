package com.alertnotes.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.alertnotes.data.dao.ReminderDao
import com.alertnotes.data.dao.ReminderHistoryDao
import com.alertnotes.data.dao.ReminderQueueDao
import com.alertnotes.data.entities.ReminderEntity
import com.alertnotes.data.entities.ReminderHistoryEntity
import com.alertnotes.data.entities.ReminderQueueEntity

/**
 * The app's single Room database. Schema history is exported to /app/schemas
 * and every version bump ships a tested migration (see Migrations.kt).
 */
@Database(
    entities = [
        ReminderEntity::class,
        ReminderQueueEntity::class,
        ReminderHistoryEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AlertNotesDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    abstract fun reminderQueueDao(): ReminderQueueDao

    abstract fun reminderHistoryDao(): ReminderHistoryDao

    companion object {
        const val NAME = "alert_notes.db"
    }
}
