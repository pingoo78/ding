package fr.pingooweb.ding.ui.run

import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.domain.model.effectiveWaitForAck
import fr.pingooweb.ding.ui.components.bento.BarSegment
import fr.pingooweb.ding.ui.components.bento.SegmentState

/** Segments de la frise d'exécution : faits pleins, courant partiel, à venir vides ; marqueur = validation. */
fun runSegments(state: RunState, now: Long): List<BarSegment> {
    val program = state.program
    val finished = state.status == RunStatus.FINISHED
    val progress = RunMath.stepProgress(state, now)
    return program.steps.mapIndexed { i, step ->
        val s = when {
            finished || i < state.currentIndex -> SegmentState.Done
            i == state.currentIndex -> SegmentState.Current
            else -> SegmentState.Upcoming
        }
        BarSegment(
            weight = step.durationMs.coerceAtLeast(1L).toFloat(),
            state = s,
            fraction = if (s == SegmentState.Current) progress else 0f,
            marker = step.effectiveWaitForAck(program),
        )
    }
}
