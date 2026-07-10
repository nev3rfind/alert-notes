package com.alertnotes.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.core.util.AppLogger
import com.alertnotes.core.util.TimeProvider
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.ReminderShareWithProfile
import com.alertnotes.domain.model.ReminderStats
import com.alertnotes.domain.model.ShareStatus
import com.alertnotes.domain.repository.ReminderQueueRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.SettingsRepository
import com.alertnotes.domain.scheduling.OccurrenceProjector
import com.alertnotes.features.calendar.CalendarOccurrence
import com.alertnotes.features.calendar.toOccurrence
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** One row of the dashboard's queue panel. */
data class QueuePanelItem(
    val entryId: Long,
    val title: String,
    val priority: ReminderPriority,
    /** When this occurrence became due — the panel shows time waited. */
    val dueAt: Instant,
)

/** Projected schedule slice for the dashboard's calendar cards. */
data class HomeSchedule(
    /** Remaining occurrences today, soonest first (capped). */
    val today: List<CalendarOccurrence> = emptyList(),
    /** Occurrences still ahead within the next seven days. */
    val weekCount: Int = 0,
    /** Days of the current month that have at least one occurrence. */
    val monthDaysWithReminders: Set<LocalDate> = emptySet(),
    val month: YearMonth = YearMonth.now(),
)

/** Cloud activity counters for the dashboard; all zero when offline. */
data class SharingPulse(
    val pendingInvitations: Int = 0,
    val unreadMessages: Int = 0,
    val unreadNotifications: Int = 0,
) {
    val isEmpty: Boolean
        get() = pendingInvitations == 0 && unreadMessages == 0 && unreadNotifications == 0
}

/** Everything the dashboard renders, derived live from the database. */
data class HomeUiState(
    val stats: ReminderStats = ReminderStats(),
    /** Pending display-queue entries with titles, presentation order. */
    val queue: List<QueuePanelItem> = emptyList(),
    /** Next few scheduled occurrences, soonest first. */
    val upcoming: List<Reminder> = emptyList(),
    /** Most recently updated reminders. */
    val recentActivity: List<Reminder> = emptyList(),
    val schedule: HomeSchedule = HomeSchedule(),
    /** Global pause end (PAUSE_INDEFINITE = until resumed); null when active. */
    val pausedUntil: Instant? = null,
) {
    /** The reminder whose alert is running right now, if any. */
    val runningEntry: QueuePanelItem? get() = queue.firstOrNull()

    /** The soonest scheduled reminder — the hero of the dashboard. */
    val nextReminder: Reminder? get() = upcoming.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    queueRepository: ReminderQueueRepository,
    settingsRepository: SettingsRepository,
    friendRepository: com.alertnotes.domain.repository.FriendRepository,
    sharingRepository: com.alertnotes.domain.repository.ReminderSharingRepository,
    chatRepository: com.alertnotes.domain.repository.ChatRepository,
    notificationCentre: com.alertnotes.domain.repository.NotificationCentreRepository,
    projector: OccurrenceProjector,
    timeProvider: TimeProvider,
    logger: AppLogger,
) : ViewModel() {

    /** Invitations, unread messages, unread notifications — one glance. */
    val sharingPulse: StateFlow<SharingPulse> = combine(
        friendRepository.incomingRequests,
        friendRepository.incomingFamilyInvitations,
        sharingRepository.incomingShares,
        chatRepository.totalUnread,
        notificationCentre.unreadCount,
    ) { requests, invitations, incoming, unreadMessages, unreadNotifications ->
        SharingPulse(
            pendingInvitations = requests.size + invitations.size +
                incoming.count { it.share.status == ShareStatus.PENDING },
            unreadMessages = unreadMessages,
            unreadNotifications = unreadNotifications,
        )
    }
        .catch { emit(SharingPulse()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharingPulse())

    /** Live shares I sent, most recent first — the dashboard preview. */
    val sharedByMe: StateFlow<List<ReminderShareWithProfile>> = sharingRepository.outgoingShares
        .map { shares -> shares.filter { it.share.status in LIVE_SHARE_STATUSES }.take(DASHBOARD_PREVIEW_COUNT) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live shares addressed to me. */
    val sharedWithMe: StateFlow<List<ReminderShareWithProfile>> = sharingRepository.incomingShares
        .map { shares -> shares.filter { it.share.status in LIVE_SHARE_STATUSES }.take(DASHBOARD_PREVIEW_COUNT) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queuePanel = queueRepository.observePending()
        .mapLatest { pending ->
            pending.take(QUEUE_PANEL_LIMIT).mapNotNull { entry ->
                reminderRepository.getReminder(entry.reminderId)?.let { reminder ->
                    QueuePanelItem(
                        entryId = entry.id,
                        title = reminder.title,
                        priority = entry.priority,
                        dueAt = entry.dueAt,
                    )
                }
            }
        }

    private val schedule = reminderRepository.observeReminders()
        .mapLatest { reminders ->
            val zone = ZoneId.systemDefault()
            val now = timeProvider.now()
            val today = now.atZone(zone).toLocalDate()
            val month = YearMonth.from(today)
            val windowStart = minOf(month.atDay(1), today)
            val windowEnd = maxOf(month.atEndOfMonth().plusDays(1), today.plusDays(7))
            val occurrences = reminders
                .filter { it.isEnabled }
                .flatMap { reminder ->
                    projector
                        .occurrencesBetween(
                            reminder,
                            windowStart.atStartOfDay(zone).toInstant(),
                            windowEnd.atStartOfDay(zone).toInstant(),
                        )
                        .map { reminder.toOccurrence(it, zone) }
                }
            val remaining = occurrences.filter { it.at >= now }.sortedBy { it.at }
            HomeSchedule(
                today = remaining.filter { it.date == today }.take(TODAY_SCHEDULE_LIMIT),
                weekCount = remaining.count { it.date < today.plusDays(7) },
                monthDaysWithReminders = occurrences
                    .asSequence()
                    .map { it.date }
                    .filter { YearMonth.from(it) == month }
                    .toSet(),
                month = month,
            )
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            reminderRepository.observeStats(dueHorizon = endOfToday(timeProvider.now())),
            queuePanel,
            reminderRepository.observeUpcoming(DASHBOARD_PREVIEW_COUNT),
            reminderRepository.observeRecentlyUpdated(DASHBOARD_PREVIEW_COUNT),
            schedule,
        ) { stats, queue, upcoming, recent, schedule ->
            HomeUiState(
                stats = stats,
                queue = queue,
                upcoming = upcoming,
                recentActivity = recent,
                schedule = schedule,
            )
        },
        settingsRepository.preferences,
    ) { state, preferences ->
        state.copy(pausedUntil = preferences.pausedUntil)
    }
        .catch { throwable ->
            logger.e(TAG, "Failed to load dashboard data", throwable)
            emit(HomeUiState())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    /** "Due today" counts triggers up to local midnight. */
    private fun endOfToday(now: Instant): Instant {
        val zone = ZoneId.systemDefault()
        return now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val DASHBOARD_PREVIEW_COUNT = 3
        const val QUEUE_PANEL_LIMIT = 6
        const val TODAY_SCHEDULE_LIMIT = 4

        /** Everything still moving; terminal shares stay on the dashboard's tracking screen only. */
        val LIVE_SHARE_STATUSES = setOf(
            ShareStatus.PENDING,
            ShareStatus.ACCEPTED,
            ShareStatus.DELIVERED,
            ShareStatus.SCHEDULED,
            ShareStatus.TRIGGERED,
        )
    }
}
