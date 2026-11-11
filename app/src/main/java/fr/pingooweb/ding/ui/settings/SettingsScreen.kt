package fr.pingooweb.ding.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pingooweb.ding.R
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.EditorInputMode
import fr.pingooweb.ding.data.ThemeMode
import fr.pingooweb.ding.runtime.PermissionKind
import fr.pingooweb.ding.runtime.PermissionState
import fr.pingooweb.ding.runtime.PermissionStatus
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.SwitchRow
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.components.bento.BentoTile
import fr.pingooweb.ding.ui.components.bento.IconBadge
import fr.pingooweb.ding.ui.components.bento.RoundIconButton
import fr.pingooweb.ding.ui.editor.PillSegmented
import fr.pingooweb.ding.ui.editor.SoftPill
import fr.pingooweb.ding.ui.navigation.containerViewModel
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it.appContext, it.settingsRepository, it.programRepository) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Réévalue l'état des autorisations à chaque retour sur l'écran (ON_RESUME).
    var permissions by remember { mutableStateOf(PermissionStatus.all(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = PermissionStatus.all(context)
        onPauseOrDispose { }
    }

    val invalidMsg = stringResource(R.string.settings_import_invalid)
    val ioMsg = stringResource(R.string.settings_transfer_error)
    val exportedMsg = stringResource(R.string.settings_export_ok)
    val importedNone = stringResource(R.string.settings_import_none)
    val importedMsg: (Int) -> String = { n -> context.resources.getQuantityString(R.plurals.settings_imported, n, n) }

    fun report(result: TransferResult) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when (result) {
                    is TransferResult.Exported -> exportedMsg
                    is TransferResult.Imported -> if (result.count == 0) importedNone else importedMsg(result.count)
                    TransferResult.InvalidFile -> invalidMsg
                    TransferResult.IoError -> ioMsg
                },
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { report(vm.exportAll(uri)) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { report(vm.import(uri)) }
    }

    SettingsContent(
        settings = settings,
        permissions = permissions,
        versionName = remember { appVersion(context) },
        snackbarHostState = snackbar,
        onBack = onBack,
        onTheme = vm::setThemeMode,
        onDynamic = vm::setDynamicColor,
        onKeepOn = vm::setKeepScreenOn,
        onInputMode = vm::setEditorInputMode,
        onHaptics = vm::setHaptics,
        onOpenPermission = { kind ->
            try {
                context.startActivity(PermissionStatus.settingsIntent(context, kind))
            } catch (e: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(ioMsg) }
            }
        },
        onExportAll = { exportLauncher.launch("ding-programmes.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
    )
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "1.0.0"

@Composable
fun SettingsContent(
    settings: AppSettings,
    permissions: List<PermissionState>,
    versionName: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onKeepOn: (Boolean) -> Unit,
    onInputMode: (EditorInputMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onOpenPermission: (PermissionKind) -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit,
) {
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack,
                    container = MaterialTheme.extended.tile,
                )
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = margin, end = margin, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item {
                    SettingsSection(stringResource(R.string.settings_appearance)) {
                        PillSegmented(
                            options = listOf(
                                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                ThemeMode.DARK to stringResource(R.string.theme_dark),
                            ),
                            selected = settings.themeMode,
                            onSelect = onTheme,
                        )
                        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        SwitchRow(
                            title = stringResource(R.string.settings_dynamic),
                            description = stringResource(
                                if (supported) R.string.settings_dynamic_desc else R.string.settings_dynamic_unsupported,
                            ),
                            checked = settings.dynamicColor && supported,
                            enabled = supported,
                            onCheckedChange = onDynamic,
                        )
                    }
                }

                item {
                    SettingsSection(stringResource(R.string.settings_behavior)) {
                        SwitchRow(
                            stringResource(R.string.settings_keep_on), settings.keepScreenOn, onKeepOn,
                            description = stringResource(R.string.settings_keep_on_desc),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.settings_input_mode), style = MaterialTheme.typography.titleSmall)
                            PillSegmented(
                                options = listOf(
                                    EditorInputMode.CUMULATIVE to stringResource(R.string.mode_cumulative),
                                    EditorInputMode.DURATION to stringResource(R.string.mode_duration),
                                ),
                                selected = settings.editorInputMode,
                                onSelect = onInputMode,
                            )
                        }
                        SwitchRow(
                            stringResource(R.string.settings_haptics), settings.haptics, onHaptics,
                            description = stringResource(R.string.settings_haptics_desc),
                        )
                    }
                }

                item {
                    SettingsSection(stringResource(R.string.settings_permissions_short)) {
                        Text(
                            stringResource(R.string.settings_permissions_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extended.inkSecondary,
                        )
                        permissions.forEach { p -> PermissionRow(p, onOpen = { onOpenPermission(p.kind) }) }
                    }
                }

                item {
                    SettingsSection(stringResource(R.string.settings_data)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            SoftPill(
                                stringResource(R.string.settings_export_all), onExportAll,
                                modifier = Modifier.weight(1f), icon = Icons.Rounded.FileUpload,
                            )
                            SoftPill(
                                stringResource(R.string.settings_import), onImport,
                                modifier = Modifier.weight(1f), icon = Icons.Rounded.FileDownload,
                            )
                        }
                    }
                }

                item { AboutTile(versionName) }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    BentoTile(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extended.inkSecondary,
            modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun PermissionRow(state: PermissionState, onOpen: () -> Unit) {
    val ext = MaterialTheme.extended
    val (titleRes, descRes) = when (state.kind) {
        PermissionKind.NOTIFICATIONS -> R.string.perm_notifications to R.string.perm_notifications_desc
        PermissionKind.EXACT_ALARMS -> R.string.perm_alarms to R.string.perm_alarms_desc
        PermissionKind.FULL_SCREEN -> R.string.perm_fullscreen to R.string.perm_fullscreen_desc
        PermissionKind.BATTERY_OPTIMIZATION -> R.string.perm_battery to R.string.perm_battery_desc
    }
    val icon = when (state.kind) {
        PermissionKind.NOTIFICATIONS -> Icons.Rounded.Notifications
        PermissionKind.EXACT_ALARMS -> Icons.Rounded.Alarm
        PermissionKind.FULL_SCREEN -> Icons.Rounded.Fullscreen
        PermissionKind.BATTERY_OPTIMIZATION -> Icons.Rounded.BatteryChargingFull
    }
    val statusColor = if (state.granted) ext.success else ext.attentionAction
    val title = stringResource(titleRes)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, container = MaterialTheme.colorScheme.background)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(descRes) + if (!state.required) " " + stringResource(R.string.perm_optional) else "",
                style = MaterialTheme.typography.bodySmall,
                color = ext.inkSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (state.granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(if (state.granted) R.string.perm_state_on else R.string.perm_state_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
            }
        }
        SoftPill(
            stringResource(R.string.perm_open), onOpen,
            contentDescription = stringResource(R.string.perm_open_for, title),
        )
    }
}

/** Tuile corail « À propos » : texte dans la couleur (encre / encre inversée) au meilleur contraste sur le corail. */
@Composable
private fun AboutTile(versionName: String) {
    val ext = MaterialTheme.extended
    val onBrand = listOf(ext.ink, MaterialTheme.colorScheme.onPrimary)
        .maxBy { contrastRatio(it, ext.brand) }
    BentoTile(Modifier.fillMaxWidth(), color = ext.brand, contentColor = onBrand) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Rounded.NotificationsActive, container = onBrand, tint = ext.brand)
            Column {
                Text(
                    stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            stringResource(R.string.settings_version, stringResource(R.string.app_name), versionName),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.offline_notice),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.settings_license),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        val context = LocalContext.current
        Text(
            stringResource(R.string.settings_source_code),
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            modifier = Modifier
                .clickable {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pingoo78/ding"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }
                .padding(top = 8.dp, bottom = 4.dp),
        )
        Text(
            stringResource(R.string.settings_app_license),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

@ThemePreviews
@Composable
private fun SettingsPreview() = PreviewSurface {
    SettingsContent(
        settings = AppSettings(),
        permissions = listOf(
            PermissionState(PermissionKind.NOTIFICATIONS, true, true),
            PermissionState(PermissionKind.EXACT_ALARMS, false, true),
            PermissionState(PermissionKind.FULL_SCREEN, false, false),
            PermissionState(PermissionKind.BATTERY_OPTIMIZATION, true, false),
        ),
        versionName = "1.0.0",
        snackbarHostState = remember { SnackbarHostState() },
        onBack = {}, onTheme = {}, onDynamic = {}, onKeepOn = {}, onInputMode = {}, onHaptics = {},
        onOpenPermission = {}, onExportAll = {}, onImport = {},
    )
}
