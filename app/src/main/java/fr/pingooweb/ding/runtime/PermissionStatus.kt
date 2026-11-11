package fr.pingooweb.ding.runtime

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class PermissionKind { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN, BATTERY_OPTIMIZATION }

/** [required] : l'application ne peut pas remplir sa mission sans cette autorisation. */
data class PermissionState(val kind: PermissionKind, val granted: Boolean, val required: Boolean)

/** État des autorisations et accès aux écrans système correspondants (centre d'autorisations des Réglages). */
object PermissionStatus {

    fun all(context: Context): List<PermissionState> = PermissionKind.entries.map { state(context, it) }

    private fun state(context: Context, kind: PermissionKind): PermissionState = when (kind) {
        PermissionKind.NOTIFICATIONS ->
            PermissionState(kind, NotificationManagerCompat.from(context).areNotificationsEnabled(), required = true)

        PermissionKind.EXACT_ALARMS -> {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            PermissionState(kind, granted, required = true)
        }

        PermissionKind.FULL_SCREEN -> {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            PermissionState(kind, granted, required = false)
        }

        PermissionKind.BATTERY_OPTIMIZATION -> {
            val granted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            PermissionState(kind, granted, required = false)
        }
    }

    fun settingsIntent(context: Context, kind: PermissionKind): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        val intent = when (kind) {
            PermissionKind.NOTIFICATIONS -> appNotifications
            PermissionKind.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
                } else appDetails
            PermissionKind.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
                } else appNotifications
            // Liste globale des exceptions : n'exige pas la permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
            PermissionKind.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
