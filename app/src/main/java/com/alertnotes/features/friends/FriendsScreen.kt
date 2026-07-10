package com.alertnotes.features.friends

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SearchField
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.domain.model.FamilyInvitationWithProfile
import com.alertnotes.domain.model.FamilyMember
import com.alertnotes.domain.model.FamilyPermissions
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.FriendRequestWithProfile
import com.alertnotes.domain.model.FriendUser
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Debounced live search — feels instant without a query per keystroke. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<FriendUser>> = _query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .mapLatest { term ->
            runCatching { friendRepository.search(term) }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val friends: StateFlow<List<FriendUser>> = friendRepository.friends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incoming: StateFlow<List<FriendRequestWithProfile>> = friendRepository.incomingRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outgoing: StateFlow<List<FriendRequestWithProfile>> = friendRepository.outgoingRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val family: StateFlow<List<FamilyMember>> = friendRepository.family
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomingFamily: StateFlow<List<FamilyInvitationWithProfile>> =
        friendRepository.incomingFamilyInvitations
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outgoingFamily: StateFlow<List<FamilyInvitationWithProfile>> =
        friendRepository.outgoingFamilyInvitations
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun accept(requestId: String) = act { friendRepository.acceptRequest(requestId) }

    fun reject(requestId: String) = act { friendRepository.rejectRequest(requestId) }

    fun cancel(requestId: String) = act { friendRepository.cancelRequest(requestId) }

    fun removeFriend(uid: String) = act { friendRepository.removeFriend(uid) }

    fun acceptFamily(id: String) = act { friendRepository.acceptFamilyInvitation(id) }

    fun declineFamily(id: String) = act { friendRepository.declineFamilyInvitation(id) }

    fun cancelFamily(id: String) = act { friendRepository.cancelFamilyInvitation(id) }

    fun removeFamily(uid: String) = act { friendRepository.removeFamilyMember(uid) }

    fun setFamilyPermissions(uid: String, permissions: FamilyPermissions) =
        act { friendRepository.setFamilyPermissions(uid, permissions) }

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

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

/**
 * The Friend Centre: instant people search, live incoming/outgoing request
 * cards, the friends list, and the blocked-users placeholder — every list
 * driven by snapshot listeners so both sides of an action update at once.
 */
@Composable
fun FriendsScreen(
    onOpenUser: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val family by viewModel.family.collectAsStateWithLifecycle()
    val incomingFamily by viewModel.incomingFamily.collectAsStateWithLifecycle()
    val outgoingFamily by viewModel.outgoingFamily.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    // Relationship badge for a search result, computed from the live lists.
    val badgeFor: @Composable (String) -> String? = { uid ->
        when {
            family.any { it.uid == uid } -> stringResource(R.string.family_state_member)
            friends.any { it.uid == uid } -> stringResource(R.string.friends_state_friends)
            outgoing.any { it.request.toUid == uid } ->
                stringResource(R.string.friends_state_request_sent)
            incoming.any { it.request.fromUid == uid } ->
                stringResource(R.string.friends_state_pending)
            outgoingFamily.any { it.invitation.toUid == uid } ->
                stringResource(R.string.family_state_invite_sent)
            incomingFamily.any { it.invitation.fromUid == uid } ->
                stringResource(R.string.family_state_invite_received)
            else -> null
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.nav_friends)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
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
                    SectionCard(title = stringResource(R.string.friends_section_find)) {
                        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
                            SearchField(
                                query = query,
                                onQueryChange = viewModel::onQueryChange,
                                placeholder = stringResource(R.string.friends_search_hint),
                            )
                            when {
                                query.isBlank() -> Text(
                                    text = stringResource(R.string.friends_search_suggestion),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                                )

                                results.isEmpty() -> EmptyHint(
                                    text = stringResource(R.string.friends_search_empty),
                                )

                                else -> results.forEach { user ->
                                    PersonRow(
                                        profile = user.profile,
                                        badge = badgeFor(user.uid),
                                        onClick = { onOpenUser(user.uid) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (incoming.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.friends_section_incoming)) {
                            incoming.forEach { item ->
                                PersonRow(
                                    profile = item.profile,
                                    supportingOverride = item.request.createdAt?.let {
                                        stringResource(
                                            R.string.friends_request_sent_at,
                                            it.toDisplayDateTime(ZoneId.systemDefault()),
                                        )
                                    },
                                    onClick = { onOpenUser(item.request.fromUid) },
                                ) {
                                    TextButton(onClick = { viewModel.accept(item.request.id) }) {
                                        Text(text = stringResource(R.string.friends_accept))
                                    }
                                    TextButton(onClick = { viewModel.reject(item.request.id) }) {
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
                if (outgoing.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.friends_section_outgoing)) {
                            outgoing.forEach { item ->
                                PersonRow(
                                    profile = item.profile,
                                    supportingOverride = stringResource(R.string.friends_state_pending),
                                    onClick = { onOpenUser(item.request.toUid) },
                                ) {
                                    TextButton(onClick = { viewModel.cancel(item.request.id) }) {
                                        Text(text = stringResource(R.string.friends_cancel_request))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.friends_section_friends)) {
                        if (friends.isEmpty()) {
                            EmptyHint(text = stringResource(R.string.friends_list_empty))
                        } else {
                            friends.forEach { friend ->
                                PersonRow(
                                    profile = friend.profile,
                                    showLastSeen = true,
                                    onClick = { onOpenUser(friend.uid) },
                                ) {
                                    TextButton(onClick = { viewModel.removeFriend(friend.uid) }) {
                                        Text(
                                            text = stringResource(R.string.friends_remove),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (incomingFamily.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.family_section_incoming)) {
                            incomingFamily.forEach { item ->
                                PersonRow(
                                    profile = item.profile,
                                    supportingOverride = item.invitation.message.ifBlank {
                                        stringResource(R.string.family_invite_default)
                                    },
                                    onClick = { onOpenUser(item.invitation.fromUid) },
                                ) {
                                    TextButton(onClick = { viewModel.acceptFamily(item.invitation.id) }) {
                                        Text(text = stringResource(R.string.family_accept))
                                    }
                                    TextButton(onClick = { viewModel.declineFamily(item.invitation.id) }) {
                                        Text(
                                            text = stringResource(R.string.family_decline),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (outgoingFamily.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.family_section_outgoing)) {
                            outgoingFamily.forEach { item ->
                                PersonRow(
                                    profile = item.profile,
                                    supportingOverride = stringResource(R.string.family_state_invite_sent),
                                    onClick = { onOpenUser(item.invitation.toUid) },
                                ) {
                                    TextButton(onClick = { viewModel.cancelFamily(item.invitation.id) }) {
                                        Text(text = stringResource(R.string.family_cancel_invitation))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.family_section_my)) {
                        if (family.isEmpty()) {
                            EmptyHint(text = stringResource(R.string.family_list_empty))
                        } else {
                            family.forEach { member ->
                                FamilyMemberRow(
                                    member = member,
                                    onOpen = { onOpenUser(member.uid) },
                                    onRemove = { viewModel.removeFamily(member.uid) },
                                    onPermissions = { permissions ->
                                        viewModel.setFamilyPermissions(member.uid, permissions)
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.family_section_settings)) {
                        AppListItem(
                            title = stringResource(R.string.family_settings_privacy),
                            supportingText = stringResource(R.string.profile_coming_soon),
                            leadingIcon = Icons.Outlined.Shield,
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.friends_section_blocked)) {
                        AppListItem(
                            title = stringResource(R.string.friends_blocked_placeholder),
                            supportingText = stringResource(R.string.profile_coming_soon),
                            leadingIcon = Icons.Outlined.Block,
                        )
                    }
                }
            }
        }
    }

    notice?.let { error ->
        FriendNoticeDialog(error = error, onDismiss = viewModel::dismissNotice)
    }
}

/** Shared person card row: avatar, identity, presence, action slot. */
@Composable
internal fun PersonRow(
    profile: PublicProfile,
    onClick: () -> Unit,
    supportingOverride: String? = null,
    showLastSeen: Boolean = false,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val supporting = supportingOverride ?: buildString {
        append("@${profile.username}")
        if (profile.statusMessage.isNotBlank()) append(" · ${profile.statusMessage}")
        if (showLastSeen && !profile.online && profile.lastSeen != null) {
            append(
                " · " + stringResource(
                    R.string.profile_last_seen,
                    profile.lastSeen.toDisplayDateTime(ZoneId.systemDefault()),
                ),
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(profile = profile, size = 44.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.spacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .padding(start = MaterialTheme.spacing.small)
                        .size(8.dp)
                        .background(
                            color = if (profile.online) {
                                Color(0xFF4CD964)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                RelationshipBadge(
                    text = badge,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            TextButton(onClick = onClick) {
                Text(text = stringResource(R.string.friends_view_profile))
            }
        }
    }
}

@Composable
private fun RelationshipBadge(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}

/**
 * A family member with an expandable permissions editor — the per-edge
 * toggles that will later gate automatic reminder delivery, chat, and
 * presence visibility.
 */
@Composable
private fun FamilyMemberRow(
    member: FamilyMember,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onPermissions: (FamilyPermissions) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        PersonRow(
            profile = member.profile,
            showLastSeen = member.permissions.canViewLastSeen,
            onClick = onOpen,
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.family_permissions_hide else R.string.family_permissions_edit,
                    ),
                )
            }
        }
        if (expanded) {
            val p = member.permissions
            PermissionToggle(
                label = stringResource(R.string.family_perm_auto_receive),
                checked = p.autoReceiveReminders,
            ) { onPermissions(p.copy(autoReceiveReminders = it)) }
            PermissionToggle(
                label = stringResource(R.string.family_perm_send_without_approval),
                checked = p.canSendWithoutApproval,
            ) { onPermissions(p.copy(canSendWithoutApproval = it)) }
            PermissionToggle(
                label = stringResource(R.string.family_perm_send_with_approval),
                checked = p.canSendWithApproval,
            ) { onPermissions(p.copy(canSendWithApproval = it)) }
            PermissionToggle(
                label = stringResource(R.string.family_perm_view_online),
                checked = p.canViewOnlineStatus,
            ) { onPermissions(p.copy(canViewOnlineStatus = it)) }
            PermissionToggle(
                label = stringResource(R.string.family_perm_view_last_seen),
                checked = p.canViewLastSeen,
            ) { onPermissions(p.copy(canViewLastSeen = it)) }
            PermissionToggle(
                label = stringResource(R.string.family_perm_start_chat),
                checked = p.canStartChat,
            ) { onPermissions(p.copy(canStartChat = it)) }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.family_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Small circular avatar: photo when set, monogram otherwise. */
@Composable
internal fun FriendAvatar(profile: PublicProfile, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (profile.photoUrl != null) {
            AsyncImage(
                model = profile.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = profile.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium),
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.PersonSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
        )
    }
}

@Composable
internal fun FriendNoticeDialog(error: FriendError, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(R.string.profile_notice_error_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(
                    when (error) {
                        FriendError.SELF_REQUEST -> R.string.friends_error_self
                        FriendError.ALREADY_FRIENDS -> R.string.friends_error_already_friends
                        FriendError.ALREADY_PENDING -> R.string.friends_error_pending
                        FriendError.NOT_FRIENDS -> R.string.friends_error_not_friends
                        FriendError.ALREADY_FAMILY -> R.string.friends_error_already_family
                        FriendError.NETWORK -> R.string.auth_error_network
                        FriendError.UNKNOWN -> R.string.auth_error_unknown
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_done))
            }
        },
    )
}

