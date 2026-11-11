package fr.pingooweb.ding.domain

import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunEvent
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.Step
import fr.pingooweb.ding.domain.model.cumulativeToDurations
import fr.pingooweb.ding.domain.model.cumulativeOffsets
import fr.pingooweb.ding.domain.model.totalDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunEngineTest {

    private val min = 60_000L

    private fun program(
        durations: List<Long>,
        ack: Map<Int, Boolean> = emptyMap(),
        defaultAck: Boolean = false,
        repeat: Int = 1,
    ) = Program(
        id = "p",
        name = "test",
        steps = durations.mapIndexed { i, d -> Step("s$i", "Étape ${i + 1}", d, waitForAck = ack[i]) },
        defaultWaitForAck = defaultAck,
        repeatCount = repeat,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun canonical() = program(
        cumulativeToDurations(listOf(5 * min, 12 * min, 18 * min, 23 * min, 30 * min))!!
    )

    private fun reached(events: List<RunEvent>) = events.filterIsInstance<RunEvent.StepReached>()

    @Test
    fun canonical_5_12_18_23_30() {
        val p = canonical()
        assertEquals(30 * min, p.totalDurationMs)
        assertEquals(listOf(5 * min, 12 * min, 18 * min, 23 * min, 30 * min), p.cumulativeOffsets())

        var s = RunEngine.start(p, 1_000)
        val t0 = 1_000L
        val expectedAt = listOf(5L, 12L, 18L, 23L, 30L)
        for ((i, at) in expectedAt.withIndex()) {
            // Juste avant : rien.
            val (before, evBefore) = RunEngine.tick(s, t0 + at * min - 1)
            assertTrue(evBefore.isEmpty())
            assertEquals(i, before.currentIndex)
            // A l'instant : l'etape sonne.
            val (after, ev) = RunEngine.tick(s, t0 + at * min)
            val r = reached(ev)
            assertEquals(1, r.size)
            assertEquals(i, r[0].index)
            assertEquals(i == 4, r[0].isLast)
            assertEquals(i + 1, after.completedSteps)
            s = after
        }
        assertEquals(RunStatus.FINISHED, s.status)
        assertEquals(30 * min, RunEngine.elapsedEffectiveMs(s, t0 + 99 * min))
    }

    @Test
    fun finishEmitsProgramFinishedAfterLastStepReached() {
        val (s, ev) = RunEngine.tick(RunEngine.start(program(listOf(1000)), 0), 1000)
        assertEquals(RunStatus.FINISHED, s.status)
        assertEquals(2, ev.size)
        assertTrue(ev[0] is RunEvent.StepReached && (ev[0] as RunEvent.StepReached).isLast)
        assertEquals(RunEvent.ProgramFinished, ev[1])
        assertNull(RunEngine.nextDeadline(s))
    }

    @Test
    fun nextStepStartsAtDeadlineNotAtNow_noDrift() {
        val s0 = RunEngine.start(program(listOf(10_000, 10_000, 10_000)), 0)
        // Tick tardif (3 s apres l'echeance) : l'etape suivante garde son echeance a 20 s.
        val (s1, ev) = RunEngine.tick(s0, 13_000)
        assertEquals(1, reached(ev).size)
        assertEquals(1, s1.currentIndex)
        assertEquals(10_000, s1.stepStartedAt)
        assertEquals(20_000L, RunEngine.nextDeadline(s1))
        assertEquals(7_000, RunEngine.remainingInStepMs(s1, 13_000))
    }

    @Test
    fun ackStepFreezesClockAndAcknowledgeRestartsAtNow() {
        val p = program(listOf(10_000, 10_000, 10_000), ack = mapOf(0 to true))
        val s0 = RunEngine.start(p, 0)
        val (s1, ev) = RunEngine.tick(s0, 10_000)
        assertEquals(RunStatus.WAITING_ACK, s1.status)
        assertEquals(10_000L, s1.pausedAt)
        assertEquals(0, s1.currentIndex)
        assertTrue(reached(ev).single().waitsForAck)
        assertNull(RunEngine.nextDeadline(s1))
        // Tick pendant l'attente : rien.
        val (s1b, ev2) = RunEngine.tick(s1, 500_000)
        assertEquals(s1, s1b)
        assertTrue(ev2.isEmpty())
        // Validation 5 s apres l'echeance.
        val (s2, ev3) = RunEngine.acknowledge(s1, 15_000)
        assertTrue(ev3.isEmpty())
        assertEquals(RunStatus.RUNNING, s2.status)
        assertEquals(1, s2.currentIndex)
        assertEquals(15_000, s2.stepStartedAt)
        assertEquals(5_000, s2.totalFrozenMs)
        assertNull(s2.pausedAt)
        assertEquals(25_000L, RunEngine.nextDeadline(s2))
        // Temps effectif = 10 s (etape 1) + 3 s apres validation.
        assertEquals(13_000, RunEngine.elapsedEffectiveMs(s2, 18_000))
        // Pendant l'attente, le temps effectif est fige.
        assertEquals(10_000, RunEngine.elapsedEffectiveMs(s1, 99_000))
    }

    @Test
    fun longAbsenceCrossesAllNonAckStepsAndStopsAtFirstAck() {
        val p = program(listOf(1000, 1000, 1000, 1000, 1000), ack = mapOf(2 to true))
        val (s, ev) = RunEngine.tick(RunEngine.start(p, 0), 1_000_000)
        assertEquals(listOf(0, 1, 2), reached(ev).map { it.index })
        assertEquals(RunStatus.WAITING_ACK, s.status)
        assertEquals(2, s.currentIndex)
        assertEquals(3000L, s.pausedAt)
        assertEquals(3, s.completedSteps)
        // Valider longtemps apres : les etapes 3 et 4 redemarrent a now.
        val (s2, _) = RunEngine.acknowledge(s, 1_000_000)
        assertEquals(3, s2.currentIndex)
        assertEquals(1_000_000, s2.stepStartedAt)
    }

    @Test
    fun longAbsenceWithNoAckFinishesProgram() {
        val p = program(listOf(1000, 1000, 1000))
        val (s, ev) = RunEngine.tick(RunEngine.start(p, 0), 1_000_000)
        assertEquals(RunStatus.FINISHED, s.status)
        assertEquals(3, reached(ev).size)
        assertEquals(RunEvent.ProgramFinished, ev.last())
    }

    @Test
    fun lastStepWithAckWaitsThenAckFinishes() {
        val p = program(listOf(1000, 1000), ack = mapOf(1 to true))
        val (s1, ev) = RunEngine.tick(RunEngine.start(p, 0), 2000)
        assertEquals(RunStatus.WAITING_ACK, s1.status)
        assertTrue(reached(ev).last().isLast)
        assertTrue(ev.none { it is RunEvent.ProgramFinished })
        val (s2, ev2) = RunEngine.acknowledge(s1, 5000)
        assertEquals(RunStatus.FINISHED, s2.status)
        assertEquals(listOf<RunEvent>(RunEvent.ProgramFinished), ev2)
        assertEquals(2000, RunEngine.elapsedEffectiveMs(s2, 99_999))
    }

    @Test
    fun finiteCyclesEmitCycleCompletedThenFinish() {
        val p = program(listOf(1000, 1000), repeat = 3)
        val (s, ev) = RunEngine.tick(RunEngine.start(p, 0), 6000)
        assertEquals(RunStatus.FINISHED, s.status)
        assertEquals(3, s.cycle)
        assertEquals(6, reached(ev).size)
        assertEquals(listOf(1, 2), ev.filterIsInstance<RunEvent.CycleCompleted>().map { it.cycle })
        assertEquals(RunEvent.ProgramFinished, ev.last())
        assertEquals(listOf(1, 1, 2, 2, 3, 3), reached(ev).map { it.cycle })
    }

    @Test
    fun cycleBoundaryWithAckOnLastStep() {
        val p = program(listOf(1000, 1000), ack = mapOf(1 to true), repeat = 2)
        val (s1, _) = RunEngine.tick(RunEngine.start(p, 0), 2000)
        assertEquals(RunStatus.WAITING_ACK, s1.status)
        val (s2, ev) = RunEngine.acknowledge(s1, 3000)
        assertEquals(listOf<RunEvent>(RunEvent.CycleCompleted(1)), ev)
        assertEquals(2, s2.cycle)
        assertEquals(0, s2.currentIndex)
        assertEquals(0, s2.completedSteps)
        assertEquals(3000, s2.stepStartedAt)
    }

    @Test
    fun infiniteCyclesNeverFinish() {
        val p = program(listOf(1000, 1000), repeat = 0)
        val (s, ev) = RunEngine.tick(RunEngine.start(p, 0), 100_000)
        assertEquals(RunStatus.RUNNING, s.status)
        assertEquals(51, s.cycle)
        assertEquals(100, reached(ev).size)
        assertTrue(ev.none { it is RunEvent.ProgramFinished })
        assertNotNull(RunEngine.nextDeadline(s))
    }

    @Test
    fun pauseThenResumeShiftsDeadlines() {
        val s0 = RunEngine.start(program(listOf(10_000, 10_000)), 0)
        val paused = RunEngine.pause(s0, 4_000)
        assertEquals(RunStatus.PAUSED, paused.status)
        assertNull(RunEngine.nextDeadline(paused))
        assertEquals(6_000, RunEngine.remainingInStepMs(paused, 4_000))
        assertEquals(6_000, RunEngine.remainingInStepMs(paused, 50_000)) // gele
        assertEquals(4_000, RunEngine.elapsedEffectiveMs(paused, 50_000))
        // Tick en pause : rien.
        assertTrue(RunEngine.tick(paused, 100_000).second.isEmpty())
        val resumed = RunEngine.resume(paused, 34_000) // 30 s de pause
        assertEquals(RunStatus.RUNNING, resumed.status)
        assertEquals(30_000, resumed.totalFrozenMs)
        assertEquals(40_000L, RunEngine.nextDeadline(resumed))
        assertEquals(6_000, RunEngine.remainingInStepMs(resumed, 34_000))
        assertEquals(4_000, RunEngine.elapsedEffectiveMs(resumed, 34_000))
        val (after, ev) = RunEngine.tick(resumed, 40_000)
        assertEquals(1, reached(ev).size)
        assertEquals(1, after.currentIndex)
        assertEquals(50_000L, RunEngine.nextDeadline(after))
    }

    @Test
    fun skipReachesCurrentStepWithoutRinging() {
        val p = program(listOf(10_000, 10_000, 10_000), defaultAck = true)
        val s0 = RunEngine.start(p, 0)
        val (s1, ev) = RunEngine.skip(s0, 3_000)
        assertTrue(ev.isEmpty())
        assertEquals(RunStatus.RUNNING, s1.status)
        assertEquals(1, s1.currentIndex)
        assertEquals(3_000, s1.stepStartedAt)
        assertEquals(1, s1.completedSteps)
        assertEquals(13_000L, RunEngine.nextDeadline(s1))
    }

    @Test
    fun skipOnLastStepFinishesOrCycles() {
        val one = program(listOf(10_000))
        val (f, ev) = RunEngine.skip(RunEngine.start(one, 0), 1_000)
        assertEquals(RunStatus.FINISHED, f.status)
        assertEquals(listOf<RunEvent>(RunEvent.ProgramFinished), ev)

        val cyc = program(listOf(10_000), repeat = 2)
        val (c, ev2) = RunEngine.skip(RunEngine.start(cyc, 0), 1_000)
        assertEquals(RunStatus.RUNNING, c.status)
        assertEquals(2, c.cycle)
        assertEquals(listOf<RunEvent>(RunEvent.CycleCompleted(1)), ev2)
    }

    @Test
    fun skipWhilePausedStaysPaused() {
        val s0 = RunEngine.pause(RunEngine.start(program(listOf(10_000, 10_000)), 0), 2_000)
        val (s1, ev) = RunEngine.skip(s0, 5_000)
        assertTrue(ev.isEmpty())
        assertEquals(RunStatus.PAUSED, s1.status)
        assertEquals(1, s1.currentIndex)
        assertEquals(10_000, RunEngine.remainingInStepMs(s1, 9_000))
        val r = RunEngine.resume(s1, 9_000)
        assertEquals(19_000L, RunEngine.nextDeadline(r))
    }

    @Test
    fun skipWhileWaitingAckActsLikeAcknowledge() {
        val p = program(listOf(1000, 1000), ack = mapOf(0 to true))
        val (w, _) = RunEngine.tick(RunEngine.start(p, 0), 1000)
        val (s, ev) = RunEngine.skip(w, 4000)
        assertTrue(ev.isEmpty())
        assertEquals(1, s.currentIndex)
        assertEquals(4000, s.stepStartedAt)
    }

    @Test
    fun acknowledgeWhenNotWaitingIsNoOp() {
        val s = RunEngine.start(program(listOf(1000)), 0)
        val (s2, ev) = RunEngine.acknowledge(s, 500)
        assertEquals(s, s2)
        assertTrue(ev.isEmpty())
    }

    @Test
    fun remainingNeverNegative() {
        val s = RunEngine.start(program(listOf(1000)), 0)
        assertEquals(0, RunEngine.remainingInStepMs(s, 5000))
    }

    @Test
    fun stepInheritsAndOverridesAckFromProgram() {
        // defaultWaitForAck = true, mais l'etape 0 force false.
        val p = program(listOf(1000, 1000), ack = mapOf(0 to false), defaultAck = true)
        val (s, ev) = RunEngine.tick(RunEngine.start(p, 0), 1000)
        assertEquals(RunStatus.RUNNING, s.status)
        assertEquals(false, reached(ev).single().waitsForAck)
        val (s2, _) = RunEngine.tick(s, 2000)
        assertEquals(RunStatus.WAITING_ACK, s2.status)
    }

    @Test
    fun cumulativeConversionRejectsNonIncreasing() {
        assertNull(cumulativeToDurations(listOf(5 * min, 5 * min)))
        assertNull(cumulativeToDurations(listOf(5 * min, 3 * min)))
        assertEquals(listOf(5 * min, 7 * min), cumulativeToDurations(listOf(5 * min, 12 * min)))
    }

    @Suppress("unused")
    private fun State(s: RunState) = s
}
