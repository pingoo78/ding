package fr.pingooweb.ding.runtime

import fr.pingooweb.ding.data.ProgramRepository
import fr.pingooweb.ding.domain.Alert
import fr.pingooweb.ding.domain.RunController
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunEvent
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Implémentation de secours : pas de service, pas d'alarme, aucune persistance. Fonctionne tant que le
 * process vit. Remplacée en phase 2 par `DefaultRunController`.
 *
 * Doit être utilisée avec un [scope] mono-thread (ex. Dispatchers.Main.immediate) : l'état n'est pas verrouillé.
 */
class InMemoryRunController(
    private val programs: ProgramRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : RunController {

    private val _state = MutableStateFlow<RunState?>(null)
    override val state: StateFlow<RunState?> = _state.asStateFlow()

    private val _alerting = MutableStateFlow<Alert?>(null)
    override val alerting: StateFlow<Alert?> = _alerting.asStateFlow()

    private var tickJob: Job? = null
    private var autoSilenceJob: Job? = null

    override fun start(programId: String) {
        scope.launch {
            val program = programs.get(programId) ?: return@launch
            stopInternal()
            _state.value = RunEngine.start(program, clock())
            programs.upsert(program.copy(lastRunAt = wallClock()))
            scheduleNext()
        }
    }

    override fun pause() {
        val s = tickNow() ?: return
        _state.value = RunEngine.pause(s, clock())
        scheduleNext()
    }

    override fun resume() {
        val s = _state.value ?: return
        _state.value = RunEngine.resume(s, clock())
        scheduleNext()
    }

    override fun acknowledge() {
        val s = tickNow() ?: return
        clearAlert()
        val (next, events) = RunEngine.acknowledge(s, clock())
        _state.value = next
        handle(next, events)
        scheduleNext()
    }

    override fun silence() = clearAlert()

    override fun skip() {
        val s = tickNow() ?: return
        clearAlert()
        val (next, events) = RunEngine.skip(s, clock())
        _state.value = next
        handle(next, events)
        scheduleNext()
    }

    override fun stop() = stopInternal()

    private fun stopInternal() {
        tickJob?.cancel()
        tickJob = null
        clearAlert()
        _state.value = null
    }

    /** Applique les échéances échues et renvoie l'état courant (null si aucun programme). */
    private fun tickNow(): RunState? {
        val s = _state.value ?: return null
        val (next, events) = RunEngine.tick(s, clock())
        if (next != s) _state.value = next
        handle(next, events)
        return next
    }

    private fun handle(state: RunState, events: List<RunEvent>) {
        for (e in events) {
            if (e !is RunEvent.StepReached) continue
            val step = state.program.steps[e.index]
            val finished = e.isLast && state.program.repeatCount != 0 && e.cycle >= state.program.repeatCount
            val alert = Alert(
                stepIndex = e.index,
                stepLabel = step.label.ifBlank { "Étape ${e.index + 1}" },
                waitsForAck = e.waitsForAck,
                isFinal = finished,
            )
            _alerting.value = alert
            autoSilenceJob?.cancel()
            if (!alert.waitsForAck) {
                val ringMs = state.program.ringSeconds * 1000L
                autoSilenceJob = scope.launch {
                    delay(ringMs)
                    if (_alerting.value == alert) _alerting.value = null
                }
            }
        }
    }

    private fun clearAlert() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        _alerting.value = null
    }

    private fun scheduleNext() {
        tickJob?.cancel()
        tickJob = null
        val s = _state.value ?: return
        if (s.status != RunStatus.RUNNING) return
        val deadline = RunEngine.nextDeadline(s) ?: return
        tickJob = scope.launch {
            delay((deadline - clock()).coerceAtLeast(0L))
            tickNow()
            scheduleNext()
        }
    }
}
