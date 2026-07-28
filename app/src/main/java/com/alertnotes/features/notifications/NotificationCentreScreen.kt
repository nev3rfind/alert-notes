package com.alertnotes.features.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.components.SkeletonCircle
import com.alertnotes.core.ui.components.SkeletonLine
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.AppNotification
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.NotificationCategory
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.domain.repository.NotificationCentreRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Read-state view over the centre. */
enum class CentreFilter { UNREAD, ALL, ARCHIVED }

/** Coarse category buckets — the full enum would drown the chip row. */
enum class CentreCategoryFilter { ALL, INVITATIONS, REMINDERS, MESSAGES, SECURITY }

/** The centre's list, pre-grouped the way the screen renders it. */
data class CentreGroups(
    val pinned: List<AppNotification> = emptyList(),
    val today: List<AppNotification> = emptyList(),
    val yesterday: List<AppNotification> = emptyList(),
    val earlier: List<AppNotification> = emptyList(),
) {
    val isEmpty: Boolean
        get() = pinned.isEmpty() && today.isEmpty() && yesterday.isEmpty() && earlier.isEmpty()
}

@HiltViewModel
class NotificationCentreViewModel @Inject constructor(
    private val repository: NotificationCentreRepository,
    private val friendRepository: FriendRepository,
    private val sharingRepository: ReminderSharingRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(CentreFilter.UNREAD)
    val filter: StateFlow<CentreFilter> = _filter.asStateFlow()

    private val _category = MutableStateFlow(CentreCategoryFilter.ALL)
    val category: StateFlow<CentreCategoryFilter> = _category.asStateFlow()

    /** `null` until the first snapshot arrives — the screen shows loading. */
    val groups: StateFlow<CentreGroups?> = combine(
        repository.notifications,
        _query,
        _filter,
        _category,
    ) { entries, query, filter, category ->
        val visible = entries
            .filter {
                when (filter) {
                    CentreFilter.UNREAD -> !it.read && !it.archived
                    CentreFilter.ALL -> !it.archived
                    CentreFilter.ARCHIVED -> it.archived
                }
            }
            .filter { it.category.inBucket(category) }
            .filter { it.matchesQuery(query) }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (pinned, rest) = visible.partition { it.pinned }
        val byDay = rest.groupBy { entry ->
            entry.createdAt?.atZone(zone)?.toLocalDate()
        }
        CentreGroups(
            pinned = pinned,
            today = byDay[today].orEmpty(),
            yesterday = byDay[today.minusDays(1)].orEmpty(),
            earlier = rest.filter { entry ->
                val day = entry.createdAt?.atZone(zone)?.toLocalDate()
                day == null || (day != today && day != today.minusDays(1))
            },
        )
    }.map<CentreGroups, CentreGroups?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unreadCount: StateFlow<Int> = repository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onFilterChange(value: CentreFilter) {
        _filter.value = value
    }

    fun onCategoryChange(value: CentreCategoryFilter) {
        _category.value = value
    }

    fun markRead(id: String) = act { repository.markRead(id) }

    fun markAllRead() = act { repository.markAllRead() }

    fun setArchived(id: String, archived: Boolean) = act { repository.setArchived(id, archived) }

    fun setPinned(id: String, pinned: Boolean) = act { repository.setPinned(id, pinned) }

    fun delete(id: String) = act { repository.delete(id) }

    /** Quick-accept straight from the card — no screen change needed. */
    fun accept(entry: AppNotification) = act {
        val me = authRepository.currentUser?.uid ?: return@act
        when (entry.category) {
            NotificationCategory.FRIEND_REQUEST ->
                friendRepository.acceptRequest("${entry.senderUid}_$me")

            NotificationCategory.FAMILY_INVITATION ->
                friendRepository.acceptFamilyInvitation("${entry.senderUid}_$me")

            NotificationCategory.REMINDER_INVITATION ->
                sharingRepository.incomingShares.first()
                    .firstOrNull { it.share.id == entry.refId }
                    // No live share behind the card means it was cancelled,
                    // already accepted, or delivered elsewhere. Saying so
                    // beats silently marking the card read.
                    ?.let { sharingRepository.acceptShare(it.share) }
                    ?: throw FriendException(FriendError.UNKNOWN)

            else -> Unit
        }
        repository.markRead(entry.id)
    }

    fun decline(entry: AppNotification) = act {
        val me = authRepository.currentUser?.uid ?: return@act
        when (entry.category) {
            NotificationCategory.FRIEND_REQUEST ->
                friendRepository.rejectRequest("${entry.senderUid}_$me")

            NotificationCategory.FAMILY_INVITATION ->
                friendRepository.declineFamilyInvitation("${entry.senderUid}_$me")

            NotificationCategory.REMINDER_INVITATION ->
                sharingRepository.declineShare(entry.refId)

            else -> Unit
        }
        repository.markRead(entry.id)
    }

    /**
     * A notification-centre entry is a durable record of something that
     * happened, not a live view of it. By the time it is tapped the request
     * may have been answered on another device, the share may already be
     * delivered, or the request may have expired.
     *
     * `act` used to swallow every failure, so those taps looked like they
     * worked: the card was marked read and vanished, while the friendship or
     * the share was left exactly as it was. Failures are now surfaced.
     */
    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    private fun act(operation: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { operation() }
                .onFailure { throwable ->
                    _notice.value = (throwable as? FriendException)?.error ?: FriendError.UNKNOWN
                }
        }
    }
}

