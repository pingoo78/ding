@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.pingooweb.ding.ui.editor

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pingooweb.ding.R
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.EditorInputMode
import fr.pingooweb.ding.di.LocalAppContainer
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.SoundChoice
import fr.pingooweb.ding.domain.model.Step
import fr.pingooweb.ding.domain.model.effectiveWaitForAck
import fr.pingooweb.ding.ui.components.ColorSwatchRow
import fr.pingooweb.ding.ui.components.ConfirmDialog
import fr.pingooweb.ding.ui.components.Haptics
import fr.pingooweb.ding.ui.components.PreviewData
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.SwitchRow
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.components.bento.BarSegment
import fr.pingooweb.ding.ui.components.bento.BentoTile
import fr.pingooweb.ding.ui.components.bento.PillButton
import fr.pingooweb.ding.ui.components.bento.PillSize
import fr.pingooweb.ding.ui.components.bento.PillVariant
import fr.pingooweb.ding.ui.components.bento.RoundIconButton
import fr.pingooweb.ding.ui.components.bento.SegmentBar
import fr.pingooweb.ding.ui.components.bento.SegmentState
import fr.pingooweb.ding.ui.components.labelOrDefault
import fr.pingooweb.ding.ui.components.rememberHaptics
import fr.pingooweb.ding.ui.components.rememberProgramStarter
import fr.pingooweb.ding.ui.navigation.containerViewModel
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.TimerMedium
import fr.pingooweb.ding.ui.theme.TimerSmall
import fr.pingooweb.ding.ui.theme.extended
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EditorRoute(programId: String, onBack: () -> Unit, onOpenRun: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = containerViewModel(key = "editor-$programId") {
        EditorViewModel(programId, it.programRepository, it.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val haptics = rememberHaptics(settings.haptics)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val defaultName = stringResource(R.string.editor_default_name)
    val invalidMessage = stringResource(R.string.editor_fix_errors)

    val starter = rememberProgramStarter(
        runController = container.runController,
        onStarted = onOpenRun,
        onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    // Sélecteur de sonnerie système : cible "" = son par défaut du programme, sinon id d'étape.
    var pickTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val customFallback = stringResource(R.string.sound_custom_fallback)
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = pickTarget
        pickTarget = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            if (uri != null) {
                val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
                val choice = SoundChoice.Custom(uri.toString(), title?.takeIf { it.isNotBlank() } ?: customFallback)
                if (target.isEmpty()) vm.setDefaultSound(choice) else vm.setStepSound(target, choice)
            }
        }
    }
    val pickerTitle = stringResource(R.string.sound_picker_title)
    val onPickSound: (String, SoundChoice?) -> Unit = { target, current ->
        pickTarget = target
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
            (current as? SoundChoice.Custom)?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it.uri))
            }
        }
        runCatching { ringtoneLauncher.launch(intent) }.onFailure { pickTarget = null }
    }

    fun save(thenStart: Boolean) {
        scope.launch {
            when (val result = vm.save(defaultName)) {
                is SaveResult.Saved -> {
                    haptics.confirm()
                    if (thenStart) starter(result.program.id, result.program.name)
                    else {
                        snackbar.currentSnackbarData?.dismiss()
                        onBack()
                    }
                }
                SaveResult.Invalid -> snackbar.showSnackbar(invalidMessage)
            }
        }
    }

    EditorScreen(
        state = state,
        actions = vm,
        haptics = haptics,
        snackbarHostState = snackbar,
        onBack = onBack,
        onSave = { save(false) },
        onStart = { save(true) },
        onPickSound = onPickSound,
    )
}

