package com.alertnotes.features.alerts

import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.di.ApplicationScope
import com.alertnotes.domain.model.AcknowledgeMethod
import com.alertnotes.domain.model.QueuedReminder
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderDrawing
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.repository.ReminderHistoryRepository
import com.alertnotes.domain.repository.ReminderQueueRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A queue entry joined with its reminder, currently on screen. */
data class ActiveAlert(
    val entryId: Long,
    val reminder: Reminder,
    /** When presentation started — the auto-dismiss deadline anchors here. */
    val shownAt: Instant,
)

/**
 * The presentation engine's brain, **application-scoped** so every surface —
 * the in-app host, the lock-screen [AlertActivity], the overlay window, and
 * notifications — shares one source of truth:
 *
 * - one alert at a time, highest priority first, then earliest due;
 * - the visible alert stays up while it remains pending — except that a
 *   CRITICAL arrival may replace a lower-priority alert (user setting);
 *   the replaced entry stays pending and reappears afterwards;
 * - dismissing (or snoozing) consumes the entry and records history; the
 *   next pending entry appears immediately. Nothing is ever dropped.
 *
 * Scheduling never touches this class; presentation never schedules — the
 * queue table is the only bridge between the two engines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AlertPresenter @Inject constructor(
    private val queueRepository: ReminderQueueRepository,
    private val reminderRepository: ReminderRepository,
    private val historyRepository: ReminderHistoryRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val crashGuard: AlertCrashGuard,
    private val logger: AppLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    /** Entry currently on screen; feeds back into selection for stability. */
    private val visibleEntryId = MutableStateFlow<Long?>(null)

    /**
     * Entries already resolved this process — makes dismiss/snooze idempotent
     * so an auto-dismiss racing a manual dismiss (or a double-tap) cannot
     * resolve a second, newer history entry for the same reminder.
     */
    private val resolvedEntryIds: MutableSet<Long> =
        Collections.newSetFromMap(ConcurrentHashMap())

    val activeAlert: StateFlow<ActiveAlert?> = combine(
        queueRepository.observePending(),
        settingsRepository.preferences,
        visibleEntryId,
    ) { pending, preferences, visibleId ->
        chooseEntry(pending, visibleId, preferences.criticalInterruptsEnabled)
    }
        .distinctUntilChangedBy { it?.id }
        .mapLatest { entry -> entry?.let { loadAlert(it) } }
        .onEach { alert ->
            visibleEntryId.value = alert?.entryId
            // Crash-loop bookkeeping: mark the render attempt; any graceful
            // change of the visible alert clears the marker.
            if (alert != null) crashGuard.onShown(alert.entryId) else crashGuard.onGone()
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    init {
        // Auto-dismiss: one accurate timer per visible alert, cancelled the
        // moment the alert changes.
        scope.launch {
            activeAlert.collectLatest { alert ->
                val autoDismissAfter = alert?.reminder?.autoDismissAfter ?: return@collectLatest
                delay(autoDismissAfter.toMillis())
                logger.d(TAG, "Auto-dismissing alert ${alert.entryId}")
                dismiss(alert, AcknowledgeMethod.AUTO)
            }
        }
        // Proof completion runs HERE, at process level. The alert UI is
        // deliberately suspended (renders nothing) while a camera/location
        // proof is captured — so a collector inside the composition would
        // never see the confirmation. That was the camera confirm loop:
        // the result was emitted while no UI collector existed, evaporated
        // (no replay), and the untouched alert simply reappeared.
        scope.launch {
            com.alertnotes.services.AcknowledgementSession.results.collect { result ->
                if (!result.confirmed) return@collect
                val alert = activeAlert.value ?: return@collect
                if (alert.reminder.id == result.reminderId) {
                    logger.d(TAG, "Proof confirmed (${result.method}) — dismissing ${alert.entryId}")
                    dismiss(alert, result.method)
                }
            }
        }
    }

    /**
     * Marks the alert consumed; the queue flow then surfaces the next one.
     * Returns the launched job so broadcast callers can await completion
     * before their process is allowed to die. Idempotent per entry.
     */
    fun dismiss(
        alert: ActiveAlert,
        method: AcknowledgeMethod,
        signature: ReminderDrawing? = null,
    ): Job {
        if (!resolvedEntryIds.add(alert.entryId)) return completedJob()
        return scope.launch {
            try {
                queueRepository.markConsumed(alert.entryId)
                historyRepository.recordDismissed(
                    reminder = alert.reminder,
                    at = timeProvider.now(),
                    method = method,
                    signature = signature,
                )
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to dismiss alert ${alert.entryId}", throwable)
            } finally {
                pruneResolved()
            }
        }
    }

    /** Consumes the alert and postpones the reminder by [duration]. */
    fun snooze(alert: ActiveAlert, duration: Duration): Job {
        if (!resolvedEntryIds.add(alert.entryId)) return completedJob()
        return scope.launch {
            try {
                queueRepository.markConsumed(alert.entryId)
                historyRepository.recordSnoozed(alert.reminder, timeProvider.now(), duration)
                coordinator.snooze(alert.reminder.id, duration)
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to snooze alert ${alert.entryId}", throwable)
            } finally {
                pruneResolved()
            }
        }
    }

    /** Dismisses by queue entry id (used by notification actions). */
    fun dismissEntry(entryId: Long, method: AcknowledgeMethod): Job {
        val alert = activeAlert.value?.takeIf { it.entryId == entryId }
        if (alert != null) {
            return dismiss(alert, method)
        }
        if (!resolvedEntryIds.add(entryId)) return completedJob()
        return scope.launch {
            try {
                queueRepository.markConsumed(entryId)
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to consume entry $entryId", throwable)
            } finally {
                pruneResolved()
            }
        }
    }

    private fun completedJob(): Job = Job().apply { complete() }

    /** Keeps the idempotence set bounded; ids are never re-presented anyway. */
    private fun pruneResolved() {
        if (resolvedEntryIds.size > RESOLVED_CACHE_LIMIT) resolvedEntryIds.clear()
    }

    private fun chooseEntry(
        pending: List<QueuedReminder>,
        visibleId: Long?,
        criticalInterruptsEnabled: Boolean,
    ): QueuedReminder? {
        val head = pending.firstOrNull() ?: return null
        val visible = pending.firstOrNull { it.id == visibleId } ?: return head
        val headIsCriticalUpgrade = head.priority == ReminderPriority.CRITICAL &&
            visible.priority != ReminderPriority.CRITICAL
        return if (criticalInterruptsEnabled && headIsCriticalUpgrade) head else visible
    }

    private suspend fun loadAlert(entry: QueuedReminder): ActiveAlert? {
        // Fail-safe: an entry that repeatedly killed the process mid-render
        // is consumed instead of re-rendered forever. Reminder data stays.
        if (crashGuard.isQuarantined(entry.id)) {
            logger.w(TAG, "Queue entry ${entry.id} crash-looped — consuming (fail-safe)")
            consumeQuietly(entry.id)
            crashGuard.onGone()
            return null
        }
        val reminder = try {
            reminderRepository.getReminder(entry.reminderId)
        } catch (throwable: Throwable) {
            // Fail-safe: an unreadable reminder must never soft-lock alerts.
            logger.e(TAG, "Queue entry ${entry.id} failed to load — consuming", throwable)
            consumeQuietly(entry.id)
            return null
        }
        if (reminder == null) {
            // Orphan guard: the reminder vanished between enqueue and display.
            logger.w(TAG, "Queue entry ${entry.id} has no reminder — consuming")
            consumeQuietly(entry.id)
            return null
        }
        return ActiveAlert(
            entryId = entry.id,
            reminder = reminder,
            shownAt = timeProvider.now(),
        )
    }

    private suspend fun consumeQuietly(entryId: Long) {
        try {
            queueRepository.markConsumed(entryId)
        } catch (throwable: Throwable) {
            logger.e(TAG, "Failed to consume entry $entryId", throwable)
        }
    }

    private companion object {
        const val TAG = "AlertPresenter"
        const val RESOLVED_CACHE_LIMIT = 200
    }
}
