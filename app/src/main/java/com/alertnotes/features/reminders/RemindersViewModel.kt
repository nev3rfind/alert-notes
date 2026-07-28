package com.alertnotes.features.reminders

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.repository.ReminderQueueRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import com.alertnotes.domain.model.AppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Loading → Error | Content; "empty" is Content with no reminders. */
sealed interface RemindersUiState {

    data object Loading : RemindersUiState

    data class Error(@param:StringRes val messageRes: Int) : RemindersUiState

    data class Content(
        /** Reminders after filters, search, and sorting. */
        val reminders: List<Reminder>,
        val query: String,
        val sort: ReminderSort,
        val activeFilters: Set<ReminderFilter>,
        /** True when the database itself is empty (vs. no matches). */
        val hasNoReminders: Boolean,
        /** Live status per reminder id, recomputed on every data change. */
        val statuses: Map<Long, ReminderDisplayStatus> = emptyMap(),
    ) : RemindersUiState {

        fun statusOf(reminder: Reminder): ReminderDisplayStatus =
            statuses[reminder.id] ?: ReminderDisplayStatus.EXPIRED
    }
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    queueRepository: ReminderQueueRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    private val settingsRepository: com.alertnotes.domain.repository.SettingsRepository,
    private val sharingRepository: com.alertnotes.domain.repository.ReminderSharingRepository,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(ReminderSort.NEXT_TRIGGER)
    private val filters = MutableStateFlow<Set<ReminderFilter>>(emptySet())

    val uiState: StateFlow<RemindersUiState> = combine(
        reminderRepository.observeReminders(),
        query,
        sort,
        filters,
        queueRepository.observePending(),
    ) { reminders, query, sort, filters, pending ->
        val runningId = pending.firstOrNull()?.reminderId
        val queuedIds = pending.drop(1).map { it.reminderId }.toSet()
        val now = timeProvider.now()
        val statuses = reminders.associate { reminder ->
            reminder.id to reminder.displayStatus(runningId, queuedIds, now)
        }
        val visible = reminders
            .filter { reminder ->
                val status = statuses.getValue(reminder.id)
                filters.all { it.matches(reminder, status) }
            }
            .filter { it.matchesQuery(query) }
            .sortedBy(sort)
        RemindersUiState.Content(
            reminders = visible,
            query = query,
            sort = sort,
            activeFilters = filters,
            hasNoReminders = reminders.isEmpty(),
            statuses = statuses,
        ) as RemindersUiState
    }
        .flowOn(Dispatchers.Default)
        .catch { throwable ->
            logger.e(TAG, "Failed to load reminders", throwable)
            emit(RemindersUiState.Error(R.string.reminders_load_error))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RemindersUiState.Loading,
        )

    /** Reminder awaiting a possible snackbar undo. */
    private val _recentlyDeleted = MutableStateFlow<Reminder?>(null)
    val recentlyDeleted: StateFlow<Reminder?> = _recentlyDeleted.asStateFlow()

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onSortChange(newSort: ReminderSort) {
        sort.value = newSort
    }

    fun onFilterToggle(filter: ReminderFilter) {
        filters.value = filters.value.let { current ->
            if (filter in current) current - filter else current + filter
        }
    }

    fun setEnabled(reminder: Reminder, isEnabled: Boolean) {
        launchSafely("toggle reminder ${reminder.id}") {
            coordinator.setEnabled(reminder.id, isEnabled)
        }
    }

    /** Copies everything except identity and scheduling state. */
    fun duplicate(reminder: Reminder, copyTitle: String) {
        val now = timeProvider.now()
        val copy = reminder.copy(
            id = Reminder.NEW_ID,
            title = copyTitle,
            nextTriggerAt = null,
            lastTriggeredAt = null,
            createdAt = now,
            updatedAt = now,
        )
        launchSafely("duplicate reminder ${reminder.id}") { coordinator.saveAndSchedule(copy) }
    }

    /**
     * Deletes immediately; the snackbar offers undo via [undoDelete].
     *
     * Online, deletion goes through the sharing repository so every live share
     * of this reminder is cancelled and its recipients are told - otherwise
     * they keep a scheduled copy of a reminder the owner thinks is gone.
     *
     * Undo is deliberately NOT offered for a reminder that had shares:
     * restoring the local row would not re-issue the cancelled shares, so the
     * owner would get back a reminder the recipients no longer have, with the
     * dashboard silently disagreeing with reality. Deleting a shared reminder
     * is a decision the user makes once.
     */
    fun delete(reminder: Reminder) {
        launchSafely("delete reminder ${reminder.id}") {
            val online = runCatching {
                settingsRepository.preferences.first().appMode == AppMode.ONLINE
            }.getOrDefault(false)
            val hadShares = if (online) {
                runCatching { sharingRepository.cancelSharesFor(reminder.id) }
                    .getOrElse {
                        logger.w(TAG, "Share cancellation failed for ${reminder.id}", it)
                        false
                    }
            } else {
                false
            }
            coordinator.delete(reminder.id)
            _recentlyDeleted.value = reminder.takeUnless { hadShares }
        }
    }

    fun undoDelete() {
        val reminder = _recentlyDeleted.value ?: return
        _recentlyDeleted.value = null
        launchSafely("restore reminder ${reminder.id}") { coordinator.restore(reminder) }
    }

    fun onUndoExpired() {
        _recentlyDeleted.value = null
    }

    private fun launchSafely(operation: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to $operation", throwable)
            }
        }
    }

    private companion object {
        const val TAG = "RemindersViewModel"
    }
}
