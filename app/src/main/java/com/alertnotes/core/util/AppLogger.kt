package com.alertnotes.core.util

import android.util.Log
import com.alertnotes.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable logging facade. Keeps `android.util.Log` out of business logic
 * so scheduling code stays unit-testable on the JVM.
 */
interface AppLogger {
    fun d(tag: String, message: String)

    /**
     * Info logs ship in RELEASE builds too — reserved for one-off lifecycle
     * milestones (e.g. Firebase initialization). Must never contain user
     * data; anything referencing reminders belongs in [d].
     */
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

@Singleton
class AndroidAppLogger @Inject constructor() : AppLogger {

    override fun d(tag: String, message: String) {
        // Debug logs carry user data (reminder ids, exact trigger times) —
        // they must never reach logcat in release builds, where any app
        // with a connected bug report could read the user's full schedule.
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
