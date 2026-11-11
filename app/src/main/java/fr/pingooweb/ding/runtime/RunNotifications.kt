package fr.pingooweb.ding.runtime

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import fr.pingooweb.ding.DingApp
import fr.pingooweb.ding.MainActivity
import fr.pingooweb.ding.R
import fr.pingooweb.ding.alert.AlertActivity
import fr.pingooweb.ding.domain.Alert
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.domain.model.displayLabel

/** Construction et publication des notifications (en cours, alerte, terminé). */
class RunNotifications(
    private val context: Context,
    private val clock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val nm: NotificationManager = context.getSystemService(NotificationManager::class.java)

    private fun stepLabel(state: RunState, index: Int): String =
        state.program.steps[index].displayLabel(index) { context.getString(R.string.step_default_label, it) }

    private fun programColor(state: RunState): Int =
        0xFFFF5A3C.toInt() // corail Ding!

    private fun openRunIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_RUN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQ_OPEN, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context, REQ_ACTION_BASE + action.hashCode().and(0xFFFF), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Notification de premier plan neutre, affichée tant que l'état n'est pas encore connu. */
    fun placeholder(): Notification =
        NotificationCompat.Builder(context, DingApp.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_rt_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.rt_loading))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openRunIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Notification persistante : mise à jour à chaque transition d'état uniquement. */
    fun running(state: RunState): Notification {
        val program = state.program
        val label = stepLabel(state, state.currentIndex)
        val stepText = context.getString(R.string.rt_text_step, label, state.currentIndex + 1, program.steps.size)
        val b = NotificationCompat.Builder(context, DingApp.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_rt_notification)
            .setContentTitle(program.name)
            .setColor(programColor(state))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openRunIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        when (state.status) {
            RunStatus.RUNNING -> {
                val remaining = RunEngine.remainingInStepMs(state, clock())
                b.setContentText(stepText)
                    .setShowWhen(true)
                    .setWhen(wallClock() + remaining)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(0, context.getString(R.string.action_pause), actionIntent(ACTION_PAUSE))
                    .addAction(0, context.getString(R.string.action_skip), actionIntent(ACTION_SKIP))
                    .addAction(0, context.getString(R.string.action_stop), actionIntent(ACTION_STOP))
            }
            RunStatus.PAUSED -> {
                val remaining = RunEngine.remainingInStepMs(state, clock())
                b.setContentText(context.getString(R.string.rt_text_paused, TimeFormat.format(remaining)))
                    .setSubText(stepText)
                    .setShowWhen(false)
                    .addAction(0, context.getString(R.string.action_resume), actionIntent(ACTION_RESUME))
                    .addAction(0, context.getString(R.string.action_skip), actionIntent(ACTION_SKIP))
                    .addAction(0, context.getString(R.string.action_stop), actionIntent(ACTION_STOP))
            }
            RunStatus.WAITING_ACK, RunStatus.FINISHED -> {
                b.setContentText(context.getString(R.string.rt_text_waiting_ack))
                    .setSubText(stepText)
                    .setShowWhen(false)
                    .addAction(0, context.getString(R.string.action_acknowledge), actionIntent(ACTION_ACK))
                    .addAction(0, context.getString(R.string.action_stop), actionIntent(ACTION_STOP))
            }
        }
        return b.build()
    }

    fun showAlert(state: RunState, alert: Alert, appInForeground: Boolean) {
        val fullScreen = PendingIntent.getActivity(
            context, REQ_FULL_SCREEN,
            Intent(context, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, DingApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_rt_notification)
            .setContentTitle(alert.stepLabel)
            .setContentText(
                if (alert.isFinal) "${state.program.name} · ${context.getString(R.string.rt_alert_final)}"
                else state.program.name,
            )
            .setSubText(
                if (alert.waitsForAck) context.getString(R.string.rt_alert_waiting)
                else context.getString(R.string.rt_step_progress, alert.stepIndex + 1, state.program.steps.size),
            )
            .setColor(programColor(state))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(alert.waitsForAck)
            .setAutoCancel(!alert.waitsForAck)
            .setContentIntent(openRunIntent())
        if (!appInForeground) b.setFullScreenIntent(fullScreen, true)
        if (alert.waitsForAck) b.addAction(0, context.getString(R.string.action_acknowledge), actionIntent(ACTION_ACK))
        b.addAction(0, context.getString(R.string.action_silence), actionIntent(ACTION_SILENCE))
        b.addAction(0, context.getString(R.string.action_stop), actionIntent(ACTION_STOP))
        runCatching { nm.notify(ID_ALERT, b.build()) }
    }

    fun cancelAlert() = nm.cancel(ID_ALERT)

    fun showFinished(state: RunState) {
        val n = NotificationCompat.Builder(context, DingApp.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_rt_notification)
            .setContentTitle(context.getString(R.string.rt_finished_title))
            .setContentText(context.getString(R.string.rt_finished_text, state.program.name))
            .setColor(programColor(state))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openRunIntent())
            .build()
        runCatching { nm.notify(ID_FINISHED, n) }
    }

    fun cancelFinished() = nm.cancel(ID_FINISHED)

    fun cancelAll() {
        nm.cancel(ID_ALERT)
        nm.cancel(ID_FINISHED)
    }

    companion object {
        const val ID_RUNNING = 1001
        const val ID_ALERT = 1002
        const val ID_FINISHED = 1003

        private const val REQ_OPEN = 10
        private const val REQ_FULL_SCREEN = 11
        private const val REQ_ACTION_BASE = 1000

        const val ACTION_PAUSE = "fr.pingooweb.ding.action.PAUSE"
        const val ACTION_RESUME = "fr.pingooweb.ding.action.RESUME"
        const val ACTION_SKIP = "fr.pingooweb.ding.action.SKIP"
        const val ACTION_STOP = "fr.pingooweb.ding.action.STOP"
        const val ACTION_ACK = "fr.pingooweb.ding.action.ACK"
        const val ACTION_SILENCE = "fr.pingooweb.ding.action.SILENCE"
    }
}
