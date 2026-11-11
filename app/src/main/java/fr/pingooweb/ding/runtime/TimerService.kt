package fr.pingooweb.ding.runtime

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import fr.pingooweb.ding.DingApp
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.SystemClock

/**
 * Service de premier plan (specialUse) : maintient le process en vie, affiche la notification persistante
 * (mise à jour à chaque changement d'état, jamais chaque seconde) et déclenche le tick à chaque échéance.
 * L'AlarmManager reste le filet de sécurité en Doze.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private lateinit var notifications: RunNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notifications = RunNotifications(applicationContext, SystemClock::elapsedRealtime)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as DingApp).container.runController
        // Doit être appelé dans les secondes suivant startForegroundService.
        val initial = controller.state.value
        enterForeground(
            if (initial != null && initial.status != RunStatus.FINISHED) notifications.running(initial)
            else notifications.placeholder(),
        )
        job?.cancel()
        job = scope.launch {
            controller.awaitRestored()
            controller.state.collectLatest { s ->
                if (s == null) {
                    shutDown()
                    return@collectLatest
                }
                if (s.status == RunStatus.FINISHED) {
                    // Garde le process au premier plan tant que l'alerte finale sonne.
                    controller.alerting.first { it == null }
                    shutDown()
                    return@collectLatest
                }
                enterForeground(notifications.running(s))
                if (s.status == RunStatus.RUNNING) {
                    val deadline = RunEngine.nextDeadline(s) ?: return@collectLatest
                    // Boucle : un réveil légèrement en avance ne doit pas laisser l'échéance sans tick.
                    // Dès que onAlarm() change l'état, collectLatest annule ce bloc.
                    while (true) {
                        val wait = deadline - SystemClock.elapsedRealtime()
                        if (wait > 0) {
                            delay(wait)
                        } else {
                            controller.onAlarm().join()
                            delay(50)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun enterForeground(notification: Notification) {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, RunNotifications.ID_RUNNING, notification, type)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException & co : l'alarme et les notifications prennent le relais.
            Log.w(TAG, "startForeground impossible", e)
            stopSelf()
        }
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15 : délai du type specialUse dépassé ; on s'arrête proprement (alarmes et alertes continuent). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "onTimeout(fgsType=$fgsType) : arrêt du service, l'alarme reste planifiée")
        shutDown()
    }

    override fun onTimeout(startId: Int) {
        onTimeout(startId, 0)
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TimerService"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
