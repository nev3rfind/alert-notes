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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alertnotes.data.entities.toReminderDrawingOrNull
import com.alertnotes.domain.model.timeline
import com.alertnotes.features.drawing.drawReminderStrokes
import com.alertnotes.features.history.labelRes
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.Reminder
import com.alertnotes.domain.model.RelationshipType
import com.alertnotes.domain.model.ReminderOwnership
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One selectable person in the sharing wizard, relationship included. */
data class RecipientOption(
    val uid: String,
    val profile: PublicProfile,
    val isFamily: Boolean,
)

@HiltViewModel
class ShareReminderViewModel @Inject constructor(
    reminderRepository: ReminderRepository,
    friendRepository: FriendRepository,
    private val sharingRepository: ReminderSharingRepository,
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = reminderRepository.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Family first (auto-delivery capable), then plain friends — one list. */
    val recipients: StateFlow<List<RecipientOption>> = combine(
        friendRepository.friends,
        friendRepository.family,
    ) { friends, family ->
        val familyUids = family.map { it.uid }.toSet()
        family.map { RecipientOption(it.uid, it.profile, isFamily = true) } +
            friends.filter { it.uid !in familyUids }
                .map { RecipientOption(it.uid, it.profile, isFamily = false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sent = MutableStateFlow<ReminderOwnership?>(null)
    val sent: StateFlow<ReminderOwnership?> = _sent.asStateFlow()

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun send(reminder: Reminder, recipientUids: List<String>, ownership: ReminderOwnership) {
        if (_isSending.value || recipientUids.isEmpty()) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                sharingRepository.shareReminder(reminder, recipientUids, ownership)
                _sent.value = ownership
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
 * The sharing wizard: pick one of your reminders, select recipients (friends
 * and family in one list), choose who the reminder belongs to — with a live
 * preview of exactly what each ownership mode means — then review and send.
 * Deliberately a standalone flow rather than a rebuild of the production
 * reminder editor — the offline engine stays untouched.
 */
@Composable
fun ShareReminderScreen(
    onNavigateBack: () -> Unit,
    initialReminderId: Long = -1L,
    initialRecipientUid: String? = null,
    viewModel: ShareReminderViewModel = hiltViewModel(),
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val recipients by viewModel.recipients.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val sent by viewModel.sent.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var selectedReminderId by rememberSaveable {
        mutableStateOf(initialReminderId.takeIf { it > 0 })
    }
    var selectedUids by rememberSaveable {
        mutableStateOf(setOfNotNull(initialRecipientUid))
    }
    var ownership by rememberSaveable { mutableStateOf(ReminderOwnership.ME_AND_RECIPIENTS) }
    var reminderQuery by rememberSaveable { mutableStateOf("") }
    var visibleReminderCount by rememberSaveable { mutableStateOf(5) }

    val selectedReminder = reminders.firstOrNull { it.id == selectedReminderId }
    val sendsToOthers = ownership != ReminderOwnership.ONLY_ME

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
                            SearchField(
                                query = reminderQuery,
                                onQueryChange = {
                                    reminderQuery = it
                                    visibleReminderCount = 5
                                },
                                placeholder = stringResource(R.string.sharing_search_reminders),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = MaterialTheme.spacing.large,
                                        vertical = MaterialTheme.spacing.small,
                                    ),
                            )
                            val matching = reminders.filter { it.matchesQuery(reminderQuery) }
                            if (matching.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.sharing_search_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                                )
                            } else {
                                matching.take(visibleReminderCount).forEach { reminder ->
                                    SelectableRow(
                                        title = reminder.title,
                                        subtitle = reminder.nextTriggerAt
                                            ?.toDisplayDateTime(ZoneId.systemDefault())
                                            ?: stringResource(R.string.editor_status_none),
                                        selected = reminder.id == selectedReminderId,
                                        onClick = { selectedReminderId = reminder.id },
                                    )
                                }
                                if (matching.size > visibleReminderCount) {
                                    TextButton(
                                        onClick = { visibleReminderCount += 5 },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(text = stringResource(R.string.sharing_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.sharing_step_recipients)) {
                        if (recipients.isEmpty()) {
                            Text(
                                text = stringResource(R.string.sharing_no_recipients),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.large),
                            )
                        } else {
                            recipients.forEach { option ->
                                val isSelected = option.uid in selectedUids
                                PersonRow(
                                    profile = option.profile,
                                    badge = if (option.isFamily) {
                                        stringResource(R.string.family_state_member)
                                    } else {
                                        stringResource(R.string.friends_state_friends)
                                    },
                                    onClick = {
                                        selectedUids =
                                            if (isSelected) selectedUids - option.uid else selectedUids + option.uid
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
                item {
                    SectionCard(title = stringResource(R.string.sharing_step_ownership)) {
                        SelectableRow(
                            title = stringResource(R.string.sharing_mode_only_me),
                            subtitle = stringResource(R.string.sharing_ownership_only_me_hint),
                            selected = ownership == ReminderOwnership.ONLY_ME,
                            onClick = { ownership = ReminderOwnership.ONLY_ME },
                        )
                        SelectableRow(
                            title = stringResource(R.string.sharing_ownership_me_and),
                            subtitle = stringResource(R.string.sharing_ownership_me_and_hint),
                            selected = ownership == ReminderOwnership.ME_AND_RECIPIENTS,
                            onClick = { ownership = ReminderOwnership.ME_AND_RECIPIENTS },
                        )
                        SelectableRow(
                            title = stringResource(R.string.sharing_ownership_recipients_only),
                            subtitle = stringResource(R.string.sharing_ownership_recipients_only_hint),
                            selected = ownership == ReminderOwnership.RECIPIENTS_ONLY,
                            onClick = { ownership = ReminderOwnership.RECIPIENTS_ONLY },
                        )
                        OwnershipPreview(
                            ownership = ownership,
                            recipientCount = selectedUids.size,
                        )
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
                                label = stringResource(R.string.sharing_step_ownership),
                                value = ownership.label(),
                            )
                            ReviewLine(
                                label = stringResource(R.string.sharing_step_recipients),
                                value = if (sendsToOthers) selectedUids.size.toString() else "0",
                            )
                            PrimaryButton(
                                text = stringResource(
                                    if (sendsToOthers) R.string.sharing_send else R.string.sharing_done_local,
                                ),
                                onClick = {
                                    if (!sendsToOthers) {
                                        onNavigateBack()
                                    } else {
                                        selectedReminder?.let {
                                            viewModel.send(it, selectedUids.toList(), ownership)
                                        }
                                    }
                                },
                                enabled = !sendsToOthers ||
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
    sent?.let { sentOwnership ->
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
            text = {
                Text(
                    text = stringResource(
                        if (sentOwnership == ReminderOwnership.RECIPIENTS_ONLY) {
                            R.string.sharing_sent_assigned_message
                        } else {
                            R.string.sharing_sent_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(text = stringResource(R.string.action_done))
                }
            },
        )
    }
}

/** Plain-language live preview of what the chosen ownership mode will do. */
@Composable
private fun OwnershipPreview(ownership: ReminderOwnership, recipientCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when (ownership) {
                    ReminderOwnership.ONLY_ME ->
                        stringResource(R.string.sharing_preview_only_me)

                    ReminderOwnership.ME_AND_RECIPIENTS ->
                        stringResource(R.string.sharing_preview_me_and, recipientCount)

                    ReminderOwnership.RECIPIENTS_ONLY ->
                        stringResource(R.string.sharing_preview_recipients_only, recipientCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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

    fun acceptUpdate(item: ReminderShareWithProfile) = act {
        sharingRepository.acceptShareUpdate(item.share)
    }

    fun declineUpdate(item: ReminderShareWithProfile) = act {
        sharingRepository.declineShareUpdate(item.share)
    }

    fun deleteOwned(reminderId: Long) = act { sharingRepository.deleteOwnedReminder(reminderId) }

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
 * preview), pending content updates awaiting approval, and everything the
 * user has sent — owner, recipient, ownership, sharing mode, live status,
 * and full owner control (edit/cancel/delete) over recipients-only
 * assignments.
 */
@Composable
fun SharedRemindersScreen(
    onNavigateBack: () -> Unit,
    onOpenEditor: (Long) -> Unit,
    viewModel: SharedRemindersViewModel = hiltViewModel(),
) {
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<ShareStatus?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val pendingIncoming = incoming.filter { it.share.status == ShareStatus.PENDING }
    val pendingUpdates = incoming.filter {
        it.share.updateRequested && it.share.hasPendingUpdate
    }
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
                                IncomingShareCard(
                                    item = item,
                                    onAccept = { viewModel.accept(item) },
                                    onDecline = { viewModel.decline(item.share.id) },
                                )
                            }
                        }
                    }
                }
                if (pendingUpdates.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.sharing_update_incoming_title)) {
                            pendingUpdates.forEach { item ->
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.large,
                                        vertical = MaterialTheme.spacing.small,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.sharing_update_from,
                                            item.profile.displayName,
                                            item.share.title,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (item.share.scheduleSummary.isNotBlank()) {
                                        Text(
                                            text = item.share.scheduleSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row {
                                        TextButton(onClick = { viewModel.acceptUpdate(item) }) {
                                            Text(text = stringResource(R.string.sharing_update_apply))
                                        }
                                        TextButton(onClick = { viewModel.declineUpdate(item) }) {
                                            Text(
                                                text = stringResource(R.string.sharing_update_keep),
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
                            ShareStatus.TRIGGERED,
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
                                OutgoingShareCard(
                                    item = item,
                                    onCancel = { viewModel.cancel(item.share.id) },
                                    onEdit = { onOpenEditor(item.share.reminderId) },
                                    onDelete = { pendingDeleteId = item.share.reminderId },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    notice?.let { FriendNoticeDialog(error = it, onDismiss = viewModel::dismissNotice) }
    pendingDeleteId?.let { reminderId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(text = stringResource(R.string.sharing_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.sharing_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteOwned(reminderId)
                        pendingDeleteId = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.sharing_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Incoming invitation with preview; flags recipients-only assignments. */
@Composable
private fun IncomingShareCard(
    item: ReminderShareWithProfile,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
    ) {
        Text(
            text = stringResource(R.string.sharing_incoming_from, item.profile.displayName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.share.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.share.ownership == ReminderOwnership.RECIPIENTS_ONLY) {
                OwnershipChip(ownership = item.share.ownership)
            }
        }
        if (item.share.scheduleSummary.isNotBlank()) {
            Text(
                text = item.share.scheduleSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.share.ownership == ReminderOwnership.RECIPIENTS_ONLY) {
            Text(
                text = stringResource(R.string.sharing_assigned_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row {
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

/**
 * One sent share: owner, recipient, ownership, sharing mode, live status,
 * timeline, and — for recipients-only assignments — full owner control.
 */
@Composable
private fun OutgoingShareCard(
    item: ReminderShareWithProfile,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val share = item.share
    Column(
        modifier = Modifier.padding(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = share.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OwnershipChip(ownership = share.ownership)
            StatusChip(status = share.status)
        }
        ReviewLine(
            label = stringResource(R.string.sharing_detail_owner),
            value = stringResource(R.string.sharing_owner_you),
        )
        ReviewLine(
            label = stringResource(R.string.sharing_detail_recipient),
            value = item.profile.displayName,
        )
        ReviewLine(
            label = stringResource(R.string.sharing_detail_sharing_mode),
            value = share.relationship.label() + " · " + stringResource(
                if (share.approvalRequired) {
                    R.string.sharing_delivery_approval
                } else {
                    R.string.sharing_delivery_auto
                },
            ),
        )
        share.ackAt?.let { ackAt ->
            ReviewLine(
                label = stringResource(R.string.sharing_ack_label),
                value = listOfNotNull(
                    share.ackMethod?.let { stringResource(it.labelRes()) },
                    ackAt.toDisplayDateTime(ZoneId.systemDefault()),
                ).joinToString(" · "),
            )
            share.ackDelaySeconds?.let { delay ->
                ReviewLine(
                    label = stringResource(R.string.sharing_ack_delay),
                    value = formatResponseDelay(delay),
                )
            }
            share.ackSignature.toReminderDrawingOrNull()?.let { signature ->
                SignaturePreview(signature = signature)
            }
            if (share.ackPhotoUrl.isNotBlank()) {
                PhotoProofPreview(url = share.ackPhotoUrl)
            }
            if (share.ackLat != null && share.ackLng != null) {
                LocationProofDetails(
                    latitude = share.ackLat,
                    longitude = share.ackLng,
                    accuracyMeters = share.ackAccuracyM,
                    address = share.ackAddress,
                )
            }
            if (share.ackLocationUnavailable) {
                Text(
                    text = stringResource(R.string.sharing_ack_location_unavailable) +
                        share.ackLocationNote.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (share.updateRequested && share.hasPendingUpdate) {
            Text(
                text = stringResource(R.string.sharing_update_pending_owner),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        ShareTimelineColumn(share = share)
        Row {
            if (share.status == ShareStatus.PENDING || share.status == ShareStatus.DELIVERED) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.friends_cancel_request))
                }
            }
            if (share.ownership == ReminderOwnership.RECIPIENTS_ONLY &&
                share.status != ShareStatus.CANCELLED &&
                share.status != ShareStatus.REJECTED
            ) {
                TextButton(onClick = onEdit) {
                    Text(text = stringResource(R.string.sharing_edit))
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.sharing_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The recipient's signature, re-rendered from its vector — proof inline. */
@Composable
internal fun SignaturePreview(signature: com.alertnotes.domain.model.ReminderDrawing) {
    val description = stringResource(R.string.sharing_ack_signature_cd)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.extraSmall)
            .aspectRatio(SIGNATURE_PREVIEW_ASPECT)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = description },
    ) {
        drawReminderStrokes(signature.strokes)
    }
}

/** The live camera proof; tap to inspect it full screen. */
@Composable
internal fun PhotoProofPreview(url: String) {
    var showFullScreen by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    coil.compose.SubcomposeAsyncImage(
        model = url,
        contentDescription = stringResource(R.string.sharing_ack_photo_cd),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.extraSmall)
            .aspectRatio(PHOTO_PREVIEW_ASPECT)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { showFullScreen = true },
        loading = {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(MaterialTheme.spacing.small),
                )
            }
        },
    )
    if (showFullScreen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showFullScreen = false }) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.sharing_ack_photo_cd),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { showFullScreen = false },
            )
        }
    }
}

private const val PHOTO_PREVIEW_ASPECT = 4f / 3f

/**
 * THE timeline renderer: a vertical audit trail derived from the share
 * document by [com.alertnotes.domain.model.timeline] — the one derivation
 * every surface shares, so tracking, details, and chat can never disagree.
 */
@Composable
internal fun ShareTimelineColumn(share: com.alertnotes.domain.model.ReminderShare) {
    val events = share.timeline()
    if (events.isEmpty()) return
    val zone = ZoneId.systemDefault()
    Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall)) {
        events.forEachIndexed { index, event ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = event.type.icon(),
                        contentDescription = null,
                        tint = event.type.tint(),
                        modifier = Modifier
                            .padding(2.dp)
                            .size(16.dp),
                    )
                    if (index != events.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                                .width(2.dp)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                val delaySuffix = if (
                    event.type == com.alertnotes.domain.model.ShareTimelineEventType.ACKNOWLEDGED &&
                    share.ackDelaySeconds != null
                ) {
                    " · " + formatResponseDelay(share.ackDelaySeconds)
                } else {
                    ""
                }
                Text(
                    text = stringResource(event.type.labelRes()) + delaySuffix,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MaterialTheme.spacing.small),
                )
                Text(
                    text = event.at.toDisplayDateTime(zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun com.alertnotes.domain.model.ShareTimelineEventType.labelRes(): Int = when (this) {
    com.alertnotes.domain.model.ShareTimelineEventType.CREATED -> R.string.sharing_time_created
    com.alertnotes.domain.model.ShareTimelineEventType.ACCEPTED -> R.string.sharing_status_accepted
    com.alertnotes.domain.model.ShareTimelineEventType.REJECTED -> R.string.sharing_status_rejected
    com.alertnotes.domain.model.ShareTimelineEventType.SCHEDULED -> R.string.sharing_time_scheduled
    com.alertnotes.domain.model.ShareTimelineEventType.UPDATED -> R.string.sharing_update_incoming_title
    com.alertnotes.domain.model.ShareTimelineEventType.TRIGGERED -> R.string.sharing_time_fired
    com.alertnotes.domain.model.ShareTimelineEventType.ACKNOWLEDGED -> R.string.sharing_ack_label
    com.alertnotes.domain.model.ShareTimelineEventType.COMPLETED -> R.string.sharing_status_completed
    com.alertnotes.domain.model.ShareTimelineEventType.CANCELLED -> R.string.sharing_status_cancelled
}

@Composable
private fun com.alertnotes.domain.model.ShareTimelineEventType.icon():
    androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    com.alertnotes.domain.model.ShareTimelineEventType.CREATED -> Icons.Outlined.Info
    com.alertnotes.domain.model.ShareTimelineEventType.ACCEPTED,
    com.alertnotes.domain.model.ShareTimelineEventType.ACKNOWLEDGED,
    com.alertnotes.domain.model.ShareTimelineEventType.COMPLETED,
    -> Icons.Outlined.CheckCircle

    com.alertnotes.domain.model.ShareTimelineEventType.REJECTED,
    com.alertnotes.domain.model.ShareTimelineEventType.CANCELLED,
    -> Icons.Outlined.RadioButtonUnchecked

    else -> Icons.Outlined.Info
}

@Composable
private fun com.alertnotes.domain.model.ShareTimelineEventType.tint():
    androidx.compose.ui.graphics.Color = when (this) {
    com.alertnotes.domain.model.ShareTimelineEventType.REJECTED,
    com.alertnotes.domain.model.ShareTimelineEventType.CANCELLED,
    -> MaterialTheme.colorScheme.error

    com.alertnotes.domain.model.ShareTimelineEventType.ACKNOWLEDGED,
    com.alertnotes.domain.model.ShareTimelineEventType.COMPLETED,
    -> MaterialTheme.colorScheme.primary

    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Location proof: mini map, coordinates, address, and a jump to Maps. */
@Composable
internal fun LocationProofDetails(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double?,
    address: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OsmMiniMap(latitude = latitude, longitude = longitude)
    ReviewLine(
        label = stringResource(R.string.sharing_ack_location),
        value = "%.5f, %.5f".format(latitude, longitude) +
            (accuracyMeters?.let { "  ±%.0f m".format(it) } ?: ""),
    )
    if (address.isNotBlank()) {
        Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude"),
                    ),
                )
            }
        },
    ) {
        Text(text = stringResource(R.string.sharing_open_maps))
    }
}

/**
 * Keyless mini map: the OpenStreetMap tile containing the proof location,
 * attributed per OSM policy. Tapping opens the exact point in Google Maps.
 *
 * Tile-usage-policy compliance: OSM actively blocks requests carrying a
 * generic HTTP client User-Agent ("Access blocked" tiles) — every request
 * therefore identifies this application explicitly. Volume stays trivially
 * low by design: exactly one z15 tile per acknowledgement, memory- and
 * disk-cached by Coil, so recompositions and repeat visits reuse the
 * cached tile instead of re-requesting it.
 */
@Composable
private fun OsmMiniMap(latitude: Double, longitude: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zoom = 15
    val n = 1 shl zoom
    val x = ((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(latitude)
    val y = (
        (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) /
            2.0 * n
        ).toInt().coerceIn(0, n - 1)
    var attempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.extraSmall)
            .aspectRatio(MAP_PREVIEW_ASPECT)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { openInMaps(context, latitude, longitude) },
    ) {
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data("https://tile.openstreetmap.org/$zoom/$x/$y.png")
                // Identify ourselves per the OSM tile usage policy.
                .setHeader("User-Agent", OSM_USER_AGENT)
                .memoryCacheKey("osm_${zoom}_${x}_${y}_$attempt")
                .build(),
            contentDescription = stringResource(R.string.sharing_ack_map_cd),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(MaterialTheme.spacing.small),
                    )
                }
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.sharing_map_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { attempt++ }) {
                        Text(text = stringResource(R.string.proof_retry))
                    }
                }
            },
        )
        Text(
            text = stringResource(R.string.sharing_map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = MaterialTheme.spacing.extraSmall),
        )
    }
}

/** OSM policy requires a valid, identifying User-Agent per application. */
private const val OSM_USER_AGENT =
    "AlertNotes/1.0 (Android; https://github.com/nev3rfind/alert-notes)"

private fun openInMaps(context: android.content.Context, latitude: Double, longitude: Double) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude"),
            ),
        )
    }
}

private const val MAP_PREVIEW_ASPECT = 2.4f

/** Human response delay: "37s", "4m 18s", "1h 12m". */
internal fun formatResponseDelay(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3_600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
}

private const val SIGNATURE_PREVIEW_ASPECT = 3f

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
private fun OwnershipChip(ownership: ReminderOwnership) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = ownership.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}

@Composable
private fun ReminderOwnership.label(): String = stringResource(
    when (this) {
        ReminderOwnership.ONLY_ME -> R.string.sharing_mode_only_me
        ReminderOwnership.ME_AND_RECIPIENTS -> R.string.sharing_ownership_chip_shared
        ReminderOwnership.RECIPIENTS_ONLY -> R.string.sharing_ownership_chip_assigned
    },
)

@Composable
private fun RelationshipType.label(): String = stringResource(
    when (this) {
        RelationshipType.FAMILY -> R.string.family_state_member
        RelationshipType.FRIEND -> R.string.friends_state_friends
    },
)

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

// endregion
