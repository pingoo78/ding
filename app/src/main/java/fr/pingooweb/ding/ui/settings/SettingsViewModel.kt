package fr.pingooweb.ding.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.EditorInputMode
import fr.pingooweb.ding.data.ImportExport
import fr.pingooweb.ding.data.ProgramRepository
import fr.pingooweb.ding.data.SettingsRepository
import fr.pingooweb.ding.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TransferResult {
    data class Exported(val count: Int) : TransferResult
    data class Imported(val count: Int) : TransferResult
    data object InvalidFile : TransferResult
    data object IoError : TransferResult
}

class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val programs: ProgramRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepository.setKeepScreenOn(v) }
    fun setEditorInputMode(m: EditorInputMode) = viewModelScope.launch { settingsRepository.setEditorInputMode(m) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsRepository.setHaptics(v) }

    suspend fun exportAll(uri: Uri): TransferResult = withContext(Dispatchers.IO) {
        try {
            val list = programs.programs.first()
            val bytes = ImportExport.encode(list).toByteArray(Charsets.UTF_8)
            val out = appContext.contentResolver.openOutputStream(uri, "wt") ?: return@withContext TransferResult.IoError
            out.use { it.write(bytes) }
            TransferResult.Exported(list.size)
        } catch (e: Exception) {
            TransferResult.IoError
        }
    }

    suspend fun import(uri: Uri): TransferResult = withContext(Dispatchers.IO) {
        val text = try {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext TransferResult.IoError
        } catch (e: Exception) {
            return@withContext TransferResult.IoError
        }
        try {
            val imported = ImportExport.decode(text)
            val toAdd = ImportExport.mergeInto(programs.programs.first(), imported)
            programs.addAll(toAdd)
            TransferResult.Imported(toAdd.size)
        } catch (e: IllegalArgumentException) {
            TransferResult.InvalidFile
        } catch (e: Exception) {
            TransferResult.IoError
        }
    }
}
