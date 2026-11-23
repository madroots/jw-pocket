package app.jwpocket.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import app.jwpocket.media.PlaybackService
import app.jwpocket.media.pocketGraph
import app.jwpocket.tv.ui.LocalTv
import app.jwpocket.tv.ui.Tv
import app.jwpocket.tv.ui.TvColors
import app.jwpocket.tv.ui.TvPocket
import app.jwpocket.tv.ui.TvRoot

class MainActivity : ComponentActivity() {

    private val graph by lazy { pocketGraph }
    private var openPlayer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Google TV boxes can show system bars; keep the picture full screen.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        graph.media.player.warmUp()
        if (savedInstanceState == null) graph.core.updates.check()
        handle(intent)
        val pocket = TvPocket(graph.core, graph.media)
        setContent {
            MaterialTheme(colorScheme = TvColors) {
                Surface(colors = SurfaceDefaults.colors(containerColor = Tv.Background)) {
                    CompositionLocalProvider(LocalTv provides pocket) {
                        TvRoot(openPlayer, onPlayerOpened = { openPlayer = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) openPlayer = true
    }

    /** On a TV, leaving the app (Home button) pauses the video; Resume picks it up. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) graph.media.player.pause()
    }
}
