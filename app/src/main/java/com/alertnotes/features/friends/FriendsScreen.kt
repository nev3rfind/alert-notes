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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun accept(requestId: String) = act { friendRepository.acceptRequest(requestId) }

    fun reject(requestId: String) = act { friendRepository.rejectRequest(requestId) }

    fun cancel(requestId: String) = act { friendRepository.cancelRequest(requestId) }

    fun removeFriend(uid: String) = act { friendRepository.removeFriend(uid) }

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
    val notice by viewModel.notice.collectAsStateWithLifecycle()

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

