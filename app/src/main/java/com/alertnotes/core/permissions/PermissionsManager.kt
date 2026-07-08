package com.alertnotes.core.permissions

import android.content.Intent

/**
 * Single source of truth for permission state, plus the system-settings
 * deep link for each capability so the Permission Center can send users to
 * exactly the right screen.
 */
interface PermissionsManager {

    fun statusOf(permission: AppPermission): PermissionStatus

    /** Status of every [AppPermission], in declaration order. */
    fun allStatuses(): List<PermissionState>

    /** System settings screen for [permission], or null when none applies. */
    fun settingsIntent(permission: AppPermission): Intent?
}
