package fr.pingooweb.ding.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Horloge d'affichage (SystemClock.elapsedRealtime) rafraîchie toutes les [periodMs] ms uniquement si [active]
 * ET tant que l'écran est visible (STARTED) ; sinon aucun réveil.
 */
@Composable
fun rememberNowMs(active: Boolean, periodMs: Long = 150L): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        now.longValue = SystemClock.elapsedRealtime()
        if (active) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now.longValue = SystemClock.elapsedRealtime()
                    delay(periodMs)
                }
            }
        }
    }
    return now
}

/** Vrai si les animations système sont désactivées (ANIMATOR_DURATION_SCALE == 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Retour haptique léger, neutralisé si le réglage est désactivé. */
@Stable
class Haptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    fun tick() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}

/** Libellé affichable de l'étape ([index] 0-based) ; « Étape n » si vide. */
@Composable
fun fr.pingooweb.ding.domain.model.Step.labelOrDefault(index: Int): String =
    label.ifBlank { androidx.compose.ui.res.stringResource(fr.pingooweb.ding.R.string.step_default_label, index + 1) }
