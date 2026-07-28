package com.alertnotes.features.account

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertnotes.R
import com.alertnotes.domain.model.AuthError
import com.alertnotes.core.ui.theme.spacing

/** Screens of the email/password flow; [WELCOME] is the entry point. */
enum class AuthStep {
    WELCOME,
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
}

/**
 * The whole authentication journey (welcome → login/register → forgot
 * password) as one self-contained state machine, deliberately independent of
 * the main NavHost so both the first-run gate and the settings mode switch
 * can host it. System back walks the steps; back on [AuthStep.WELCOME]
 * leaves via [onExit].
 */
@Composable
fun AuthFlow(
    viewModel: AccountViewModel,
    onExit: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(AuthStep.WELCOME) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Leftover errors from one screen must not greet the next.
    LaunchedEffect(step) { viewModel.resetTransientState() }

    val stepBack: () -> Unit = {
        when (step) {
            AuthStep.WELCOME -> onExit()
            AuthStep.LOGIN, AuthStep.REGISTER -> step = AuthStep.WELCOME
            AuthStep.FORGOT_PASSWORD -> step = AuthStep.LOGIN
        }
    }
    BackHandler(onBack = stepBack)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = fadeIn() + slideInHorizontally { if (forward) it / 3 else -it / 3 }
                val exit = fadeOut() + slideOutHorizontally { if (forward) -it / 3 else it / 3 }
                enter togetherWith exit
            },
            label = "authStep",
            modifier = Modifier.widthIn(max = 560.dp),
        ) { target ->
            when (target) {
                AuthStep.WELCOME -> WelcomeScreen(
                    onSignIn = { step = AuthStep.LOGIN },
                    onCreateAccount = { step = AuthStep.REGISTER },
                    onBack = onExit,
                )

                AuthStep.LOGIN -> LoginScreen(
                    state = state,
                    onSubmit = viewModel::signIn,
                    onForgotPassword = { step = AuthStep.FORGOT_PASSWORD },
                    onCreateAccount = { step = AuthStep.REGISTER },
                    onBack = stepBack,
                )

                AuthStep.REGISTER -> RegisterScreen(
                    state = state,
                    onSubmit = viewModel::register,
                    onSignIn = { step = AuthStep.LOGIN },
                    onBack = stepBack,
                )

                AuthStep.FORGOT_PASSWORD -> ForgotPasswordScreen(
                    state = state,
                    onSubmit = viewModel::sendPasswordReset,
                    onBack = stepBack,
                )
            }
        }
    }
}

/** Friendly message for every backend auth failure. */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.USER_NOT_FOUND -> R.string.auth_error_user_not_found
    AuthError.EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_in_use
    AuthError.USERNAME_TAKEN -> R.string.auth_error_username_taken
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
    AuthError.ACCOUNT_DISABLED -> R.string.auth_error_account_disabled
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}

@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.auth_field_required
    FieldError.USERNAME_INVALID -> R.string.auth_field_username_invalid
    FieldError.EMAIL_INVALID -> R.string.auth_error_invalid_email
    FieldError.PASSWORD_TOO_SHORT -> R.string.auth_field_password_too_short
    FieldError.PASSWORDS_DO_NOT_MATCH -> R.string.auth_field_passwords_mismatch
}

/** Error banner shown above the submit button after a failed request. */
@Composable
internal fun AuthErrorText(error: AuthError?) {
    if (error != null) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.medium),
        )
    }
}
