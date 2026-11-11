package fr.pingooweb.ding.ui.run

import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus

/** Calculs d'affichage purs de l'écran Exécution (testables en JVM). */
object RunMath {

    /** Progression linéaire 0..1 de l'étape courante. */
    fun stepProgress(state: RunState, now: Long): Float {
        if (state.status == RunStatus.FINISHED) return 1f
        if (state.status == RunStatus.WAITING_ACK) return 1f
        val step = state.program.steps[state.currentIndex.coerceIn(0, state.program.steps.lastIndex)]
        val total = step.durationMs.coerceAtLeast(1L).toFloat()
        val remaining = RunEngine.remainingInStepMs(state, now).toFloat()
        return (1f - remaining / total).coerceIn(0f, 1f)
    }

    /** Temps restant avant la fin du programme (tous cycles) ; null si répétition infinie. */
    fun totalRemainingMs(state: RunState, now: Long): Long? {
        if (state.status == RunStatus.FINISHED) return 0L
        val program = state.program
        if (program.repeatCount == 0) return null
        val idx = state.currentIndex.coerceIn(0, program.steps.lastIndex)
        val inStep = RunEngine.remainingInStepMs(state, now)
        val after = program.steps.drop(idx + 1).sumOf { it.durationMs }
        val cycleTotal = program.steps.sumOf { it.durationMs }
        val futureCycles = (program.repeatCount - state.cycle).coerceAtLeast(0)
        return inStep + after + futureCycles * cycleTotal
    }

    /** Instants cumulés (depuis le début du cycle) auxquels chaque étape est atteinte. */
    fun cycleOffsets(state: RunState): List<Long> {
        var acc = 0L
        return state.program.steps.map { acc += it.durationMs; acc }
    }

    /**
     * Heure estimée (epoch ms) d'atteinte de l'étape [index] >= courante, en supposant aucune pause ni
     * attente de validation future.
     */
    fun estimatedReachEpochMs(state: RunState, now: Long, wallNow: Long, index: Int): Long {
        val idx = state.currentIndex
        val inStep = RunEngine.remainingInStepMs(state, now)
        val last = index.coerceAtMost(state.program.steps.lastIndex)
        val between = if (last > idx) state.program.steps.subList(idx + 1, last + 1).sumOf { it.durationMs } else 0L
        return wallNow + inStep + between
    }
}
