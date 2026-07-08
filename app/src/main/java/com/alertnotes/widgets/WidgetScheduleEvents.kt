package com.alertnotes.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.alertnotes.core.util.AppLogger
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.scheduling.ScheduleEvents
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps home-screen widgets in sync with the scheduling engine: every
 * schedule change (save, toggle, delete, fire, snooze, reboot resweep)
 * triggers a widget refresh — event-driven, never polled.
 */
@Singleton
class WidgetScheduleEvents @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: AppLogger,
) : ScheduleEvents {

    override fun onScheduleChanged() {
        scope.launch {
            runCatching { AlertNotesWidget().updateAll(context) }
                .onFailure { logger.w(TAG, "Widget update failed", it) }
        }
    }

    private companion object {
        const val TAG = "WidgetScheduleEvents"
    }
}
