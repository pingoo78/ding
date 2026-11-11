package fr.pingooweb.ding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.pingooweb.ding.domain.engine.RunState
import kotlinx.coroutines.flow.first

/**
 * Persiste le programme actif. Les instants du [RunState] sont des `elapsedRealtime` : ils ne survivent
 * pas à un redémarrage, d'où le `bootCount` (Settings.Global.BOOT_COUNT) stocké avec l'état.
 */
interface ActiveRunStore {
    /** Persiste [state] (ou efface si null) avec le compteur de démarrages courant. */
    suspend fun save(state: RunState?, bootCount: Int)

    /** Restaure l'état si [currentBootCount] correspond à celui stocké ; sinon efface et renvoie null. */
    suspend fun restore(currentBootCount: Int): RunState?
}

class DataStoreActiveRunStore(private val dataStore: DataStore<Preferences>) : ActiveRunStore {

    private val stateKey = stringPreferencesKey("active_run_json")
    private val bootKey = intPreferencesKey("active_run_boot_count")

    override suspend fun save(state: RunState?, bootCount: Int) {
        dataStore.edit { p ->
            if (state == null) {
                p.remove(stateKey)
                p.remove(bootKey)
            } else {
                p[stateKey] = DingJson.encodeToString(RunState.serializer(), state)
                p[bootKey] = bootCount
            }
        }
    }

    override suspend fun restore(currentBootCount: Int): RunState? {
        val p = dataStore.data.first()
        val raw = p[stateKey] ?: return null
        val boot = p[bootKey]
        val state = if (boot == currentBootCount) {
            runCatching { DingJson.decodeFromString(RunState.serializer(), raw) }.getOrNull()
        } else null
        if (state == null) save(null, currentBootCount)
        return state
    }
}
