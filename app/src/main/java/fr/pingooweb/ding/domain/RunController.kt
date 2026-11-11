package fr.pingooweb.ding.domain

import fr.pingooweb.ding.domain.engine.RunState
import kotlinx.coroutines.flow.StateFlow

interface RunController {
    val state: StateFlow<RunState?>
    val alerting: StateFlow<Alert?>
    fun start(programId: String)
    fun pause()
    fun resume()
    fun acknowledge()
    fun silence()
    fun skip()
    fun stop()
}

data class Alert(val stepIndex: Int, val stepLabel: String, val waitsForAck: Boolean, val isFinal: Boolean)
