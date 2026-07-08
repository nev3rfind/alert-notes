package com.alertnotes.features.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.PrimaryCard
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.spacing

/**
 * First-launch choice between the fully local experience and a cloud
 * account. Shown before authentication and before onboarding; the app is
 * unusable until a mode is picked, so both cards carry their whole pitch.
 */
@Composable
fun ModeSelectionScreen(
    onChooseOffline: () -> Unit,
    onChooseOnline: () -> Unit,
) {
    // One-shot staggered entrance: header, then each card slides into place.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EnterTransition(visible = entered, delayMillis = 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.mode_select_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.huge),
                    )
                    Text(
                        text = stringResource(R.string.mode_select_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
            EnterTransition(visible = entered, delayMillis = 120) {
                ModeCard(
                    icon = Icons.Outlined.Smartphone,
                    title = stringResource(R.string.mode_select_offline_title),
                    features = listOf(
                        stringResource(R.string.mode_select_offline_line_local),
                        stringResource(R.string.mode_select_offline_line_no_account),
                        stringResource(R.string.mode_select_offline_line_privacy),
                        stringResource(R.string.mode_select_offline_line_works_offline),
                    ),
                    onChoose = onChooseOffline,
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.mode_select_offline_button),
                        onClick = onChooseOffline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            EnterTransition(visible = entered, delayMillis = 240) {
                ModeCard(
                    icon = Icons.Outlined.Cloud,
                    title = stringResource(R.string.mode_select_online_title),
                    features = listOf(
                        stringResource(R.string.mode_select_online_line_backup),
                        stringResource(R.string.mode_select_online_line_sharing),
                        stringResource(R.string.mode_select_online_line_family),
                        stringResource(R.string.mode_select_online_line_chat),
                        stringResource(R.string.mode_select_online_line_sync),
                    ),
                    onChoose = onChooseOnline,
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.mode_select_online_button),
                        onClick = onChooseOnline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            EnterTransition(visible = entered, delayMillis = 360) {
                Text(
                    text = stringResource(R.string.mode_select_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EnterTransition(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 350, delayMillis = delayMillis),
                initialOffsetY = { it / 6 },
            ),
    ) {
        content()
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    features: List<String>,
    onChoose: () -> Unit,
    button: @Composable () -> Unit,
) {
    PrimaryCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onChoose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
            )
            Column(
                modifier = Modifier.padding(
                    top = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.large,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                features.forEach { feature ->
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall),
                    )
                }
            }
            button()
        }
    }
}
