package fr.pingooweb.ding.ui.editor

import fr.pingooweb.ding.domain.model.Step

/**
 * Logique pure de l'éditeur (testable en JVM). Les durées peuvent être <= 0 le temps d'une saisie
 * intermédiaire : ces étapes sont alors signalées par [invalidIndices] et bloquent l'enregistrement.
 */
object EditorLogic {

    fun cumulative(steps: List<Step>): List<Long> {
        var acc = 0L
        return steps.map { acc += it.durationMs; acc }
    }

    /** Index des étapes dont l'instant n'est pas strictement après le précédent (durée <= 0). */
    fun invalidIndices(steps: List<Step>): Set<Int> =
        steps.indices.filter { steps[it].durationMs <= 0L }.toSet()

    fun isValid(steps: List<Step>): Boolean = steps.isNotEmpty() && invalidIndices(steps).isEmpty()

    /** Mode « Durée d'étape » : remplace la durée de l'étape [index]. */
    fun setDuration(steps: List<Step>, index: Int, durationMs: Long): List<Step> =
        steps.mapIndexed { i, s -> if (i == index) s.copy(durationMs = durationMs) else s }

    /**
     * Mode « Heure de sonnerie » : l'étape [index] sonne à [cumulativeMs] ; les instants des AUTRES étapes ne
     * bougent pas (la durée de l'étape suivante absorbe la différence).
     */
    fun setCumulative(steps: List<Step>, index: Int, cumulativeMs: Long): List<Step> {
        if (index !in steps.indices) return steps
        val cum = cumulative(steps)
        val prev = if (index == 0) 0L else cum[index - 1]
        val out = steps.toMutableList()
        out[index] = steps[index].copy(durationMs = cumulativeMs - prev)
        if (index < steps.lastIndex) out[index + 1] = steps[index + 1].copy(durationMs = cum[index + 1] - cumulativeMs)
        return out
    }

    /** Déplace une étape ; sa durée la suit (les instants cumulés sont recalculés). */
    fun move(steps: List<Step>, from: Int, to: Int): List<Step> {
        if (from !in steps.indices || to !in steps.indices || from == to) return steps
        val out = steps.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }
}
