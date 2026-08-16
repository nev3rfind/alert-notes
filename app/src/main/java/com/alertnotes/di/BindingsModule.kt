package com.alertnotes.di

import com.alertnotes.core.permissions.AndroidPermissionsManager
import com.alertnotes.core.permissions.PermissionsManager
import com.alertnotes.core.util.AndroidAppLogger
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.SystemTimeProvider
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.data.repository.AuthRepositoryImpl
import com.alertnotes.data.repository.CloudBackupRepositoryImpl
import com.alertnotes.data.repository.FriendRepositoryImpl
import com.alertnotes.data.repository.ReminderHistoryRepositoryImpl
import com.alertnotes.data.repository.ReminderSharingRepositoryImpl
import com.alertnotes.data.repository.ReminderQueueRepositoryImpl
import com.alertnotes.data.repository.ReminderRepositoryImpl
import com.alertnotes.data.repository.SettingsRepositoryImpl
import com.alertnotes.data.repository.UserProfileRepositoryImpl
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.CloudBackupRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.domain.repository.ReminderHistoryRepository
import com.alertnotes.domain.repository.ReminderQueueRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.repository.UserProfileRepository
import com.alertnotes.domain.scheduling.ReminderScheduler
import com.alertnotes.domain.scheduling.ScheduleEvents
import com.alertnotes.services.AlarmManagerReminderScheduler
import com.alertnotes.widgets.WidgetScheduleEvents
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Maps every interface the app consumes to its production implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun bindReminderQueueRepository(impl: ReminderQueueRepositoryImpl): ReminderQueueRepository

    @Binds
    abstract fun bindReminderHistoryRepository(
        impl: ReminderHistoryRepositoryImpl,
    ): ReminderHistoryRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindCloudBackupRepository(impl: CloudBackupRepositoryImpl): CloudBackupRepository

    @Binds
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    @Binds
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    @Binds
    abstract fun bindChatRepository(
        impl: com.alertnotes.data.repository.ChatRepositoryImpl,
    ): com.alertnotes.domain.repository.ChatRepository

    @Binds
    abstract fun bindReminderSharingRepository(
        impl: ReminderSharingRepositoryImpl,
    ): ReminderSharingRepository

    @Binds
    abstract fun bindNotificationCentreRepository(
        impl: com.alertnotes.data.repository.NotificationCentreRepositoryImpl,
    ): com.alertnotes.domain.repository.NotificationCentreRepository

    @Binds
    abstract fun bindTemplateRepository(
        impl: com.alertnotes.data.repository.TemplateRepositoryImpl,
    ): com.alertnotes.domain.repository.TemplateRepository

    @Binds
    abstract fun bindReminderScheduler(impl: AlarmManagerReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindPermissionsManager(impl: AndroidPermissionsManager): PermissionsManager

    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    abstract fun bindScheduleEvents(impl: WidgetScheduleEvents): ScheduleEvents

    @Binds
    abstract fun bindAppLogger(impl: AndroidAppLogger): AppLogger
}
