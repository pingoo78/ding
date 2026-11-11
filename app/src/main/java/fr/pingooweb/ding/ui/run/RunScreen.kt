package fr.pingooweb.ding.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pingooweb.ding.R
import fr.pingooweb.ding.domain.Alert
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.domain.model.effectiveWaitForAck
import fr.pingooweb.ding.ui.components.ConfirmDialog
import fr.pingooweb.ding.ui.components.Haptics
import fr.pingooweb.ding.ui.components.PreviewData
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.StepReachedFromRun
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.components.bento.BentoTile
import fr.pingooweb.ding.ui.components.bento.IconBadge
import fr.pingooweb.ding.ui.components.bento.PillButton
import fr.pingooweb.ding.ui.components.bento.PillSize
import fr.pingooweb.ding.ui.components.bento.PillVariant
import fr.pingooweb.ding.ui.components.bento.RoundIconButton
import fr.pingooweb.ding.ui.components.bento.SegmentBar
import fr.pingooweb.ding.ui.components.bento.StatTile
import fr.pingooweb.ding.ui.components.labelOrDefault
import fr.pingooweb.ding.ui.components.rememberHaptics
import fr.pingooweb.ding.ui.components.rememberNowMs
import fr.pingooweb.ding.ui.components.rememberReducedMotion
import fr.pingooweb.ding.ui.navigation.containerViewModel
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.ProgramColors
import fr.pingooweb.ding.ui.theme.TimerHero
import fr.pingooweb.ding.ui.theme.TimerSmall
import fr.pingooweb.ding.ui.theme.extended
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun RunRoute(onClose: () -> Unit) {
    val vm = containerViewModel { RunViewModel(it.runController, it.settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val alert by vm.alerting.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(settings.haptics)
    val reduced = rememberReducedMotion()

    val close = remember {
        var done = false
        { if (!done) { done = true; onClose() } }
    }

    // state == null : retour accueil (avec un court délai de grâce tant qu'on n'a jamais vu d'état :
    // le démarrage du contrôleur est asynchrone).
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(state == null) {
        if (state != null) seen = true
        else {
            delay(if (seen) 0L else 2_500L)
            close()
        }
    }

    // Écran maintenu allumé (réglage) pendant que l'écran est affiché.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn, view) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val current = state ?: return
    val nowState = rememberNowMs(active = current.status == RunStatus.RUNNING)

    RunScreenContent(
        state = current,
        alert = alert,
        nowMs = nowState.value,
        reducedMotion = reduced,
        haptics = haptics,
        onBack = close,
        onPauseResume = {
            haptics.confirm()
            if (current.status == RunStatus.PAUSED) vm.resume() else vm.pause()
        },
        onSkip = { haptics.tick(); vm.skip() },
        onStop = { vm.stop() },
        onAcknowledge = { haptics.confirm(); vm.acknowledge() },
        onSilence = { haptics.tick(); vm.silence() },
        onRestart = { haptics.confirm(); vm.restart(current.program.id) },
        onFinish = { vm.stop(); close() },
    )
}

@Composable
fun RunScreenContent(
    state: RunState,
    alert: Alert?,
    nowMs: Long,
    reducedMotion: Boolean,
    haptics: Haptics,
    onBack: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onAcknowledge: () -> Unit,
    onSilence: () -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    val finished = state.status == RunStatus.FINISHED
    val attention = !finished && (alert != null || state.status == RunStatus.WAITING_ACK)
    val container = if (attention) MaterialTheme.extended.attention else MaterialTheme.colorScheme.background

    Scaffold(containerColor = container) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                finished -> FinishedContent(state, nowMs, alert != null, onRestart, onFinish, onSilence)
                attention -> AttentionContent(state, alert, reducedMotion, onAcknowledge, onSilence) { confirmStop = true }
                else -> RunningContent(state, nowMs, onBack, onPauseResume, onSkip) { confirmStop = true }
            }
        }
    }

    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.run_stop_title),
            message = stringResource(R.string.run_stop_message, state.program.name),
            confirmLabel = stringResource(R.string.action_stop),
            destructive = true,
            onConfirm = { confirmStop = false; onStop() },
            onDismiss = { confirmStop = false },
        )
    }
}

