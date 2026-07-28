package com.alertnotes.features.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.alertnotes.R
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.theme.spacing

@Composable
internal fun RegisterScreen(
    state: AccountUiState,
    onSubmit: (
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ) -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val errors = state.registerErrors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_register_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_register_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        AppTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = stringResource(R.string.auth_field_display_name),
            isError = errors.displayName != null,
            errorText = errors.displayName?.let { stringResource(it.messageRes()) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        AppTextField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.auth_field_username),
            isError = errors.username != null,
            errorText = errors.username?.let { stringResource(it.messageRes()) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        AppTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_field_email),
            isError = errors.email != null,
            errorText = errors.email?.let { stringResource(it.messageRes()) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.auth_field_password),
            imeAction = ImeAction.Next,
            isError = errors.password != null,
            errorText = errors.password?.let { stringResource(it.messageRes()) },
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        PasswordTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = stringResource(R.string.auth_field_confirm_password),
            imeAction = ImeAction.Done,
            isError = errors.confirmPassword != null,
            errorText = errors.confirmPassword?.let { stringResource(it.messageRes()) },
        )
        AuthErrorText(error = state.error)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        PrimaryButton(
            text = stringResource(R.string.auth_register_submit),
            onClick = { onSubmit(displayName, username, email, password, confirmPassword) },
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        ) {
            Text(
                text = stringResource(R.string.auth_register_have_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSignIn) {
                Text(text = stringResource(R.string.auth_welcome_sign_in))
            }
        }
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.onboarding_back))
        }
    }
}
