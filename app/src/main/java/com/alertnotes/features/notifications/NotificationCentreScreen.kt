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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.AppNotification
import com.alertnotes.domain.model.NotificationCategory
import com.alertnotes.domain.repository.NotificationCentreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Read-state view over the centre. */
enum class CentreFilter { UNREAD, ALL, ARCHIVED }

/** Coarse category buckets — ten raw categories would drown the chip row. */
enum class CentreCategoryFilter { ALL, SOCIAL, REMINDERS, MESSAGES }

@HiltViewModel
class NotificationCentreViewModel @Inject constructor(
    private val repository: NotificationCentreRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(CentreFilter.UNREAD)
    val filter: StateFlow<CentreFilter> = _filter.asStateFlow()

    private val _category = MutableStateFlow(CentreCategoryFilter.ALL)
    val category: StateFlow<CentreCategoryFilter> = _category.asStateFlow()

    val items: StateFlow<List<AppNotification>> = combine(
        repository.notifications,
        _query,
        _filter,
        _category,
    ) { entries, query, filter, category ->
        entries
            .filter {
                when (filter) {
                    CentreFilter.UNREAD -> !it.read && !it.archived
                    CentreFilter.ALL -> !it.archived
                    CentreFilter.ARCHIVED -> it.archived
                }
            }
            .filter { it.category.inBucket(category) }
            .filter { it.matchesQuery(query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun delete(id: String) = act { repository.delete(id) }

    private fun act(operation: suspend () -> Unit) {
        viewModelScope.launch { runCatching { operation() } }
    }
}

private fun NotificationCategory.inBucket(bucket: CentreCategoryFilter): Boolean = when (bucket) {
    CentreCategoryFilter.ALL -> true
    CentreCategoryFilter.SOCIAL ->
        this == NotificationCategory.FRIEND_REQUEST || this == NotificationCategory.FAMILY_INVITATION

    CentreCategoryFilter.REMINDERS -> this in REMINDER_CATEGORIES
    CentreCategoryFilter.MESSAGES -> this == NotificationCategory.CHAT_MESSAGE
}

private val REMINDER_CATEGORIES = setOf(
    NotificationCategory.REMINDER_INVITATION,
    NotificationCategory.REMINDER_ACCEPTED,
    NotificationCategory.REMINDER_REJECTED,
    NotificationCategory.REMINDER_CANCELLED,
    NotificationCategory.REMINDER_UPDATED,
    NotificationCategory.REMINDER_TRIGGERED,
)

/**
 * The Notification Centre: every important event, kept after the push
 * banner is long gone — searchable, filterable by read state and category,
 * archivable, deletable, and each entry deep-links to where it happened.
 */
@Composable
fun NotificationCentreScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenShared: () -> Unit,
    onOpenInbox: () -> Unit,
    viewModel: NotificationCentreViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

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
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
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
                        if (unreadCount > 0) {
                            TextButton(onClick = viewModel::markAllRead) {
                                Text(text = stringResource(R.string.notifications_mark_all_read))
                            }
                        }
                    }
                }
                if (items.isEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.notifications_section)) {
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
                    }
                } else {
                    item {
                        SectionCard(title = stringResource(R.string.notifications_section)) {
                            Column {
                                items.forEach { entry ->
                                    NotificationRow(
                                        entry = entry,
                                        time = entry.createdAt?.toDisplayDateTime(zone).orEmpty(),
                                        onOpen = {
                                            viewModel.markRead(entry.id)
                                            when (entry.category) {
                                                NotificationCategory.CHAT_MESSAGE ->
                                                    if (entry.refId.isNotBlank()) onOpenChat(entry.refId)

                                                NotificationCategory.FRIEND_REQUEST,
                                                NotificationCategory.FAMILY_INVITATION,
                                                -> onOpenInbox()

                                                else -> onOpenShared()
                                            }
                                        },
                                        onArchiveToggle = {
                                            viewModel.setArchived(entry.id, !entry.archived)
                                        },
                                        onDelete = { viewModel.delete(entry.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    entry: AppNotification,
    time: String,
    onOpen: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
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
        IconButton(onClick = onArchiveToggle) {
            Icon(
                imageVector = if (entry.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                contentDescription = stringResource(
                    if (entry.archived) R.string.notifications_unarchive else R.string.notifications_archive,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

private fun NotificationCategory.icon(): ImageVector = when (this) {
    NotificationCategory.FRIEND_REQUEST -> Icons.Outlined.PersonAdd
    NotificationCategory.FAMILY_INVITATION -> Icons.Outlined.FamilyRestroom
    NotificationCategory.CHAT_MESSAGE -> Icons.Outlined.ChatBubbleOutline
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
    CentreCategoryFilter.SOCIAL -> R.string.notifications_category_social
    CentreCategoryFilter.REMINDERS -> R.string.notifications_category_reminders
    CentreCategoryFilter.MESSAGES -> R.string.notifications_category_messages
}