@Composable
fun EditorScreen(
    state: EditorState,
    actions: EditorActions,
    haptics: Haptics,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
    onPickSound: (target: String, current: SoundChoice?) -> Unit,
) {
    var showDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (state.dirty) showDiscard = true else onBack() }
    BackHandler(enabled = state.dirty) { showDiscard = true }
    val margin = Dimens.screenMargin(LocalConfiguration.current.screenWidthDp.dp)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .padding(horizontal = margin, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), requestBack,
                    container = MaterialTheme.extended.tile,
                )
                Text(
                    stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (state.draft != null && !state.isNew) {
                    RoundIconButton(
                        Icons.Rounded.PlayArrow, stringResource(R.string.editor_start), onStart,
                        container = MaterialTheme.extended.tile,
                    )
                }
                if (state.draft != null) {
                    PillButton(stringResource(R.string.action_save), onSave, variant = PillVariant.Primary, size = PillSize.M)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val draft = state.draft
            when {
                !state.loaded -> Unit
                draft == null -> Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.editor_not_found), style = MaterialTheme.typography.bodyLarge)
                    PillButton(stringResource(R.string.action_back), onBack, variant = PillVariant.Primary)
                }
                else -> EditorBody(state, draft, actions, haptics, onPickSound)
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            message = stringResource(R.string.editor_discard_message),
            confirmLabel = stringResource(R.string.editor_discard_confirm),
            dismissLabel = stringResource(R.string.editor_discard_keep),
            destructive = true,
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun EditorBody(
    state: EditorState,
    draft: Program,
    actions: EditorActions,
    haptics: Haptics,
    onPickSound: (String, SoundChoice?) -> Unit,
) {
    val margin = Dimens.screenMargin(LocalConfiguration.current.screenWidthDp.dp)
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        actions.moveStep(from.key, to.key)
        haptics.tick()
    }
    var sheetStepId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(draft.name) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val cumulative = remember(draft.steps) { EditorLogic.cumulative(draft.steps) }
    val programColors = MaterialTheme.extended.program(draft.colorIndex)
    val steps = draft.steps
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)

    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        item(key = "header") {
            BentoTile(programColors, modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(60); actions.setName(name) },
                    label = { Text(stringResource(R.string.editor_name)) },
                    placeholder = { Text(stringResource(R.string.editor_default_name)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    shape = FieldShape,
                    colors = bentoTextFieldColors(MaterialTheme.extended.tile.copy(alpha = 0.6f)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel(stringResource(R.string.editor_color), Modifier.padding(top = 16.dp, bottom = 4.dp))
                ColorSwatchRow(draft.colorIndex, { actions.setColor(it); haptics.tick() })
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(stringResource(R.string.editor_preview), Modifier.weight(1f))
                    Text(
                        TimeFormat.format(draft.steps.sumOf { it.durationMs.coerceAtLeast(0) }, roundUp = false),
                        style = TimerMedium,
                    )
                }
                SegmentBar(
                    segments = steps.map {
                        BarSegment(
                            weight = it.durationMs.coerceAtLeast(1L).toFloat(),
                            state = SegmentState.Done,
                            marker = it.effectiveWaitForAck(draft),
                        )
                    },
                    fillColor = programColors.strong,
                    trackColor = MaterialTheme.extended.track,
                    contentDescription = pluralStringResource(
                        R.plurals.timeline_desc, steps.size, steps.size, steps.count { it.effectiveWaitForAck(draft) },
                    ),
                )
                if (!state.valid) {
                    Row(
                        Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.editor_fix_errors),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item(key = "mode") {
            PillSegmented(
                options = listOf(
                    EditorInputMode.CUMULATIVE to stringResource(R.string.mode_cumulative),
                    EditorInputMode.DURATION to stringResource(R.string.mode_duration),
                ),
                selected = state.inputMode,
                onSelect = actions::setInputMode,
                container = MaterialTheme.extended.tile,
            )
        }

        items(count = steps.size, key = { steps[it].id }) { index ->
            val step = steps[index]
            ReorderableItem(reorderState, key = step.id) { isDragging ->
                val handle = Modifier.draggableHandle(
                    onDragStarted = { haptics.confirm() },
                    onDragStopped = { haptics.tick() },
                )
                StepRow(
                    index = index,
                    step = step,
                    program = draft,
                    cumulativeMs = cumulative[index],
                    inputMode = state.inputMode,
                    isLast = index == steps.lastIndex,
                    invalid = index in state.invalidIndices,
                    dragging = isDragging,
                    handleModifier = handle,
                    onClick = { sheetStepId = step.id },
                    onMoveUp = if (index > 0) ({ actions.moveStepBy(step.id, -1) }) else null,
                    onMoveDown = if (index < steps.lastIndex) ({ actions.moveStepBy(step.id, 1) }) else null,
                    moveUpLabel = moveUpLabel,
                    moveDownLabel = moveDownLabel,
                )
            }
        }

        item(key = "add") {
            AddStepTile(onClick = { sheetStepId = actions.addStep(); haptics.tick() })
        }

        item(key = "options") {
            OptionsTile(
                program = draft,
                expanded = optionsOpen,
                onToggle = { optionsOpen = !optionsOpen },
                actions = actions,
                onPickSound = { onPickSound("", draft.defaultSound) },
            )
        }
    }

    val sheetStep = steps.firstOrNull { it.id == sheetStepId }
    if (sheetStep != null) {
        val index = steps.indexOf(sheetStep)
        StepSheet(
            step = sheetStep,
            index = index,
            isLast = index == steps.lastIndex,
            program = draft,
            cumulativeMs = cumulative[index],
            inputMode = state.inputMode,
            canDelete = steps.size > 1,
            actions = actions,
            haptics = haptics,
            onPickSound = { onPickSound(sheetStep.id, sheetStep.sound) },
            onDismiss = { sheetStepId = null },
        )
    }
}

@Composable
private fun AddStepTile(onClick: () -> Unit) {
    val dash = MaterialTheme.colorScheme.outline
    val radius = Dimens.TileRadius
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(radius))
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                )
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.extended.ink)
        Text(
            "  " + stringResource(R.string.editor_add_step),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.extended.ink,
        )
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: Step,
    program: Program,
    cumulativeMs: Long,
    inputMode: EditorInputMode,
    isLast: Boolean,
    invalid: Boolean,
    dragging: Boolean,
    handleModifier: Modifier,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    moveUpLabel: String,
    moveDownLabel: String,
) {
    val ext = MaterialTheme.extended
    val label = step.labelOrDefault(index)
    val waits = step.effectiveWaitForAck(program)
    val silent = (step.sound ?: program.defaultSound) == SoundChoice.Silent
    val atText = stringResource(R.string.step_at, TimeFormat.format(cumulativeMs, roundUp = false))
    val plusText = stringResource(R.string.step_plus, TimeFormat.format(step.durationMs, roundUp = false))
    val primary = if (inputMode == EditorInputMode.CUMULATIVE) atText else plusText
    val secondary = if (inputMode == EditorInputMode.CUMULATIVE) plusText else atText

    BentoTile(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (dragging) 1.02f else 1f
                scaleX = s; scaleY = s
            }
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { f -> CustomAccessibilityAction(moveUpLabel) { f(); true } },
                    onMoveDown?.let { f -> CustomAccessibilityAction(moveDownLabel) { f(); true } },
                )
            },
        color = if (dragging) ext.tileRaised else ext.tile,
        onClick = onClick,
        padding = PaddingValues(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(Modifier.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.editor_drag_handle, index + 1),
                    tint = ext.inkSecondary,
                )
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(ext.program(program.colorIndex).container),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = ext.ink,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLast || waits || silent) {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isLast) {
                            Icon(
                                Icons.Rounded.Flag, contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = ext.inkSecondary,
                            )
                            Text(
                                stringResource(R.string.step_final_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.inkSecondary,
                            )
                        }
                        if (waits) Icon(
                            Icons.Rounded.PanTool, stringResource(R.string.step_icon_ack),
                            Modifier.size(18.dp), ext.inkSecondary,
                        )
                        if (silent) Icon(
                            Icons.Rounded.NotificationsOff, stringResource(R.string.step_icon_silent),
                            Modifier.size(18.dp), ext.inkSecondary,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    primary, style = TimerSmall,
                    color = if (invalid) MaterialTheme.colorScheme.error else ext.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    secondary, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = ext.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (invalid) {
            Row(
                Modifier.padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(
                        if (inputMode == EditorInputMode.CUMULATIVE) R.string.error_after_previous else R.string.error_duration_positive,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OptionsTile(
    program: Program,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: EditorActions,
    onPickSound: () -> Unit,
) {
    val ext = MaterialTheme.extended
    val expandedDesc = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    BentoTile(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = expandedDesc }
                .padding(horizontal = Dimens.TilePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.options_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            Column(
                Modifier.padding(start = Dimens.TilePadding, end = Dimens.TilePadding, bottom = Dimens.TilePadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SwitchRow(
                    stringResource(R.string.options_wait_ack),
                    program.defaultWaitForAck, actions::setDefaultAck,
                    description = stringResource(R.string.options_wait_ack_desc),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.options_sound))
                    SoundChooser(
                        value = program.defaultSound,
                        allowInherit = false,
                        onSelect = { it?.let(actions::setDefaultSound) },
                        onPick = onPickSound,
                    )
                }
                SwitchRow(stringResource(R.string.options_vibrate), program.defaultVibrate, actions::setDefaultVibrate)
                SwitchRow(
                    stringResource(R.string.options_announce),
                    program.announceLabels, actions::setAnnounce,
                    description = stringResource(R.string.options_announce_desc),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.options_ring))
                    ChoiceFlow {
                        listOf(5, 8, 15, 30).forEach { s ->
                            ChoicePill(
                                stringResource(R.string.options_seconds, s),
                                selected = program.ringSeconds == s,
                                onClick = { actions.setRingSeconds(s) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.options_ring_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.inkSecondary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.options_repeat))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIconButton(
                            Icons.Rounded.Remove, stringResource(R.string.options_repeat_less),
                            onClick = { actions.setRepeat(program.repeatCount - 1) },
                            container = MaterialTheme.colorScheme.background,
                            enabled = program.repeatCount > 1,
                        )
                        Text(
                            if (program.repeatCount == 0) stringResource(R.string.options_repeat_infinite_value)
                            else stringResource(R.string.options_repeat_value, program.repeatCount),
                            style = TimerSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 56.dp),
                        )
                        RoundIconButton(
                            Icons.Rounded.Add, stringResource(R.string.options_repeat_more),
                            onClick = { actions.setRepeat(program.repeatCount + 1) },
                            container = MaterialTheme.colorScheme.background,
                            enabled = program.repeatCount in 1..19,
                        )
                        ChoicePill(
                            stringResource(R.string.options_repeat_infinite),
                            selected = program.repeatCount == 0,
                            onClick = { actions.setRepeat(if (program.repeatCount == 0) 1 else 0) },
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun EditorPreview() = PreviewSurface {
    val p = PreviewData.program()
    EditorScreen(
        state = EditorState(loaded = true, isNew = false, draft = p, original = p),
        actions = object : EditorActions {},
        haptics = rememberHaptics(false),
        snackbarHostState = remember { SnackbarHostState() },
        onBack = {}, onSave = {}, onStart = {}, onPickSound = { _, _ -> },
    )
}
