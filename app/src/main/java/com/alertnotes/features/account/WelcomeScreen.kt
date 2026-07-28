package com.alertnotes.features.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alertnotes.R
import com.alertnotes.core.ui.components.PrimaryButton
import com.alertnotes.core.ui.components.SecondaryButton
import com.alertnotes.core.ui.theme.spacing
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.features.alerts.AlertIconBadge

/** Online-mode entry point: pick between signing in and creating an account. */
@Composable
internal fun WelcomeScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlertIconBadge(priority = ReminderPriority.NORMAL, size = 72.dp)
        Text(
            text = stringResource(R.string.auth_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.large),
        )
        Text(
            text = stringResource(R.string.auth_welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        PrimaryButton(
            text = stringResource(R.string.auth_welcome_sign_in),
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        SecondaryButton(
            text = stringResource(R.string.auth_welcome_create_account),
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        ) {
            Text(text = stringResource(R.string.auth_welcome_back))
        }
    }
}
