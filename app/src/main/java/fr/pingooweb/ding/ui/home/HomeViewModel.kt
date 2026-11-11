package fr.pingooweb.ding.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.ImportExport
import fr.pingooweb.ding.data.ProgramRepository
import fr.pingooweb.ding.data.SettingsRepository
import fr.pingooweb.ding.data.Templates
import fr.pingooweb.ding.domain.RunController
import fr.pingooweb.ding.domain.model.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val appContext: Context,
    private val repository: ProgramRepository,
    val runController: RunController,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** null = chargement en cours. */
    val programs: StateFlow<List<Program>?> =
        repository.programs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun delete(program: Program) {
        viewModelScope.launch { repository.delete(program.id) }
    }

    fun restore(program: Program) {
        viewModelScope.launch { repository.upsert(program) }
    }

    fun duplicate(program: Program) {
        viewModelScope.launch { repository.duplicate(program.id) }
    }

    fun addTemplate(kind: Templates.Kind) {
        viewModelScope.launch { repository.upsert(Templates.create(kind, System.currentTimeMillis())) }
    }

    /** Écrit le programme dans [uri] (document SAF). Renvoie true si OK. */
    suspend fun exportProgram(uri: Uri, programId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val program = repository.get(programId) ?: return@runCatching false
            val bytes = ImportExport.encode(listOf(program)).toByteArray(Charsets.UTF_8)
            val out = appContext.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
            out.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }
}