// ---------------------------------------------------------------- En cours

@Composable
private fun RunningContent(
    state: RunState,
    now: Long,
    onBack: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val program = state.program
    val colors = MaterialTheme.extended.program(program.colorIndex)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = maxWidth
        val margin = Dimens.screenMargin(boxW)
        Column(Modifier.fillMaxSize().padding(horizontal = margin)) {
            HeaderRow(program.name, onBack, Modifier.padding(vertical = 8.dp))
            if (boxW > this@BoxWithConstraints.maxHeight) {
                // Paysage : tuile principale + stats à gauche ; étapes + contrôles à droite.
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                    ) {
                        MainTile(state, now, colors)
                        StatsGrid(state, now)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                        StepsTile(state, now, colors, scrollable = true, modifier = Modifier.weight(1f, fill = false))
                        ControlsTile(state.status, colors, boxW / 2, onPauseResume, onSkip, onStop)
                        Spacer(Modifier.height(Dimens.Space2))
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    MainTile(state, now, colors)
                    StatsGrid(state, now)
                    StepsTile(state, now, colors, scrollable = false)
                }
                Spacer(Modifier.height(Dimens.Gap))
                ControlsTile(state.status, colors, boxW - margin * 2, onPauseResume, onSkip, onStop)
                Spacer(Modifier.height(Dimens.Space2))
            }
        }
    }
}

@Composable
private fun HeaderRow(name: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(
            Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.run_back_home), onBack,
            container = MaterialTheme.extended.tile,
        )
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.extended.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

@Composable
private fun MainTile(state: RunState, now: Long, colors: ProgramColors) {
    val ext = MaterialTheme.extended
    val program = state.program
    val index = state.currentIndex.coerceIn(0, program.steps.lastIndex)
    val step = program.steps[index]
    val digits = TimeFormat.format(RunEngine.remainingInStepMs(state, now))
    val progress = RunMath.stepProgress(state, now)
    val fontScale = LocalDensity.current.fontScale
    val pctDesc = stringResource(R.string.run_progress_desc)

    BentoTile(colors, Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.run_step_of, index + 1, program.steps.size).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.08.sp),
        )
        if (step.label.isNotBlank()) {
            Text(
                step.label,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.status == RunStatus.PAUSED) {
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.banner_paused), style = MaterialTheme.typography.labelLarge)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val digitSp = (maxWidth.value / (digits.length * 0.6f) / fontScale).coerceIn(36f, 96f)
            BasicText(
                digits,
                style = TimerHero.copy(
                    fontSize = digitSp.sp,
                    color = ext.ink,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
        // Barre épaisse (14 dp), linéaire, sans animation de lissage.
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(ext.tile)
                .semantics {
                    contentDescription = pctDesc
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.strong, CircleShape))
        }
    }
}

