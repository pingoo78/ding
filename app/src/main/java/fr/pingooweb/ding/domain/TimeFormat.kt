package fr.pingooweb.ding.domain

import java.util.Locale

object TimeFormat {
    /** "m:ss" ou "h:mm:ss". [roundUp] true (decompte) : arrondi a la seconde superieure. */
    fun format(ms: Long, roundUp: Boolean = true): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = if (roundUp) (clamped + 999) / 1000 else clamped / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Pave numerique type horloge : les chiffres remplissent h:mm:ss depuis la droite ("1230" -> 12:30). Max 6 chiffres. */
    fun parseDigits(digits: String): Long {
        val d = digits.filter { it.isDigit() }.takeLast(6).padStart(6, '0')
        val h = d.substring(0, 2).toLong()
        val m = d.substring(2, 4).toLong()
        val s = d.substring(4, 6).toLong()
        return ((h * 60 + m) * 60 + s) * 1000
    }

    /** Affichage "h:mm:ss" progressif de la saisie (sans zeros de tete inutiles : "1230" -> "12:30"). */
    fun formatDigits(digits: String): String {
        val d = digits.filter { it.isDigit() }.takeLast(6).trimStart('0').padStart(3, '0')
        val sec = d.takeLast(2)
        val rest = d.dropLast(2)
        val min = rest.takeLast(2)
        val hours = rest.dropLast(2)
        return if (hours.isEmpty()) "${min.trimStart('0').ifEmpty { "0" }}:$sec" else "$hours:${min.padStart(2, '0')}:$sec"
    }

    /** Inverse de [parseDigits] : chiffres bruts pour pre-remplir le pave. */
    fun toDigits(ms: Long): String {
        val totalSec = ms.coerceAtLeast(0L) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.ROOT, "%d%02d%02d", h, m, s).trimStart('0')
    }
}
