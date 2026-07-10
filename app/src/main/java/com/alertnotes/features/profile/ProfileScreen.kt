package com.alertnotes.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alertnotes.BuildConfig
import com.alertnotes.R
import com.alertnotes.core.extensions.toDisplayDateTime
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppToggleRow
import com.alertnotes.core.ui.components.AppTopBar
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SectionCard
import com.alertnotes.core.ui.components.StaggeredEntrance
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.PublicProfile
import com.alertnotes.domain.model.UserPreferences
import com.alertnotes.domain.model.UserProfile
import com.alertnotes.features.settings.ThemePickerDialog
import com.alertnotes.features.settings.labelRes
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val ContentMaxWidth = 640.dp
private val HeroBannerHeight = 128.dp
private val HeroAvatarSize = 96.dp
private val PreviewBannerHeight = 64.dp
private val PreviewAvatarSize = 56.dp

/** LazyColumn index of the personal-information card (quick-action target). */
private const val PERSONAL_SECTION_INDEX = 4

/** Which edit dialog is open; kept as a plain saveable string key. */
private const val DIALOG_NONE = ""
private const val DIALOG_DISPLAY_NAME = "displayName"
private const val DIALOG_USERNAME = "username"
private const val DIALOG_STATUS = "status"
private const val DIALOG_PASSWORD = "password"
private const val DIALOG_AVATAR = "avatar"
private const val DIALOG_THEME = "theme"

