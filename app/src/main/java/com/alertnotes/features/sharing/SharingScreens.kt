package com.alertnotes.features.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.FamilyMember
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.FriendUser
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.RelationshipType
import com.alertnotes.domain.model.ReminderShareWithProfile
import com.alertnotes.domain.model.ShareStatus
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.domain.repository.ReminderRepository
import com.alertnotes.domain.repository.ReminderSharingRepository
import com.alertnotes.features.friends.FriendNoticeDialog
import com.alertnotes.features.friends.PersonRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Who a reminder is for; drives the explanatory copy and recipient list. */
enum class SharingMode { ONLY_ME, FRIEND, FAMILY }

@HiltViewModel
class ShareReminderViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    friendRepository: FriendRepository,
    private val sharingRepository: ReminderSharingRepository,
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = reminderRepository.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val friends: StateFlow<List<FriendUser>> = friendRepository.friends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val family: StateFlow<List<FamilyMember>> = friendRepository.family
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sent = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = _sent.asStateFlow()

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun send(reminder: Reminder, recipientUids: List<String>) {
        if (_isSending.value || recipientUids.isEmpty()) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                sharingRepository.shareReminder(reminder, recipientUids)
                _sent.value = true
            } catch (exception: FriendException) {
                _notice.value = exception.error
            } finally {
                _isSending.value = false
            }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }
}

/**
 * The sharing flow: pick one of your reminders, choose who it's for (with
 * plain-language explanations of each mode), select recipients, review, and
 * send. Deliberately a standalone flow rather than a rebuild of the
 * production reminder editor — the offline engine stays untouched.
 */