@Composable
private fun StatsGrid(state: RunState, now: Long) {
    val elapsed = RunEngine.elapsedEffectiveMs(state, now)
    val total = RunMath.totalRemainingMs(state, now)
    val repeat = state.program.repeatCount
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            StatTile(
                stringResource(R.string.run_stat_elapsed), TimeFormat.format(elapsed, roundUp = false),
                Modifier.weight(1f),
            )
            StatTile(
                stringResource(R.string.run_stat_remaining),
                total?.let { TimeFormat.format(it) } ?: stringResource(R.string.infinity),
                Modifier.weight(1f),
            )
        }
        if (repeat != 1) {
            StatTile(
                stringResource(R.string.run_stat_cycle),
                stringResource(
                    R.string.run_cycle_value, state.cycle,
                    if (repeat == 0) stringResource(R.string.infinity) else repeat.toString(),
                ),
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepsTile(
    state: RunState,
    now: Long,
    colors: ProgramColors,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val ext = MaterialTheme.extended
    val program = state.program
    val offsets = remember(program) { RunMath.cycleOffsets(state) }
    val context = LocalContext.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val wallNow = System.currentTimeMillis()
    val doneDesc = stringResource(R.string.timeline_state_done)
    val currentDesc = stringResource(R.string.timeline_state_current)
    val upcomingDesc = stringResource(R.string.timeline_state_upcoming)
    val listScroll = rememberScrollState()

    BentoTile(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.run_steps_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        SegmentBar(runSegments(state, now), fillColor = colors.strong, trackColor = ext.track, markerColor = ext.ink)
        Spacer(Modifier.height(8.dp))
        Column(
            (if (scrollable) Modifier.weight(1f, fill = false).verticalScroll(listScroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            program.steps.forEachIndexed { i, step ->
                val done = i < state.currentIndex
                val current = i == state.currentIndex
                val last = i == program.steps.lastIndex
                val waits = step.effectiveWaitForAck(program)
                val requester = remember { BringIntoViewRequester() }
                if (scrollable && current) {
                    LaunchedEffect(state.currentIndex) { requester.bringIntoView() }
                }
                val muted = if (done) ext.inkSecondary else ext.ink
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(requester)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (current) colors.container else Color.Transparent)
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (done) doneDesc else if (current) currentDesc else upcomingDesc
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StepMarker(i + 1, done, current, colors)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (last && step.label.isBlank()) stringResource(R.string.step_final_label) else step.labelOrDefault(i),
                                style = if (current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                                color = muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (waits) Icon(
                                Icons.Rounded.PanTool, stringResource(R.string.step_icon_ack),
                                Modifier.size(16.dp), muted,
                            )
                        }
                        if (last && step.label.isNotBlank()) {
                            Text(
                                stringResource(R.string.step_final_label),
                                style = MaterialTheme.typography.labelMedium, color = ext.inkSecondary,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            TimeFormat.format(offsets[i], roundUp = false),
                            style = TimerSmall,
                            color = muted,
                            maxLines = 1,
                        )
                        if (!done && state.status == RunStatus.RUNNING) {
                            val eta = RunMath.estimatedReachEpochMs(state, now, wallNow, i)
                            Text(
                                stringResource(R.string.timeline_eta, timeFormat.format(Date(eta))),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = ext.inkSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fait = coche ; en cours = disque plein numéroté ; à venir = numéro sur fond neutre (jamais la couleur seule). */
@Composable
private fun StepMarker(number: Int, done: Boolean, current: Boolean, colors: ProgramColors) {
    val ext = MaterialTheme.extended
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        when {
            done -> Icon(
                Icons.Rounded.CheckCircle, contentDescription = null,
                tint = ext.inkSecondary, modifier = Modifier.size(28.dp),
            )
            else -> Box(
                Modifier.size(28.dp).background(if (current) colors.strong else ext.track, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (current) colors.onStrong else ext.ink,
                )
            }
        }
    }
}

@Composable
private fun ControlsTile(
    status: RunStatus,
    colors: ProgramColors,
    availableWidth: androidx.compose.ui.unit.Dp,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val paused = status == RunStatus.PAUSED
    val size = if (availableWidth >= 440.dp) PillSize.XL else PillSize.L
    BentoTile(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(Icons.Rounded.Stop, stringResource(R.string.action_stop), onStop, container = MaterialTheme.colorScheme.background, size = 56.dp)
            PillButton(
                text = stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                onClick = onPauseResume,
                modifier = Modifier.weight(1f),
                variant = PillVariant.Strong,
                size = size,
                icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                program = colors,
            )
            RoundIconButton(Icons.Rounded.SkipNext, stringResource(R.string.action_skip), onSkip, container = MaterialTheme.colorScheme.background, size = 56.dp)
        }
    }
}

// ---------------------------------------------------------------- Étape atteinte

@Composable
private fun AttentionContent(
    state: RunState,
    alert: Alert?,
    reducedMotion: Boolean,
    onAcknowledge: () -> Unit,
    onSilence: () -> Unit,
    onStop: () -> Unit,
) {
    val waiting = state.status == RunStatus.WAITING_ACK
    StepReachedFromRun(
        state = state,
        alertIndex = alert?.stepIndex,
        alertLabel = alert?.stepLabel,
        waiting = waiting,
        reducedMotion = reducedMotion,
        onPrimary = if (waiting) onAcknowledge else onSilence,
        onSilence = if (alert != null) onSilence else null,
        onStop = onStop,
    )
}

// ---------------------------------------------------------------- Fin

@Composable
private fun FinishedContent(
    state: RunState,
    now: Long,
    ringing: Boolean,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    onSilence: () -> Unit,
) {
    val program = state.program
    val colors = MaterialTheme.extended.program(program.colorIndex)
    val elapsed = RunEngine.elapsedEffectiveMs(state, now)
    val cycles = state.cycle.coerceAtLeast(1)
    val stepsDone = program.steps.size * cycles
    val margin = Dimens.screenMargin(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = margin, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap, Alignment.CenterVertically),
        ) {
            BentoTile(colors, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(
                        Icons.Rounded.Check,
                        container = colors.strong, tint = colors.onStrong,
                        size = 96.dp, iconSize = 60.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.finished_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        program.name,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                StatTile(
                    stringResource(R.string.finished_duration), TimeFormat.format(elapsed, roundUp = false),
                    Modifier.weight(1f),
                )
                StatTile(stringResource(R.string.finished_steps), stepsDone.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(Dimens.Space2))
            if (ringing) {
                PillButton(
                    stringResource(R.string.action_silence), onSilence, Modifier.fillMaxWidth(),
                    variant = PillVariant.Tonal, size = PillSize.L, icon = Icons.AutoMirrored.Rounded.VolumeOff,
                )
            }
            PillButton(
                stringResource(R.string.finished_done), onFinish, Modifier.fillMaxWidth(),
                variant = PillVariant.Primary, size = PillSize.L,
            )
            PillButton(
                stringResource(R.string.finished_restart), onRestart, Modifier.fillMaxWidth(),
                variant = PillVariant.Tonal, size = PillSize.L, icon = Icons.Rounded.Refresh,
            )
        }
    }
}

// ---------------------------------------------------------------- Aperçus

@ThemePreviews
@Composable
private fun RunPreview() = PreviewSurface {
    RunScreenContent(
        state = PreviewData.runState(1, 1_000_000L), alert = null, nowMs = 1_000_000L,
        reducedMotion = true, haptics = rememberHaptics(false),
        onBack = {}, onPauseResume = {}, onSkip = {}, onStop = {}, onAcknowledge = {}, onSilence = {},
        onRestart = {}, onFinish = {},
    )
}

@ThemePreviews
@Composable
private fun RunAttentionPreview() = PreviewSurface {
    val s = PreviewData.runState(1, 1_000_000L).copy(status = RunStatus.WAITING_ACK, pausedAt = 1_000_000L)
    RunScreenContent(
        state = s, alert = Alert(1, "Arroser", waitsForAck = true, isFinal = false), nowMs = 1_000_000L,
        reducedMotion = true, haptics = rememberHaptics(false),
        onBack = {}, onPauseResume = {}, onSkip = {}, onStop = {}, onAcknowledge = {}, onSilence = {},
        onRestart = {}, onFinish = {},
    )
}

@ThemePreviews
@Composable
private fun RunFinishedPreview() = PreviewSurface {
    val s = PreviewData.runState(4, 1_000_000L).copy(status = RunStatus.FINISHED, pausedAt = 1_000_000L)
    RunScreenContent(
        state = s, alert = null, nowMs = 1_000_000L,
        reducedMotion = true, haptics = rememberHaptics(false),
        onBack = {}, onPauseResume = {}, onSkip = {}, onStop = {}, onAcknowledge = {}, onSilence = {},
        onRestart = {}, onFinish = {},
    )
}
