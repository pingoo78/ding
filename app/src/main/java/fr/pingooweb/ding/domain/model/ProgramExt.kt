package fr.pingooweb.ding.domain.model

/** Duree d'un cycle complet (somme des durees d'etapes). */
val Program.totalDurationMs: Long get() = steps.sumOf { it.durationMs }

/** Instants cumules (depuis le depart du cycle) auxquels chaque etape est atteinte. */
fun Program.cumulativeOffsets(): List<Long> {
    var acc = 0L
    return steps.map { acc += it.durationMs; acc }
}

fun Step.effectiveWaitForAck(p: Program): Boolean = waitForAck ?: p.defaultWaitForAck
fun Step.effectiveSound(p: Program): SoundChoice = sound ?: p.defaultSound
fun Step.effectiveVibrate(p: Program): Boolean = vibrate ?: p.defaultVibrate

/** Libelle affichable : "Etape n" (1-based) si vide. Le format francais est porte par [defaultLabelFormat]. */
fun Step.displayLabel(index: Int, defaultLabelFormat: (Int) -> String = { "Étape $it" }): String =
    label.ifBlank { defaultLabelFormat(index + 1) }

/** Convertit des instants cumules (strictement croissants) en durees d'etapes. Null si non strictement croissants ou <= 0. */
fun cumulativeToDurations(cumulative: List<Long>): List<Long>? {
    var prev = 0L
    val out = ArrayList<Long>(cumulative.size)
    for (c in cumulative) {
        if (c <= prev) return null
        out += c - prev
        prev = c
    }
    return out
}
