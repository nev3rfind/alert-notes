package com.alertnotes.features.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.AppTextField
import com.alertnotes.core.ui.theme.spacing

@Composable
internal fun ForgotPasswordScreen(
    state: AccountUiState,
    onSubmit: (email: String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.resetEmailSent) {
            Icon(
                imageVector = Icons.Outlined.MarkEmailRead,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.auth_forgot_sent_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.large),
            )
            Text(
                text = stringResource(R.string.auth_forgot_sent_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
            PrimaryButton(
                text = stringResource(R.string.auth_forgot_back_to_login),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.auth_forgot_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.auth_forgot_message),
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
                    imeAction = ImeAction.Done,
                ),
            )
            AuthErrorText(error = state.error)
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            PrimaryButton(
                text = stringResource(R.string.auth_forgot_submit),
                onClick = { onSubmit(email) },
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            ) {
                Text(text = stringResource(R.string.onboarding_back))
            }
        }
    }
}
