package app.jwpocket.phone

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.jwpocket.media.PlaybackService
import app.jwpocket.media.pocketGraph
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.PhoneRoot
import app.jwpocket.phone.ui.PhoneUiState
import app.jwpocket.phone.ui.Pocket
import app.jwpocket.phone.ui.player.VideoSurface
import app.jwpocket.phone.ui.theme.PocketTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The one activity. Playback lives in the player service, so this only
 * decides what happens to it when the person leaves: shrink to
 * picture-in-picture, keep playing as audio, or pause (Settings).
 */
class MainActivity : ComponentActivity() {

    private val graph by lazy { pocketGraph }
    private val ui = PhoneUiState()
    private val pocket by lazy { Pocket(graph.core, graph.media) }
    private val hasPip by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        ui.inPictureInPicture = info.isInPictureInPictureMode
        // Closing the floating window (rather than expanding it) stops the video.
        if (!info.isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) graph.media.player.pause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        graph.media.player.warmUp()
        if (savedInstanceState == null) graph.core.updates.check()
        handle(intent)
        addOnPictureInPictureModeChangedListener(pipListener)

        setContent {
            PocketTheme {
                CompositionLocalProvider(LocalPocket provides pocket) {
                    if (ui.inPictureInPicture) PipContent()
                    else PhoneRoot(ui, onPictureInPicture = ::enterPip, onToggleFullscreen = ::setFullscreen)
                }
            }
        }

        // Keep the system's picture-in-picture settings in step with playback.
        if (hasPip) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(graph.media.player.state, graph.core.settings.settings) { p, s -> p.isPlaying && s.autoPictureInPicture }
                    .distinctUntilChanged()
                    .collect { updatePipParams() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Tapping the media notification opens the full player. */
    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) ui.expanded = true
    }

    private fun shouldAutoPip(): Boolean =
        hasPip && ui.expanded && graph.core.settings.current.autoPictureInPicture && graph.media.player.state.value.isPlaying

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoPip()).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipParams()?.let { runCatching { setPictureInPictureParams(it) } }
    }

    private fun enterPip() {
        if (!hasPip || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        ui.expanded = true
        pipParams()?.let { runCatching { enterPictureInPictureMode(it) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters by itself (auto-enter); older versions need this.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoPip()) enterPip()
    }

    override fun onResume() {
        super.onResume()
        updatePipParams()
    }

    override fun onStop() {
        super.onStop()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip && !isChangingConfigurations && !graph.core.settings.current.backgroundPlayback) {
            graph.media.player.pause()
        }
    }

    override fun onDestroy() {
        removeOnPictureInPictureModeChangedListener(pipListener)
        super.onDestroy()
    }

    /** Full screen turns the phone sideways; leaving it turns it back. */
    private fun setFullscreen(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@androidx.compose.runtime.Composable
private fun PipContent() {
    val pocket = LocalPocket.current
    val controller by pocket.media.player.controller.collectAsState()
    val settings by pocket.core.settings.settings.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(controller, Modifier.fillMaxSize(), subtitleScale = settings.subtitleScale * 0.7f)
    }
}
