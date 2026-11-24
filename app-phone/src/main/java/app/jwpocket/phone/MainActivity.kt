package app.jwpocket.phone

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.jwpocket.phone.ui.player.ListenVisual
import app.jwpocket.phone.ui.player.LocalVideoSurfacesOff
import android.content.Context
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
import app.jwpocket.brand.PocketSplash
import app.jwpocket.core.util.withAppLanguage
import app.jwpocket.media.PlaybackService
import app.jwpocket.media.pocketGraph
import app.jwpocket.phone.notify.NewVideoNotifier
import app.jwpocket.phone.clips.ClipStore
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

    // The app's own text in the language chosen in Settings (or the device's).
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.withAppLanguage())

    private val graph by lazy { pocketGraph }
    private val ui = PhoneUiState()
    private val pocket by lazy { Pocket(graph.core, graph.media, ClipStore.get(this)) }
    private val hasPip by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * The floating window just closed. Expanding it resumes the app next;
     * closing it (×, or dragged away) stops the app next, which pauses.
     */
    private var leftPip = false

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        ui.inPictureInPicture = info.isInPictureInPictureMode
        if (!info.isInPictureInPictureMode) {
            // Already stopped (some Android versions report the close after onStop): pause right away.
            if (lifecycle.currentState == Lifecycle.State.CREATED) graph.media.player.pause() else leftPip = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The update check waits for the launch animation, so its dialog never covers it.
        val splash = PocketSplash.install(this, savedInstanceState) { if (savedInstanceState == null) graph.core.updates.check() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setFullscreen(false)
        graph.media.player.warmUp()
        // The last thing played waits in the mini player, paused, as it was left.
        if (savedInstanceState == null) graph.media.player.restoreLastSession()
        handle(intent, opensDetails = savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0)
        addOnPictureInPictureModeChangedListener(pipListener)

        setContent {
            Box(Modifier.fillMaxSize()) {
                PocketTheme {
                    CompositionLocalProvider(LocalPocket provides pocket) {
                        // The screens stay underneath (tab, open pages, scroll) for when the window is expanded;
                        // their video surfaces let go so the floating one shows the picture.
                        CompositionLocalProvider(LocalVideoSurfacesOff provides ui.inPictureInPicture) {
                            PhoneRoot(ui, onPictureInPicture = ::enterPip, onToggleFullscreen = ::setFullscreen)
                        }
                        if (ui.inPictureInPicture) PipContent()
                    }
                }
                splash.Overlay()
            }
        }

        // Keep the system's picture-in-picture settings (and its play/pause button) in step with playback.
        if (hasPip) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(graph.media.player.state, graph.core.settings.settings) { p, s -> Triple(p.isPlaying, s.autoPictureInPicture, p.video?.isAudio) }
                    .distinctUntilChanged()
                    .collect { updatePipParams() }
            }
        }
        // Not only while started: the floating window's buttons are pressed while the app is in the background.
        ContextCompat.registerReceiver(this, pipControls, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent, opensDetails = true)
    }

    /**
     * Tapping the media notification opens the full player; tapping a new-video
     * one opens that video's details. A screen recreated, or reopened from
     * Recents, with the same launch intent ([opensDetails] false) doesn't open them again.
     */
    private fun handle(intent: Intent?, opensDetails: Boolean) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) ui.expanded = true
        if (opensDetails) NewVideoNotifier.videoFrom(intent)?.let { ui.openDetails = it }
    }

    /**
     * Leaving the app while something plays shrinks it to the floating window:
     * a video from the full player, audio from anywhere (it plays on out of
     * sight otherwise, with nothing on screen saying so).
     */
    private fun shouldAutoPip(): Boolean {
        val state = graph.media.player.state.value
        return hasPip && graph.core.settings.current.autoPictureInPicture && state.isPlaying &&
            (ui.expanded || state.video?.isAudio == true)
    }

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(pipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoPip()).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /** Play/pause and next in the floating window (next chapter, song or video). */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun pipActions(): List<RemoteAction> {
        val playing = graph.media.player.state.value.isPlaying
        fun action(control: Int, icon: Int, label: Int) = RemoteAction(
            Icon.createWithResource(this, icon), getString(label), getString(label),
            PendingIntent.getBroadcast(
                this, control, Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL, control),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return listOf(
            if (playing) action(CONTROL_TOGGLE, android.R.drawable.ic_media_pause, R.string.common_pause)
            else action(CONTROL_TOGGLE, android.R.drawable.ic_media_play, R.string.common_play),
            action(CONTROL_NEXT, android.R.drawable.ic_media_next, R.string.player_next),
        )
    }

    private val pipControls = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_TOGGLE -> graph.media.player.togglePlay()
                CONTROL_NEXT -> graph.media.player.next()
            }
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipParams()?.let { runCatching { setPictureInPictureParams(it) } }
    }

    private fun enterPip() {
        if (!hasPip || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // A video shows from the full player; audio has its own card and leaves the screens as they were.
        if (graph.media.player.state.value.video?.isAudio != true) ui.expanded = true
        pipParams()?.let { runCatching { enterPictureInPictureMode(it) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters by itself (auto-enter); older versions need this.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoPip()) enterPip()
    }

    override fun onResume() {
        super.onResume()
        leftPip = false // the floating window was expanded back into the app
        updatePipParams()
    }

    override fun onStart() {
        super.onStart()
        graph.media.player.setInBackground(false)
    }

    override fun onStop() {
        super.onStop()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip && !isChangingConfigurations) {
            when {
                // The floating window was closed (×, or dragged away): pause.
                leftPip -> graph.media.player.pause()
                // Out of sight: pause, or keep playing as sound only (smallest quality if the setting says so).
                graph.core.settings.current.backgroundPlayback -> graph.media.player.setInBackground(true)
                else -> graph.media.player.pause()
            }
        }
        leftPip = false
    }

    override fun onDestroy() {
        removeOnPictureInPictureModeChangedListener(pipListener)
        unregisterReceiver(pipControls)
        super.onDestroy()
    }

    /**
     * The app stays upright; only the full-screen button turns the video
     * sideways (turning the phone alone doesn't), and leaving full screen
     * turns it back.
     */
    private fun setFullscreen(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

private const val ACTION_PIP_CONTROL = "app.jwpocket.PIP_CONTROL"
private const val EXTRA_CONTROL = "control"
private const val CONTROL_TOGGLE = 1
private const val CONTROL_NEXT = 2

/**
 * The floating window: a video shows its picture; audio (the Bible, songs,
 * dramas) a small card with the sound bars, the title and, for the Bible,
 * the verse being read.
 */
@androidx.compose.runtime.Composable
private fun PipContent() {
    val pocket = LocalPocket.current
    val controller by pocket.media.player.controller.collectAsState()
    val settings by pocket.core.settings.settings.collectAsState()
    val state by pocket.media.player.state.collectAsState()
    val video = state.video
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (video == null || !video.isAudio) {
            VideoSurface(controller, Modifier.fillMaxSize(), subtitleScale = settings.subtitleScale * 0.7f)
            return@Box
        }
        val verse = pocket.core.bible.chapter(video.id)?.verseAt(state.positionMs)
        Column(Modifier.fillMaxSize()) {
            ListenVisual(video, state.playWhenReady && !state.ended, cue = null, Modifier.fillMaxWidth().weight(1f), compact = true)
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(video.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (verse != null) Text(stringResource(R.string.bible_verse) + " $verse", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
