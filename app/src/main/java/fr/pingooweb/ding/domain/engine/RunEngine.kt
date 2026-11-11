package fr.pingooweb.ding.domain.engine

import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.effectiveWaitForAck
import kotlinx.serialization.Serializable

@Serializable
enum class RunStatus { RUNNING, PAUSED, WAITING_ACK, FINISHED }

/**
 * Etat d'execution immuable. Toutes les valeurs temporelles sont des instants "elapsed" (monotones).
 * En FINISHED, [pausedAt] contient l'instant de fin (fige le temps effectif).
 */
@Serializable
data class RunState(
    val program: Program,
    val cycle: Int,
    val currentIndex: Int,
    val status: RunStatus,
    val stepStartedAt: Long,
    val pausedAt: Long?,
    val runStartedAt: Long,
    val totalFrozenMs: Long,
    val completedSteps: Int,
)

sealed interface RunEvent {
    data class StepReached(val index: Int, val cycle: Int, val waitsForAck: Boolean, val isLast: Boolean) : RunEvent
    data class CycleCompleted(val cycle: Int) : RunEvent
    data object ProgramFinished : RunEvent
}

object RunEngine {

    fun start(program: Program, now: Long): RunState {
        require(program.steps.isNotEmpty()) { "Un programme doit avoir au moins une étape" }
        return RunState(
            program = program,
            cycle = 1,
            currentIndex = 0,
            status = RunStatus.RUNNING,
            stepStartedAt = now,
            pausedAt = null,
            runStartedAt = now,
            totalFrozenMs = 0L,
            completedSteps = 0,
        )
    }

    private fun stepDuration(state: RunState): Long =
        state.program.steps[state.currentIndex].durationMs.coerceAtLeast(1L)

    private fun deadlineOf(state: RunState): Long = state.stepStartedAt + stepDuration(state)

    /** Avance à l'étape suivante (ou cycle suivant / fin) en démarrant le décompte à [startAt]. */
    private fun advance(state: RunState, startAt: Long): Pair<RunState, List<RunEvent>> {
        val isLast = state.currentIndex >= state.program.steps.lastIndex
        if (!isLast) {
            return state.copy(
                currentIndex = state.currentIndex + 1,
                status = RunStatus.RUNNING,
                stepStartedAt = startAt,
                pausedAt = null,
            ) to emptyList()
        }
        val repeat = state.program.repeatCount
        val hasMoreCycles = repeat == 0 || state.cycle < repeat
        return if (hasMoreCycles) {
            state.copy(
                cycle = state.cycle + 1,
                currentIndex = 0,
                status = RunStatus.RUNNING,
                stepStartedAt = startAt,
                pausedAt = null,
                completedSteps = 0,
            ) to listOf(RunEvent.CycleCompleted(state.cycle))
        } else {
            state.copy(
                status = RunStatus.FINISHED,
                pausedAt = startAt,
            ) to listOf(RunEvent.ProgramFinished)
        }
    }

    fun tick(state: RunState, now: Long): Pair<RunState, List<RunEvent>> {
        if (state.status != RunStatus.RUNNING) return state to emptyList()
        var s = state
        val events = ArrayList<RunEvent>()
        while (s.status == RunStatus.RUNNING) {
            val deadline = deadlineOf(s)
            if (now < deadline) break
            val waits = s.program.steps[s.currentIndex].effectiveWaitForAck(s.program)
            val isLast = s.currentIndex >= s.program.steps.lastIndex
            events += RunEvent.StepReached(s.currentIndex, s.cycle, waits, isLast)
            s = s.copy(completedSteps = s.currentIndex + 1)
            if (waits) {
                s = s.copy(status = RunStatus.WAITING_ACK, pausedAt = deadline)
            } else {
                val (next, evs) = advance(s, deadline)
                s = next
                events += evs
            }
        }
        return s to events
    }

    fun pause(state: RunState, now: Long): RunState =
        if (state.status == RunStatus.RUNNING) state.copy(status = RunStatus.PAUSED, pausedAt = now) else state

    fun resume(state: RunState, now: Long): RunState {
        if (state.status != RunStatus.PAUSED) return state
        val frozen = (now - (state.pausedAt ?: now)).coerceAtLeast(0L)
        return state.copy(
            status = RunStatus.RUNNING,
            pausedAt = null,
            stepStartedAt = state.stepStartedAt + frozen,
            totalFrozenMs = state.totalFrozenMs + frozen,
        )
    }

    fun acknowledge(state: RunState, now: Long): Pair<RunState, List<RunEvent>> {
        if (state.status != RunStatus.WAITING_ACK) return state to emptyList()
        val frozen = (now - (state.pausedAt ?: now)).coerceAtLeast(0L)
        return advance(state.copy(totalFrozenMs = state.totalFrozenMs + frozen), now)
    }

    /**
     * Atteint immédiatement l'étape courante sans sonner (aucun StepReached) et passe à la suivante,
     * dont le décompte démarre à [now]. Depuis WAITING_ACK équivaut à valider. Depuis PAUSED reste en pause.
     */
    fun skip(state: RunState, now: Long): Pair<RunState, List<RunEvent>> {
        return when (state.status) {
            RunStatus.FINISHED -> state to emptyList()
            RunStatus.WAITING_ACK -> acknowledge(state, now)
            RunStatus.RUNNING -> advance(state.copy(completedSteps = state.currentIndex + 1), now)
            RunStatus.PAUSED -> {
                val frozen = (now - (state.pausedAt ?: now)).coerceAtLeast(0L)
                val base = state.copy(
                    completedSteps = state.currentIndex + 1,
                    totalFrozenMs = state.totalFrozenMs + frozen,
                )
                val (next, evs) = advance(base, now)
                val result =
                    if (next.status == RunStatus.RUNNING) next.copy(status = RunStatus.PAUSED, pausedAt = now) else next
                result to evs
            }
        }
    }

    fun remainingInStepMs(state: RunState, now: Long): Long = when (state.status) {
        RunStatus.RUNNING -> (deadlineOf(state) - now).coerceAtLeast(0L)
        RunStatus.PAUSED -> (deadlineOf(state) - (state.pausedAt ?: now)).coerceAtLeast(0L)
        RunStatus.WAITING_ACK, RunStatus.FINISHED -> 0L
    }

    /** Temps écoulé hors gels (pauses et attentes de validation). */
    fun elapsedEffectiveMs(state: RunState, now: Long): Long {
        val ref = state.pausedAt ?: now
        return (ref - state.runStartedAt - state.totalFrozenMs).coerceAtLeast(0L)
    }

    fun nextDeadline(state: RunState): Long? =
        if (state.status == RunStatus.RUNNING) deadlineOf(state) else null
}
