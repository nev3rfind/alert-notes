package com.alertnotes.features.alerts

import android.content.Context
import com.alertnotes.core.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fail-safe against alert crash loops. A marker naming the on-screen queue
 * entry is persisted while it renders and cleared the moment the alert
 * changes or is consumed. If the process dies mid-render, the marker
 * survives — so an entry seen [MAX_ATTEMPTS] times in quick succession
 * without ever being cleared is one that keeps killing the app, and the
 * presenter quarantines it (consumes the entry) instead of re-rendering it
 * forever. Reminder data itself is never touched.
 *
 * The attempt counter is deliberately **window-scoped**. `activeAlert` is an
 * eagerly-started flow, so every cold process start re-emits the still-pending
 * head entry and calls [onShown] again — with a plain counter, three unrelated
 * process starts over a week were enough to quarantine a perfectly healthy
 * reminder and silently drop the alert. A genuine crash loop restarts within
 * seconds, so only attempts inside [CRASH_LOOP_WINDOW_MILLIS] of each other
 * accumulate; anything slower resets the count to one.
 */
@Singleton
class AlertCrashGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** True when the entry has crashed the render loop too many times. */
    fun isQuarantined(entryId: Long): Boolean =
        prefs.getLong(KEY_ENTRY_ID, NONE) == entryId &&
            prefs.getInt(KEY_ATTEMPTS, 0) >= MAX_ATTEMPTS

    /** Records that this entry is about to render. */
    fun onShown(entryId: Long) {
        val now = timeProvider.now().toEpochMilli()
        val sameEntry = prefs.getLong(KEY_ENTRY_ID, NONE) == entryId
        val sinceLast = now - prefs.getLong(KEY_LAST_SHOWN_AT, 0L)
        val attempts = if (sameEntry && sinceLast <= CRASH_LOOP_WINDOW_MILLIS) {
            prefs.getInt(KEY_ATTEMPTS, 0) + 1
        } else {
            1
        }
        prefs.edit()
            .putLong(KEY_ENTRY_ID, entryId)
            .putInt(KEY_ATTEMPTS, attempts)
            .putLong(KEY_LAST_SHOWN_AT, now)
            .apply()
    }

    /** The visible alert changed gracefully; nothing is crash-looping. */
    fun onGone() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "alert_crash_guard"
        const val KEY_ENTRY_ID = "entry_id"
        const val KEY_ATTEMPTS = "attempts"
        const val KEY_LAST_SHOWN_AT = "last_shown_at"
        const val NONE = -1L

        /** Renders without a graceful clear before quarantine kicks in. */
        const val MAX_ATTEMPTS = 3

        /**
         * How close together those renders must be to count as a loop. A
         * process that dies while rendering is restarted by the alarm or by
         * the user within seconds; a minute is generous.
         */
        const val CRASH_LOOP_WINDOW_MILLIS = 60_000L
    }
}
