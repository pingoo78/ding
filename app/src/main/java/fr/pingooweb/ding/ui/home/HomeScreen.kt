package fr.pingooweb.ding.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pingooweb.ding.R
import fr.pingooweb.ding.data.Templates
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.domain.engine.RunEngine
import fr.pingooweb.ding.domain.engine.RunState
import fr.pingooweb.ding.domain.engine.RunStatus
import fr.pingooweb.ding.domain.model.Program
import fr.pingooweb.ding.domain.model.effectiveWaitForAck
import fr.pingooweb.ding.domain.model.totalDurationMs
import fr.pingooweb.ding.ui.components.PreviewData
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.components.bento.BarSegment
import fr.pingooweb.ding.ui.components.bento.BentoTile
import fr.pingooweb.ding.ui.components.bento.IconBadge
import fr.pingooweb.ding.ui.components.bento.RoundIconButton
import fr.pingooweb.ding.ui.components.bento.SegmentBar
import fr.pingooweb.ding.ui.components.bento.SegmentState
import fr.pingooweb.ding.ui.components.labelOrDefault
import fr.pingooweb.ding.ui.components.rememberHaptics
import fr.pingooweb.ding.ui.components.rememberNowMs
import fr.pingooweb.ding.ui.components.rememberProgramStarter
import fr.pingooweb.ding.ui.navigation.containerViewModel
import fr.pingooweb.ding.ui.run.runSegments
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.TimerLarge
import fr.pingooweb.ding.ui.theme.TimerSmall
import fr.pingooweb.ding.ui.theme.extended
import kotlinx.coroutines.launch

/** Au-delà de ce nombre de programmes, un « + » est aussi proposé dans l'en-tête. */
private const val LONG_LIST = 6

@Composable
fun HomeRoute(
    onNewProgram: () -> Unit,
    onEditProgram: (String) -> Unit,
    onOpenRun: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = containerViewModel { HomeViewModel(it.appContext, it.programRepository, it.runController, it.settingsRepository) }
    val programs by vm.programs.collectAsStateWithLifecycle()
    val runState by vm.runController.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(settings.haptics)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nowState = rememberNowMs(active = runState?.status == RunStatus.RUNNING)

    val starter = rememberProgramStarter(
        runController = vm.runController,
        onStarted = onOpenRun,
        onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    var exportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val exportOk = stringResource(R.string.home_export_ok)
    val exportFail = stringResource(R.string.home_export_fail)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportTarget
        exportTarget = null
        if (uri != null && id != null) {
            scope.launch {
                val ok = vm.exportProgram(uri, id)
                snackbar.showSnackbar(if (ok) exportOk else exportFail)
            }
        }
    }

    val deletedMessage = stringResource(R.string.home_deleted)
    val undoLabel = stringResource(R.string.action_cancel)
    val duplicatedMessage = stringResource(R.string.home_duplicated)
    val templateAdded = stringResource(R.string.home_template_added)

    HomeContent(
        programs = programs,
        runState = runState,
        nowMs = nowState.value,
        snackbarHostState = snackbar,
        onNew = onNewProgram,
        onEdit = { onEditProgram(it.id) },
        onStart = { p -> haptics.confirm(); starter(p.id, p.name) },
        onOpenRun = onOpenRun,
        onPauseResume = {
            haptics.confirm()
            if (runState?.status == RunStatus.PAUSED) vm.runController.resume() else vm.runController.pause()
        },
        onOpenSettings = onOpenSettings,
        onDuplicate = { p ->
            vm.duplicate(p)
            scope.launch { snackbar.showSnackbar(duplicatedMessage) }
        },
        onExport = { p ->
            exportTarget = p.id
            exportLauncher.launch(fileNameFor(p.name))
        },
        onDelete = { p ->
            vm.delete(p)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) vm.restore(p)
            }
        },
        onTemplate = { kind ->
            vm.addTemplate(kind)
            scope.launch { snackbar.showSnackbar(templateAdded) }
        },
    )
}

private fun fileNameFor(name: String): String {
    val safe = name.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "-").trim('-').ifEmpty { "programme" }
    return "ding-$safe.json"
}