/**
 * The profile dashboard: personalised hero (banner theme + floating
 * avatar), live statistics, quick actions, editable details, and a preview
 * of exactly what other users will eventually see.
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val editorImage by viewModel.editorImage.collectAsStateWithLifecycle()

    // The user may return from their email app having just verified.
    LifecycleResumeEffect(Unit) {
        if (uiState is ProfileUiState.Ready) viewModel.refreshEmailVerification()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.nav_profile)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (val state = uiState) {
                ProfileUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                ProfileUiState.Unavailable -> ProfileUnavailable(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.align(Alignment.Center),
                )

                is ProfileUiState.Ready -> ProfileContent(
                    profile = state.profile,
                    preferences = state.preferences,
                    dashboard = dashboard,
                    avatarState = avatarState,
                    editState = editState,
                    viewModel = viewModel,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }

    notice?.let { current ->
        ProfileNoticeDialog(notice = current, onDismiss = viewModel::dismissNotice)
    }
    editorImage?.let { bitmap ->
        AvatarEditorDialog(
            source = bitmap,
            onConfirm = viewModel::confirmAvatarCrop,
            onDismiss = viewModel::cancelAvatarEdit,
        )
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    preferences: UserPreferences,
    dashboard: ProfileDashboard,
    avatarState: AvatarUiState,
    editState: ProfileEditState,
    viewModel: ProfileViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openDialog by rememberSaveable { mutableStateOf(DIALOG_NONE) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val themeColors = profile.publicProfile.bannerTheme.colors()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    // Camera captures land in a FileProvider-shared cache file.
    val captureUri = remember {
        val file = File(context.cacheDir, "camera/avatar_capture.jpg")
        file.parentFile?.mkdirs()
        FileProvider.getUriForFile(context, "com.alertnotes.fileprovider", file)
    }
    val pickFromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let(viewModel::beginAvatarEdit)
    }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) viewModel.beginAvatarEdit(captureUri)
    }

    val quickActions = buildList {
        add(
            QuickAction(Icons.Outlined.Edit, R.string.profile_action_edit_profile) {
                scope.launch { listState.animateScrollToItem(PERSONAL_SECTION_INDEX) }
            },
        )
        add(
            QuickAction(Icons.Outlined.PhotoCamera, R.string.profile_change_photo) {
                openDialog = DIALOG_AVATAR
            },
        )
        add(
            QuickAction(Icons.Outlined.Lock, R.string.profile_change_password) {
                viewModel.resetEditState()
                openDialog = DIALOG_PASSWORD
            },
        )
        if (!profile.security.emailVerified) {
            add(
                QuickAction(Icons.Outlined.MarkEmailRead, R.string.profile_verify_email) {
                    viewModel.sendEmailVerification()
                },
            )
        }
        add(QuickAction(Icons.Outlined.Cloud, R.string.settings_section_mode, onClick = onOpenSettings))
        add(
            QuickAction(
                Icons.Outlined.NotificationsNone,
                R.string.settings_section_notifications,
                onClick = onOpenSettings,
            ),
        )
        add(QuickAction(Icons.Outlined.Shield, R.string.settings_section_privacy, onClick = onOpenSettings))
        add(
            QuickAction(
                Icons.Outlined.Devices,
                R.string.profile_stat_devices,
                comingSoon = true,
            ),
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.large,
            vertical = MaterialTheme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
    ) {
        item {
            StaggeredEntrance(visible = entered, delayMillis = 0) {
                HeroCard(
                    publicProfile = profile.publicProfile,
                    memberSince = profile.privateProfile.memberSince,
                    themeColors = themeColors,
                    avatarState = avatarState,
                    onAvatarClick = { openDialog = DIALOG_AVATAR },
                    // Gentle parallax: the hero trails the scroll slightly.
                    modifier = Modifier.graphicsLayer {
                        translationY = if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset * 0.35f
                        } else {
                            0f
                        }
                    },
                )
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 60) {
                SectionCard(title = stringResource(R.string.profile_section_theme)) {
                    ThemeChipsRow(
                        selected = profile.publicProfile.bannerTheme,
                        onSelect = viewModel::setBannerTheme,
                    )
                }
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 120) {
                Column {
                    SectionLabel(text = stringResource(R.string.profile_section_dashboard))
                    StatsGrid(
                        stats = dashboard.toStatItems(),
                        accent = themeColors.accent,
                    )
                }
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 180) {
                Column {
                    SectionLabel(text = stringResource(R.string.profile_section_quick_actions))
                    QuickActionsGrid(actions = quickActions, accent = themeColors.accent)
                }
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 240) {
                SectionCard(title = stringResource(R.string.profile_section_personal)) {
                    AppListItem(
                        title = stringResource(R.string.auth_field_display_name),
                        supportingText = profile.publicProfile.displayName,
                        leadingIcon = Icons.Outlined.Badge,
                        leadingIconTint = themeColors.accent,
                        onClick = {
                            viewModel.resetEditState()
                            openDialog = DIALOG_DISPLAY_NAME
                        },
                        trailingContent = { EditChevron() },
                    )
                    AppListItem(
                        title = stringResource(R.string.auth_field_username),
                        supportingText = "@${profile.publicProfile.username}",
                        leadingIcon = Icons.Outlined.AlternateEmail,
                        leadingIconTint = themeColors.accent,
                        onClick = {
                            viewModel.resetEditState()
                            openDialog = DIALOG_USERNAME
                        },
                        trailingContent = { EditChevron() },
                    )
                    AppListItem(
                        title = stringResource(R.string.profile_status_message),
                        supportingText = profile.publicProfile.statusMessage
                            .ifBlank { stringResource(R.string.profile_status_empty) },
                        leadingIcon = Icons.Outlined.ChatBubbleOutline,
                        leadingIconTint = themeColors.accent,
                        onClick = {
                            viewModel.resetEditState()
                            openDialog = DIALOG_STATUS
                        },
                        trailingContent = { EditChevron() },
                    )
                }
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 300) {
                AccountSection(profile = profile)
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 360) {
                SecuritySection(
                    profile = profile,
                    onChangePassword = {
                        viewModel.resetEditState()
                        openDialog = DIALOG_PASSWORD
                    },
                    onVerifyEmail = viewModel::sendEmailVerification,
                )
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 420) {
                PreferencesSection(
                    preferences = preferences,
                    onPickTheme = { openDialog = DIALOG_THEME },
                    onToggleNotifications = viewModel::setNotificationsEnabled,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
        item {
            StaggeredEntrance(visible = entered, delayMillis = 480) {
                PublicPreviewSection(
                    publicProfile = profile.publicProfile,
                    themeColors = themeColors,
                )
            }
        }
    }

    when (openDialog) {
        DIALOG_DISPLAY_NAME -> EditFieldDialog(
            title = stringResource(R.string.profile_edit_display_name),
            label = stringResource(R.string.auth_field_display_name),
            initialValue = profile.publicProfile.displayName,
            editState = editState,
            onSave = viewModel::saveDisplayName,
            onDismiss = { openDialog = DIALOG_NONE },
        )

        DIALOG_USERNAME -> EditFieldDialog(
            title = stringResource(R.string.profile_edit_username),
            label = stringResource(R.string.auth_field_username),
            initialValue = profile.publicProfile.username,
            editState = editState,
            supportingText = stringResource(R.string.auth_field_username_invalid),
            onSave = viewModel::saveUsername,
            onDismiss = { openDialog = DIALOG_NONE },
        )

        DIALOG_STATUS -> EditFieldDialog(
            title = stringResource(R.string.profile_edit_status),
            label = stringResource(R.string.profile_status_message),
            initialValue = profile.publicProfile.statusMessage,
            editState = editState,
            supportingText = stringResource(R.string.profile_status_hint),
            onSave = viewModel::saveStatusMessage,
            onDismiss = { openDialog = DIALOG_NONE },
        )

        DIALOG_PASSWORD -> ChangePasswordDialog(
            editState = editState,
            onSubmit = viewModel::changePassword,
            onDismiss = { openDialog = DIALOG_NONE },
        )

        DIALOG_AVATAR -> AvatarOptionsDialog(
            hasPhoto = profile.publicProfile.photoUrl != null,
            onChooseFromGallery = {
                openDialog = DIALOG_NONE
                pickFromGallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onTakePhoto = {
                openDialog = DIALOG_NONE
                takePhoto.launch(captureUri)
            },
            onRemovePhoto = {
                openDialog = DIALOG_NONE
                viewModel.removeAvatar()
            },
            onDismiss = { openDialog = DIALOG_NONE },
        )

        DIALOG_THEME -> ThemePickerDialog(
            currentThemeMode = preferences.themeMode,
            onSelect = { themeMode ->
                viewModel.setThemeMode(themeMode)
                openDialog = DIALOG_NONE
            },
            onDismiss = { openDialog = DIALOG_NONE },
        )
    }
}

private fun ProfileDashboard.toStatItems(): List<StatItem> = buildList {
    add(StatItem(activeReminders.toString(), R.string.profile_stat_active))
    add(StatItem(completed.toString(), R.string.profile_stat_completed))
    add(StatItem(shared.toString(), R.string.profile_stat_shared))
    add(StatItem(friends.toString(), R.string.profile_stat_friends))
    add(StatItem(family.toString(), R.string.profile_stat_family))
    add(StatItem(incomingRequests.toString(), R.string.profile_stat_incoming))
    add(StatItem(incomingFamilyInvitations.toString(), R.string.profile_stat_family_incoming))
    add(StatItem(devices.toString(), R.string.profile_stat_devices))
    accountAgeDays?.let { add(StatItem(it.toString(), R.string.profile_stat_account_age)) }
}

// region Hero

@Composable
private fun HeroCard(
    publicProfile: PublicProfile,
    memberSince: Instant?,
    themeColors: ProfileThemeColors,
    avatarState: AvatarUiState,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column {
                // The personalised banner the avatar floats over.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeroBannerHeight)
                        .background(themeColors.banner),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = HeroAvatarSize / 2 + MaterialTheme.spacing.medium,
                            start = MaterialTheme.spacing.extraLarge,
                            end = MaterialTheme.spacing.extraLarge,
                            bottom = MaterialTheme.spacing.extraLarge,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = publicProfile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "@${publicProfile.username}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PresenceRow(
                        publicProfile = publicProfile,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                    ) {
                        AccountTypeChip(accent = themeColors.accent)
                        if (memberSince != null) {
                            Text(
                                text = stringResource(
                                    R.string.profile_member_since,
                                    memberSince.toMemberSinceDate(),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
                            )
                        }
                    }
                }
            }
            HeroAvatar(
                publicProfile = publicProfile,
                accent = themeColors.accent,
                avatarState = avatarState,
                onClick = onAvatarClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = HeroBannerHeight - HeroAvatarSize / 2),
            )
        }
    }
}

@Composable
private fun PresenceRow(
    publicProfile: PublicProfile,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (publicProfile.online) {
                        Color(0xFF4CD964)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = stringResource(
                if (publicProfile.online) {
                    R.string.profile_presence_online
                } else {
                    R.string.profile_presence_offline
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
        )
        if (!publicProfile.online && publicProfile.lastSeen != null) {
            Text(
                text = stringResource(
                    R.string.profile_last_seen,
                    publicProfile.lastSeen.toDisplayDateTime(ZoneId.systemDefault()),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = MaterialTheme.spacing.small),
            )
        }
    }
}

@Composable
private fun AccountTypeChip(accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color = accent.copy(alpha = 0.14f), shape = MaterialTheme.shapes.extraLarge)
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.profile_account_type_online),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall),
        )
    }
}

@Composable
private fun HeroAvatar(
    publicProfile: PublicProfile,
    accent: Color,
    avatarState: AvatarUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The avatar springs in slightly larger-than-life, then settles.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "avatarEntrance",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(
            modifier = Modifier
                .size(HeroAvatarSize)
                .border(
                    width = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                publicProfile = publicProfile,
                size = HeroAvatarSize,
                monogramStyle = MaterialTheme.typography.headlineMedium,
            )
            when (avatarState) {
                AvatarUiState.Idle -> Unit
                AvatarUiState.Processing -> AvatarScrim {
                    CircularProgressIndicator(color = Color.White)
                }

                is AvatarUiState.Uploading -> AvatarScrim {
                    CircularProgressIndicator(
                        progress = { avatarState.fraction },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = stringResource(R.string.profile_change_photo),
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AvatarScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(HeroAvatarSize)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Photo when set, monogram of the display name's initials otherwise. */
@Composable
private fun Avatar(
    publicProfile: PublicProfile,
    size: Dp,
    monogramStyle: androidx.compose.ui.text.TextStyle,
) {
    if (publicProfile.photoUrl != null) {
        AsyncImage(
            model = publicProfile.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Text(
            text = publicProfile.displayName.toMonogram(),
            style = monogramStyle,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

// endregion

// region Sections

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.small,
        ),
    )
}

@Composable
private fun AccountSection(profile: UserProfile) {
    val zone = ZoneId.systemDefault()
    SectionCard(title = stringResource(R.string.profile_section_account)) {
        AppListItem(
            title = stringResource(R.string.auth_field_email),
            supportingText = profile.privateProfile.email,
            leadingIcon = Icons.Outlined.MarkEmailRead,
            trailingContent = {
                VerificationBadge(verified = profile.security.emailVerified)
            },
        )
        AppListItem(
            title = stringResource(R.string.profile_uid),
            supportingText = profile.uid,
            leadingIcon = Icons.Outlined.VerifiedUser,
        )
        AppListItem(
            title = stringResource(R.string.profile_member_since_row),
            supportingText = profile.privateProfile.memberSince?.toMemberSinceDate()
                ?: stringResource(R.string.editor_status_never),
            leadingIcon = Icons.Outlined.Schedule,
        )
        AppListItem(
            title = stringResource(R.string.profile_last_login),
            supportingText = profile.metadata.lastLogin?.toDisplayDateTime(zone)
                ?: stringResource(R.string.editor_status_never),
            leadingIcon = Icons.Outlined.Schedule,
        )
        AppListItem(
            title = stringResource(R.string.settings_version),
            supportingText = BuildConfig.VERSION_NAME,
            leadingIcon = Icons.Outlined.Cloud,
        )
        AppListItem(
            title = stringResource(R.string.profile_device),
            supportingText = "${profile.privateProfile.deviceModel} · Android " +
                profile.privateProfile.androidVersion,
            leadingIcon = Icons.Outlined.Devices,
        )
    }
}

@Composable
private fun SecuritySection(
    profile: UserProfile,
    onChangePassword: () -> Unit,
    onVerifyEmail: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.profile_section_security)) {
        AppListItem(
            title = stringResource(R.string.profile_change_password),
            supportingText = profile.security.lastPasswordChange
                ?.let {
                    stringResource(
                        R.string.profile_password_changed_at,
                        it.toDisplayDateTime(ZoneId.systemDefault()),
                    )
                }
                ?: stringResource(R.string.profile_change_password_subtitle),
            leadingIcon = Icons.Outlined.Lock,
            onClick = onChangePassword,
            trailingContent = { EditChevron() },
        )
        if (!profile.security.emailVerified) {
            AppListItem(
                title = stringResource(R.string.profile_verify_email),
                supportingText = stringResource(R.string.profile_verify_email_subtitle),
                leadingIcon = Icons.Outlined.MarkEmailRead,
                onClick = onVerifyEmail,
                trailingContent = { EditChevron() },
            )
        }
        AppListItem(
            title = stringResource(R.string.profile_manage_sessions),
            supportingText = stringResource(R.string.profile_manage_sessions_subtitle),
            leadingIcon = Icons.Outlined.Devices,
            trailingContent = { ComingSoonBadge() },
        )
        AppListItem(
            title = stringResource(R.string.profile_mfa),
            supportingText = stringResource(R.string.profile_mfa_subtitle),
            leadingIcon = Icons.Outlined.VerifiedUser,
            trailingContent = { ComingSoonBadge() },
        )
    }
}

