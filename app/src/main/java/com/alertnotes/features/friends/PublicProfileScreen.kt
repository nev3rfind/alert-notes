package com.alertnotes.features.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.navigation.PublicProfileRoute
import com.alertnotes.core.ui.components.AppDialog
import com.alertnotes.core.ui.components.AppOutlinedButton
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.FamilyState
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.FriendshipState
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.PublicStatistics
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.features.profile.colors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Loading resolves to Ready or, after a grace period, Unavailable. */
sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState
    data class Ready(val profile: PublicProfile) : PublicProfileUiState

    /** Missing document, revoked rules, or no connection — never a spinner. */
    data object Unavailable : PublicProfileUiState
}

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uid: String = savedStateHandle.toRoute<PublicProfileRoute>().uid

    private val retry = MutableStateFlow(0)

    /**
     * The fix for the endless-spinner bug: listener errors used to surface
     * as null forever. Loading now times out into an explicit Unavailable
     * state the user can retry from.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PublicProfileUiState> = retry
        .flatMapLatest {
            friendRepository.observePublicProfile(uid)
                .map<PublicProfile?, PublicProfileUiState> { profile ->
                    if (profile == null) {
                        PublicProfileUiState.Loading
                    } else {
                        PublicProfileUiState.Ready(profile)
                    }
                }
                .onStart { emit(PublicProfileUiState.Loading) }
                .transformLatest { state ->
                    emit(state)
                    if (state is PublicProfileUiState.Loading) {
                        delay(LOAD_GRACE_MILLIS)
                        emit(PublicProfileUiState.Unavailable)
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PublicProfileUiState.Loading)

    val friendshipState: StateFlow<FriendshipState?> = friendRepository
        .observeFriendshipState(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val familyState: StateFlow<FamilyState?> = friendRepository
        .observeFamilyState(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _statistics = MutableStateFlow(PublicStatistics())
    val statistics: StateFlow<PublicStatistics> = _statistics.asStateFlow()

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    init {
        viewModelScope.launch { _statistics.value = friendRepository.publicStatistics(uid) }
    }

    fun retryLoad() {
        retry.value += 1
    }

    fun sendRequest() = act { friendRepository.sendRequest(uid) }

    /** Deterministic ids make request references derivable on both sides. */
    fun cancelRequest() = act { friendRepository.cancelRequest("${requireMe()}_$uid") }

    fun acceptRequest() = act { friendRepository.acceptRequest("${uid}_${requireMe()}") }

    fun rejectRequest() = act { friendRepository.rejectRequest("${uid}_${requireMe()}") }

    fun removeFriend() = act { friendRepository.removeFriend(uid) }

    fun inviteToFamily(message: String) = act { friendRepository.inviteToFamily(uid, message) }

    fun cancelFamilyInvitation() = act {
        friendRepository.cancelFamilyInvitation("${requireMe()}_$uid")
    }

    fun acceptFamilyInvitation() = act {
        friendRepository.acceptFamilyInvitation("${uid}_${requireMe()}")
    }

    fun declineFamilyInvitation() = act {
        friendRepository.declineFamilyInvitation("${uid}_${requireMe()}")
    }

    fun removeFamilyMember() = act { friendRepository.removeFamilyMember(uid) }

    /**
     * Whether the signed-in user has blocked this account.
     *
     * Blocking was only reachable from the friends list, so the one place a
     * user actually meets a stranger - their profile, reached from search or
     * from a request - offered no way to stop them.
     */
    val isBlocked: StateFlow<Boolean> = friendRepository.blockedUsers
        .map { blocked -> blocked.any { it.uid == uid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun block() = act { friendRepository.blockUser(uid) }

    fun unblock() = act { friendRepository.unblockUser(uid) }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun requireMe(): String =
        authRepository.currentUser?.uid ?: throw FriendException(FriendError.UNKNOWN)

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
        const val LOAD_GRACE_MILLIS = 6_000L
    }
}

/**
 * Another user's profile — strictly the public section plus shareable
 * statistics and relationship actions. Email, uid, and device data never
 * appear here.
 */
@Composable
fun PublicProfileScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: PublicProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val friendState by viewModel.friendshipState.collectAsStateWithLifecycle()
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val isBlocked by viewModel.isBlocked.collectAsStateWithLifecycle()
    var confirmBlock by rememberSaveable { mutableStateOf(false) }

    if (confirmBlock) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmBlock = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(text = stringResource(R.string.friends_block_confirm_title_generic)) },
            text = { Text(text = stringResource(R.string.friends_block_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.block()
                        confirmBlock = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.friends_block),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.friends_public_profile_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (friendState != FriendshipState.SELF) {
                        IconButton(
                            onClick = {
                                if (isBlocked) viewModel.unblock() else confirmBlock = true
                            },
                        ) {
                            Icon(
                                imageVector = if (isBlocked) {
                                    Icons.Outlined.LockOpen
                                } else {
                                    Icons.Outlined.Block
                                },
                                contentDescription = stringResource(
                                    if (isBlocked) R.string.friends_unblock else R.string.friends_block,
                                ),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (val state = uiState) {
                PublicProfileUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                PublicProfileUiState.Unavailable -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(MaterialTheme.spacing.huge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.friends_profile_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.friends_profile_retry),
                        onClick = viewModel::retryLoad,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.large),
                    )
                }

                is PublicProfileUiState.Ready -> Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(MaterialTheme.spacing.large),
                ) {
                    PublicHero(
                        profile = state.profile,
                        friendState = friendState,
                        familyState = familyState,
                    )
                    PublicStatsRow(
                        statistics = statistics,
                        accent = state.profile.bannerTheme.colors().accent,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraLarge),
                    )
                    if (friendState == FriendshipState.FRIENDS || familyState == FamilyState.FAMILY) {
                        PrimaryButton(
                            text = stringResource(R.string.friends_message),
                            onClick = { onOpenChat(viewModel.uid) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MaterialTheme.spacing.extraLarge),
                        )
                    }
                    RelationshipActions(
                        friendState = friendState,
                        familyState = familyState,
                        viewModel = viewModel,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraLarge),
                    )
                }
            }
        }
    }

    notice?.let { error ->
        FriendNoticeDialog(error = error, onDismiss = viewModel::dismissNotice)
    }
}

