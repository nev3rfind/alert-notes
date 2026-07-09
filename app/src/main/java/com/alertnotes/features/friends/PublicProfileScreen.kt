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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
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
import com.alertnotes.core.ui.components.AppOutlinedButton
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.FriendError
import com.alertnotes.domain.model.FriendException
import com.alertnotes.domain.model.FriendshipState
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.FriendRepository
import com.alertnotes.features.profile.colors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val uid: String = savedStateHandle.toRoute<PublicProfileRoute>().uid

    val profile: StateFlow<PublicProfile?> = friendRepository.observePublicProfile(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val friendshipState: StateFlow<FriendshipState?> = friendRepository
        .observeFriendshipState(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notice = MutableStateFlow<FriendError?>(null)
    val notice: StateFlow<FriendError?> = _notice.asStateFlow()

    fun sendRequest() = act { friendRepository.sendRequest(uid) }

    /** Deterministic ids make request references derivable on both sides. */
    fun cancelRequest() = act {
        friendRepository.cancelRequest("${requireMe()}_$uid")
    }

    fun acceptRequest() = act {
        friendRepository.acceptRequest("${uid}_${requireMe()}")
    }

    fun rejectRequest() = act {
        friendRepository.rejectRequest("${uid}_${requireMe()}")
    }

    fun removeFriend() = act { friendRepository.removeFriend(uid) }

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
}

/**
 * Another user's profile — strictly the public section (banner, avatar,
 * identity, status, presence), plus a friendship button that follows the
 * live relationship state. Email, uid, and device data never appear here.
 */
@Composable
fun PublicProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: PublicProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val state by viewModel.friendshipState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    androidx.compose.material3.Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.friends_public_profile_title),
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val current = profile
            if (current == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(MaterialTheme.spacing.large),
                ) {
                    PublicHero(profile = current)
                    FriendshipActions(
                        state = state,
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
private fun PublicHero(profile: PublicProfile) {
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
private fun FriendshipActions(
    state: FriendshipState?,
    viewModel: PublicProfileViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        when (state) {
            null -> Unit

            FriendshipState.SELF -> Text(
                text = stringResource(R.string.friends_state_self),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FriendshipState.NONE -> PrimaryButton(
                text = stringResource(R.string.friends_add),
                onClick = viewModel::sendRequest,
                modifier = Modifier.fillMaxWidth(),
            )

            FriendshipState.REQUEST_SENT -> {
                StateBadge(text = stringResource(R.string.friends_state_request_sent))
                AppOutlinedButton(
                    text = stringResource(R.string.friends_cancel_request),
                    onClick = viewModel::cancelRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FriendshipState.REQUEST_RECEIVED -> {
                StateBadge(text = stringResource(R.string.friends_state_pending))
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
                StateBadge(text = stringResource(R.string.friends_state_friends))
                AppOutlinedButton(
                    text = stringResource(R.string.friends_remove),
                    onClick = viewModel::removeFriend,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StateBadge(text: String) {
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
