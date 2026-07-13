package com.alertnotes.features.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.navigation.ChatRoute
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.components.SkeletonLine
import com.alertnotes.core.ui.components.SkeletonList
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.data.entities.toReminderDrawingOrNull
import com.alertnotes.domain.model.ChatConversation
import com.alertnotes.domain.model.ChatMessage
import com.alertnotes.domain.model.FamilyInvitationWithProfile
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.FriendRequestWithProfile
import com.alertnotes.domain.model.ReminderShareWithProfile
import com.alertnotes.domain.model.MessageStatus
import com.alertnotes.domain.model.MessageType
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.SystemMessageKind
import com.alertnotes.domain.repository.ChatRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.features.friends.FriendAvatar
import com.alertnotes.features.friends.FriendNoticeDialog
import com.alertnotes.features.history.labelRes
import com.alertnotes.features.sharing.LocationProofDetails
import com.alertnotes.features.sharing.PhotoProofPreview
import com.alertnotes.features.sharing.SignaturePreview
import com.alertnotes.features.sharing.formatResponseDelay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val sharingRepository: com.alertnotes.domain.repository.ReminderSharingRepository,
    friendRepository: FriendRepository,
) : ViewModel() {

    val otherUid: String = savedStateHandle.toRoute<ChatRoute>().otherUid

    /** Live shares keyed by id so reminder cards track status in realtime. */
    val sharesById: StateFlow<Map<String, com.alertnotes.domain.model.ReminderShare>> =
        kotlinx.coroutines.flow.combine(
            sharingRepository.incomingShares,
            sharingRepository.outgoingShares,
        ) { incoming, outgoing ->
            (incoming + outgoing).associate { it.share.id to it.share }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Relationship badge for the conversation header. */
    val isFamily: StateFlow<Boolean> = friendRepository.family
        .map { members -> members.any { it.uid == otherUid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun acceptShare(share: com.alertnotes.domain.model.ReminderShare) {
        viewModelScope.launch {
            try {
                sharingRepository.acceptShare(share)
            } catch (exception: FriendException) {
                _notice.value = exception.error
            }
        }
    }

    fun declineShare(shareId: String) {
        viewModelScope.launch {
            try {
                sharingRepository.declineShare(shareId)
            } catch (exception: FriendException) {
                _notice.value = exception.error
            }
        }
    }

    /** `null` until the first snapshot arrives — the screen shows loading. */
    val messages: StateFlow<List<ChatMessage>?> = chatRepository.observeMessages(otherUid)
        .map<List<ChatMessage>, List<ChatMessage>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val otherProfile: StateFlow<PublicProfile?> = friendRepository.observePublicProfile(otherUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conversation: StateFlow<ChatConversation?> = chatRepository.conversations
        .map { list -> list.firstOrNull { it.otherUid == otherUid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    private var typingJob: Job? = null

    fun send(text: String) {
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(otherUid, text)
            } catch (exception: FriendException) {
                _notice.value = exception.error
            }
        }
    }

    /** Typing flag with a trailing-edge clear so it never sticks on. */
    fun onInputChanged(text: String) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            chatRepository.setTyping(otherUid, text.isNotBlank())
            delay(TYPING_CLEAR_MILLIS)
            chatRepository.setTyping(otherUid, false)
        }
    }

    fun markRead() {
        viewModelScope.launch { runCatching { chatRepository.markRead(otherUid) } }
    }

    /** Smart delivery: suppress pushes for the conversation on screen. */
    fun setChatVisible(visible: Boolean) {
        viewModelScope.launch {
            runCatching { chatRepository.setActiveConversation(if (visible) otherUid else null) }
        }
    }

    fun deleteForMe(messageId: String) {
        viewModelScope.launch {
            runCatching { chatRepository.deleteForMe(otherUid, messageId) }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private companion object {
        const val TYPING_CLEAR_MILLIS = 4_000L
    }
}

/** Realtime 1:1 conversation with bubbles, receipts, and typing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onOpenTracking: () -> Unit,
    onSendReminder: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messagesState by viewModel.messages.collectAsStateWithLifecycle()
    // Loading (null) renders skeleton bubbles instead of a false "no messages".
    val messagesLoading = messagesState == null
    val messages = messagesState.orEmpty()
    val profile by viewModel.otherProfile.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val sharesById by viewModel.sharesById.collectAsStateWithLifecycle()
    val isFamily by viewModel.isFamily.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    // Every new message: mark read + keep the list pinned to the bottom.
    LaunchedEffect(messages.size) {
        viewModel.markRead()
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Smart delivery: while this conversation is on screen, its pushes are
    // suppressed (locally and server-side) — realtime updates render first.
    // Lifecycle-scoped, not composition-scoped: backgrounding the app or
    // locking the screen (ON_STOP) must lift the suppression too, otherwise
    // new messages from this partner arrive silently while nothing renders.
    LifecycleStartEffect(Unit) {
        viewModel.setChatVisible(true)
        onStopOrDispose { viewModel.setChatVisible(false) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = profile?.displayName ?: stringResource(R.string.chat_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    // Send a reminder to this person without leaving the
                    // conversation — the wizard opens with them preselected.
                    androidx.compose.material3.IconButton(
                        onClick = { onSendReminder(viewModel.otherUid) },
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.NotificationAdd,
                            contentDescription = stringResource(R.string.chat_send_reminder),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .fillMaxSize(),
        ) {
            // Identity + presence line under the title.
            profile?.let { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                ) {
                    FriendAvatar(profile = p, size = 24.dp)
                    Text(
                        text = stringResource(
                            if (isFamily) R.string.family_state_member else R.string.friends_state_friends,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                    )
                }
                Text(
                    text = when {
                        conversation?.otherTyping == true ->
                            stringResource(R.string.chat_typing)

                        p.presence == com.alertnotes.domain.model.PresenceState.ONLINE ->
                            stringResource(R.string.profile_presence_online)

                        p.presence == com.alertnotes.domain.model.PresenceState.AWAY ->
                            stringResource(R.string.profile_presence_away)

                        p.lastSeen != null -> stringResource(
                            R.string.profile_last_seen,
                            p.lastSeen.toDisplayDateTime(zone),
                        )

                        else -> stringResource(R.string.profile_presence_offline)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (conversation?.otherTyping == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                if (messagesLoading) {
                    item {
                        MessageBubbleSkeletons()
                    }
                } else if (messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.chat_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MaterialTheme.spacing.huge),
                        )
                    }
                }
                var lastDay: LocalDate? = null
                messages.forEach { message ->
                    val day = message.createdAt?.atZone(zone)?.toLocalDate()
                    if (day != null && day != lastDay) {
                        lastDay = day
                        item(key = "day-$day") { DaySeparator(day) }
                    }
                    val mine = message.senderUid != viewModel.otherUid
                    item(key = message.id) {
                        if (message.type == MessageType.SYSTEM) {
                            Column {
                                SystemBubble(kind = message.systemKind, mine = mine)
                                if (message.shareId != null) {
                                    ReminderShareCard(
                                        share = sharesById[message.shareId],
                                        fallbackTitle = message.shareTitle,
                                        fallbackSchedule = message.shareSchedule,
                                        mine = mine,
                                        onOpen = onOpenTracking,
                                        onAccept = viewModel::acceptShare,
                                        onDecline = viewModel::declineShare,
                                    )
                                }
                            }
                        } else {
                            MessageBubble(
                                message = message,
                                mine = mine,
                                zone = zone,
                                onCopy = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                android.content.ClipData.newPlainText(
                                                    "message",
                                                    message.text,
                                                ),
                                            ),
                                        )
                                    }
                                },
                                onDelete = { viewModel.deleteForMe(message.id) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            viewModel.onInputChanged(it)
                        },
                        label = stringResource(R.string.chat_input_hint),
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (input.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    notice?.let { FriendNoticeDialog(error = it, onDismiss = viewModel::dismissNotice) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    zone: ZoneId,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (mine) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true },
                    ),
            ) {
                Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.createdAt
                                ?.atZone(zone)
                                ?.format(timeFormatter)
                                .orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) {
                                Color.White.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (mine) {
                            Text(
                                text = when (message.status) {
                                    MessageStatus.READ -> stringResource(R.string.chat_status_read)
                                    MessageStatus.DELIVERED ->
                                        stringResource(R.string.chat_status_delivered)

                                    MessageStatus.SENT -> stringResource(R.string.chat_status_sent)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                            )
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_copy)) },
                    onClick = {
                        menuOpen = false
                        onCopy()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_delete_for_me)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * Vinted-style reminder card inside the conversation: live status from the
 * share document, Accept/Decline for a pending recipient, Open → tracking.
 * The card persists in history; only its status chip changes over time.
 */
@Composable
private fun ReminderShareCard(
    share: com.alertnotes.domain.model.ReminderShare?,
    fallbackTitle: String,
    fallbackSchedule: String,
    mine: Boolean,
    onOpen: () -> Unit,
    onAccept: (com.alertnotes.domain.model.ReminderShare) -> Unit,
    onDecline: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Row {
                // Brand accent bar — the card reads as a reminder, not a bubble.
                Box(
                    modifier = Modifier
                        .padding(0.dp)
                        .size(width = 4.dp, height = 96.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                    Text(
                        text = share?.title?.ifBlank { fallbackTitle } ?: fallbackTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val schedule = share?.scheduleSummary?.ifBlank { fallbackSchedule }
                        ?: fallbackSchedule
                    if (schedule.isNotBlank()) {
                        Text(
                            text = schedule,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                    ) {
                        share?.let { com.alertnotes.features.sharing.StatusChip(status = it.status) }
                        androidx.compose.material3.TextButton(onClick = onOpen) {
                            Text(text = stringResource(R.string.chat_card_open))
                        }
                    }
                    // Acknowledgement evidence — the same proof renderers the
                    // sharing surfaces use, so chat can never disagree.
                    if (share?.ackAt != null) {
                        Text(
                            text = listOfNotNull(
                                stringResource(R.string.sharing_ack_label),
                                share.ackMethod?.let { stringResource(it.labelRes()) },
                                share.ackDelaySeconds?.let { formatResponseDelay(it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                        )
                        if (share.ackPhotoUrl.isNotBlank()) {
                            PhotoProofPreview(url = share.ackPhotoUrl)
                        }
                        share.ackSignature.toReminderDrawingOrNull()?.let { signature ->
                            SignaturePreview(signature = signature)
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
                                text = stringResource(
                                    R.string.chat_evidence_location_unavailable,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (share != null &&
                        share.status == com.alertnotes.domain.model.ShareStatus.PENDING &&
                        !mine
                    ) {
                        Row {
                            androidx.compose.material3.TextButton(onClick = { onAccept(share) }) {
                                Text(text = stringResource(R.string.friends_accept))
                            }
                            androidx.compose.material3.TextButton(onClick = { onDecline(share.id) }) {
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
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    chatRepository: ChatRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** `null` until the first snapshot arrives — the screen shows loading. */
    val conversations: StateFlow<List<ChatConversation>?> = chatRepository.conversations
        .map<List<ChatConversation>, List<ChatConversation>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        _query.value = value
    }
}

/** The dedicated Messages destination: conversations, search, presence. */
@Composable
fun MessagesScreen(
    onOpenChat: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenFriends: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    // Loading (null) renders neither the empty state nor stale content.
    val loaded = conversations
    val visible = loaded.orEmpty().filter {
        query.isBlank() || it.profile.displayName.contains(query, ignoreCase = true) ||
            it.profile.username.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_messages),
                actions = {
                    com.alertnotes.core.ui.components.NotificationBellAction(
                        onOpen = onOpenNotifications,
                    )
                },
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
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            ) {
                item {
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        placeholder = stringResource(R.string.inbox_search_hint),
                    )
                }
                if (loaded == null) {
                    // First snapshot still in flight — never a false "empty".
                    item {
                        SkeletonList(rows = 4)
                    }
                } else if (visible.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MaterialTheme.spacing.huge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.inbox_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            androidx.compose.material3.TextButton(onClick = onOpenFriends) {
                                Text(text = stringResource(R.string.messages_empty_action))
                            }
                        }
                    }
                } else {
                    item {
                        SectionCard(title = stringResource(R.string.inbox_conversations)) {
                            visible.forEach { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    zone = zone,
                                    onClick = { onOpenChat(conversation.otherUid) },
                                    onOpenProfile = { onOpenUser(conversation.otherUid) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemBubble(kind: SystemMessageKind, mine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        ) {
            Text(
                text = stringResource(
                    when (kind) {
                        SystemMessageKind.REMINDER_SHARED ->
                            if (mine) R.string.chat_system_shared_mine else R.string.chat_system_shared_theirs

                        SystemMessageKind.REMINDER_ASSIGNED ->
                            if (mine) R.string.chat_system_assigned_mine else R.string.chat_system_assigned_theirs

                        SystemMessageKind.REMINDER_ACCEPTED -> R.string.chat_system_accepted
                        SystemMessageKind.REMINDER_REJECTED -> R.string.chat_system_rejected
                        SystemMessageKind.REMINDER_ACKNOWLEDGED ->
                            if (mine) R.string.chat_system_acknowledged_mine else R.string.chat_system_acknowledged_theirs

                        SystemMessageKind.NONE -> R.string.chat_system_generic
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.extraSmall,
                ),
            )
        }
    }
}

@Composable
private fun DaySeparator(day: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text = day.format(dayFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
        )
    }
}

/** Bubble-shaped placeholders shown while the first message snapshot loads. */
@Composable
private fun MessageBubbleSkeletons() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        listOf(
            180.dp to Alignment.Start,
            120.dp to Alignment.End,
            220.dp to Alignment.Start,
            150.dp to Alignment.End,
        ).forEach { (width, alignment) ->
            SkeletonLine(
                width = width,
                height = 36.dp,
                modifier = Modifier.align(alignment),
            )
        }
    }
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    friendRepository: FriendRepository,
    sharingRepository: com.alertnotes.domain.repository.ReminderSharingRepository,
) : ViewModel() {

    /** `null` until the first snapshot arrives — the screen shows loading. */
    val friendRequests = friendRepository.incomingRequests
        .map<List<FriendRequestWithProfile>, List<FriendRequestWithProfile>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** `null` until the first snapshot arrives — the screen shows loading. */
    val familyInvitations = friendRepository.incomingFamilyInvitations
        .map<List<FamilyInvitationWithProfile>, List<FamilyInvitationWithProfile>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** `null` until the first snapshot arrives — the screen shows loading. */
    val reminderInvitations = sharingRepository.incomingShares
        .map<List<ReminderShareWithProfile>, List<ReminderShareWithProfile>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The Unified Inbox: everything incoming in one place — friend requests,
 * family invitations, reminder invitations, and conversations — unread
 * first, searchable by name.
 */
@Composable
fun InboxScreen(
    onOpenSharedReminders: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenMessages: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val friendRequestsState by viewModel.friendRequests.collectAsStateWithLifecycle()
    val familyInvitationsState by viewModel.familyInvitations.collectAsStateWithLifecycle()
    val reminderInvitationsState by viewModel.reminderInvitations.collectAsStateWithLifecycle()

    // Loading (any flow still null) renders neither "all clear" nor stale content.
    val isLoading = friendRequestsState == null ||
        familyInvitationsState == null ||
        reminderInvitationsState == null
    val friendRequests = friendRequestsState.orEmpty()
    val familyInvitations = familyInvitationsState.orEmpty()
    val pendingShares = reminderInvitationsState.orEmpty()
        .filter { it.share.status == com.alertnotes.domain.model.ShareStatus.PENDING }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.inbox_title)) },
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
                if (isLoading) {
                    item {
                        SkeletonList(rows = 3)
                    }
                } else if (friendRequests.isEmpty() && familyInvitations.isEmpty() &&
                    pendingShares.isEmpty()
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MaterialTheme.spacing.huge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.inbox_all_clear),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            androidx.compose.material3.TextButton(onClick = onOpenMessages) {
                                Text(text = stringResource(R.string.nav_messages))
                            }
                        }
                    }
                }
                if (friendRequests.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.friends_section_incoming)) {
                            InboxRow(
                                title = stringResource(
                                    R.string.inbox_friend_requests,
                                    friendRequests.size,
                                ),
                                onClick = onOpenFriends,
                            )
                        }
                    }
                }
                if (familyInvitations.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.family_section_incoming)) {
                            InboxRow(
                                title = stringResource(
                                    R.string.inbox_family_invitations,
                                    familyInvitations.size,
                                ),
                                onClick = onOpenFriends,
                            )
                        }
                    }
                }
                if (pendingShares.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.sharing_incoming_title)) {
                            InboxRow(
                                title = stringResource(
                                    R.string.inbox_reminder_invitations,
                                    pendingShares.size,
                                ),
                                onClick = onOpenSharedReminders,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableRow(onClick)
            .padding(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    zone: ZoneId,
    onClick: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onOpenProfile)
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(profile = conversation.profile, size = 44.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.spacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.profile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (conversation.profile.online) {
                    Box(
                        modifier = Modifier
                            .padding(start = MaterialTheme.spacing.small)
                            .size(8.dp)
                            .background(Color(0xFF4CD964), CircleShape),
                    )
                }
            }
            Text(
                text = if (conversation.otherTyping) {
                    stringResource(R.string.chat_typing)
                } else {
                    conversation.lastMessage.ifBlank {
                        stringResource(R.string.chat_system_generic)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = conversation.lastMessageAt
                    ?.toDisplayDateTime(zone)
                    .orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (conversation.unreadCount > 0) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.small,
                            vertical = 2.dp,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableRow(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
