package fr.pingooweb.ding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class EditorInputMode { CUMULATIVE, DURATION }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = true,
    val editorInputMode: EditorInputMode = EditorInputMode.CUMULATIVE,
    val haptics: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setEditorInputMode(mode: EditorInputMode)
    suspend fun setHaptics(enabled: Boolean)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val keepOnKey = booleanPreferencesKey("keep_screen_on")
    private val inputModeKey = stringPreferencesKey("editor_input_mode")
    private val hapticsKey = booleanPreferencesKey("haptics")

    override val settings: Flow<AppSettings> = dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[dynamicKey] ?: d.dynamicColor,
            keepScreenOn = p[keepOnKey] ?: d.keepScreenOn,
            editorInputMode = p[inputModeKey]?.let { runCatching { EditorInputMode.valueOf(it) }.getOrNull() }
                ?: d.editorInputMode,
            haptics = p[hapticsKey] ?: d.haptics,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[themeKey] = mode.name } }
    override suspend fun setDynamicColor(enabled: Boolean) { dataStore.edit { it[dynamicKey] = enabled } }
    override suspend fun setKeepScreenOn(enabled: Boolean) { dataStore.edit { it[keepOnKey] = enabled } }
    override suspend fun setEditorInputMode(mode: EditorInputMode) { dataStore.edit { it[inputModeKey] = mode.name } }
    override suspend fun setHaptics(enabled: Boolean) { dataStore.edit { it[hapticsKey] = enabled } }
}
