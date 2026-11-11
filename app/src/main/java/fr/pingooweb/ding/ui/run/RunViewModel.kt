package fr.pingooweb.ding.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.SettingsRepository
import fr.pingooweb.ding.domain.Alert
import fr.pingooweb.ding.domain.RunController
import fr.pingooweb.ding.domain.engine.RunState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Façade fine sur le [RunController] (le ticker d'affichage vit dans la composition, cf. `rememberNowMs`). */
class RunViewModel(
    private val controller: RunController,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<RunState?> get() = controller.state
    val alerting: StateFlow<Alert?> get() = controller.alerting

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun acknowledge() = controller.acknowledge()
    fun silence() = controller.silence()
    fun skip() = controller.skip()
    fun stop() = controller.stop()
    fun restart(programId: String) = controller.start(programId)
}
