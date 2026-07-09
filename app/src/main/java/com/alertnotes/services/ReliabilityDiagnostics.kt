package com.alertnotes.services

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory trace of the reminder pipeline — scheduling, alarm delivery,
 * receiver execution, routing decisions, notification posting, and activity
 * launches. Bounded ring buffer, newest first, surfaced live on the
 * Reminder Reliability screen so delivery problems can be diagnosed on the
 * device without Logcat. Deliberately never contains reminder content —
 * only ids, stages, and outcomes.
 */
@Singleton
class ReliabilityDiagnostics @Inject constructor() {

    /** One pipeline event. [atMillis] is wall-clock epoch millis. */
    data class Entry(
        val atMillis: Long,
        val stage: String,
        val message: String,
    )

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    /** Thread-safe: called from receivers, listeners, and main-thread routing. */
    fun log(stage: String, message: String) {
        synchronized(lock) {
            buffer.addFirst(
                Entry(atMillis = System.currentTimeMillis(), stage = stage, message = message),
            )
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeLast()
            }
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    /** Stage names shared by every instrumented component. */
    companion object {
        const val STAGE_SCHEDULED = "Scheduled"
        const val STAGE_RECEIVED = "Alarm received"
        const val STAGE_WAKE_LOCK = "Wake lock"
        const val STAGE_PROCESSED = "Receiver executed"
        const val STAGE_ROUTED = "Routed"
        const val STAGE_NOTIFIED = "Notification"
        const val STAGE_FULL_SCREEN = "Full screen"
        const val STAGE_OVERLAY = "Overlay"
        const val STAGE_PERMISSION = "Permission"

        private const val MAX_ENTRIES = 200
    }
}
