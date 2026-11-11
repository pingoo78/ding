package fr.pingooweb.ding.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.pingooweb.ding.di.AppContainer
import fr.pingooweb.ding.di.LocalAppContainer

/** Fabrique de ViewModel construisant l'instance depuis l'[AppContainer]. */
inline fun <reified VM : ViewModel> containerViewModelFactory(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory { initializer { create(container) } }

/**
 * Récupère (ou crée) un ViewModel dont le constructeur reçoit l'[AppContainer].
 * Exemple : `val vm = containerViewModel { HomeViewModel(it.programRepository, it.runController) }`.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    val factory = remember(container) { containerViewModelFactory(container, create) }
    return viewModel(key = key, factory = factory)
}