@Composable
fun ShareReminderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShareReminderViewModel = hiltViewModel(),
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val family by viewModel.family.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val sent by viewModel.sent.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var selectedReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var mode by rememberSaveable { mutableStateOf(SharingMode.ONLY_ME) }
    var selectedUids by rememberSaveable { mutableStateOf(setOf<String>()) }

    val selectedReminder = reminders.firstOrNull { it.id == selectedReminderId }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.sharing_share_row),
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
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.sharing_step_reminder)) {
                        if (reminders.isEmpty()) {
                            Text(
                                text = stringResource(R.string.sharing_no_reminders),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.large),
                            )
                        } else {
                            reminders.take(REMINDER_PICK_LIMIT).forEach { reminder ->
                                SelectableRow(
                                    title = reminder.title,
                                    subtitle = reminder.nextTriggerAt
                                        ?.toDisplayDateTime(ZoneId.systemDefault())
                                        ?: stringResource(R.string.editor_status_none),
                                    selected = reminder.id == selectedReminderId,
                                    onClick = { selectedReminderId = reminder.id },
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.sharing_step_mode)) {
                        ModeRow(
                            title = stringResource(R.string.sharing_mode_only_me),
                            explanation = stringResource(R.string.sharing_mode_only_me_hint),
                            selected = mode == SharingMode.ONLY_ME,
                        ) {
                            mode = SharingMode.ONLY_ME
                            selectedUids = emptySet()
                        }
                        ModeRow(
                            title = stringResource(R.string.sharing_mode_friend),
                            explanation = stringResource(R.string.sharing_mode_friend_hint),
                            selected = mode == SharingMode.FRIEND,
                        ) {
                            mode = SharingMode.FRIEND
                            selectedUids = emptySet()
                        }
                        ModeRow(
                            title = stringResource(R.string.sharing_mode_family),
                            explanation = stringResource(R.string.sharing_mode_family_hint),
                            selected = mode == SharingMode.FAMILY,
                        ) {
                            mode = SharingMode.FAMILY
                            selectedUids = emptySet()
                        }
                    }
                }
                if (mode != SharingMode.ONLY_ME) {
                    item {
                        SectionCard(title = stringResource(R.string.sharing_step_recipients)) {
                            val people: List<Pair<String, com.alertnotes.domain.model.PublicProfile>> =
                                if (mode == SharingMode.FAMILY) {
                                    family.map { it.uid to it.profile }
                                } else {
                                    friends.map { it.uid to it.profile }
                                }
                            if (people.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.sharing_no_recipients),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                                )
                            } else {
                                people.forEach { (uid, profile) ->
                                    val isSelected = uid in selectedUids
                                    PersonRow(
                                        profile = profile,
                                        badge = if (mode == SharingMode.FAMILY) {
                                            stringResource(R.string.family_state_member)
                                        } else {
                                            stringResource(R.string.friends_state_friends)
                                        },
                                        onClick = {
                                            selectedUids =
                                                if (isSelected) selectedUids - uid else selectedUids + uid
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) {
                                                Icons.Outlined.CheckCircle
                                            } else {
                                                Icons.Outlined.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.sharing_step_review)) {
                        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                            ReviewLine(
                                label = stringResource(R.string.reminder_field_title),
                                value = selectedReminder?.title
                                    ?: stringResource(R.string.sharing_review_none),
                            )
                            ReviewLine(
                                label = stringResource(R.string.editor_section_schedule),
                                value = selectedReminder?.nextTriggerAt
                                    ?.toDisplayDateTime(ZoneId.systemDefault())
                                    ?: stringResource(R.string.editor_status_none),
                            )
                            ReviewLine(
                                label = stringResource(R.string.sharing_step_mode),
                                value = stringResource(
                                    when (mode) {
                                        SharingMode.ONLY_ME -> R.string.sharing_mode_only_me
                                        SharingMode.FRIEND -> R.string.sharing_mode_friend
                                        SharingMode.FAMILY -> R.string.sharing_mode_family
                                    },
                                ),
                            )
                            ReviewLine(
                                label = stringResource(R.string.sharing_step_recipients),
                                value = selectedUids.size.toString(),
                            )
                            PrimaryButton(
                                text = stringResource(
                                    if (mode == SharingMode.ONLY_ME) {
                                        R.string.sharing_done_local
                                    } else {
                                        R.string.sharing_send
                                    },
                                ),
                                onClick = {
                                    if (mode == SharingMode.ONLY_ME) {
                                        onNavigateBack()
                                    } else {
                                        selectedReminder?.let {
                                            viewModel.send(it, selectedUids.toList())
                                        }
                                    }
                                },
                                enabled = mode == SharingMode.ONLY_ME ||
                                    (selectedReminder != null && selectedUids.isNotEmpty()),
                                loading = isSending,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = MaterialTheme.spacing.large),
                            )
                        }
                    }
                }
            }
        }
    }

    notice?.let { FriendNoticeDialog(error = it, onDismiss = viewModel::dismissNotice) }
    if (sent) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            shape = MaterialTheme.shapes.extraLarge,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(text = stringResource(R.string.sharing_sent_title)) },
            text = { Text(text = stringResource(R.string.sharing_sent_message)) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(text = stringResource(R.string.action_done))
                }
            },
        )
    }
}

@HiltViewModel
class SharedRemindersViewModel @Inject constructor(
    private val sharingRepository: ReminderSharingRepository,
) : ViewModel() {

    val incoming: StateFlow<List<ReminderShareWithProfile>> = sharingRepository.incomingShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outgoing: StateFlow<List<ReminderShareWithProfile>> = sharingRepository.outgoingShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun accept(item: ReminderShareWithProfile) = act {
        sharingRepository.acceptShare(item.share)
    }

    fun decline(shareId: String) = act { sharingRepository.declineShare(shareId) }

    fun cancel(shareId: String) = act { sharingRepository.cancelShare(shareId) }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun act(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (exception: FriendException) {
                _notice.value = exception.error
            }
        }
    }
}

/**
 * The sharing dashboard: incoming reminder invitations (accept/decline with
 * preview) plus everything the user has sent, with live status chips and
 * filters.
 */
