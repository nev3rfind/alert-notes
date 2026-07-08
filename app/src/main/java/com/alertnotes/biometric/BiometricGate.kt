package com.alertnotes.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * One-shot biometric confirmation for protected reminder dismissal and the
 * app lock. Fingerprint or face where available; optionally falls back to
 * the device PIN / password / pattern.
 *
 * The pre-check queries the EXACT authenticator set the prompt will use:
 *
 * - Nothing usable exists at all (no hardware AND nothing enrolled for that
 *   set) → the action proceeds. A reminder must never become undismissable,
 *   and an app lock restored from a backup onto a device with no screen
 *   lock must not brick the app.
 * - Transient states (sensor busy, security update required, unknown) are
 *   NOT a bypass: the prompt is attempted and its error path keeps the
 *   protection in place until the sensor recovers.
 */
object BiometricGate {

    fun authenticate(
        activity: FragmentActivity,
        allowDeviceCredential: Boolean,
        title: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onError: () -> Unit = {},
    ) {
        val authenticators = if (allowDeviceCredential) {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK
        }
        when (BiometricManager.from(activity.applicationContext).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            -> {
                // The device cannot authenticate with this set at all —
                // there is nothing to protect the action with.
                onSuccess()
                return
            }

            else -> Unit // SUCCESS or transient — show the prompt.
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (allowDeviceCredential) {
                    setAllowedAuthenticators(
                        Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK)
                    setNegativeButtonText(cancelLabel)
                }
            }
            .build()
        prompt.authenticate(info)
    }
}
