package com.alertnotes.features.reminders.editor

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.ChecklistItem
import com.alertnotes.domain.model.Recurrence
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.domain.model.isRecurring
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Field-level validation results; null means the field is valid. */
data class EditorValidation(
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val scheduleError: Int? = null,
    @param:StringRes val snoozeError: Int? = null,
    @param:StringRes val activeDaysError: Int? = null,
    @param:StringRes val dateRangeError: Int? = null,
    @param:StringRes val checklistError: Int? = null,
) {
    val isValid: Boolean
        get() = titleError == null && scheduleError == null && snoozeError == null &&
            activeDaysError == null && dateRangeError == null && checklistError == null
}

/** The three scheduling entry points, in segmented-control order. */
enum class ScheduleMode(@param:StringRes val labelRes: Int) {
    /** A specific date and time. */
    TRIGGER_AT(R.string.schedule_mode_at),

    /** A duration from now ("in 10 minutes"), materialized at save time. */
    TRIGGER_IN(R.string.schedule_mode_in),

    /** Repeating schedules. */
    RECURRING(R.string.schedule_mode_recurring),
}

sealed interface EditorUiState {
    data object Loading : EditorUiState

    data class Editing(
        val draft: Reminder,
        val isNew: Boolean,
        val isDirty: Boolean,
        val validation: EditorValidation,
        val scheduleMode: ScheduleMode = ScheduleMode.TRIGGER_AT,
        /** Selected "trigger in" duration; only meaningful in that mode. */
        val triggerIn: Duration? = null,
    ) : EditorUiState
}

/** The recurrence choices offered by the schedule section, in display order. */
enum class RecurrenceKind(@param:StringRes val labelRes: Int) {
    ONE_TIME(R.string.recurrence_one_time),
    EVERY_MINUTES(R.string.recurrence_every_minutes),
    EVERY_HOURS(R.string.recurrence_every_hours),
    DAILY(R.string.recurrence_daily),
    WEEKLY(R.string.recurrence_weekly),
    MONTHLY(R.string.recurrence_monthly),
    CUSTOM_INTERVAL(R.string.recurrence_custom),
    NONE(R.string.recurrence_none),
}

val Recurrence.kind: RecurrenceKind
    get() = when (this) {
        is Recurrence.None -> RecurrenceKind.NONE
        is Recurrence.OneTime -> RecurrenceKind.ONE_TIME
        is Recurrence.EveryMinutes -> RecurrenceKind.EVERY_MINUTES
        is Recurrence.EveryHours -> RecurrenceKind.EVERY_HOURS
        is Recurrence.CustomInterval -> RecurrenceKind.CUSTOM_INTERVAL
        is Recurrence.Daily -> RecurrenceKind.DAILY
        is Recurrence.Weekly -> RecurrenceKind.WEEKLY
        is Recurrence.Monthly -> RecurrenceKind.MONTHLY
    }

/**
 * One editor session for one reminder (or a new one when the assisted id is
 * [Reminder.NEW_ID]). The same ViewModel powers the full-screen editor on
 * phones and the side pane on tablets — hosts key it by reminder id.
 */
