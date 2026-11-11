package fr.pingooweb.ding.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pingooweb.ding.R
import fr.pingooweb.ding.domain.RunController
import fr.pingooweb.ding.domain.engine.RunStatus

private const val STAGE_NONE = 0
private const val STAGE_CONFIRM_REPLACE = 1
private const val STAGE_EXPLAIN_NOTIF = 2

/**
 * Démarrage d'un programme : confirmation si un autre tourne, puis (Android 13+) demande POST_NOTIFICATIONS
 * avec explication ; en cas de refus le programme démarre quand même avec un avertissement.
 * Retourne la fonction `(programId, programName) -> Unit` à appeler ; émet elle-même les dialogues.
 */
@Composable
fun rememberProgramStarter(
    runController: RunController,
    onStarted: () -> Unit,
    onMessage: (String) -> Unit,
): (String, String) -> Unit {
    val context = LocalContext.current
    val runState by runController.state.collectAsStateWithLifecycle()
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var stage by rememberSaveable { mutableStateOf(STAGE_NONE) }
    var explained by rememberSaveable { mutableStateOf(false) }
    val deniedWarning = stringResource(R.string.start_notif_denied)

    fun launchStart() {
        val id = pendingId
        stage = STAGE_NONE
        pendingId = null
        if (id != null) {
            runController.start(id)
            onStarted()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onMessage(deniedWarning)
        launchStart()
    }

    fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun afterReplaceConfirmed() {
        if (needsNotificationPermission()) {
            if (explained) {
                @Suppress("InlinedApi")
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                stage = STAGE_EXPLAIN_NOTIF
            }
        } else {
            launchStart()
        }
    }

    when (stage) {
        STAGE_CONFIRM_REPLACE -> ConfirmDialog(
            title = stringResource(R.string.start_replace_title),
            message = stringResource(
                R.string.start_replace_message,
                runState?.program?.name.orEmpty(),
                pendingName,
            ),
            confirmLabel = stringResource(R.string.start_replace_confirm),
            onConfirm = { afterReplaceConfirmed() },
            onDismiss = { stage = STAGE_NONE; pendingId = null },
        )
        STAGE_EXPLAIN_NOTIF -> ConfirmDialog(
            title = stringResource(R.string.start_notif_title),
            message = stringResource(R.string.start_notif_message),
            confirmLabel = stringResource(R.string.start_notif_allow),
            dismissLabel = stringResource(R.string.start_notif_skip),
            onConfirm = {
                explained = true
                stage = STAGE_NONE
                @Suppress("InlinedApi")
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                explained = true
                onMessage(deniedWarning)
                launchStart()
            },
        )
        else -> Unit
    }

    return { id, name ->
        pendingId = id
        pendingName = name
        val running = runState
        val active = running != null && running.status != RunStatus.FINISHED
        when {
            active && running!!.program.id == id -> {
                pendingId = null
                onStarted()
            }
            active -> stage = STAGE_CONFIRM_REPLACE
            else -> afterReplaceConfirmed()
        }
    }
}
