package com.alertnotes.features.account

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnotes.domain.model.AppMode
import com.alertnotes.domain.model.AuthError
import com.alertnotes.domain.model.AuthException
import com.alertnotes.domain.repository.AuthRepository
import com.alertnotes.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Client-side validation failures, mapped to messages in the screens. */
enum class FieldError {
    REQUIRED,
    USERNAME_INVALID,
    EMAIL_INVALID,
    PASSWORD_TOO_SHORT,
    PASSWORDS_DO_NOT_MATCH,
}

data class RegisterFieldErrors(
    val displayName: FieldError? = null,
    val username: FieldError? = null,
    val email: FieldError? = null,
    val password: FieldError? = null,
    val confirmPassword: FieldError? = null,
) {
    val hasErrors: Boolean
        get() = listOfNotNull(displayName, username, email, password, confirmPassword)
            .isNotEmpty()
}

data class AccountUiState(
    /** True while an auth request is in flight; blocks repeat submissions. */
    val isSubmitting: Boolean = false,
    /** Backend failure of the last submission, cleared on step changes. */
    val error: AuthError? = null,
    val registerErrors: RegisterFieldErrors = RegisterFieldErrors(),
    val resetEmailSent: Boolean = false,
    /**
     * Set once sign-in or registration fully succeeded and online mode is
     * persisted. Hosts consume this to close the auth flow — completion as
     * observable state, matching the editor's isFinished pattern.
     */
    val isAuthCompleted: Boolean = false,
)

/**
 * Drives the mode choice and the whole email/password auth flow (welcome,
 * login, registration, password reset). Used by the first-run gate and by
 * the settings mode switch; each host gets its own instance.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    /** First-run choice: stay local. Persisting the mode closes the gate. */
    fun chooseOfflineMode() {
        viewModelScope.launch { settingsRepository.setAppMode(AppMode.OFFLINE) }
    }

    fun signIn(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = AuthError.INVALID_CREDENTIALS) }
            return
        }
        submit {
            authRepository.signIn(trimmedEmail, password)
        }
    }

    fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ) {
        val trimmedName = displayName.trim()
        val trimmedUsername = username.trim()
        val trimmedEmail = email.trim()
        val errors = validateRegistration(
            displayName = trimmedName,
            username = trimmedUsername,
            email = trimmedEmail,
            password = password,
            confirmPassword = confirmPassword,
        )
        if (errors.hasErrors) {
            _uiState.update { it.copy(registerErrors = errors, error = null) }
            return
        }
        submit {
            authRepository.register(
                displayName = trimmedName,
                username = trimmedUsername,
                email = trimmedEmail,
                password = password,
            )
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _uiState.update { it.copy(error = AuthError.INVALID_EMAIL) }
            return
        }
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(trimmedEmail)
                _uiState.update { it.copy(isSubmitting = false, resetEmailSent = true) }
            } catch (exception: AuthException) {
                _uiState.update { it.copy(isSubmitting = false, error = exception.error) }
            }
        }
    }

    /**
     * Consumes [AccountUiState.isAuthCompleted] after the host reacted, so a
     * later re-entry into the flow (e.g. after signing out again) starts
     * clean instead of instantly completing.
     */
    fun acknowledgeCompletion() {
        _uiState.update { it.copy(isAuthCompleted = false) }
    }

    /** Clears per-step results when the flow navigates; keeps in-flight state. */
    fun resetTransientState() {
        _uiState.update {
            it.copy(
                error = null,
                registerErrors = RegisterFieldErrors(),
                resetEmailSent = false,
            )
        }
    }

    /** Shared success path: authenticate, then persist online mode. */
    private fun submit(operation: suspend () -> Unit) {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(isSubmitting = true, error = null, registerErrors = RegisterFieldErrors())
        }
        viewModelScope.launch {
            try {
                operation()
                settingsRepository.setAppMode(AppMode.ONLINE)
                _uiState.update { it.copy(isSubmitting = false, isAuthCompleted = true) }
            } catch (exception: AuthException) {
                _uiState.update { it.copy(isSubmitting = false, error = exception.error) }
            }
        }
    }

    private fun validateRegistration(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): RegisterFieldErrors = RegisterFieldErrors(
        displayName = FieldError.REQUIRED.takeIf { displayName.isBlank() },
        username = when {
            username.isBlank() -> FieldError.REQUIRED
            !USERNAME_PATTERN.matches(username) -> FieldError.USERNAME_INVALID
            else -> null
        },
        email = when {
            email.isBlank() -> FieldError.REQUIRED
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> FieldError.EMAIL_INVALID
            else -> null
        },
        password = when {
            password.isBlank() -> FieldError.REQUIRED
            password.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
            else -> null
        },
        confirmPassword = when {
            confirmPassword.isBlank() -> FieldError.REQUIRED
            confirmPassword != password -> FieldError.PASSWORDS_DO_NOT_MATCH
            else -> null
        },
    )

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val USERNAME_MIN_LENGTH = 3
        const val USERNAME_MAX_LENGTH = 20

        /**
         * Letters, digits, underscore; uniqueness is enforced lowercase.
         * Shared with the profile's username editor.
         */
        val USERNAME_PATTERN =
            Regex("^[a-zA-Z0-9_]{$USERNAME_MIN_LENGTH,$USERNAME_MAX_LENGTH}$")
    }
}
