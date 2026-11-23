package app.jwpocket.phone.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.jwpocket.core.CoreGraph

/** A ViewModel built from the shared graph, scoped to the screen (and [key]). */
@Composable
inline fun <reified VM : ViewModel> pocketViewModel(key: String? = null, crossinline create: (CoreGraph) -> VM): VM {
    val core = LocalPocket.current.core
    return viewModel(key = key) { create(core) }
}
