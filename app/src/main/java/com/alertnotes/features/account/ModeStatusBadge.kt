package com.alertnotes.features.account

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alertnotes.R
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the badge shows: the chosen mode and who is signed in, if anyone. */
data class ModeStatus(
    val mode: AppMode? = null,
    val accountLabel: String? = null,
)

@HiltViewModel
class ModeStatusViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val status: StateFlow<ModeStatus> = combine(
        settingsRepository.preferences,
        authRepository.authState,
    ) { preferences, user ->
        ModeStatus(
            mode = preferences.appMode,
            accountLabel = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModeStatus(),
    )
}

/**
 * Compact "📱 offline / ☁ online" indicator for navigation surfaces (the
 * home dashboard header). Self-contained on purpose: dropping it into a
 * screen adds no state to that screen's ViewModel. Renders nothing until the
 * mode choice is made — the first-run gate covers that window anyway. Pass
 * [onClick] to make the chip a shortcut to wherever the mode is managed.
 */
@Composable
fun ModeStatusBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    viewModel: ModeStatusViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val mode = status.mode ?: return

    val online = mode == AppMode.ONLINE
    val label = if (online) {
        status.accountLabel
            ?.let { stringResource(R.string.settings_mode_signed_in_as, it) }
            ?: stringResource(R.string.settings_mode_not_signed_in)
    } else {
        stringResource(R.string.settings_mode_offline_subtitle)
    }
    val clickableModifier = if (onClick != null) {
        // Clip first so the ripple respects the pill shape.
        Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .then(clickableModifier)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (online) Icons.Outlined.Cloud else Icons.Outlined.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(
                if (online) R.string.settings_mode_online else R.string.settings_mode_offline,
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall),
        )
        Text(
            text = " · $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Premium connection card for the home dashboard: mode, account, and a live
 * animated indicator. Online pulses green (cloud features reachable); offline
 * shows a steady neutral dot. Tapping opens wherever the mode is managed.
 * Renders nothing until the mode choice is made.
 */
@Composable
fun ConnectionStatusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    viewModel: ModeStatusViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val mode = status.mode ?: return
    val online = mode == AppMode.ONLINE

    val clickable = if (onClick != null) {
        Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickable),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingIndicator(active = online)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(
                        if (online) R.string.connection_online else R.string.connection_offline,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (online) {
                        status.accountLabel
                            ?.let { stringResource(R.string.settings_mode_signed_in_as, it) }
                            ?: stringResource(R.string.connection_online_subtitle)
                    } else {
                        stringResource(R.string.connection_offline_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (online) Icons.Outlined.Cloud else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (online) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Small dot that gently pulses while [active]; steady and dim otherwise. */
@Composable
private fun PulsingIndicator(active: Boolean) {
    val online = Color(0xFF4CD964)
    if (!active) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
                .background(online.copy(alpha = 0.25f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(online, CircleShape),
        )
    }
}
