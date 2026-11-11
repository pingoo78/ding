package fr.pingooweb.ding.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.pingooweb.ding.R
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.ui.components.bento.BentoTile
import fr.pingooweb.ding.ui.components.bento.PillButton
import fr.pingooweb.ding.ui.components.bento.PillSize
import fr.pingooweb.ding.ui.components.bento.PillVariant
import fr.pingooweb.ding.ui.components.bento.StatTile
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended
import kotlinx.coroutines.delay

/**
 * Étape atteinte plein écran (Bento), partagée par l'écran Exécution et l'alerte plein écran.
 * À poser sur un fond `extended.attention` (le parent gère les insets).
 *
 * @param subtitle « Étape 2/5 · programme »
 * @param waitingSinceElapsedMs instant (elapsedRealtime) où l'attente a commencé ; null = maintenant. Puce visible si [waiting].
 * @param elapsed temps effectif écoulé (déjà formaté) ; @param nextLabel / [nextDetail] : étape suivante.
 */
@Composable
fun StepReachedContent(
    title: String,
    subtitle: String?,
    waiting: Boolean,
    reducedMotion: Boolean,
    announce: String,
    onPrimary: () -> Unit,
    onSilence: (() -> Unit)?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    primaryDescription: String? = null,
    stopLabel: String = stringResource(R.string.action_stop),
    waitingSinceElapsedMs: Long? = null,
    elapsed: String? = null,
    nextLabel: String? = null,
    nextDetail: String? = null,
) {
    val ext = MaterialTheme.extended
    val pulse = if (reducedMotion) 1f else rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale",
    ).value

    val waitStart = remember { waitingSinceElapsedMs ?: android.os.SystemClock.elapsedRealtime() }
    var waitedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(waiting, waitStart) {
        while (waiting) {
            waitedMs = (android.os.SystemClock.elapsedRealtime() - waitStart).coerceAtLeast(0L)
            delay(1000)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        val minH = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .heightIn(min = minH)
                .padding(horizontal = Dimens.Margin, vertical = Dimens.Margin),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            // Pastille d'en-tête
            Row(
                Modifier
                    .height(48.dp)
                    .background(ext.attentionAction, CircleShape)
                    .padding(start = 12.dp, end = 22.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Notifications, null, tint = ext.onAttentionAction, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(R.string.attention_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = ext.onAttentionAction,
                )
            }

            // Grande tuile : libellé
            BentoTile(
                modifier = Modifier.fillMaxWidth(),
                color = ext.tileRaised,
                padding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp,
                ),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announce },
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (waiting) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier
                            .height(40.dp)
                            .background(ext.attention, RoundedCornerShape(Dimens.PillRadius))
                            .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Schedule, null, tint = ext.attentionAction, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.attention_waiting_since, TimeFormat.format(waitedMs)),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp, fontFeatureSettings = "tnum",
                            ),
                        )
                    }
                }
            }

            // Statistiques
            if (elapsed != null || nextLabel != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    if (elapsed != null) {
                        StatTile(
                            stringResource(R.string.attention_elapsed), elapsed,
                            Modifier.weight(1f), color = ext.tileRaised,
                        )
                    }
                    if (nextLabel != null) {
                        StatTile(
                            stringResource(R.string.attention_next), nextLabel,
                            Modifier.weight(1f), detail = nextDetail, color = ext.tileRaised,
                            valueStyle = MaterialTheme.typography.titleSmall, valueMaxLines = 2,
                        )
                    }
                }
            }

            // Absorbe l'espace restant : actions ancrées en bas
            Spacer(Modifier.weight(1f))

            // Action principale (pulsation verticale seulement : largeur identique aux tuiles)
            PillButton(
                text = stringResource(if (waiting) R.string.action_acknowledge else R.string.action_silence),
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleY = pulse },
                variant = PillVariant.Attention,
                size = PillSize.XL,
                icon = if (waiting) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.VolumeOff,
                contentDescription = primaryDescription,
            )

            // Actions secondaires
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                if (waiting && onSilence != null) {
                    PillButton(
                        stringResource(R.string.attention_silence_sound), onSilence,
                        Modifier.weight(1f).heightIn(min = 56.dp),
                        variant = PillVariant.Tonal, size = PillSize.L,
                    )
                }
                PillButton(
                    stopLabel, onStop,
                    Modifier.weight(1f).heightIn(min = 56.dp),
                    variant = PillVariant.Tonal, size = PillSize.L,
                )
            }
        }
    }
}

/**
 * Variante alimentée par l'état d'exécution : calcule titre, sous-titre, écoulé et étape suivante.
 * [alertIndex]/[alertLabel] : étape de l'alerte en cours (sinon étape courante de l'état).
 */
@Composable
fun StepReachedFromRun(
    state: RunState,
    alertIndex: Int?,
    alertLabel: String?,
    waiting: Boolean,
    reducedMotion: Boolean,
    onPrimary: () -> Unit,
    onSilence: (() -> Unit)?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    primaryDescription: String? = null,
    stopLabel: String = stringResource(R.string.action_stop),
) {
    val program = state.program
    val steps = program.steps
    val index = (alertIndex ?: state.currentIndex).coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val isDefault = step.label.isBlank()
    val label = alertLabel?.takeIf { it.isNotBlank() }
        ?: step.labelOrDefault(index)
    val progress = stringResource(R.string.run_step_of, index + 1, steps.size)

    val now by rememberNowMs(active = state.status == RunStatus.RUNNING, periodMs = 1000L)
    val elapsed = TimeFormat.format(RunEngine.elapsedEffectiveMs(state, now))
    val next = steps.getOrNull(index + 1)
    val nextLabel = next?.labelOrDefault(index + 1)
        ?: stringResource(R.string.attention_program_end)
    val nextDetail = next?.let { stringResource(R.string.attention_next_detail, TimeFormat.format(it.durationMs)) }

    StepReachedContent(
        title = if (isDefault) progress else label,
        subtitle = if (isDefault) program.name
        else stringResource(R.string.rt_alert_program_step, index + 1, steps.size, program.name),
        waiting = waiting,
        reducedMotion = reducedMotion,
        announce = stringResource(R.string.attention_announce, label),
        onPrimary = onPrimary,
        onSilence = onSilence,
        onStop = onStop,
        modifier = modifier,
        primaryDescription = primaryDescription,
        stopLabel = stopLabel,
        waitingSinceElapsedMs = state.pausedAt,
        elapsed = elapsed,
        nextLabel = nextLabel,
        nextDetail = nextDetail,
    )
}
