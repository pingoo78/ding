package fr.pingooweb.ding.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import fr.pingooweb.ding.MainActivity

/** Planifie le réveil de la prochaine échéance (heure murale dérivée de l'instant elapsed). */
class AlarmScheduler(
    private val context: Context,
    private val clock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    private val operation: PendingIntent
        get() = PendingIntent.getBroadcast(
            context, REQ_ALARM,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private val showIntent: PendingIntent
        get() = PendingIntent.getActivity(
            context, REQ_SHOW,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_RUN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Programme l'alarme à [deadlineElapsed] (instant elapsed) ; null annule. */
    fun schedule(deadlineElapsed: Long?) {
        if (deadlineElapsed == null) {
            cancel()
            return
        }
        val wallTime = wallClock() + (deadlineElapsed - clock()).coerceAtLeast(0L)
        val op = operation
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            try {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(wallTime, showIntent), op)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "setAlarmClock refusé, repli inexact", e)
            }
        } else {
            Log.w(TAG, "Alarmes exactes non autorisées : repli sur setAndAllowWhileIdle (précision réduite)")
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallTime, op)
    }

    fun cancel() {
        alarmManager.cancel(operation)
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_ALARM = "fr.pingooweb.ding.action.ALARM"
        private const val REQ_ALARM = 100
        private const val REQ_SHOW = 101
    }
}
