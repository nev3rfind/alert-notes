package com.alertnotes.core.permissions

/**
 * Result of checking an [AppPermission]. Deliberately coarse — screens only
 * need to know what to display and whether a request would make sense.
 */
enum class PermissionStatus {
    GRANTED,
    NOT_GRANTED,

    /** The Android version of this device grants it implicitly. */
    NOT_REQUIRED,

    /** The device lacks the capability entirely (e.g. no biometric hardware). */
    UNAVAILABLE,
}

/** A permission paired with its current status, ready for display. */
data class PermissionState(
    val permission: AppPermission,
    val status: PermissionStatus,
)