private fun NotificationCategory.inBucket(bucket: CentreCategoryFilter): Boolean = when (bucket) {
    CentreCategoryFilter.ALL -> true
    CentreCategoryFilter.INVITATIONS -> this in INVITATION_CATEGORIES
    CentreCategoryFilter.REMINDERS -> this in REMINDER_CATEGORIES
    CentreCategoryFilter.MESSAGES -> this == NotificationCategory.CHAT_MESSAGE
    CentreCategoryFilter.SECURITY -> this == NotificationCategory.SECURITY
}

private val INVITATION_CATEGORIES = setOf(
    NotificationCategory.FRIEND_REQUEST,
    NotificationCategory.FAMILY_INVITATION,
    NotificationCategory.REMINDER_INVITATION,
)

private val REMINDER_CATEGORIES = setOf(
    NotificationCategory.REMINDER_INVITATION,
    NotificationCategory.REMINDER_ACCEPTED,
    NotificationCategory.REMINDER_REJECTED,
    NotificationCategory.REMINDER_CANCELLED,
    NotificationCategory.REMINDER_UPDATED,
    NotificationCategory.REMINDER_TRIGGERED,
    NotificationCategory.REMINDER_ACKNOWLEDGED,
)

/**
 * The Notification Centre: everything requiring attention, grouped by day
 * with pinned items on top, live search and filters, swipe to mark-read
 * (right) or archive (left), pinning, deletion, and quick Accept/Decline
 * directly on invitation cards. Every entry deep-links to where it
 * happened; the durable record outlives any dismissed push.
 */
@Composable
fun NotificationCentreScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenShared: () -> Unit,
    onOpenInbox: () -> Unit,
    viewModel: NotificationCentreViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    // Accept/Decline on a stale card fails for a real reason - already
    // answered elsewhere, already delivered, expired. Say so instead of
    // quietly marking the card read as if it had worked.
    notice?.let { error ->
        com.alertnotes.features.friends.FriendNoticeDialog(
            error = error,
            onDismiss = viewModel::dismissNotice,
        )
    }

    val openEntry: (AppNotification) -> Unit = { entry ->
        viewModel.markRead(entry.id)
        when (entry.category) {
            NotificationCategory.CHAT_MESSAGE ->
                if (entry.refId.isNotBlank()) onOpenChat(entry.refId)

            NotificationCategory.FRIEND_REQUEST,
            NotificationCategory.FAMILY_INVITATION,
            -> onOpenInbox()

            NotificationCategory.SECURITY, NotificationCategory.SYSTEM -> Unit
            else -> onOpenShared()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.notifications_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                item {
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        placeholder = stringResource(R.string.notifications_search_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        CentreFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { viewModel.onFilterChange(option) },
                                label = {
                                    Text(
                                        text = stringResource(option.labelRes()) +
                                            if (option == CentreFilter.UNREAD && unreadCount > 0) {
                                                " ($unreadCount)"
                                            } else {
                                                ""
                                            },
                                    )
                                },
                            )
                        }
                        if (unreadCount > 0) {
                            TextButton(onClick = viewModel::markAllRead) {
                                Text(text = stringResource(R.string.notifications_mark_all_read))
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        CentreCategoryFilter.entries.forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { viewModel.onCategoryChange(option) },
                                label = { Text(text = stringResource(option.labelRes())) },
                            )
                        }
                    }
                }
                // Loading (null) renders neither the empty state nor stale content.
                val loaded = groups
                if (loaded == null) {
                    items(LOADING_SKELETON_ROWS) {
                        NotificationCardSkeleton()
                    }
                } else if (loaded.isEmpty) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.spacing.extraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    when (filter) {
                                        CentreFilter.UNREAD -> R.string.notifications_empty_unread
                                        CentreFilter.ALL -> R.string.notifications_empty_all
                                        CentreFilter.ARCHIVED -> R.string.notifications_empty_archived
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                            )
                        }
                    }
                } else {
                    notificationGroup(
                        titleRes = R.string.notifications_group_pinned,
                        entries = loaded.pinned,
                        zone = zone,
                        viewModel = viewModel,
                        onOpen = openEntry,
                    )
                    notificationGroup(
                        titleRes = R.string.notifications_group_today,
                        entries = loaded.today,
                        zone = zone,
                        viewModel = viewModel,
                        onOpen = openEntry,
                    )
                    notificationGroup(
                        titleRes = R.string.notifications_group_yesterday,
                        entries = loaded.yesterday,
                        zone = zone,
                        viewModel = viewModel,
                        onOpen = openEntry,
                    )
                    notificationGroup(
                        titleRes = R.string.notifications_group_earlier,
                        entries = loaded.earlier,
                        zone = zone,
                        viewModel = viewModel,
                        onOpen = openEntry,
                    )
                }
            }
        }
    }
}

