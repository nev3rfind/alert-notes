package com.alertnotes.di

import android.content.Context
import androidx.room.Room
import com.alertnotes.data.dao.ReminderDao
import com.alertnotes.data.dao.ReminderHistoryDao
import com.alertnotes.data.dao.ReminderQueueDao
import com.alertnotes.data.database.AlertNotesDatabase
import com.alertnotes.data.database.MIGRATION_1_2
import com.alertnotes.data.database.MIGRATION_2_3
import com.alertnotes.data.database.MIGRATION_3_4
import com.alertnotes.data.database.MIGRATION_4_5
import com.alertnotes.data.database.MIGRATION_5_6
import com.alertnotes.data.database.MIGRATION_6_7
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AlertNotesDatabase =
        Room.databaseBuilder(context, AlertNotesDatabase::class.java, AlertNotesDatabase.NAME)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
            .build()

    @Provides
    fun provideReminderDao(database: AlertNotesDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideReminderQueueDao(database: AlertNotesDatabase): ReminderQueueDao =
        database.reminderQueueDao()

    @Provides
    fun provideReminderHistoryDao(database: AlertNotesDatabase): ReminderHistoryDao =
        database.reminderHistoryDao()
}
