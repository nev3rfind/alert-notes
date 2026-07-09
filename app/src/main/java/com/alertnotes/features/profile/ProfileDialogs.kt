package com.alertnotes.features.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppListItem
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.AuthError
import com.alertnotes.features.account.FieldError
import com.alertnotes.features.account.PasswordTextField
import com.alertnotes.features.account.messageRes

/**
 * Single-field editor shared by display name, username, and status message.
 * Saves through [onSave]; closes itself when [ProfileEditState.completedAt]
 * advances (the save landed in Firestore).
 */
@Composable
internal fun EditFieldDialog(
    title: String,
    label: String,
    initialValue: String,
    editState: ProfileEditState,
    supportingText: String? = null,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val openedAt = remember { editState.completedAt }
    LaunchedEffect(editState.completedAt) {
        if (editState.completedAt > openedAt) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!editState.isSubmitting) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = label,
                    isError = editState.fieldError != null,
                    errorText = editState.fieldError?.let { stringResource(it.messageRes()) },
                )
                if (supportingText != null && editState.fieldError == null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                    )
                }
                EditErrorText(error = editState.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = !editState.isSubmitting) {
                if (editState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editState.isSubmitting) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Current password confirms identity; the new one is typed twice. */
@Composable
internal fun ChangePasswordDialog(
    editState: ProfileEditState,
    onSubmit: (current: String, new: String, confirm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val openedAt = remember { editState.completedAt }
    LaunchedEffect(editState.completedAt) {
        if (editState.completedAt > openedAt) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!editState.isSubmitting) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(R.string.profile_change_password),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                PasswordTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = stringResource(R.string.profile_current_password),
                    imeAction = ImeAction.Next,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                PasswordTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = stringResource(R.string.profile_new_password),
                    imeAction = ImeAction.Next,
                    isError = editState.fieldError == FieldError.PASSWORD_TOO_SHORT,
                    errorText = editState.fieldError
                        ?.takeIf { it == FieldError.PASSWORD_TOO_SHORT }
                        ?.let { stringResource(it.messageRes()) },
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                PasswordTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = stringResource(R.string.auth_field_confirm_password),
                    imeAction = ImeAction.Done,
                    isError = editState.fieldError == FieldError.PASSWORDS_DO_NOT_MATCH ||
                        editState.fieldError == FieldError.REQUIRED,
                    errorText = editState.fieldError
                        ?.takeIf {
                            it == FieldError.PASSWORDS_DO_NOT_MATCH || it == FieldError.REQUIRED
                        }
                        ?.let { stringResource(it.messageRes()) },
                )
                EditErrorText(error = editState.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(current, new, confirm) },
                enabled = !editState.isSubmitting,
            ) {
                if (editState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editState.isSubmitting) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** "Change photo" chooser: gallery, camera, and (when set) removal. */
@Composable
internal fun AvatarOptionsDialog(
    hasPhoto: Boolean,
    onChooseFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(R.string.profile_change_photo),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                AppListItem(
                    title = stringResource(R.string.profile_photo_gallery),
                    leadingIcon = Icons.Outlined.PhotoLibrary,
                    onClick = onChooseFromGallery,
                )
                AppListItem(
                    title = stringResource(R.string.profile_photo_camera),
                    leadingIcon = Icons.Outlined.PhotoCamera,
                    onClick = onTakePhoto,
                )
                if (hasPhoto) {
                    AppListItem(
                        title = stringResource(R.string.profile_photo_remove),
                        leadingIcon = Icons.Outlined.HideImage,
                        leadingIconTint = MaterialTheme.colorScheme.error,
                        onClick = onRemovePhoto,
                    )
                }
                Text(
                    text = stringResource(R.string.profile_photo_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** One-shot outcome notices (verification sent, upload failed, …). */
@Composable
internal fun ProfileNoticeDialog(
    notice: ProfileNotice,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(notice.titleRes()),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(notice.messageRes()),
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

@Composable
private fun EditErrorText(error: AuthError?) {
    if (error != null) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
        )
    }
}

private fun ProfileNotice.titleRes(): Int = when (this) {
    ProfileNotice.AVATAR_FAILED -> R.string.profile_notice_avatar_failed_title
    ProfileNotice.VERIFICATION_SENT -> R.string.profile_notice_verification_title
    ProfileNotice.PASSWORD_CHANGED -> R.string.profile_notice_password_title
    ProfileNotice.GENERIC_ERROR, ProfileNotice.NETWORK_ERROR ->
        R.string.profile_notice_error_title
}

private fun ProfileNotice.messageRes(): Int = when (this) {
    ProfileNotice.AVATAR_FAILED -> R.string.profile_notice_avatar_failed_message
    ProfileNotice.VERIFICATION_SENT -> R.string.profile_notice_verification_message
    ProfileNotice.PASSWORD_CHANGED -> R.string.profile_notice_password_message
    ProfileNotice.GENERIC_ERROR -> R.string.auth_error_unknown
    ProfileNotice.NETWORK_ERROR -> R.string.auth_error_network
}
