package fr.pingooweb.ding.di

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import fr.pingooweb.ding.data.ActiveRunStore
import fr.pingooweb.ding.data.DataStoreActiveRunStore
import fr.pingooweb.ding.data.DataStoreProgramRepository
import fr.pingooweb.ding.data.DataStoreSettingsRepository
import fr.pingooweb.ding.data.ProgramRepository
import fr.pingooweb.ding.data.SettingsRepository
import fr.pingooweb.ding.runtime.DefaultRunController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.dingDataStore: DataStore<Preferences> by preferencesDataStore(name = "ding")

/** Conteneur d'injection manuelle, créé une fois dans `DingApp`. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Scope applicatif (thread principal). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val dataStore: DataStore<Preferences> = appContext.dingDataStore

    val programRepository: ProgramRepository = DataStoreProgramRepository(appContext, dataStore)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(dataStore)
    val activeRunStore: ActiveRunStore = DataStoreActiveRunStore(dataStore)

    // Type concret exposé : les receivers ont besoin de onAlarm()/handleAction() ; reste un RunController pour l'UI.
    val runController: DefaultRunController =
        DefaultRunController(appContext, programRepository, activeRunStore, appScope, clock = SystemClock::elapsedRealtime)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer non fourni : utiliser CompositionLocalProvider(LocalAppContainer provides ...)")
}
