package com.alertnotes.services

import com.alertnotes.domain.model.AcknowledgeMethod
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-at-a-time proof-capture latch. While a camera or location proof
 * is being captured, the alert dispatcher must stand down: without this,
 * launching the camera drops the app to background and the dispatcher's
 * routing immediately re-fronts the alert (overlay or a fresh
 * AlertActivity), killing the capture after a second — the root cause of
 * the camera-acknowledgement bounce.
 *
 * A plain object rather than an injected type: it is process-global state
 * shared by the dispatcher, the capture activity, and the alert host
 * composables, with no dependencies of its own.
 */
object AcknowledgementSession {

    enum class Phase { IDLE, CAPTURING, UPLOADING }

    /** Outcome of one capture: confirmed proof or a cancellation. */
    data class Result(
        val reminderId: Long,
        val method: AcknowledgeMethod,
        val confirmed: Boolean,
    )

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 1)
    val results: SharedFlow<Result> = _results.asSharedFlow()

    @Volatile
    var activeReminderId: Long? = null
        private set

    /** Starts a capture; false when another workflow is already running. */
    fun begin(reminderId: Long): Boolean {
        if (_phase.value != Phase.IDLE) return false
        activeReminderId = reminderId
        _phase.value = Phase.CAPTURING
        return true
    }

    /** Proof confirmed by the user; the alert host completes the dismissal. */
    fun finish(reminderId: Long, method: AcknowledgeMethod) {
        _results.tryEmit(Result(reminderId, method, confirmed = true))
        activeReminderId = null
        _phase.value = Phase.IDLE
    }

    /** Capture abandoned — the alert returns exactly as it was. */
    fun cancel() {
        activeReminderId?.let { reminderId ->
            _results.tryEmit(Result(reminderId, AcknowledgeMethod.BUTTON, confirmed = false))
        }
        activeReminderId = null
        _phase.value = Phase.IDLE
    }
}
