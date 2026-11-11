package fr.pingooweb.ding.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Program(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val steps: List<Step>,
    val defaultWaitForAck: Boolean = false,
    val defaultSound: SoundChoice = SoundChoice.SystemAlarm,
    val defaultVibrate: Boolean = true,
    val announceLabels: Boolean = false,
    val ringSeconds: Int = 8,
    val repeatCount: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long? = null,
)

@Serializable
data class Step(
    val id: String,
    val label: String,
    val durationMs: Long,
    val waitForAck: Boolean? = null,
    val sound: SoundChoice? = null,
    val vibrate: Boolean? = null,
)

@Serializable
sealed interface SoundChoice {
    @Serializable
    @SerialName("system_alarm")
    data object SystemAlarm : SoundChoice

    @Serializable
    @SerialName("silent")
    data object Silent : SoundChoice

    @Serializable
    @SerialName("custom")
    data class Custom(val uri: String, val title: String) : SoundChoice
}
