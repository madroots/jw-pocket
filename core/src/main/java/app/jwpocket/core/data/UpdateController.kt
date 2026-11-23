package app.jwpocket.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val available: AppUpdate? = null,
    val dismissed: Boolean = false,
    /** 0..1 while the APK downloads, else null. */
    val downloadProgress: Float? = null,
    /** A short message for a snackbar/toast, consumed by the UI. */
    val message: String? = null,
) {
    val showDialog: Boolean get() = available != null && !dismissed && downloadProgress == null
}

/** The self-update flow both apps share: check, offer, download, install. */
class UpdateController(
    private val updates: UpdateRepository,
    private val installer: UpdateInstaller,
    private val scope: CoroutineScope,
    private val currentVersion: String,
) {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check(manual: Boolean = false) {
        scope.launch {
            if (manual) _state.update { it.copy(message = "Checking for updates…") }
            updates.check(currentVersion)
                .onSuccess { update ->
                    _state.update {
                        when {
                            update != null -> it.copy(available = update, dismissed = false, message = null)
                            manual -> it.copy(message = "JW Pocket is up to date ($currentVersion).")
                            else -> it
                        }
                    }
                }
                .onFailure { if (manual) _state.update { it.copy(message = "Couldn't check for updates.") } }
        }
    }

    fun dismiss() = _state.update { it.copy(dismissed = true) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun install() {
        val update = _state.value.available ?: return
        scope.launch {
            _state.update { it.copy(downloadProgress = 0f) }
            val file = installer.download(update.apkUrl) { p -> _state.update { it.copy(downloadProgress = p) } }
            _state.update { it.copy(downloadProgress = null, dismissed = true) }
            if (file == null) {
                _state.update { it.copy(message = "Update download failed. Please try again.") }
                return@launch
            }
            delay(300) // let the progress dialog close first; some TVs drop the installer otherwise
            withContext(Dispatchers.Main) { installer.install(file) }
        }
    }
}
