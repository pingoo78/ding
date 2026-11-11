package fr.pingooweb.ding.data

import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.Step
import java.util.UUID

/** Modèles de programmes proposés à l'état vide. Chaque appel génère de nouveaux ids. */
object Templates {

    enum class Kind { EXAMPLE_30, POMODORO, INTERVALS }

    private const val MIN = 60_000L
    private const val SEC = 1_000L

    private fun step(label: String, durationMs: Long, waitForAck: Boolean? = null) =
        Step(id = UUID.randomUUID().toString(), label = label, durationMs = durationMs, waitForAck = waitForAck)

    /** « Exemple 30 min » : sonne à 5, 12, 18, 23 puis fin à 30 min. */
    fun example30(now: Long): Program = Program(
        id = UUID.randomUUID().toString(),
        name = "Exemple 30 min",
        colorIndex = 0,
        steps = listOf(
            step("Premier jalon", 5 * MIN),
            step("Deuxième jalon", 7 * MIN),
            step("Troisième jalon", 6 * MIN),
            step("Quatrième jalon", 5 * MIN),
            step("Fin", 7 * MIN),
        ),
        createdAt = now,
        updatedAt = now,
    )

    /** « Pomodoro » : 25 min de concentration / 5 min de pause, 4 cycles, validation à chaque étape. */
    fun pomodoro(now: Long): Program = Program(
        id = UUID.randomUUID().toString(),
        name = "Pomodoro",
        colorIndex = 3,
        steps = listOf(
            step("Concentration", 25 * MIN),
            step("Pause", 5 * MIN),
        ),
        defaultWaitForAck = true,
        repeatCount = 4,
        createdAt = now,
        updatedAt = now,
    )

    /** « Entraînement fractionné » : 30 s d'effort / 15 s de récupération, 8 cycles. */
    fun intervals(now: Long): Program = Program(
        id = UUID.randomUUID().toString(),
        name = "Entraînement fractionné",
        colorIndex = 4,
        steps = listOf(
            step("Effort", 30 * SEC),
            step("Récupération", 15 * SEC),
        ),
        ringSeconds = 3,
        repeatCount = 8,
        createdAt = now,
        updatedAt = now,
    )

    fun create(kind: Kind, now: Long): Program = when (kind) {
        Kind.EXAMPLE_30 -> example30(now)
        Kind.POMODORO -> pomodoro(now)
        Kind.INTERVALS -> intervals(now)
    }

    fun all(now: Long): List<Program> = Kind.entries.map { create(it, now) }
}
