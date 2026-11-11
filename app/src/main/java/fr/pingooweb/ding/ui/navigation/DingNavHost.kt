package fr.pingooweb.ding.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.pingooweb.ding.ui.editor.EditorRoute
import fr.pingooweb.ding.ui.home.HomeRoute
import fr.pingooweb.ding.ui.run.RunRoute
import fr.pingooweb.ding.ui.settings.SettingsRoute

/**
 * Graphe de navigation (Accueil, Éditeur, Exécution, Réglages).
 *
 * @param openRunSignal compteur incrémenté par MainActivity à chaque demande « ouvrir Exécution »
 *   (notification, deep link `ding://run`) ; une valeur > 0 déclenche la navigation vers [Routes.RUN].
 */
@Composable
fun DingNavHost(
    openRunSignal: Int,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(openRunSignal) {
        if (openRunSignal > 0) {
            navController.navigate(Routes.RUN) { launchSingleTop = true }
        }
    }

    val openRun = { navController.navigate(Routes.RUN) { launchSingleTop = true } }

    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeRoute(
                onNewProgram = { navController.navigate(Routes.editor()) },
                onEditProgram = { navController.navigate(Routes.editor(it)) },
                onOpenRun = { openRun() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_PROGRAM_ID) { type = NavType.StringType }),
        ) { entry ->
            EditorRoute(
                programId = entry.arguments?.getString(Routes.ARG_PROGRAM_ID) ?: Routes.NEW_PROGRAM,
                onBack = { navController.popBackStack() },
                onOpenRun = { openRun() },
            )
        }
        composable(Routes.RUN) {
            RunRoute(
                onClose = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) { SettingsRoute(onBack = { navController.popBackStack() }) }
    }
}