/** One day-group: uppercase header + swipeable, animating cards. */
private fun androidx.compose.foundation.lazy.LazyListScope.notificationGroup(
    titleRes: Int,
    entries: List<AppNotification>,
    zone: ZoneId,
    viewModel: NotificationCentreViewModel,
    onOpen: (AppNotification) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "header_$titleRes") {
        Text(
            text = stringResource(titleRes).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.small,
                top = MaterialTheme.spacing.medium,
            ),
        )
    }
    items(entries, key = { it.id }) { entry ->
        SwipeableNotificationCard(
            entry = entry,
            time = entry.createdAt?.toDisplayDateTime(zone).orEmpty(),
            viewModel = viewModel,
            onOpen = { onOpen(entry) },
        )
    }
}

/**
 * Swipe right = mark read, swipe left = archive/unarchive. The card snaps
 * back after the action — the live flow moves it to its new home, animated.
 */
@Composable
private fun SwipeableNotificationCard(
    entry: AppNotification,
    time: String,
    viewModel: NotificationCentreViewModel,
    onOpen: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    androidx.compose.runtime.LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                viewModel.markRead(entry.id)
                dismissState.reset()
            }

            SwipeToDismissBoxValue.EndToStart -> {
                viewModel.setArchived(entry.id, !entry.archived)
                dismissState.reset()
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MarkEmailRead,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Box(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (entry.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        NotificationCard(
            entry = entry,
            time = time,
            onOpen = onOpen,
            onPinToggle = { viewModel.setPinned(entry.id, !entry.pinned) },
            onDelete = { viewModel.delete(entry.id) },
            onAccept = { viewModel.accept(entry) },
            onDecline = { viewModel.decline(entry) },
        )
    }
}

@Composable
private fun NotificationCard(
    entry: AppNotification,
    time: String,
    onOpen: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (!entry.read && !entry.archived) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.clickable(onClick = onOpen)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.small,
                        bottom = MaterialTheme.spacing.extraSmall,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = entry.category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!entry.read) {
                            Box(
                                modifier = Modifier
                                    .padding(end = MaterialTheme.spacing.extraSmall)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (entry.read) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onPinToggle) {
                    Icon(
                        imageVector = if (entry.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(
                            if (entry.pinned) R.string.notifications_unpin else R.string.notifications_pin,
                        ),
                        tint = if (entry.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.notifications_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (entry.category in INVITATION_CATEGORIES) {
                Row(modifier = Modifier.padding(start = MaterialTheme.spacing.extraLarge)) {
                    TextButton(onClick = onAccept) {
                        Text(text = stringResource(R.string.friends_accept))
                    }
                    TextButton(onClick = onDecline) {
                        Text(
                            text = stringResource(R.string.friends_reject),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Card-shaped placeholder mirroring [NotificationCard] while loading. */
@Composable
private fun NotificationCardSkeleton() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = 24.dp)
            Column(modifier = Modifier.padding(start = MaterialTheme.spacing.medium)) {
                SkeletonLine(width = 160.dp, height = 14.dp)
                SkeletonLine(
                    width = 220.dp,
                    height = 10.dp,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                )
            }
        }
    }
}

private const val LOADING_SKELETON_ROWS = 6

private fun NotificationCategory.icon(): ImageVector = when (this) {
    NotificationCategory.FRIEND_REQUEST -> Icons.Outlined.PersonAdd
    NotificationCategory.FAMILY_INVITATION -> Icons.Outlined.FamilyRestroom
    NotificationCategory.CHAT_MESSAGE -> Icons.Outlined.ChatBubbleOutline
    NotificationCategory.SECURITY -> Icons.Outlined.Security
    NotificationCategory.SYSTEM -> Icons.Outlined.NotificationsNone
    else -> Icons.Outlined.Share
}

private fun CentreFilter.labelRes(): Int = when (this) {
    CentreFilter.UNREAD -> R.string.notifications_filter_unread
    CentreFilter.ALL -> R.string.notifications_filter_all
    CentreFilter.ARCHIVED -> R.string.notifications_filter_archived
}

private fun CentreCategoryFilter.labelRes(): Int = when (this) {
    CentreCategoryFilter.ALL -> R.string.notifications_category_all
    CentreCategoryFilter.INVITATIONS -> R.string.notifications_category_invitations
    CentreCategoryFilter.REMINDERS -> R.string.notifications_category_reminders
    CentreCategoryFilter.MESSAGES -> R.string.notifications_category_messages
    CentreCategoryFilter.SECURITY -> R.string.notifications_category_security
}