@Composable
private fun PreferencesSection(
    preferences: UserPreferences,
    onPickTheme: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.profile_section_preferences)) {
        AppListItem(
            title = stringResource(R.string.settings_theme),
            supportingText = stringResource(preferences.themeMode.labelRes()),
            leadingIcon = Icons.Outlined.Palette,
            onClick = onPickTheme,
            trailingContent = { EditChevron() },
        )
        AppToggleRow(
            title = stringResource(R.string.settings_notifications_toggle),
            supportingText = stringResource(R.string.settings_notifications_subtitle),
            checked = preferences.remindersNotificationsEnabled,
            onCheckedChange = onToggleNotifications,
        )
        AppListItem(
            title = stringResource(R.string.settings_section_mode),
            supportingText = stringResource(R.string.settings_mode_online),
            leadingIcon = Icons.Outlined.Cloud,
            onClick = onOpenSettings,
            trailingContent = { EditChevron() },
        )
        AppListItem(
            title = stringResource(R.string.profile_language),
            supportingText = Locale.getDefault().displayLanguage
                .replaceFirstChar { it.uppercase(Locale.getDefault()) },
            leadingIcon = Icons.Outlined.Language,
        )
        AppListItem(
            title = stringResource(R.string.profile_timezone),
            supportingText = ZoneId.systemDefault().id,
            leadingIcon = Icons.Outlined.Public,
        )
    }
}

