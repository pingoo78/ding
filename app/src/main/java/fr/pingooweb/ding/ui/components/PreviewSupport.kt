package fr.pingooweb.ding.ui.components

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.Step
import fr.pingooweb.ding.ui.theme.DingTheme

/** Aperçus clair + sombre sur fond de thème. */
@Preview(name = "Clair", showBackground = true, widthDp = 360, heightDp = 720)
@Preview(name = "Sombre", showBackground = true, widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    val dark = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    DingTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

object PreviewData {
    private const val MIN = 60_000L

    fun program(name: String = "Rôti de bœuf", colorIndex: Int = 0, id: String = "p1"): Program = Program(
        id = id,
        name = name,
        colorIndex = colorIndex,
        steps = listOf(
            Step("s1", "Retourner la viande", 5 * MIN),
            Step("s2", "Arroser", 7 * MIN, waitForAck = true),
            Step("s3", "", 6 * MIN),
            Step("s4", "Sortir du four", 5 * MIN, waitForAck = true),
            Step("s5", "Repos terminé", 7 * MIN),
        ),
        repeatCount = 2,
        createdAt = 0, updatedAt = 0,
    )

    fun runState(index: Int = 1, now: Long = 1_000_000L): RunState =
        RunEngine.start(program(), now - 3 * MIN).copy(currentIndex = index, stepStartedAt = now - 3 * MIN, completedSteps = index)
}
