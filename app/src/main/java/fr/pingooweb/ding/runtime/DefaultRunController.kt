package fr.pingooweb.ding.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import fr.pingooweb.ding.R
import fr.pingooweb.ding.alert.AlertActivity
import fr.pingooweb.ding.alert.AlertPlayer
import fr.pingooweb.ding.alert.AlertRequest
import fr.pingooweb.ding.data.ActiveRunStore
import fr.pingooweb.ding.data.ProgramRepository
import fr.pingooweb.ding.domain.Alert
import fr.pingooweb.ding.domain.RunController
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunEvent
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.domain.model.displayLabel
import fr.pingooweb.ding.domain.model.effectiveSound
import fr.pingooweb.ding.domain.model.effectiveVibrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Contrôleur de production : applique [RunEngine], persiste, planifie l'alarme, pilote le service de
 * premier plan, les notifications et le [AlertPlayer].
 *
 * Toutes les mutations se font de façon synchrone sur le thread principal ([scope] doit être
 * `Dispatchers.Main.immediate`) : aucune section critique ne contient de point de suspension.
 */
class DefaultRunController(
    private val context: Context,
    private val programs: ProgramRepository,
    private val store: ActiveRunStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val bootCount: () -> Int = {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    },
) : RunController {

    private val appContext = context.applicationContext
    private val alarms = AlarmScheduler(appContext, clock, wallClock)
    private val notifications = RunNotifications(appContext, clock, wallClock)
    private val player = AlertPlayer(appContext)

    private val _state = MutableStateFlow<RunState?>(null)
    override val state: StateFlow<RunState?> = _state.asStateFlow()

    private val _alerting = MutableStateFlow<Alert?>(null)
    override val alerting: StateFlow<Alert?> = _alerting.asStateFlow()

    private var autoSilenceJob: Job? = null
    private var restoreJob: Job? = null

    // Persistance ordonnée : la dernière valeur l'emporte.
    private val persistQueue = Channel<Snapshot>(Channel.CONFLATED)

    private class Snapshot(val state: RunState?)

    private var startedActivities = 0

    init {
        (appContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity !is AlertActivity) startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity !is AlertActivity && startedActivities > 0) startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            for (snap in persistQueue) {
                runCatching { store.save(snap.state, bootCount()) }
                    .onFailure { Log.w(TAG, "Persistance de l'état impossible", it) }
            }
        }
    }

    private val appInForeground: Boolean get() = startedActivities > 0

    // --- API publique ---------------------------------------------------------------------------

    /** À appeler au démarrage de l'application : restaure un programme actif et relance service/alarme. */
    fun restore() {
        if (restoreJob != null) return
        restoreJob = scope.launch {
            val restored = runCatching { store.restore(bootCount()) }.getOrNull() ?: return@launch
            if (_state.value != null) return@launch // l'utilisateur a déjà démarré autre chose
            if (restored.status == RunStatus.FINISHED) {
                _state.value = restored
                return@launch
            }
            _state.value = restored
            tickInternal()
            val s = _state.value ?: return@launch
            reschedule(s)
            // Étape à valider restaurée : elle doit sonner jusqu'à validation (si tick ne l'a pas déjà fait).
            if (s.status == RunStatus.WAITING_ACK && _alerting.value == null) {
                raiseAlert(
                    s,
                    RunEvent.StepReached(
                        index = s.currentIndex,
                        cycle = s.cycle,
                        waitsForAck = true,
                        isLast = s.currentIndex == s.program.steps.lastIndex,
                    ),
                )
            }
            if (s.status != RunStatus.FINISHED) ensureService()
        }
    }

    /** Suspend jusqu'à la fin de la restauration initiale. */
    suspend fun awaitRestored() {
        restoreJob?.join()
    }

    /** Échéance atteinte (alarme ou boucle du service) : applique les étapes échues. */
    fun onAlarm(): Job = scope.launch {
        restoreJob?.join()
        tickInternal()
        _state.value?.let { reschedule(it) }
    }

    /** Actions de notification ([RunNotifications].ACTION_*), exécutées après la restauration. */
    fun handleAction(action: String): Job = scope.launch {
        restoreJob?.join()
        when (action) {
            RunNotifications.ACTION_PAUSE -> pause()
            RunNotifications.ACTION_RESUME -> resume()
            RunNotifications.ACTION_SKIP -> skip()
            RunNotifications.ACTION_STOP -> stop()
            RunNotifications.ACTION_ACK -> acknowledge()
            RunNotifications.ACTION_SILENCE -> silence()
        }
    }

    override fun start(programId: String) {
        scope.launch {
            restoreJob?.join()
            val program = programs.get(programId) ?: return@launch
            // Section synchrone (pas de suspension) : remplace tout programme actif.
            clearAlert()
            notifications.cancelFinished()
            val started = RunEngine.start(program, clock())
            commit(started)
            ensureService()
            launch { runCatching { programs.upsert(program.copy(lastRunAt = wallClock())) } }
        }
    }

    override fun pause() {
        val s = tickInternal() ?: return
        commit(RunEngine.pause(s, clock()))
    }

    override fun resume() {
        val s = _state.value ?: return
        commit(RunEngine.resume(s, clock()))
    }

    override fun acknowledge() {
        val s = tickInternal() ?: return
        clearAlert()
        val (next, events) = RunEngine.acknowledge(s, clock())
        commit(next)
        handle(next, events)
    }

    override fun silence() = clearAlert()

    override fun skip() {
        val s = tickInternal() ?: return
        clearAlert()
        val (next, events) = RunEngine.skip(s, clock())
        commit(next)
        handle(next, events)
    }

    override fun stop() {
        clearAlert()
        notifications.cancelAll()
        commit(null)
        runCatching { appContext.stopService(Intent(appContext, TimerService::class.java)) }
    }

    // --- Interne --------------------------------------------------------------------------------

    /** Publie, persiste et replanifie l'alarme. */
    private fun commit(s: RunState?) {
        _state.value = s
        persistQueue.trySend(Snapshot(s))
        reschedule(s)
    }

    private fun reschedule(s: RunState?) {
        runCatching { alarms.schedule(s?.let { RunEngine.nextDeadline(it) }) }
            .onFailure { Log.w(TAG, "Planification de l'alarme impossible", it) }
    }

    /** Applique les échéances échues, publie et gère les événements ; renvoie l'état courant. */
    private fun tickInternal(): RunState? {
        val s = _state.value ?: return null
        val now = clock()
        val (next, events) = RunEngine.tick(s, now)
        if (next != s) commit(next)
        handle(next, events)
        return next
    }

    private fun handle(state: RunState, events: List<RunEvent>) {
        if (events.isEmpty()) return
        var lastReached: RunEvent.StepReached? = null
        var finished = false
        for (e in events) when (e) {
            is RunEvent.StepReached -> lastReached = e
            is RunEvent.ProgramFinished -> finished = true
            is RunEvent.CycleCompleted -> Unit
        }
        lastReached?.let { raiseAlert(state, it) }
        if (finished) notifications.showFinished(state)
    }

    private fun raiseAlert(state: RunState, e: RunEvent.StepReached) {
        val program = state.program
        val step = program.steps[e.index]
        val ringMs = program.ringSeconds.coerceAtLeast(1) * 1000L
        // Retard (process endormi) : une étape sans validation trop ancienne ne sonne plus.
        val reachedAt = state.pausedAt ?: state.stepStartedAt
        // Tolérance minimale de 30 s : un réveil inexact (Doze, alarmes exactes refusées) ne doit pas rendre muet.
        if (!e.waitsForAck && clock() - reachedAt > maxOf(ringMs, MAX_LATE_RING_MS)) return

        val label = step.displayLabel(e.index) { appContext.getString(R.string.step_default_label, it) }
        val alert = Alert(
            stepIndex = e.index,
            stepLabel = label,
            waitsForAck = e.waitsForAck,
            isFinal = e.isLast && program.repeatCount != 0 && e.cycle >= program.repeatCount,
        )
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        _alerting.value = alert
        player.play(
            AlertRequest(
                sound = step.effectiveSound(program),
                vibrate = step.effectiveVibrate(program),
                speakLabel = if (program.announceLabels) label else null,
                loop = e.waitsForAck,
                durationMs = ringMs,
            ),
        )
        notifications.showAlert(state, alert, appInForeground)
        if (!alert.waitsForAck) {
            autoSilenceJob = scope.launch {
                delay(ringMs)
                if (_alerting.value == alert) clearAlert()
            }
        }
        ensureService()
    }

    private fun clearAlert() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        player.stop()
        notifications.cancelAlert()
        _alerting.value = null
    }

    private fun ensureService() {
        val s = _state.value ?: return
        if (s.status == RunStatus.FINISHED || TimerService.isRunning) return
        runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, TimerService::class.java))
        }.onFailure { Log.w(TAG, "Démarrage du service de premier plan refusé", it) }
    }

    private companion object {
        const val TAG = "RunController"
        const val MAX_LATE_RING_MS = 30_000L
    }
}
