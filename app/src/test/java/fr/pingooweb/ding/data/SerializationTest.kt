package fr.pingooweb.ding.data

import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.SoundChoice
import fr.pingooweb.ding.domain.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    private fun sample(id: String = "p1") = Program(
        id = id,
        name = "Cuisson",
        colorIndex = 3,
        steps = listOf(
            Step("a", "Retourner", 300_000, waitForAck = true, sound = SoundChoice.Silent, vibrate = false),
            Step("b", "Sortir", 420_000, sound = SoundChoice.Custom("content://x/1", "Cloche")),
            Step("c", "", 60_000),
        ),
        defaultWaitForAck = false,
        defaultSound = SoundChoice.SystemAlarm,
        announceLabels = true,
        repeatCount = 0,
        createdAt = 10, updatedAt = 20, lastRunAt = 30,
    )

    @Test
    fun programJsonRoundTrip() {
        val p = sample()
        val json = DingJson.encodeToString(Program.serializer(), p)
        assertTrue(json.contains("\"custom\""))
        assertEquals(p, DingJson.decodeFromString(Program.serializer(), json))
    }

    @Test
    fun runStateJsonRoundTrip() {
        val s: RunState = RunEngine.start(sample(), 12_345)
        val json = DingJson.encodeToString(RunState.serializer(), s)
        assertEquals(s, DingJson.decodeFromString(RunState.serializer(), json))
    }

    @Test
    fun importExportRoundTrip() {
        val list = listOf(sample("p1"), sample("p2"))
        val json = ImportExport.encode(list)
        assertTrue(json.contains("\"version\""))
        assertEquals(list, ImportExport.decode(json))
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsGarbage() {
        ImportExport.decode("pas du json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsFutureVersion() {
        ImportExport.decode("""{"version":99,"programs":[]}""")
    }

    @Test
    fun mergeRegeneratesCollidingIds() {
        val existing = listOf(sample("p1"))
        val merged = ImportExport.mergeInto(existing, listOf(sample("p1"), sample("p9"), sample("p9")))
        assertEquals(3, merged.size)
        assertNotEquals("p1", merged[0].id)
        assertEquals("p9", merged[1].id)
        assertNotEquals("p9", merged[2].id)
        assertEquals(3, merged.map { it.id }.toSet().size)
        assertNotEquals(sample().steps[0].id, merged[0].steps[0].id)
    }

    @Test
    fun importSanitizesOutOfRangeValues() {
        val bad = sample().copy(
            colorIndex = 42,
            ringSeconds = 0,
            repeatCount = -3,
            steps = listOf(Step("a", "x", -5), Step("b", "y", 0)),
        )
        val out = ImportExport.decode(ImportExport.encode(listOf(bad))).single()
        assertEquals(7, out.colorIndex)
        assertEquals(1, out.ringSeconds)
        assertEquals(1, out.repeatCount)
        assertTrue(out.steps.all { it.durationMs >= 1_000L })
    }

    @Test
    fun templatesAreValid() {
        val all = Templates.all(0)
        assertEquals(3, all.size)
        assertEquals(30 * 60_000L, all[0].steps.sumOf { it.durationMs })
        assertEquals(4, all[1].repeatCount)
        assertTrue(all[1].defaultWaitForAck)
        assertEquals(8, all[2].repeatCount)
        assertEquals(3, all.map { it.id }.toSet().size)
    }
}
