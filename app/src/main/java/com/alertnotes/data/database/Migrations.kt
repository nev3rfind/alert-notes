package com.alertnotes.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.TimeZone

/**
 * Version 1 → 2: expands the minimal Phase-1 reminder table to the full
 * scheduling model and introduces the reminder queue.
 *
 * The reminders table is rebuilt (copy–drop–rename) instead of ALTERed
 * because `notes` becomes `description` and RENAME COLUMN is unavailable on
 * the SQLite shipped with minSdk 26. Migrated rows get safe defaults and
 * `Recurrence.None` — they never fire until the user gives them a schedule.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `reminders_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `is_enabled` INTEGER NOT NULL,
                `is_archived` INTEGER NOT NULL,
                `priority` TEXT NOT NULL,
                `display_mode` TEXT NOT NULL,
                `floating_position` TEXT NOT NULL,
                `floating_size` TEXT NOT NULL,
                `auto_dismiss_seconds` INTEGER,
                `requires_acknowledgement` INTEGER NOT NULL,
                `dismiss_countdown_seconds` INTEGER,
                `requires_biometric` INTEGER NOT NULL,
                `snooze_enabled` INTEGER NOT NULL,
                `snooze_durations_minutes` TEXT NOT NULL,
                `history_enabled` INTEGER NOT NULL,
                `vibration_enabled` INTEGER NOT NULL,
                `sound_enabled` INTEGER NOT NULL,
                `wake_screen` INTEGER NOT NULL,
                `show_on_lock_screen` INTEGER NOT NULL,
                `overlay_preference` TEXT NOT NULL,
                `recurrence_type` TEXT NOT NULL,
                `recurrence_trigger_at` INTEGER,
                `recurrence_interval_minutes` INTEGER,
                `recurrence_time_of_day_minutes` INTEGER,
                `recurrence_day_of_month` INTEGER,
                `active_days` INTEGER NOT NULL,
                `active_hours_start_minutes` INTEGER,
                `active_hours_end_minutes` INTEGER,
                `start_date` TEXT,
                `end_date` TEXT,
                `time_zone` TEXT NOT NULL,
                `next_trigger_at` INTEGER,
                `last_triggered_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        // Time-zone ids contain no quotes, so direct interpolation is safe here.
        val zoneId = TimeZone.getDefault().id
        db.execSQL(
            """
            INSERT INTO `reminders_new` (
                id, title, description, type, is_enabled, is_archived, priority,
                display_mode, floating_position, floating_size,
                auto_dismiss_seconds, requires_acknowledgement, dismiss_countdown_seconds,
                requires_biometric, snooze_enabled, snooze_durations_minutes,
                history_enabled, vibration_enabled, sound_enabled, wake_screen,
                show_on_lock_screen, overlay_preference,
                recurrence_type, recurrence_trigger_at, recurrence_interval_minutes,
                recurrence_time_of_day_minutes, recurrence_day_of_month,
                active_days, active_hours_start_minutes, active_hours_end_minutes,
                start_date, end_date, time_zone,
                next_trigger_at, last_triggered_at, created_at, updated_at
            )
            SELECT
                id, title, notes, 'TEXT', is_enabled, 0, 'NORMAL',
                'FULL_SCREEN', 'CENTER', 'MEDIUM',
                NULL, 0, NULL,
                0, 1, '5,10,30',
                1, 1, 1, 1,
                1, 'AUTO',
                'NONE', NULL, NULL,
                NULL, NULL,
                127, NULL, NULL,
                NULL, NULL, '$zoneId',
                NULL, NULL, created_at, updated_at
            FROM `reminders`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `reminders`")
        db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_is_enabled` ON `reminders` (`is_enabled`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_next_trigger_at` ON `reminders` (`next_trigger_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_updated_at` ON `reminders` (`updated_at`)")

        db.execSQL(
            """
            CREATE TABLE `reminder_queue` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `reminder_id` INTEGER NOT NULL,
                `due_at` INTEGER NOT NULL,
                `enqueued_at` INTEGER NOT NULL,
                `priority_rank` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                FOREIGN KEY(`reminder_id`) REFERENCES `reminders`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_queue_reminder_id` ON `reminder_queue` (`reminder_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_queue_state` ON `reminder_queue` (`state`)")
    }
}

/**
 * Version 2 → 3: the boolean `requires_acknowledgement` becomes the richer
 * `acknowledgement` gesture enum (true → TAP, false → NONE), and biometric
 * security gains a device-PIN fallback flag (on by default).
 *
 * Rebuild again rather than ALTER: SQLite on minSdk 26 cannot drop or rename
 * columns, and Room's schema verification rejects leftover columns.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `reminders_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `is_enabled` INTEGER NOT NULL,
                `is_archived` INTEGER NOT NULL,
                `priority` TEXT NOT NULL,
                `display_mode` TEXT NOT NULL,
                `floating_position` TEXT NOT NULL,
                `floating_size` TEXT NOT NULL,
                `auto_dismiss_seconds` INTEGER,
                `acknowledgement` TEXT NOT NULL,
                `dismiss_countdown_seconds` INTEGER,
                `requires_biometric` INTEGER NOT NULL,
                `biometric_pin_fallback` INTEGER NOT NULL,
                `snooze_enabled` INTEGER NOT NULL,
                `snooze_durations_minutes` TEXT NOT NULL,
                `history_enabled` INTEGER NOT NULL,
                `vibration_enabled` INTEGER NOT NULL,
                `sound_enabled` INTEGER NOT NULL,
                `wake_screen` INTEGER NOT NULL,
                `show_on_lock_screen` INTEGER NOT NULL,
                `overlay_preference` TEXT NOT NULL,
                `recurrence_type` TEXT NOT NULL,
                `recurrence_trigger_at` INTEGER,
                `recurrence_interval_minutes` INTEGER,
                `recurrence_time_of_day_minutes` INTEGER,
                `recurrence_day_of_month` INTEGER,
                `active_days` INTEGER NOT NULL,
                `active_hours_start_minutes` INTEGER,
                `active_hours_end_minutes` INTEGER,
                `start_date` TEXT,
                `end_date` TEXT,
                `time_zone` TEXT NOT NULL,
                `next_trigger_at` INTEGER,
                `last_triggered_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO `reminders_new` (
                id, title, description, type, is_enabled, is_archived, priority,
                display_mode, floating_position, floating_size,
                auto_dismiss_seconds, acknowledgement, dismiss_countdown_seconds,
                requires_biometric, biometric_pin_fallback,
                snooze_enabled, snooze_durations_minutes,
                history_enabled, vibration_enabled, sound_enabled, wake_screen,
                show_on_lock_screen, overlay_preference,
                recurrence_type, recurrence_trigger_at, recurrence_interval_minutes,
                recurrence_time_of_day_minutes, recurrence_day_of_month,
                active_days, active_hours_start_minutes, active_hours_end_minutes,
                start_date, end_date, time_zone,
                next_trigger_at, last_triggered_at, created_at, updated_at
            )
            SELECT
                id, title, description, type, is_enabled, is_archived, priority,
                display_mode, floating_position, floating_size,
                auto_dismiss_seconds,
                CASE WHEN requires_acknowledgement = 1 THEN 'TAP' ELSE 'NONE' END,
                dismiss_countdown_seconds,
                requires_biometric, 1,
                snooze_enabled, snooze_durations_minutes,
                history_enabled, vibration_enabled, sound_enabled, wake_screen,
                show_on_lock_screen, overlay_preference,
                recurrence_type, recurrence_trigger_at, recurrence_interval_minutes,
                recurrence_time_of_day_minutes, recurrence_day_of_month,
                active_days, active_hours_start_minutes, active_hours_end_minutes,
                start_date, end_date, time_zone,
                next_trigger_at, last_triggered_at, created_at, updated_at
            FROM `reminders`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `reminders`")
        db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_is_enabled` ON `reminders` (`is_enabled`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_next_trigger_at` ON `reminders` (`next_trigger_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_updated_at` ON `reminders` (`updated_at`)")
    }
}

/**
 * Version 3 → 4: per-reminder themes, swipe-acknowledgement direction, and
 * hand drawings (vector JSON). Pure additions, so plain ALTERs suffice.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `theme` TEXT NOT NULL DEFAULT 'PRIMARY_ORANGE'")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `swipe_direction` TEXT NOT NULL DEFAULT 'UP'")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `drawing` TEXT")
    }
}

/** Version 4 → 5: drawing layout (size fraction and position). Additive. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `drawing_size` TEXT NOT NULL DEFAULT 'SIZE_60'")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `drawing_position` TEXT NOT NULL DEFAULT 'CENTER'")
    }
}

/** Version 5 → 6: reminder history. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `reminder_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `reminder_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `triggered_at` INTEGER NOT NULL,
                `dismissed_at` INTEGER,
                `method` TEXT,
                `snoozed_minutes` INTEGER,
                FOREIGN KEY(`reminder_id`) REFERENCES `reminders`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_history_reminder_id` ON `reminder_history` (`reminder_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_history_triggered_at` ON `reminder_history` (`triggered_at`)")
    }
}

/** Version 6 → 7: checklist reminders and stored signature vectors. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `checklist` TEXT")
        db.execSQL("ALTER TABLE `reminder_history` ADD COLUMN `signature` TEXT")
    }
}
