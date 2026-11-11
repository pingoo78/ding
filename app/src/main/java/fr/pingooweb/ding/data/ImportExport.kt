package fr.pingooweb.ding.data

import fr.pingooweb.ding.domain.model.Program
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable
data class ExportFile(val version: Int = ImportExport.CURRENT_VERSION, val programs: List<Program>)

object ImportExport {
    const val CURRENT_VERSION = 1

    private val prettyJson = kotlinx.serialization.json.Json(from = DingJson) { prettyPrint = true }

    fun encode(programs: List<Program>): String =
        prettyJson.encodeToString(ExportFile(CURRENT_VERSION, programs))

    /** Décode un fichier exporté. Lève [IllegalArgumentException] si invalide ou version non supportée. */
    fun decode(json: String): List<Program> {
        val file = try {
            DingJson.decodeFromString<ExportFile>(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Fichier Ding invalide", e)
        }
        require(file.version in 1..CURRENT_VERSION) { "Version non supportée : ${file.version}" }
        return file.programs.filter { it.steps.isNotEmpty() }.map(::sanitize)
    }

    /** Ramène les valeurs hors bornes d'un fichier importé à des valeurs que le moteur et l'éditeur acceptent. */
    internal fun sanitize(p: Program): Program = p.copy(
        colorIndex = p.colorIndex.coerceIn(0, 7),
        steps = p.steps.map { it.copy(durationMs = it.durationMs.coerceIn(1_000L, 100L * 3_600_000L)) },
        ringSeconds = p.ringSeconds.coerceIn(1, 60),
        repeatCount = if (p.repeatCount < 0) 1 else p.repeatCount.coerceAtMost(20),
    )

    /**
     * Fusionne [imported] dans [existing] : tout programme dont l'id est déjà présent (ou dupliqué dans
     * l'import) reçoit un nouvel id ; les ids d'étapes sont régénérés en cas de collision également.
     * Renvoie uniquement les programmes à ajouter.
     */
    fun mergeInto(existing: List<Program>, imported: List<Program>): List<Program> {
        val usedIds = existing.map { it.id }.toMutableSet()
        return imported.map { p ->
            val newId = if (p.id in usedIds || p.id.isBlank()) UUID.randomUUID().toString() else p.id
            usedIds += newId
            if (newId == p.id) p
            else p.copy(id = newId, steps = p.steps.map { it.copy(id = UUID.randomUUID().toString()) })
        }
    }
}
