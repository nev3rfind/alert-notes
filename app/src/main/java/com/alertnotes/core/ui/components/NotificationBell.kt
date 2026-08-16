package com.alertnotes.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.NotificationCentreRepository
import com.alertnotes.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Live unread count + visibility for the global notification bell. */
@HiltViewModel
class NotificationBellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    authRepository: AuthRepository,
    notificationCentre: NotificationCentreRepository,
) : ViewModel() {

    val visible: StateFlow<Boolean> = combine(
        settingsRepository.preferences,
        authRepository.authState,
    ) { preferences, user ->
        preferences.appMode == AppMode.ONLINE && user != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val unread: StateFlow<Int> = notificationCentre.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/**
 * The global notification bell: lives in every major screen's top bar,
 * badge animating with each new arrival, one tap into the Notification
 * Centre. Invisible in offline mode — the offline edition has no cloud
 * activity to announce.
 */
@Composable
fun NotificationBellAction(
    onOpen: () -> Unit,
    viewModel: NotificationBellViewModel = hiltViewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    if (!visible) return
    IconButton(onClick = onOpen) {
        BadgedBox(
            badge = {
                if (unread > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        // Pop-in on every count change — the arrival cue.
                        AnimatedContent(
                            targetState = if (unread > MAX_BADGE_COUNT) "99+" else unread.toString(),
                            transitionSpec = {
                                (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                            },
                            label = "bellBadge",
                        ) { label ->
                            Text(text = label)
                        }
                    }
                }
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = stringResource(R.string.notifications_title),
            )
        }
    }
}

private const val MAX_BADGE_COUNT = 99