@Composable
fun SharedRemindersScreen(
    onNavigateBack: () -> Unit,
    viewModel: SharedRemindersViewModel = hiltViewModel(),
) {
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<ShareStatus?>(null) }

    val pendingIncoming = incoming.filter { it.share.status == ShareStatus.PENDING }
    val filteredOutgoing = outgoing.filter { filter == null || it.share.status == filter }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.sharing_dashboard_row),
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
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            ) {
                if (pendingIncoming.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.sharing_incoming_title)) {
                            pendingIncoming.forEach { item ->
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.large,
                                        vertical = MaterialTheme.spacing.small,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.sharing_incoming_from,
                                            item.profile.displayName,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = item.share.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.share.scheduleSummary.isNotBlank()) {
                                        Text(
                                            text = item.share.scheduleSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row {
                                        TextButton(onClick = { viewModel.accept(item) }) {
                                            Text(text = stringResource(R.string.friends_accept))
                                        }
                                        TextButton(onClick = { viewModel.decline(item.share.id) }) {
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
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text(stringResource(R.string.sharing_filter_all)) },
                        )
                        listOf(
                            ShareStatus.PENDING,
                            ShareStatus.SCHEDULED,
                            ShareStatus.COMPLETED,
                            ShareStatus.REJECTED,
                        ).forEach { status ->
                            FilterChip(
                                selected = filter == status,
                                onClick = { filter = if (filter == status) null else status },
                                label = { Text(status.label()) },
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.sharing_outgoing_title)) {
                        if (filteredOutgoing.isEmpty()) {
                            Text(
                                text = stringResource(R.string.sharing_outgoing_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.large),
                            )
                        } else {
                            filteredOutgoing.forEach { item ->
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.large,
                                        vertical = MaterialTheme.spacing.small,
                                    ),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.share.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusChip(status = item.share.status)
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.sharing_outgoing_to,
                                            item.profile.displayName,
                                        ) + " · " + item.share.relationship.name
                                            .lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val timeline = buildList {
                                        item.share.createdAt?.let {
                                            add(
                                                stringResource(R.string.sharing_time_created) +
                                                    " " + it.toDisplayDateTime(ZoneId.systemDefault()),
                                            )
                                        }
                                        item.share.respondedAt?.let {
                                            add(
                                                stringResource(R.string.sharing_time_responded) +
                                                    " " + it.toDisplayDateTime(ZoneId.systemDefault()),
                                            )
                                        }
                                        item.share.lastSyncAt?.let {
                                            add(
                                                stringResource(R.string.sharing_time_sync) +
                                                    " " + it.toDisplayDateTime(ZoneId.systemDefault()),
                                            )
                                        }
                                    }
                                    if (timeline.isNotEmpty()) {
                                        Text(
                                            text = timeline.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (item.share.status == ShareStatus.PENDING ||
                                        item.share.status == ShareStatus.DELIVERED
                                    ) {
                                        TextButton(onClick = { viewModel.cancel(item.share.id) }) {
                                            Text(text = stringResource(R.string.friends_cancel_request))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    notice?.let { FriendNoticeDialog(error = it, onDismiss = viewModel::dismissNotice) }
}

// region shared pieces

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (selected) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.extraSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModeRow(
    title: String,
    explanation: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableRow(title = title, subtitle = explanation, selected = selected, onClick = onClick)
}

@Composable
internal fun StatusChip(status: ShareStatus) {
    val (container, content) = when (status) {
        ShareStatus.PENDING, ShareStatus.DELIVERED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        ShareStatus.ACCEPTED, ShareStatus.SCHEDULED, ShareStatus.COMPLETED ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) to MaterialTheme.colorScheme.primary

        ShareStatus.TRIGGERED ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface

        ShareStatus.REJECTED, ShareStatus.CANCELLED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = container) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}

@Composable
private fun ShareStatus.label(): String = stringResource(
    when (this) {
        ShareStatus.PENDING -> R.string.sharing_status_pending
        ShareStatus.ACCEPTED -> R.string.sharing_status_accepted
        ShareStatus.REJECTED -> R.string.sharing_status_rejected
        ShareStatus.DELIVERED -> R.string.sharing_status_delivered
        ShareStatus.SCHEDULED -> R.string.sharing_status_scheduled
        ShareStatus.TRIGGERED -> R.string.sharing_status_triggered
        ShareStatus.COMPLETED -> R.string.sharing_status_completed
        ShareStatus.CANCELLED -> R.string.sharing_status_cancelled
    },
)

private const val REMINDER_PICK_LIMIT = 25

// endregion