/** Exactly the `public` section — what a friend will eventually see. */
@Composable
private fun PublicPreviewSection(
    publicProfile: PublicProfile,
    themeColors: ProfileThemeColors,
) {
    Column {
        SectionLabel(text = stringResource(R.string.profile_public_preview))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PreviewBannerHeight)
                            .background(themeColors.banner),
                    )
                    Column(
                        modifier = Modifier.padding(
                            top = PreviewAvatarSize / 2 + MaterialTheme.spacing.small,
                            start = MaterialTheme.spacing.large,
                            end = MaterialTheme.spacing.large,
                            bottom = MaterialTheme.spacing.large,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = publicProfile.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = MaterialTheme.spacing.small)
                                    .size(8.dp)
                                    .background(
                                        color = if (publicProfile.online) {
                                            Color(0xFF4CD964)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                        }
                        Text(
                            text = "@${publicProfile.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (publicProfile.statusMessage.isNotBlank()) {
                            Text(
                                text = publicProfile.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = MaterialTheme.spacing.large)
                        .offset(y = PreviewBannerHeight - PreviewAvatarSize / 2)
                        .size(PreviewAvatarSize)
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = CircleShape,
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(themeColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(
                        publicProfile = publicProfile,
                        size = PreviewAvatarSize,
                        monogramStyle = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.profile_public_preview_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.large,
                top = MaterialTheme.spacing.small,
            ),
        )
    }
}

// endregion

// region Small pieces

@Composable
private fun ProfileUnavailable(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.profile_unavailable_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.large),
        )
        Text(
            text = stringResource(R.string.profile_unavailable_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
        PrimaryButton(
            text = stringResource(R.string.profile_unavailable_action),
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun VerificationBadge(verified: Boolean) {
    Text(
        text = stringResource(
            if (verified) R.string.profile_email_verified else R.string.profile_email_unverified,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = if (verified) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
    )
}

@Composable
private fun ComingSoonBadge() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.profile_coming_soon),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}

@Composable
private fun EditChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun String.toMonogram(): String = split(" ")
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifBlank { "?" }

private fun Instant.toMemberSinceDate(): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(this)

// endregion