@Composable
private fun PublicHero(
    profile: PublicProfile,
    friendState: FriendshipState?,
    familyState: FamilyState?,
) {
    val themeColors = profile.bannerTheme.colors()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(themeColors.banner),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 40.dp + MaterialTheme.spacing.medium,
                            start = MaterialTheme.spacing.extraLarge,
                            end = MaterialTheme.spacing.extraLarge,
                            bottom = MaterialTheme.spacing.extraLarge,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "@${profile.username}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (profile.statusMessage.isNotBlank()) {
                        Text(
                            text = profile.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (profile.online) {
                                        Color(0xFF4CD964)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = stringResource(
                                if (profile.online) {
                                    R.string.profile_presence_online
                                } else {
                                    R.string.profile_presence_offline
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                        )
                        if (!profile.online && profile.lastSeen != null) {
                            Text(
                                text = stringResource(
                                    R.string.profile_last_seen,
                                    profile.lastSeen.toDisplayDateTime(ZoneId.systemDefault()),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                    ) {
                        if (friendState == FriendshipState.FRIENDS) {
                            RelationBadge(text = stringResource(R.string.friends_state_friends))
                        }
                        if (familyState == FamilyState.FAMILY) {
                            RelationBadge(text = stringResource(R.string.family_state_member))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 112.dp - 40.dp),
            ) {
                FriendAvatar(profile = profile, size = 80.dp)
            }
        }
    }
}

@Composable
private fun PublicStatsRow(
    statistics: PublicStatistics,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        PublicStat(statistics.friendCount.toString(), R.string.profile_stat_friends, accent, Modifier.weight(1f))
        PublicStat(statistics.familyCount.toString(), R.string.profile_stat_family, accent, Modifier.weight(1f))
        PublicStat("—", R.string.profile_stat_shared, accent, Modifier.weight(1f))
        PublicStat("—", R.string.family_stat_shared_completed, accent, Modifier.weight(1f))
    }
}

@Composable
private fun PublicStat(value: String, labelRes: Int, accent: Color, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RelationshipActions(
    friendState: FriendshipState?,
    familyState: FamilyState?,
    viewModel: PublicProfileViewModel,
    modifier: Modifier = Modifier,
) {
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var inviteMessage by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        when (friendState) {
            null, FriendshipState.SELF -> if (friendState == FriendshipState.SELF) {
                Text(
                    text = stringResource(R.string.friends_state_self),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FriendshipState.NONE -> PrimaryButton(
                text = stringResource(R.string.friends_add),
                onClick = viewModel::sendRequest,
                modifier = Modifier.fillMaxWidth(),
            )

            FriendshipState.REQUEST_SENT -> AppOutlinedButton(
                text = stringResource(R.string.friends_cancel_request),
                onClick = viewModel::cancelRequest,
                modifier = Modifier.fillMaxWidth(),
            )

            FriendshipState.REQUEST_RECEIVED -> {
                PrimaryButton(
                    text = stringResource(R.string.friends_accept),
                    onClick = viewModel::acceptRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.friends_reject),
                    onClick = viewModel::rejectRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FriendshipState.FRIENDS -> {
                // Family actions unlock once you are friends.
                when (familyState) {
                    null, FamilyState.NONE -> PrimaryButton(
                        text = stringResource(R.string.family_invite),
                        onClick = { showInviteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FamilyState.INVITE_SENT -> AppOutlinedButton(
                        text = stringResource(R.string.family_cancel_invitation),
                        onClick = viewModel::cancelFamilyInvitation,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FamilyState.INVITE_RECEIVED -> {
                        PrimaryButton(
                            text = stringResource(R.string.family_accept),
                            onClick = viewModel::acceptFamilyInvitation,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SecondaryButton(
                            text = stringResource(R.string.family_decline),
                            onClick = viewModel::declineFamilyInvitation,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FamilyState.FAMILY -> AppOutlinedButton(
                        text = stringResource(R.string.family_remove),
                        onClick = viewModel::removeFamilyMember,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = viewModel::removeFriend) {
                    Text(
                        text = stringResource(R.string.friends_remove_friend),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showInviteDialog) {
        AppDialog(
            title = stringResource(R.string.family_invite),
            onDismiss = { showInviteDialog = false },
            confirmText = stringResource(R.string.family_invite_send),
            onConfirm = {
                showInviteDialog = false
                viewModel.inviteToFamily(inviteMessage)
                inviteMessage = ""
            },
        ) {
            Text(
                text = stringResource(R.string.family_invite_explainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
            )
            AppTextField(
                value = inviteMessage,
                onValueChange = { inviteMessage = it },
                label = stringResource(R.string.family_invite_message),
            )
        }
    }
}

@Composable
private fun RelationBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}
