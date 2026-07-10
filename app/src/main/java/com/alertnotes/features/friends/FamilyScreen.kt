package com.alertnotes.features.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.theme.spacing

/**
 * The Family Centre: trusted members with their per-edge permissions,
 * incoming and outgoing invitations, and the future privacy settings.
 * Shares [FriendsViewModel] — same listeners, no duplicated queries.
 */
@Composable
fun FamilyScreen(
    onOpenUser: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val family by viewModel.family.collectAsStateWithLifecycle()
    val incomingFamily by viewModel.incomingFamily.collectAsStateWithLifecycle()
    val outgoingFamily by viewModel.outgoingFamily.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val familyEstablished by viewModel.familyEstablished.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.family_centre_title),
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
            }
        }
    }

    notice?.let { error ->
        FriendNoticeDialog(error = error, onDismiss = viewModel::dismissNotice)
    }
    if (familyEstablished) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissFamilyEstablished,
            shape = MaterialTheme.shapes.extraLarge,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.family_established_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.family_established_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFamilyEstablished) {
                    Text(text = stringResource(R.string.action_done))
                }
            },
        )
    }
}
