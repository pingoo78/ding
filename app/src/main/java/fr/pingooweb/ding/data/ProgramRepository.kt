package fr.pingooweb.ding.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.pingooweb.ding.domain.model.Program
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

interface ProgramRepository {
    val programs: Flow<List<Program>>
    suspend fun get(id: String): Program?

    /** Insère ou remplace (par id). Un nouveau programme est ajouté en tête de liste. */
    suspend fun upsert(program: Program)
    suspend fun delete(id: String)

    /** Duplique un programme (nouveaux ids, nom « … (copie) »). Renvoie la copie, ou null si introuvable. */
    suspend fun duplicate(id: String): Program?

    /** Ajoute plusieurs programmes d'un coup (import, modèles). */
    suspend fun addAll(programs: List<Program>)
}

class DataStoreProgramRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val copySuffix: String = " (copie)",
) : ProgramRepository {

    private val key = stringPreferencesKey("programs_json")
    private val serializer = ListSerializer(Program.serializer())

    override val programs: Flow<List<Program>> = dataStore.data.map { decode(it[key]) }

    override suspend fun get(id: String): Program? = programs.first().firstOrNull { it.id == id }

    override suspend fun upsert(program: Program) = update { list ->
        if (list.any { it.id == program.id }) list.map { if (it.id == program.id) program else it }
        else listOf(program) + list
    }

    override suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    override suspend fun duplicate(id: String): Program? {
        var copy: Program? = null
        update { list ->
            val src = list.firstOrNull { it.id == id } ?: return@update list
            val now = clock()
            val c = src.copy(
                id = UUID.randomUUID().toString(),
                name = src.name + copySuffix,
                steps = src.steps.map { it.copy(id = UUID.randomUUID().toString()) },
                createdAt = now,
                updatedAt = now,
                lastRunAt = null,
            )
            copy = c
            val idx = list.indexOfFirst { it.id == id }
            list.toMutableList().apply { add(idx + 1, c) }
        }
        return copy
    }

    override suspend fun addAll(programs: List<Program>) = update { list -> programs + list }

    private suspend fun update(transform: (List<Program>) -> List<Program>) {
        dataStore.edit { prefs ->
            val new = transform(decode(prefs[key]))
            prefs[key] = DingJson.encodeToString(serializer, new)
        }
    }

    private var lastCorruptBackedUp: String? = null

    private fun decode(raw: String?): List<Program> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { DingJson.decodeFromString(serializer, raw) }.getOrElse { e ->
            backupCorrupt(raw, e)
            emptyList()
        }
    }

    /** Sauvegarde le contenu illisible (une seule fois par contenu) avant qu'une écriture ne l'écrase. */
    private fun backupCorrupt(raw: String, e: Throwable) {
        synchronized(this) {
            if (lastCorruptBackedUp == raw) return
            lastCorruptBackedUp = raw
        }
        runCatching {
            val file = File(context.filesDir, "programs-corrupt-${clock()}.json")
            file.writeText(raw)
            Log.w("ProgramRepository", "JSON des programmes illisible, sauvegardé dans ${file.name}", e)
        }.onFailure { Log.w("ProgramRepository", "Sauvegarde du JSON corrompu impossible", it) }
    }
}
