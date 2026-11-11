package fr.pingooweb.ding

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.pingooweb.ding.data.AppSettings
import fr.pingooweb.ding.data.ThemeMode
import fr.pingooweb.ding.di.LocalAppContainer
import fr.pingooweb.ding.ui.navigation.DingNavHost
import fr.pingooweb.ding.ui.theme.DingTheme

class MainActivity : ComponentActivity() {

    /** Incrémenté à chaque demande d'ouverture de l'écran Exécution (intent de notification / deep link). */
    private var openRunSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && isOpenRunIntent(intent)) openRunSignal++
        val container = (application as DingApp).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                DingTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        DingNavHost(openRunSignal = openRunSignal)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isOpenRunIntent(intent)) openRunSignal++
    }

    private fun isOpenRunIntent(intent: Intent?): Boolean =
        intent != null && (
            intent.getBooleanExtra(EXTRA_OPEN_RUN, false) ||
                (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "ding" && intent.data?.host == "run")
            )

    companion object {
        /** Extra booléen : ouvre directement l'écran Exécution. */
        const val EXTRA_OPEN_RUN = "fr.pingooweb.ding.extra.OPEN_RUN"
    }
}
