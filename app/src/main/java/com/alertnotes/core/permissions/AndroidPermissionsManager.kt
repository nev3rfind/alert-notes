package com.alertnotes.core.permissions

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.alertnotes.biometric.BiometricAvailability
import com.alertnotes.biometric.BiometricStatusProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val biometricStatusProvider: BiometricStatusProvider,
) : PermissionsManager {

    override fun statusOf(permission: AppPermission): PermissionStatus = when (permission) {
        AppPermission.NOTIFICATIONS -> notificationsStatus()
        AppPermission.EXACT_ALARMS -> exactAlarmsStatus()
        AppPermission.DISPLAY_OVER_OTHER_APPS -> overlayStatus()
        AppPermission.FULL_SCREEN_ALERTS -> fullScreenIntentStatus()
        AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> batteryOptimizationStatus()
        AppPermission.BIOMETRIC -> biometricStatus()
    }

    override fun allStatuses(): List<PermissionState> =
        AppPermission.entries.map { PermissionState(it, statusOf(it)) }

    override fun settingsIntent(permission: AppPermission): Intent? = when (permission) {
        AppPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        AppPermission.EXACT_ALARMS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
            } else {
                null
            }

        AppPermission.DISPLAY_OVER_OTHER_APPS ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:${context.packageName}"))

        AppPermission.FULL_SCREEN_ALERTS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:${context.packageName}"))
            } else {
                null
            }

        // The non-forcing list screen, per Play policy: guide, never demand.
        AppPermission.IGNORE_BATTERY_OPTIMIZATIONS ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        AppPermission.BIOMETRIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun notificationsStatus(): PermissionStatus =
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }

    private fun exactAlarmsStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return PermissionStatus.NOT_REQUIRED
        val alarmManager = context.getSystemService<AlarmManager>()
            ?: return PermissionStatus.UNAVAILABLE
        return if (alarmManager.canScheduleExactAlarms()) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }
    }

    private fun overlayStatus(): PermissionStatus =
        if (Settings.canDrawOverlays(context)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }

    private fun fullScreenIntentStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return PermissionStatus.NOT_REQUIRED
        }
        val manager = context.getSystemService<android.app.NotificationManager>()
            ?: return PermissionStatus.UNAVAILABLE
        return if (manager.canUseFullScreenIntent()) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }
    }

    private fun batteryOptimizationStatus(): PermissionStatus {
        val powerManager = context.getSystemService<PowerManager>()
            ?: return PermissionStatus.UNAVAILABLE
        return if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }
    }

    private fun biometricStatus(): PermissionStatus =
        when (biometricStatusProvider.availability()) {
            BiometricAvailability.AVAILABLE -> PermissionStatus.GRANTED
            BiometricAvailability.NOT_ENROLLED -> PermissionStatus.NOT_GRANTED
            BiometricAvailability.UNAVAILABLE -> PermissionStatus.UNAVAILABLE
        }
}
