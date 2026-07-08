package com.alertnotes.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device biometric capability, independent of any app setting. The prompt
 * flow that consumes this arrives with the app-lock phase.
 */
enum class BiometricAvailability {
    AVAILABLE,

    /** Hardware exists but the user has not enrolled a fingerprint/face. */
    NOT_ENROLLED,

    /** No usable biometric hardware on this device. */
    UNAVAILABLE,
}

@Singleton
class BiometricStatusProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
}
