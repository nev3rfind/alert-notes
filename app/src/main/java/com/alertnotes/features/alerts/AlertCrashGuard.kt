package com.alertnotes.features.alerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fail-safe against alert crash loops. A marker naming the on-screen queue
 * entry is persisted while it renders and cleared the moment the alert
 * changes or is consumed. If the process dies mid-render, the marker
 * survives — so an entry seen [MAX_ATTEMPTS] times without ever being
 * cleared is one that keeps killing the app, and the presenter quarantines
 * it (consumes the entry) instead of re-rendering it forever. Reminder data
 * itself is never touched.
 */
@Singleton
class AlertCrashGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
        val attempts = if (prefs.getLong(KEY_ENTRY_ID, NONE) == entryId) {
            prefs.getInt(KEY_ATTEMPTS, 0) + 1
        } else {
            1
        }
        prefs.edit()
            .putLong(KEY_ENTRY_ID, entryId)
            .putInt(KEY_ATTEMPTS, attempts)
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
        const val NONE = -1L

        /** Renders without a graceful clear before quarantine kicks in. */
        const val MAX_ATTEMPTS = 3
    }
}
