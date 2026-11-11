package fr.pingooweb.ding.ui

import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.Step
import fr.pingooweb.ding.ui.editor.EditorLogic
import fr.pingooweb.ding.ui.run.RunMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLogicTest {
    private val min = 60_000L
    private fun steps(vararg minutes: Long) = minutes.mapIndexed { i, m -> Step("s$i", "", m * min) }

    @Test
    fun cumulativeOffsets() {
        assertEquals(listOf(5 * min, 12 * min, 18 * min), EditorLogic.cumulative(steps(5, 7, 6)))
    }

    @Test
    fun setCumulativeKeepsOtherInstants() {
        val out = EditorLogic.setCumulative(steps(5, 7, 6), 1, 14 * min)
        assertEquals(listOf(5 * min, 14 * min, 18 * min), EditorLogic.cumulative(out))
        assertTrue(EditorLogic.isValid(out))
    }

    @Test
    fun setCumulativeBeyondNextInstantIsInvalid() {
        val out = EditorLogic.setCumulative(steps(5, 7, 6), 1, 20 * min)
        assertEquals(setOf(2), EditorLogic.invalidIndices(out))
        // l'instant de la 3e étape est conservé
        assertEquals(18 * min, EditorLogic.cumulative(out)[2])
    }

    @Test
    fun setCumulativeBeforePreviousIsInvalid() {
        val out = EditorLogic.setCumulative(steps(5, 7), 1, 4 * min)
        assertFalse(EditorLogic.isValid(out))
    }

    @Test
    fun setDurationAndMoveKeepDurations() {
        val s = EditorLogic.setDuration(steps(5, 7, 6), 0, 2 * min)
        assertEquals(2 * min, s[0].durationMs)
        val moved = EditorLogic.move(s, 0, 2)
        assertEquals(listOf(7 * min, 6 * min, 2 * min), moved.map { it.durationMs })
        assertEquals(moved, EditorLogic.move(moved, 5, 0))
    }

    @Test
    fun emptyIsInvalid() {
        assertFalse(EditorLogic.isValid(emptyList()))
    }

    @Test
    fun runMathTotalRemaining() {
        val p = Program("p", "n", steps = steps(5, 7, 6), repeatCount = 2, createdAt = 0, updatedAt = 0)
        val s = RunEngine.start(p, 0L)
        assertEquals((18 + 18) * min, RunMath.totalRemainingMs(s, 0L))
        assertEquals(0f, RunMath.stepProgress(s, 0L), 0.001f)
        assertEquals(0.5f, RunMath.stepProgress(s, 150_000L), 0.001f)
        assertNull(RunMath.totalRemainingMs(RunEngine.start(p.copy(repeatCount = 0), 0L), 0L))
    }
}
