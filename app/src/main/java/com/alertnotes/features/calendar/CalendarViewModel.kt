package com.alertnotes.features.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderTheme
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.scheduling.OccurrenceProjector
import com.alertnotes.domain.scheduling.ReminderSchedulingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far the timeline looks ahead. */
private const val TIMELINE_DAYS = 30L

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.MONTH,
    /** The date the visible period is anchored on (month/week/day of it). */
    val anchor: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    /** Selected day for the day view and the tablet side panel. */
    val selectedDate: LocalDate = LocalDate.now(),
    /** Projected occurrences for the visible window, keyed by local date. */
    val occurrencesByDay: Map<LocalDate, List<CalendarOccurrence>> = emptyMap(),
    val query: String = "",
    val filters: Set<CalendarFilter> = emptySet(),
    val themeFilter: ReminderTheme? = null,
    val isLoading: Boolean = true,
) {
    fun occurrencesOn(date: LocalDate): List<CalendarOccurrence> =
        occurrencesByDay[date].orEmpty()
}

/** Immutable selection snapshot combined into projection. */
private data class CalendarSelection(
    val mode: CalendarMode,
    val anchor: LocalDate,
    val selectedDate: LocalDate,
    val query: String,
    val filters: Set<CalendarFilter>,
    val themeFilter: ReminderTheme?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    private val projector: OccurrenceProjector,
    private val coordinator: ReminderSchedulingCoordinator,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate get() = timeProvider.now().atZone(zone).toLocalDate()

    private val selection = MutableStateFlow(
        CalendarSelection(
            mode = CalendarMode.MONTH,
            anchor = LocalDate.now(),
            selectedDate = LocalDate.now(),
            query = "",
            filters = emptySet(),
            themeFilter = null,
        ),
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        reminderRepository.observeReminders(),
        selection,
    ) { reminders, selection -> reminders to selection }
        .mapLatest { (reminders, selection) -> project(reminders, selection) }
        .flowOn(Dispatchers.Default)
        .catch { throwable ->
            logger.e(TAG, "Failed to project calendar occurrences", throwable)
            emit(CalendarUiState(isLoading = false))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(),
        )

    // region Selection changes

    fun onModeChange(mode: CalendarMode) {
        selection.value = selection.value.copy(mode = mode)
    }

    fun onQueryChange(query: String) {
        selection.value = selection.value.copy(query = query)
    }

    fun onFilterToggle(filter: CalendarFilter) {
        val current = selection.value.filters
        selection.value = selection.value.copy(
            filters = if (filter in current) current - filter else current + filter,
        )
    }

    fun onThemeFilterChange(theme: ReminderTheme?) {
        selection.value = selection.value.copy(themeFilter = theme)
    }

    /** Steps the visible period backwards/forwards by one mode-unit. */
    fun onStep(forward: Boolean) {
        val current = selection.value
        val amount = if (forward) 1L else -1L
        val anchor = when (current.mode) {
            CalendarMode.MONTH -> current.anchor.plusMonths(amount)
            CalendarMode.WEEK -> current.anchor.plusWeeks(amount)
            CalendarMode.DAY -> current.anchor.plusDays(amount)
            CalendarMode.TIMELINE -> current.anchor
        }
        selection.value = current.copy(
            anchor = anchor,
            selectedDate = if (current.mode == CalendarMode.DAY) anchor else current.selectedDate,
        )
    }

    fun onGoToToday() {
        selection.value = selection.value.copy(anchor = today, selectedDate = today)
    }

    /** Selecting a day anchors the day view and the tablet side panel. */
    fun onSelectDate(date: LocalDate) {
        selection.value = selection.value.copy(selectedDate = date, anchor = date)
    }

    fun onOpenDay(date: LocalDate) {
        selection.value = selection.value.copy(
            mode = CalendarMode.DAY,
            anchor = date,
            selectedDate = date,
        )
    }

    // endregion

    // region Quick actions

    fun setEnabled(reminder: Reminder, isEnabled: Boolean) {
        launchSafely("toggle reminder ${reminder.id}") {
            coordinator.setEnabled(reminder.id, isEnabled)
        }
    }

    fun delete(reminder: Reminder) {
        launchSafely("delete reminder ${reminder.id}") {
            coordinator.delete(reminder.id)
        }
    }

    // endregion

    private fun project(
        reminders: List<Reminder>,
        selection: CalendarSelection,
    ): CalendarUiState {
        val today = today
        val weekStartField = WeekFields.of(Locale.getDefault()).dayOfWeek()
        val (startDate, endDateExclusive) = when (selection.mode) {
            CalendarMode.MONTH -> {
                val first = selection.anchor.withDayOfMonth(1)
                first to first.plusMonths(1)
            }

            CalendarMode.WEEK -> {
                val weekStart = selection.anchor.with(weekStartField, 1)
                weekStart to weekStart.plusDays(7)
            }

            CalendarMode.DAY -> selection.anchor to selection.anchor.plusDays(1)

            CalendarMode.TIMELINE -> today to today.plusDays(TIMELINE_DAYS)
        }
        // The tablet side panel always needs the selected day too.
        val windowStart = minOf(startDate, selection.selectedDate)
        val windowEnd = maxOf(endDateExclusive, selection.selectedDate.plusDays(1))

        val visibleReminders = reminders
            .filter { reminder -> selection.filters.all { it.matches(reminder) } }
            .filter { selection.themeFilter == null || it.theme == selection.themeFilter }
            .filter { it.matchesQuery(selection.query) }

        val fromInstant = windowStart.atStartOfDay(zone).toInstant()
        val untilInstant = windowEnd.atStartOfDay(zone).toInstant()
        val occurrences = visibleReminders.flatMap { reminder ->
            projector
                .occurrencesBetween(reminder, fromInstant, untilInstant)
                .map { reminder.toOccurrence(it, zone) }
        }

        return CalendarUiState(
            mode = selection.mode,
            anchor = selection.anchor,
            today = today,
            selectedDate = selection.selectedDate,
            occurrencesByDay = occurrences
                .groupBy { it.date }
                .mapValues { (_, dayOccurrences) -> dayOccurrences.sortedBy { it.at } },
            query = selection.query,
            filters = selection.filters,
            themeFilter = selection.themeFilter,
            isLoading = false,
        )
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
        const val TAG = "CalendarViewModel"
    }
}