@HiltViewModel(assistedFactory = ReminderEditorViewModel.Factory::class)
class ReminderEditorViewModel @AssistedInject constructor(
    @Assisted("reminderId") private val reminderId: Long,
    /** Epoch day to pre-fill a new reminder's one-time date; -1 = none. */
    @Assisted("initialEpochDay") private val initialEpochDay: Long,
    private val reminderRepository: ReminderRepository,
    private val coordinator: ReminderSchedulingCoordinator,
    private val settingsRepository: com.alertnotes.domain.repository.SettingsRepository,
    private val templateRepository: com.alertnotes.domain.repository.TemplateRepository,
    private val sharingRepository: com.alertnotes.domain.repository.ReminderSharingRepository,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("reminderId") reminderId: Long,
            @Assisted("initialEpochDay") initialEpochDay: Long,
        ): ReminderEditorViewModel
    }

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** Set once the edit session is complete; hosts close the editor. */
    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    /**
     * Id of a NEWLY created reminder saved in online mode — hosts that can
     * navigate offer the "who should receive this?" flow instead of just
     * closing. Never set by edits, deletes, or duplicates.
     */
    private val _savedForSharing = MutableStateFlow<Long?>(null)
    val savedForSharing: StateFlow<Long?> = _savedForSharing.asStateFlow()

    /** True while a save is in flight; disables Save so it cannot double-fire. */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** What the draft is compared against to decide dirtiness. */
    private var baseline: Reminder? = null

    init {
        viewModelScope.launch {
            val existing = if (reminderId == Reminder.NEW_ID) {
                null
            } else {
                reminderRepository.getReminder(reminderId)
            }
            val base = existing ?: newReminder()
            baseline = base
            _uiState.value = EditorUiState.Editing(
                draft = base,
                isNew = existing == null,
                isDirty = false,
                validation = validate(base),
                scheduleMode = if (base.recurrence.isRecurring) {
                    ScheduleMode.RECURRING
                } else {
                    ScheduleMode.TRIGGER_AT
                },
            )
        }
    }

    /** Applies a field change to the draft, revalidating and tracking dirtiness. */
    fun update(transform: (Reminder) -> Reminder) {
        val current = _uiState.value as? EditorUiState.Editing ?: return
        val draft = transform(current.draft)
        _uiState.value = current.copy(
            draft = draft,
            isDirty = draft != baseline,
            validation = validate(draft),
        )
    }

    /**
     * Switches the scheduling entry point, seeding a sensible schedule for
     * the new mode so the form is never in an invalid in-between state.
     */
    fun setScheduleMode(mode: ScheduleMode) {
        val current = _uiState.value as? EditorUiState.Editing ?: return
        if (current.scheduleMode == mode) return
        _uiState.value = current.copy(scheduleMode = mode, triggerIn = null)
        when (mode) {
            ScheduleMode.TRIGGER_AT -> update { draft ->
                if (draft.recurrence is Recurrence.OneTime) {
                    draft
                } else {
                    draft.copy(recurrence = Recurrence.OneTime(defaultOneTimeTrigger()))
                }
            }

            ScheduleMode.TRIGGER_IN -> setTriggerIn(DEFAULT_TRIGGER_IN)

            ScheduleMode.RECURRING -> update { draft ->
                if (draft.recurrence.isRecurring) {
                    draft
                } else {
                    draft.copy(recurrence = Recurrence.Daily(DEFAULT_TIME_OF_DAY))
                }
            }
        }
    }

    /**
     * "Trigger in" selection: the draft immediately reflects now + duration
     * (for preview and validation) and is re-anchored at save so the
     * countdown starts from the moment the user actually saves.
     */
    fun setTriggerIn(duration: Duration) {
        val current = _uiState.value as? EditorUiState.Editing ?: return
        _uiState.value = current.copy(triggerIn = duration)
        update { draft ->
            draft.copy(recurrence = Recurrence.OneTime(timeProvider.now().plus(duration)))
        }
    }

    /**
     * Switches the recurrence kind, carrying compatible parameters over
     * (time of day between day-based kinds, interval length between
     * interval kinds) so users don't lose input while exploring.
     */
    fun changeRecurrenceKind(kind: RecurrenceKind) {
        update { draft ->
            val previous = draft.recurrence
            val carriedTime = previous.timeOfDayOrNull() ?: DEFAULT_TIME_OF_DAY
            val carriedMinutes = previous.intervalMinutesOrNull()
            draft.copy(
                recurrence = when (kind) {
                    RecurrenceKind.NONE -> Recurrence.None
                    RecurrenceKind.ONE_TIME -> Recurrence.OneTime(defaultOneTimeTrigger())
                    RecurrenceKind.EVERY_MINUTES ->
                        Recurrence.EveryMinutes((carriedMinutes ?: 30L).coerceIn(1, MAX_MINUTES))

                    RecurrenceKind.EVERY_HOURS ->
                        Recurrence.EveryHours(((carriedMinutes ?: 60L) / 60L).coerceIn(1, MAX_HOURS))

                    RecurrenceKind.DAILY -> Recurrence.Daily(carriedTime)
                    RecurrenceKind.WEEKLY -> Recurrence.Weekly(carriedTime)
                    RecurrenceKind.MONTHLY -> Recurrence.Monthly(DEFAULT_DAY_OF_MONTH, carriedTime)
                    RecurrenceKind.CUSTOM_INTERVAL ->
                        Recurrence.CustomInterval(Duration.ofMinutes(carriedMinutes ?: 90L))
                },
            )
        }
    }

    fun save() {
        val state = _uiState.value as? EditorUiState.Editing ?: return
        if (!state.validation.isValid) return
        // Re-entry guard. A new reminder carries id 0 (Room's "not yet
        // inserted"), and the coordinator's mutex serialises saves without
        // deduplicating them — so a double-tap on Save inserted two rows and
        // scheduled two alarms for the same reminder. None of the button's
        // enabled predicates change when a save begins, so the guard has to
        // live here.
        if (_isSaving.value) return
        _isSaving.value = true
        val now = timeProvider.now()
        val triggerIn = state.triggerIn
        val reminder = state.draft.copy(
            title = state.draft.title.trim(),
            description = state.draft.description.trim(),
            // Blank rows are editing leftovers; other types carry no checklist
            // so stale items can never gate their alerts.
            checklist = if (state.draft.type == ReminderType.CHECKLIST) {
                state.draft.checklist
                    .map { ChecklistItem(it.text.trim()) }
                    .filter { it.text.isNotEmpty() }
            } else {
                emptyList()
            },
            // Re-anchor "trigger in" at the actual save moment.
            recurrence = if (state.scheduleMode == ScheduleMode.TRIGGER_IN && triggerIn != null) {
                Recurrence.OneTime(now.plus(triggerIn))
            } else {
                state.draft.recurrence
            },
            updatedAt = now,
        )
        viewModelScope.launch {
            try {
                val savedId = coordinator.saveAndSchedule(reminder)
                // Brand-new reminders in online mode flow into the sharing
                // chooser; edits close as before.
                val online = runCatching {
                    settingsRepository.preferences.first().appMode ==
                        com.alertnotes.domain.model.AppMode.ONLINE
                }.getOrDefault(false)
                if (state.isNew && online) {
                    _savedForSharing.value = savedId
                }
                _isFinished.value = true
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to save reminder ${reminder.id}", throwable)
            } finally {
                // Cleared even on success: the screen is finishing, but a
                // failed save must leave Save usable again.
                _isSaving.value = false
            }
        }
    }

    fun delete() {
        val state = _uiState.value as? EditorUiState.Editing ?: return
        if (state.isNew) return
        viewModelScope.launch {
            try {
                // Deleting a reminder that was shared has to cancel its shares
                // too, otherwise the recipients keep a scheduled copy of a
                // reminder the owner believes is gone - and the owner's
                // dashboard keeps tracking a reminder that no longer exists.
                // deleteOwnedReminder cancels every live share, tells the
                // recipients, and then deletes the local reminder.
                deleteWithShares(state.draft.id)
                _isFinished.value = true
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to delete reminder ${state.draft.id}", throwable)
            }
        }
    }

    /** Online: cancel shares then delete. Offline: there are no shares. */
    private suspend fun deleteWithShares(reminderId: Long) {
        val online = runCatching {
            settingsRepository.preferences.first().appMode ==
                com.alertnotes.domain.model.AppMode.ONLINE
        }.getOrDefault(false)
        if (online) {
            runCatching { sharingRepository.deleteOwnedReminder(reminderId) }
                .getOrElse {
                    logger.w(TAG, "Share cancellation failed; deleting locally anyway", it)
                    coordinator.delete(reminderId)
                }
        } else {
            coordinator.delete(reminderId)
        }
    }

    /** Captures the draft's behaviour (never its schedule) as a template. */
    fun saveAsTemplate() {
        val state = _uiState.value as? EditorUiState.Editing ?: return
        viewModelScope.launch {
            runCatching {
                templateRepository.saveCustom(
                    com.alertnotes.domain.model.ReminderTemplate(
                        id = java.util.UUID.randomUUID().toString(),
                        name = state.draft.title.trim().ifBlank { DEFAULT_TEMPLATE_NAME },
                        title = state.draft.title.trim(),
                        description = state.draft.description.trim(),
                        priority = state.draft.priority,
                        type = state.draft.type,
                        acknowledgement = state.draft.acknowledgement,
                        theme = state.draft.theme,
                    ),
                )
            }.onFailure { logger.e(TAG, "Save as template failed", it) }
        }
    }

    /** Saves an independent copy of the current draft under [copyTitle]. */
    fun duplicate(copyTitle: String) {
        val state = _uiState.value as? EditorUiState.Editing ?: return
        if (!state.validation.isValid) return
        val now = timeProvider.now()
        val copy = state.draft.copy(
            id = Reminder.NEW_ID,
            title = copyTitle,
            nextTriggerAt = null,
            lastTriggeredAt = null,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            try {
                coordinator.saveAndSchedule(copy)
                _isFinished.value = true
            } catch (throwable: Throwable) {
                logger.e(TAG, "Failed to duplicate reminder ${state.draft.id}", throwable)
            }
        }
    }

    private fun validate(draft: Reminder): EditorValidation = EditorValidation(
        titleError = if (draft.title.isBlank()) R.string.editor_error_title_required else null,
        scheduleError = validateSchedule(draft),
        snoozeError = if (draft.snoozeEnabled && draft.allowedSnoozeDurations.isEmpty()) {
            R.string.editor_error_snooze_durations
        } else {
            null
        },
        activeDaysError = if (draft.recurrence.isRecurring && draft.activeDays.isEmpty()) {
            R.string.editor_error_active_days
        } else {
            null
        },
        dateRangeError = run {
            val start = draft.startDate
            val end = draft.endDate
            if (start != null && end != null && end < start) {
                R.string.editor_error_date_range
            } else {
                null
            }
        },
        checklistError = if (
            draft.type == ReminderType.CHECKLIST &&
            draft.checklist.none { it.text.isNotBlank() }
        ) {
            R.string.editor_error_checklist_items
        } else {
            null
        },
    )

    @StringRes
    private fun validateSchedule(draft: Reminder): Int? {
        val recurrence = draft.recurrence
        // Only reject past one-time triggers the user set in this session —
        // an already-fired reminder must stay editable (title, toggles, …).
        val scheduleChanged = recurrence != baseline?.recurrence
        return when {
            recurrence is Recurrence.OneTime && scheduleChanged &&
                recurrence.triggerAt <= timeProvider.now() ->
                R.string.editor_error_one_time_past

            recurrence is Recurrence.EveryMinutes && recurrence.minutes !in 1..MAX_MINUTES ->
                R.string.editor_error_interval_invalid

            recurrence is Recurrence.EveryHours && recurrence.hours !in 1..MAX_HOURS ->
                R.string.editor_error_interval_invalid

            recurrence is Recurrence.CustomInterval &&
                recurrence.interval.toMinutes() !in 1..MAX_MINUTES ->
                R.string.editor_error_interval_invalid

            recurrence.timeOfDayOrNull()?.let { time ->
                draft.activeHours?.let { time !in it } == true
            } == true ->
                R.string.editor_error_time_outside_hours

            else -> null
        }
    }

    /**
     * New reminders default to a one-time occurrence: at 09:00 on the
     * calendar-provided date when there is one (falling back to an hour from
     * now if that moment is already past), else an hour from now.
     */
    private fun newReminder(): Reminder {
        val now = timeProvider.now()
        return Reminder(
            title = "",
            recurrence = Recurrence.OneTime(defaultOneTimeTrigger()),
            timeZone = ZoneId.systemDefault(),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun defaultOneTimeTrigger(): Instant {
        val inAnHour = timeProvider.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES)
        if (initialEpochDay < 0) return inAnHour
        val requested = LocalDate.ofEpochDay(initialEpochDay)
            .atTime(DEFAULT_NEW_REMINDER_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        return if (requested > timeProvider.now()) requested else inAnHour
    }

    private companion object {
        const val TAG = "ReminderEditor"
        const val DEFAULT_TEMPLATE_NAME = "My template"
        const val MAX_MINUTES = 10_080L // one week
        const val MAX_HOURS = 720L // thirty days
        const val DEFAULT_DAY_OF_MONTH = 1
        val DEFAULT_TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_NEW_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_TRIGGER_IN: Duration = Duration.ofMinutes(10)
    }
}

private fun Recurrence.timeOfDayOrNull(): LocalTime? = when (this) {
    is Recurrence.Daily -> timeOfDay
    is Recurrence.Weekly -> timeOfDay
    is Recurrence.Monthly -> timeOfDay
    else -> null
}

private fun Recurrence.intervalMinutesOrNull(): Long? = when (this) {
    is Recurrence.EveryMinutes -> minutes
    is Recurrence.EveryHours -> hours * 60L
    is Recurrence.CustomInterval -> interval.toMinutes()
    else -> null
}
