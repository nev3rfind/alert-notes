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
internal fun LoginScreen(
    state: AccountUiState,
    onSubmit: (email: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_login_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_login_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        AppTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_field_email),
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
            imeAction = ImeAction.Done,
        )
        AuthErrorText(error = state.error)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        PrimaryButton(
            text = stringResource(R.string.auth_login_submit),
            onClick = { onSubmit(email, password) },
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        ) {
            Text(text = stringResource(R.string.auth_login_forgot))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.auth_login_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCreateAccount) {
                Text(text = stringResource(R.string.auth_welcome_create_account))
            }
        }
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.onboarding_back))
        }
    }
}