@Composable
fun HomeContent(
    programs: List<Program>?,
    runState: RunState?,
    nowMs: Long,
    snackbarHostState: SnackbarHostState,
    onNew: () -> Unit,
    onEdit: (Program) -> Unit,
    onStart: (Program) -> Unit,
    onOpenRun: () -> Unit,
    onPauseResume: () -> Unit,
    onOpenSettings: () -> Unit,
    onDuplicate: (Program) -> Unit,
    onExport: (Program) -> Unit,
    onDelete: (Program) -> Unit,
    onTemplate: (Templates.Kind) -> Unit,
) {
    val margin = Dimens.screenMargin(LocalConfiguration.current.screenWidthDp.dp)
    val showEmpty = programs != null && programs.isEmpty() && runState == null
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (programs != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = margin, end = margin, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    fullSpan("header") {
                        HomeHeader(
                            showAdd = programs.size >= LONG_LIST,
                            onNew = onNew,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                    if (runState != null) {
                        fullSpan("running") { RunningTile(runState, nowMs, onOpenRun, onPauseResume) }
                    }
                    if (showEmpty) {
                        fullSpan("welcome") { WelcomeTile() }
                        emptyTemplates(onTemplate)
                    } else {
                        programTiles(programs, onEdit, onStart, onDuplicate, onExport, onDelete)
                    }
                    fullSpan("new") { NewProgramTile(onNew) }
                }
            }
        }
    }
}

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun HomeHeader(showAdd: Boolean, onNew: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.extended.inkSecondary,
            )
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.extended.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (showAdd) {
            RoundIconButton(
                Icons.Rounded.Add, stringResource(R.string.home_new_program), onNew,
                container = MaterialTheme.extended.tile,
            )
            Spacer(Modifier.size(8.dp))
        }
        RoundIconButton(
            Icons.Rounded.Settings, stringResource(R.string.home_settings), onOpenSettings,
            container = MaterialTheme.extended.tile,
        )
    }
}

// ---------------------------------------------------------------- Programme en cours

@Composable
private fun RunningTile(state: RunState, nowMs: Long, onOpen: () -> Unit, onPauseResume: () -> Unit) {
    val ext = MaterialTheme.extended
    val program = state.program
    val colors = ext.program(program.colorIndex)
    val waiting = state.status == RunStatus.WAITING_ACK
    val finished = state.status == RunStatus.FINISHED
    val paused = state.status == RunStatus.PAUSED
    val step = program.steps[state.currentIndex.coerceIn(0, program.steps.lastIndex)]
    val statusText = when (state.status) {
        RunStatus.WAITING_ACK -> stringResource(R.string.banner_reached)
        RunStatus.PAUSED -> stringResource(R.string.banner_paused)
        RunStatus.FINISHED -> stringResource(R.string.banner_finished)
        RunStatus.RUNNING -> stringResource(R.string.banner_running)
    }
    val statusIcon = when (state.status) {
        RunStatus.WAITING_ACK -> Icons.Rounded.NotificationsActive
        RunStatus.PAUSED -> Icons.Rounded.Pause
        RunStatus.FINISHED -> Icons.Rounded.CheckCircle
        RunStatus.RUNNING -> Icons.Rounded.PlayArrow
    }
    BentoTile(
        color = if (waiting) ext.attention else colors.container,
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.home_open_run),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(statusIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(statusText, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        Text(program.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (step.label.isBlank()) {
                stringResource(R.string.run_step_of, state.currentIndex + 1, program.steps.size)
            } else {
                stringResource(
                    R.string.banner_step, state.currentIndex + 1, program.steps.size,
                    step.labelOrDefault(state.currentIndex),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!waiting && !finished) {
                Text(
                    TimeFormat.format(RunEngine.remainingInStepMs(state, nowMs)),
                    style = TimerLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                    onPauseResume,
                    container = colors.strong,
                    tint = colors.onStrong,
                    size = 56.dp,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        SegmentBar(
            runSegments(state, nowMs),
            fillColor = colors.strong,
            trackColor = ext.tile,
            markerColor = ext.ink,
        )
    }
}

// ---------------------------------------------------------------- Programmes

private fun LazyGridScope.programTiles(
    programs: List<Program>,
    onEdit: (Program) -> Unit,
    onStart: (Program) -> Unit,
    onDuplicate: (Program) -> Unit,
    onExport: (Program) -> Unit,
    onDelete: (Program) -> Unit,
) {
    // Règle : paires de tuiles carrées ; si le nombre est impair, la dernière est large.
    val lastIsWide = programs.size % 2 == 1
    items(
        items = programs,
        key = { it.id },
        span = { p -> GridItemSpan(if (lastIsWide && p === programs.last()) maxLineSpan else 1) },
    ) { program ->
        ProgramTile(
            program = program,
            wide = lastIsWide && program === programs.last(),
            onClick = { onEdit(program) },
            onStart = { onStart(program) },
            onDuplicate = { onDuplicate(program) },
            onExport = { onExport(program) },
            onDelete = { onDelete(program) },
        )
    }
}

/** Icône simple déduite du nom ; minuteur par défaut. */
private fun iconFor(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        listOf("sport", "entra", "fraction", "muscu", "run", "course").any { it in n } -> Icons.Rounded.FitnessCenter
        listOf("cuisine", "rôti", "roti", "four", "gâteau", "gateau", "pâtes", "pates", "recette").any { it in n } ->
            Icons.Rounded.Restaurant
        else -> Icons.Rounded.Timer
    }
}

@Composable
private fun ProgramTile(
    program: Program,
    wide: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = MaterialTheme.extended
    val colors = ext.program(program.colorIndex)
    val stepCount = program.steps.size
    val ackCount = program.steps.count { it.effectiveWaitForAck(program) }
    val repeat = when (program.repeatCount) {
        1 -> ""
        0 -> " · " + stringResource(R.string.home_repeat_infinite)
        else -> " · " + stringResource(R.string.home_repeat, program.repeatCount)
    }
    val meta = pluralStringResource(R.plurals.home_steps_count, stepCount, stepCount) + " · " +
        TimeFormat.format(program.totalDurationMs, roundUp = false) + repeat
    val segments = remember(program) {
        program.steps.map {
            BarSegment(
                it.durationMs.coerceAtLeast(1L).toFloat(), SegmentState.Done,
                marker = it.effectiveWaitForAck(program),
            )
        }
    }
    val bar: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            SegmentBar(
                segments, fillColor = colors.strong, trackColor = ext.tile, barHeight = 10.dp,
                contentDescription = pluralStringResource(R.plurals.timeline_desc, stepCount, stepCount, ackCount),
            )
        }
    }
    val badge: @Composable () -> Unit = {
        IconBadge(iconFor(program.name), container = colors.strong, tint = colors.onStrong)
    }
    val play: @Composable () -> Unit = {
        RoundIconButton(
            Icons.Rounded.PlayArrow, stringResource(R.string.home_start_named, program.name), onStart,
            container = colors.strong, tint = colors.onStrong,
        )
    }
    val menu: @Composable () -> Unit = { ProgramMenu(program.name, onDuplicate, onExport, onDelete) }

    BentoTile(
        program = colors,
        modifier = modifier.fillMaxWidth().heightIn(min = if (wide) 128.dp else 184.dp),
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_edit_named, program.name),
        padding = PaddingValues(if (wide) 20.dp else 16.dp),
    ) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                badge()
                Column(Modifier.weight(1f)) {
                    Text(program.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                menu()
                play()
            }
            Spacer(Modifier.height(16.dp))
            bar(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge()
                Spacer(Modifier.weight(1f))
                menu()
            }
            Spacer(Modifier.height(8.dp))
            Text(program.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bar(Modifier.weight(1f))
                play()
            }
        }
    }
}

