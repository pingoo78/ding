package fr.pingooweb.ding.alert

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pingooweb.ding.DingApp
import fr.pingooweb.ding.R
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.ThemeMode
import fr.pingooweb.ding.domain.Alert
import fr.pingooweb.ding.domain.RunController
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.ui.components.StepReachedContent
import fr.pingooweb.ding.ui.components.StepReachedFromRun
import fr.pingooweb.ding.ui.components.rememberReducedMotion
import fr.pingooweb.ding.ui.theme.DingTheme
import fr.pingooweb.ding.ui.theme.extended

/** Alerte plein écran (au-dessus de l'écran de verrouillage). Se ferme quand l'alerte disparaît. */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        enableEdgeToEdge()

        val container = (application as DingApp).container
        val controller: RunController = container.runController

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val alert by controller.alerting.collectAsState()
            val runState by controller.state.collectAsState()

            LaunchedEffect(alert) { if (alert == null) finish() }

            DingTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                alert?.let { a ->
                    AlertScreen(
                        alert = a,
                        runState = runState,
                        onAcknowledge = controller::acknowledge,
                        onSilence = controller::silence,
                        onStop = controller::stop,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertScreen(
    alert: Alert,
    runState: RunState?,
    onAcknowledge: () -> Unit,
    onSilence: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.extended
    val reduced = rememberReducedMotion()
    var confirmStop by remember { mutableStateOf(false) }

    val total = runState?.program?.steps?.size
    val isDefault = runState?.program?.steps?.getOrNull(alert.stepIndex)?.label?.isBlank() ?: false
    val progress = total?.let { stringResource(R.string.run_step_of, alert.stepIndex + 1, it) }
    val programName = runState?.program?.name
    val subtitle = if (total != null && programName != null) {
        if (isDefault) programName
        else stringResource(R.string.rt_alert_program_step, alert.stepIndex + 1, total, programName)
    } else null

    Surface(modifier = Modifier.fillMaxSize(), color = colors.attention, contentColor = colors.onAttention) {
        val primaryDescription = stringResource(
            if (alert.waitsForAck) R.string.rt_alert_acknowledge_description else R.string.rt_alert_silence_description,
        )
        if (runState != null) {
            StepReachedFromRun(
                state = runState,
                alertIndex = alert.stepIndex,
                alertLabel = alert.stepLabel,
                waiting = alert.waitsForAck,
                reducedMotion = reduced,
                onPrimary = if (alert.waitsForAck) onAcknowledge else onSilence,
                onSilence = onSilence,
                onStop = { confirmStop = true },
                modifier = Modifier.systemBarsPadding(),
                primaryDescription = primaryDescription,
                stopLabel = stringResource(R.string.rt_alert_stop_program),
            )
        } else {
            StepReachedContent(
                title = if (isDefault && progress != null) progress else alert.stepLabel,
                subtitle = subtitle,
                waiting = alert.waitsForAck,
                reducedMotion = reduced,
                announce = stringResource(R.string.attention_announce, alert.stepLabel),
                onPrimary = if (alert.waitsForAck) onAcknowledge else onSilence,
                onSilence = onSilence,
                onStop = { confirmStop = true },
                modifier = Modifier.systemBarsPadding(),
                primaryDescription = primaryDescription,
                stopLabel = stringResource(R.string.rt_alert_stop_program),
            )
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.rt_alert_stop_confirm_title)) },
            text = { Text(stringResource(R.string.rt_alert_stop_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmStop = false; onStop() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.rt_alert_stop_confirm_yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmStop = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.rt_alert_stop_confirm_no)) }
            },
        )
    }
}
