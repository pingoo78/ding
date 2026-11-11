@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.pingooweb.ding.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.pingooweb.ding.R
import fr.pingooweb.ding.data.EditorInputMode
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.Step
import fr.pingooweb.ding.ui.components.ConfirmDialog
import fr.pingooweb.ding.ui.components.DigitKeypad
import fr.pingooweb.ding.ui.components.Haptics
import fr.pingooweb.ding.ui.components.PreviewData
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.components.rememberHaptics
import fr.pingooweb.ding.ui.components.bento.PillButton
import fr.pingooweb.ding.ui.components.bento.PillSize
import fr.pingooweb.ding.ui.components.bento.PillVariant
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.TimerLarge
import fr.pingooweb.ding.ui.theme.extended

private const val MAX_DIGITS = 6

/** Bottom sheet d'édition d'une étape (fond tile, rayon haut 28). Modifications appliquées en direct au brouillon. */
@Composable
fun StepSheet(
    step: Step,
    index: Int,
    isLast: Boolean,
    program: Program,
    cumulativeMs: Long,
    inputMode: EditorInputMode,
    canDelete: Boolean,
    actions: EditorActions,
    haptics: Haptics,
    onPickSound: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.extended.tile,
        contentColor = MaterialTheme.extended.ink,
        shape = RoundedCornerShape(topStart = Dimens.TileRadius, topEnd = Dimens.TileRadius),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.extended.track) },
    ) {
        StepSheetContent(
            step, index, isLast, program, cumulativeMs, inputMode, canDelete, actions, haptics, onPickSound, onDismiss,
        )
    }
}

@Composable
internal fun StepSheetContent(
    step: Step,
    index: Int,
    isLast: Boolean,
    program: Program,
    cumulativeMs: Long,
    inputMode: EditorInputMode,
    canDelete: Boolean,
    actions: EditorActions,
    haptics: Haptics,
    onPickSound: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ext = MaterialTheme.extended
    val cumulative = inputMode == EditorInputMode.CUMULATIVE
    var digits by remember(step.id, inputMode) {
        mutableStateOf(TimeFormat.toDigits(if (cumulative) cumulativeMs else step.durationMs))
    }
    var label by remember(step.id) { mutableStateOf(step.label) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun apply(newDigits: String) {
        digits = newDigits
        haptics.tick()
        actions.setStepTime(step.id, TimeFormat.parseDigits(newDigits))
    }

    fun append(d: String) {
        val next = (digits + d).trimStart('0')
        if (next.length <= MAX_DIGITS) apply(next)
    }

    Column(
        Modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (isLast) stringResource(R.string.step_final_label)
            else stringResource(R.string.step_default_label, index + 1),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        TextField(
            value = label,
            onValueChange = { label = it.take(80); actions.setLabel(step.id, label) },
            label = { Text(stringResource(R.string.sheet_label)) },
            placeholder = { Text(stringResource(R.string.step_default_label, index + 1)) },
            singleLine = true,
            shape = FieldShape,
            colors = bentoTextFieldColors(controlContainer()),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Temps
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (cumulative) R.string.mode_cumulative_caption else R.string.mode_duration_caption),
                style = MaterialTheme.typography.labelMedium,
                color = ext.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                TimeFormat.formatDigits(digits),
                style = TimerLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (step.durationMs > 0) {
                val secondary = if (cumulative) {
                    stringResource(R.string.sheet_secondary_duration, TimeFormat.format(step.durationMs, roundUp = false))
                } else {
                    stringResource(R.string.sheet_secondary_at, TimeFormat.format(cumulativeMs, roundUp = false))
                }
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = ext.inkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(if (cumulative) R.string.error_after_previous else R.string.error_duration_positive),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                30_000L to R.string.shortcut_30s,
                60_000L to R.string.shortcut_1min,
                300_000L to R.string.shortcut_5min,
            ).forEach { (delta, text) ->
                SoftPill(
                    text = stringResource(text),
                    onClick = {
                        val current = TimeFormat.parseDigits(digits)
                        apply(TimeFormat.toDigits(current + delta).takeLast(MAX_DIGITS))
                    },
                )
            }
        }

        DigitKeypad(
            onDigit = { append(it.toString()) },
            onDoubleZero = { append("00") },
            onBackspace = { apply(digits.dropLast(1)) },
            onClear = { apply("") },
            keyColor = controlContainer(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.sheet_wait_ack))
            TriStateRow(step.waitForAck, { actions.setStepAck(step.id, it) })
            if (step.waitForAck == null) {
                Text(
                    stringResource(
                        if (program.defaultWaitForAck) R.string.sheet_inherit_yes else R.string.sheet_inherit_no,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.inkSecondary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.sheet_sound))
            SoundChooser(
                value = step.sound,
                allowInherit = true,
                onSelect = { actions.setStepSound(step.id, it) },
                onPick = onPickSound,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.sheet_vibration))
            TriStateRow(step.vibrate, { actions.setStepVibrate(step.id, it) })
        }

        PillButton(
            text = stringResource(R.string.sheet_done),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            variant = PillVariant.Primary,
            size = PillSize.L,
        )

        if (canDelete) {
            SoftPill(
                text = stringResource(R.string.sheet_delete),
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.DeleteOutline,
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.sheet_delete_title),
            message = stringResource(R.string.sheet_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                actions.deleteStep(step.id)
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.extended.inkSecondary,
        modifier = modifier.semantics { heading() },
    )
}

@ThemePreviews
@Composable
private fun StepSheetPreview() = PreviewSurface {
    val p = PreviewData.program()
    androidx.compose.material3.Surface(
        color = MaterialTheme.extended.tile,
        shape = RoundedCornerShape(topStart = Dimens.TileRadius, topEnd = Dimens.TileRadius),
    ) {
        StepSheetContent(
            step = p.steps[1], index = 1, isLast = false, program = p, cumulativeMs = 12 * 60_000L,
            inputMode = EditorInputMode.CUMULATIVE, canDelete = true,
            actions = object : EditorActions {}, haptics = rememberHaptics(false),
            onPickSound = {}, onDismiss = {},
        )
    }
}
