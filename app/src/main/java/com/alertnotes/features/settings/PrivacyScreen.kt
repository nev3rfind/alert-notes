package com.alertnotes.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppRadioItem
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.EmptyState
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.components.SettingValueRow
import com.alertnotes.core.ui.components.SkeletonListItem
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.PrivacyAudience
import com.alertnotes.domain.model.PrivacyControl

private val ContentMaxWidth = 640.dp

/** Controls that govern what other people may *do* to this account. */
private val REACH_CONTROLS = listOf(
    PrivacyControl.FRIEND_REQUESTS,
    PrivacyControl.FAMILY_INVITATIONS,
    PrivacyControl.REMINDER_SHARING,
    PrivacyControl.MESSAGE_REQUESTS,
)

/** Controls that govern what other people may *see*. */
private val VISIBILITY_CONTROLS = listOf(
    PrivacyControl.PROFILE_VISIBILITY,
    PrivacyControl.ONLINE_STATUS,
    PrivacyControl.LAST_SEEN,
    PrivacyControl.ANALYTICS_VISIBILITY,
)

/**
 * Privacy & who can reach you.
 *
 * Every row here maps to an audience the security rules enforce server-side —
 * the screen is the way to set them, never the thing that guarantees them.
 * Offline mode has no account and nothing to configure, so the screen says so
 * rather than showing controls that would do nothing.
 */
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf<PrivacyControl?>(null) }

    val savedMessage = stringResource(R.string.privacy_saved)
    val failedMessage = stringResource(R.string.privacy_save_failed)
    LaunchedEffect(state.saved, state.error) {
        when {
            state.saved -> snackbarHostState.showSnackbar(savedMessage)
            state.error -> snackbarHostState.showSnackbar(failedMessage)
            else -> return@LaunchedEffect
        }
        viewModel.consumeFeedback()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.privacy_title),
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                !state.isOnline -> EmptyState(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.privacy_title),
                    message = stringResource(R.string.privacy_offline_notice),
                    modifier = Modifier.align(Alignment.Center),
                )

                state.loading -> PrivacySkeleton(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                )

                else -> PrivacyContent(
                    state = state,
                    onPick = { picking = it },
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    picking?.let { control ->
        AudiencePickerDialog(
            control = control,
            selected = state.settings[control],
            onSelect = { viewModel.setAudience(control, it) },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PrivacyContent(
    state: PrivacyUiState,
    onPick: (PrivacyControl) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
    ) {
        item(key = "reach") {
            SectionCard(title = stringResource(R.string.privacy_section_reach)) {
                REACH_CONTROLS.forEach { control ->
                    AudienceRow(control, state, onPick)
                }
            }
        }
        item(key = "visibility") {
            SectionCard(title = stringResource(R.string.privacy_section_visibility)) {
                VISIBILITY_CONTROLS.forEach { control ->
                    AudienceRow(control, state, onPick)
                }
                // Narrowing profile visibility silently removes the account
                // from search, so say so where the choice is made.
                AnimatedVisibility(
                    visible = !state.settings.isDiscoverable,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.spacing.large),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.privacy_not_discoverable_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudienceRow(
    control: PrivacyControl,
    state: PrivacyUiState,
    onPick: (PrivacyControl) -> Unit,
) {
    val saving = state.savingControl == control
    SettingValueRow(
        title = stringResource(control.titleRes()),
        supportingText = stringResource(control.summaryRes()),
        value = if (saving) {
            stringResource(R.string.privacy_saving)
        } else {
            stringResource(state.settings[control].labelRes())
        },
        onClick = { onPick(control) },
    )
}

/**
 * Audience picker. Each option carries a one-line explanation of who it
 * actually includes — "friends of friends" is not self-evident, and getting a
 * privacy setting wrong because the label was terse is not acceptable.
 */
@Composable
private fun AudiencePickerDialog(
    control: PrivacyControl,
    selected: PrivacyAudience,
    onSelect: (PrivacyAudience) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(control.titleRes()),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(control.summaryRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
                )
                control.options.forEach { audience ->
                    Column {
                        AppRadioItem(
                            title = stringResource(audience.labelRes()),
                            selected = audience == selected,
                            onSelect = {
                                onSelect(audience)
                                onDismiss()
                            },
                        )
                        Text(
                            text = stringResource(audience.hintRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = MaterialTheme.spacing.huge,
                                bottom = MaterialTheme.spacing.small,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Content-shaped placeholder: two cards of four rows, matching the real layout. */
@Composable
private fun PrivacySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
    ) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                repeat(4) { SkeletonListItem() }
            }
        }
    }
}