@Composable
private fun ProgramMenu(name: String, onDuplicate: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(
            Icons.Rounded.MoreVert, stringResource(R.string.home_more_named, name), { open = true },
            container = Color.Transparent,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_duplicate)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = { open = false; onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_export)) },
                leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                onClick = { open = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun NewProgramTile(onNew: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val label = stringResource(R.string.home_new_program)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(Dimens.TileRadius))
            .background(MaterialTheme.extended.tile)
            .drawBehind {
                val w = 2.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(Dimens.TileRadius.toPx() - w / 2),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                )
            }
            .clickable(role = Role.Button, onClick = onNew)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.extended.ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.extended.ink)
    }
}

// ---------------------------------------------------------------- État vide

@Composable
private fun WelcomeTile() {
    BentoTile(Modifier.fillMaxWidth()) {
        IconBadge(
            Icons.Rounded.NotificationsActive,
            container = MaterialTheme.extended.program(2).container,
            tint = MaterialTheme.extended.attentionAction,
            size = 64.dp, iconSize = 36.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extended.inkSecondary,
        )
    }
}

private fun LazyGridScope.emptyTemplates(onTemplate: (Templates.Kind) -> Unit) {
    val templates = listOf(
        Triple(Templates.Kind.EXAMPLE_30, R.string.template_example_name, R.string.template_example_desc),
        Triple(Templates.Kind.POMODORO, R.string.template_pomodoro_name, R.string.template_pomodoro_desc),
        Triple(Templates.Kind.INTERVALS, R.string.template_interval_name, R.string.template_interval_desc),
    )
    fullSpan("templates-title") {
        Text(
            stringResource(R.string.home_templates_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.extended.ink,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
    templates.forEachIndexed { i, (kind, name, desc) ->
        fullSpan("template-$i") {
            val colors = MaterialTheme.extended.program(i * 2 + 1)
            BentoTile(
                colors, Modifier.fillMaxWidth(),
                onClick = { onTemplate(kind) },
                onClickLabel = stringResource(R.string.home_template_use),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconBadge(Icons.Rounded.Add, container = colors.strong, tint = colors.onStrong)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Aperçus

@Composable
private fun PreviewHome(programs: List<Program>, running: RunState?) = HomeContent(
    programs = programs,
    runState = running,
    nowMs = 1_000_000L,
    snackbarHostState = remember { SnackbarHostState() },
    onNew = {}, onEdit = {}, onStart = {}, onOpenRun = {}, onPauseResume = {}, onOpenSettings = {},
    onDuplicate = {}, onExport = {}, onDelete = {}, onTemplate = {},
)

@ThemePreviews
@Composable
private fun HomePreview() = PreviewSurface {
    PreviewHome(
        listOf(
            PreviewData.program(),
            PreviewData.program("Pomodoro", 3, "p2"),
            PreviewData.program("Entraînement fractionné", 5, "p3"),
        ),
        PreviewData.runState(),
    )
}

@ThemePreviews
@Composable
private fun HomeEmptyPreview() = PreviewSurface { PreviewHome(emptyList(), null) }
