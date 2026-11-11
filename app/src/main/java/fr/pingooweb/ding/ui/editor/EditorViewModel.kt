package fr.pingooweb.ding.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.pingooweb.ding.data.EditorInputMode
import fr.pingooweb.ding.data.ProgramRepository
import fr.pingooweb.ding.data.SettingsRepository
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.SoundChoice
import fr.pingooweb.ding.domain.model.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val draft: Program? = null,
    val original: Program? = null,
    val inputMode: EditorInputMode = EditorInputMode.CUMULATIVE,
) {
    val dirty: Boolean get() = draft != null && draft != original
    val invalidIndices: Set<Int> get() = draft?.let { EditorLogic.invalidIndices(it.steps) } ?: emptySet()
    val valid: Boolean get() = draft?.let { EditorLogic.isValid(it.steps) } ?: false
}

/** Actions d'édition (implémentées par le ViewModel ; les aperçus utilisent l'implémentation vide). */
interface EditorActions {
    fun setName(name: String) {}
    fun setColor(index: Int) {}
    fun setInputMode(mode: EditorInputMode) {}
    fun addStep(): String = ""
    fun setLabel(stepId: String, label: String) {}
    fun setStepTime(stepId: String, ms: Long) {}
    fun setStepAck(stepId: String, value: Boolean?) {}
    fun setStepSound(stepId: String, value: SoundChoice?) {}
    fun setStepVibrate(stepId: String, value: Boolean?) {}
    fun deleteStep(stepId: String) {}
    fun moveStep(fromId: Any, toId: Any) {}
    fun moveStepBy(stepId: String, delta: Int) {}
    fun setDefaultAck(value: Boolean) {}
    fun setDefaultSound(value: SoundChoice) {}
    fun setDefaultVibrate(value: Boolean) {}
    fun setAnnounce(value: Boolean) {}
    fun setRingSeconds(value: Int) {}
    fun setRepeat(value: Int) {}
}

sealed interface SaveResult {
    data class Saved(val program: Program) : SaveResult
    data object Invalid : SaveResult
}

class EditorViewModel(
    private val programId: String,
    private val repository: ProgramRepository,
    private val settings: SettingsRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel(), EditorActions {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = settings.settings.first().editorInputMode
            if (programId == NEW_ID) {
                val now = nowMs()
                val p = Program(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    steps = listOf(newStep(5 * MIN)),
                    createdAt = now,
                    updatedAt = now,
                )
                _state.value = EditorState(loaded = true, isNew = true, draft = p, original = p, inputMode = mode)
            } else {
                val p = repository.get(programId)
                _state.value = if (p == null) EditorState(loaded = true, notFound = true, inputMode = mode)
                else EditorState(loaded = true, isNew = false, draft = p, original = p, inputMode = mode)
            }
        }
    }

    private fun edit(block: (Program) -> Program) = _state.update { s ->
        s.draft?.let { s.copy(draft = block(it)) } ?: s
    }

    private fun editSteps(block: (List<Step>) -> List<Step>) = edit { it.copy(steps = block(it.steps)) }

    private fun editStep(id: String, block: (Step) -> Step) =
        editSteps { steps -> steps.map { if (it.id == id) block(it) else it } }

    override fun setName(name: String) = edit { it.copy(name = name.take(60)) }
    override fun setColor(index: Int) = edit { it.copy(colorIndex = index) }
    override fun setInputMode(mode: EditorInputMode) = _state.update { it.copy(inputMode = mode) }

    override fun addStep(): String {
        val step = newStep(5 * MIN)
        editSteps { it + step }
        return step.id
    }

    override fun setLabel(stepId: String, label: String) = editStep(stepId) { it.copy(label = label.take(80)) }

    override fun setStepTime(stepId: String, ms: Long) {
        val s = _state.value
        val steps = s.draft?.steps ?: return
        val index = steps.indexOfFirst { it.id == stepId }
        if (index < 0) return
        editSteps {
            if (s.inputMode == EditorInputMode.CUMULATIVE) EditorLogic.setCumulative(it, index, ms)
            else EditorLogic.setDuration(it, index, ms)
        }
    }

    override fun setStepAck(stepId: String, value: Boolean?) = editStep(stepId) { it.copy(waitForAck = value) }
    override fun setStepSound(stepId: String, value: SoundChoice?) = editStep(stepId) { it.copy(sound = value) }
    override fun setStepVibrate(stepId: String, value: Boolean?) = editStep(stepId) { it.copy(vibrate = value) }

    override fun deleteStep(stepId: String) = editSteps { steps ->
        if (steps.size <= 1) steps else steps.filterNot { it.id == stepId }
    }

    override fun moveStep(fromId: Any, toId: Any) = editSteps { steps ->
        val from = steps.indexOfFirst { it.id == fromId }
        val to = steps.indexOfFirst { it.id == toId }
        EditorLogic.move(steps, from, to)
    }

    override fun moveStepBy(stepId: String, delta: Int) = editSteps { steps ->
        val from = steps.indexOfFirst { it.id == stepId }
        EditorLogic.move(steps, from, (from + delta).coerceIn(0, steps.lastIndex))
    }

    override fun setDefaultAck(value: Boolean) = edit { it.copy(defaultWaitForAck = value) }
    override fun setDefaultSound(value: SoundChoice) = edit { it.copy(defaultSound = value) }
    override fun setDefaultVibrate(value: Boolean) = edit { it.copy(defaultVibrate = value) }
    override fun setAnnounce(value: Boolean) = edit { it.copy(announceLabels = value) }
    override fun setRingSeconds(value: Int) = edit { it.copy(ringSeconds = value) }
    override fun setRepeat(value: Int) = edit { it.copy(repeatCount = value.coerceIn(0, 20)) }

    /** Enregistre le brouillon (met à jour `updatedAt`). [defaultName] remplace un nom vide. */
    suspend fun save(defaultName: String): SaveResult {
        val s = _state.value
        val draft = s.draft ?: return SaveResult.Invalid
        if (!s.valid) return SaveResult.Invalid
        val program = draft.copy(name = draft.name.trim().ifEmpty { defaultName }, updatedAt = nowMs())
        repository.upsert(program)
        _state.update { it.copy(draft = program, original = program, isNew = false) }
        return SaveResult.Saved(program)
    }

    private fun newStep(durationMs: Long) = Step(id = UUID.randomUUID().toString(), label = "", durationMs = durationMs)

    private companion object {
        const val NEW_ID = "new"
        const val MIN = 60_000L
    }
}
