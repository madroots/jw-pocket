package app.jwpocket.tv.ui

import android.graphics.Color as AColor
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.jwpocket.core.data.Settings
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatClock
import app.jwpocket.tv.R
import kotlinx.coroutines.delay

private enum class PlayerPanel { Subtitles, Quality, Speed }

/**
 * Full-screen player for the remote. With the controls hidden: OK shows them,
 * left/right skip 10 s (hold to go faster). Back hides the controls, then
 * closes the player.
 */
@Composable
fun TvPlayer(onExit: () -> Unit) {
    val tv = LocalTv.current
    val connection = tv.media.player
    val state by connection.state.collectAsState()
    val controller by connection.controller.collectAsState()
    val settings by tv.core.settings.settings.collectAsState()
    val languages by tv.core.languages.languages.collectAsState()
    var visible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val root = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }
    val video = state.video

    LaunchedEffect(visible, state.isPlaying, poke, panel) {
        if (visible && state.isPlaying && panel == null) {
            delay(4_000)
            visible = false
        }
    }
    LaunchedEffect(visible) {
        runCatching { if (visible) playButton.requestFocus() else root.requestFocus() }
    }
    LaunchedEffect(state.subtitleMissing, state.video?.id) {
        val wanted = settings.subtitleLanguage
        if (state.subtitleMissing && wanted != null && wanted != Settings.SUBTITLES_OFF) {
            notice = "No ${tv.core.languages.name(wanted)} subtitles for this video"
            delay(4_000)
            notice = null
        }
    }

    // Back: hide the controls first, then leave the player for the details.
    BackHandler(enabled = panel == null) {
        if (visible) visible = false else onExit()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(root)
            // Controls stay within the player; the page underneath is still there.
            .keepFocusInside()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.key.nativeKeyCode
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { connection.togglePlay(); visible = true; poke++; true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { connection.seekBy(30_000); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { connection.seekBy(-30_000); true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { connection.next(); true }
                    KeyEvent.KEYCODE_CAPTIONS -> { panel = PlayerPanel.Subtitles; true }
                    else -> if (!visible && panel == null) {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val repeat = event.nativeKeyEvent.repeatCount
                                val step = if (repeat > 10) 60_000L else if (repeat > 3) 30_000L else 10_000L
                                connection.seekBy(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -step else step)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { visible = true; true }
                            else -> false
                        }
                    } else { poke++; false }
                }
            }
    ) {
        TvVideoSurface(controller, settings.subtitleScale)

        if (state.buffering && state.playWhenReady) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        if (!visible) {
            // A slim progress line while seeking with the controls hidden.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(state.progress).height(3.dp).background(Tv.Accent))
            }
        }

        notice?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(32.dp).background(Tv.Scrim).padding(horizontal = 18.dp, vertical = 10.dp))
        }
        state.error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Tv.Scrim).padding(24.dp))
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xAA000000), 0.3f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xDD000000)))) {
                Column(Modifier.align(Alignment.TopStart).padding(48.dp)) {
                    Text(video?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(state.quality, if (state.fromDownload) "From downloads" else null, state.subtitleLanguage?.let { "Subtitles: ${tv.core.languages.name(it)}" }).joinToString(" · "),
                        color = Tv.TextSecondary,
                    )
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 48.dp, vertical = 36.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatClock(state.positionMs), color = Color.White)
                        Spacer(Modifier.width(16.dp))
                        Box(Modifier.weight(1f).height(6.dp).background(Color(0x44FFFFFF))) {
                            Box(Modifier.fillMaxWidth((state.bufferedMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)).height(6.dp).background(Color(0x55FFFFFF)))
                            Box(Modifier.fillMaxWidth(state.progress).height(6.dp).background(Tv.Accent))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(formatClock(state.durationMs), color = Color.White)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ControlButton(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", Modifier.focusRequester(playButton), showLabel = false) { connection.togglePlay() }
                        ControlButton(Icons.Rounded.Replay10, "Back 10 seconds", showLabel = false) { connection.seekBy(-10_000) }
                        ControlButton(Icons.Rounded.Forward10, "Forward 10 seconds", showLabel = false) { connection.seekBy(10_000) }
                        if (state.hasNext) ControlButton(Icons.Rounded.SkipNext, "Next") { connection.next() }
                        Spacer(Modifier.width(24.dp))
                        ControlButton(if (state.subtitleLanguage != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                            state.subtitleLanguage?.let { tv.core.languages.name(it) } ?: "Subtitles") { panel = PlayerPanel.Subtitles }
                        if (!state.fromDownload) ControlButton(Icons.Rounded.HighQuality, state.quality ?: "Quality") { panel = PlayerPanel.Quality }
                        ControlButton(Icons.Rounded.Speed, if (state.speed == 1f) "Speed" else "${state.speed}×") { panel = PlayerPanel.Speed }
                    }
                }
            }
        }
    }

    when (panel) {
        PlayerPanel.Subtitles -> if (video != null) {
            val own = video.subtitleUrl?.let { PanelOption(null, tv.core.languages.name(settings.languageCode), "Same as the video", settings.subtitleLanguage == null) }
            val others = tv.core.subtitles.otherLanguages(video, settings.languageCode, languages, settings.subtitleShortcuts)
                .map { PanelOption(it.code, it.vernacular, if (it.code in settings.recentSubtitleLanguages) "Recently used" else it.name.takeIf { n -> n != it.vernacular }, settings.subtitleLanguage == it.code) }
            TvSidePanel(
                "Subtitles", "Remembered for the next videos.",
                listOf(PanelOption(Settings.SUBTITLES_OFF, "Off", selected = settings.subtitleLanguage == Settings.SUBTITLES_OFF)) + listOfNotNull(own) + others,
                onPick = { connection.setSubtitles(it.key); panel = null },
                onDismiss = { panel = null },
            )
        }
        PlayerPanel.Quality -> if (video != null) TvSidePanel(
            "Quality",
            options = listOf(PanelOption(null, "Auto", "Up to 720p", state.quality == null)) +
                video.qualities.reversed().map { PanelOption(it, it, video.file(it)?.filesize?.let(::formatBytes), it == state.quality) },
            onPick = { connection.setQuality(it.key); panel = null },
            onDismiss = { panel = null },
        )
        PlayerPanel.Speed -> TvSidePanel(
            "Playback speed",
            options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { PanelOption(it.toString(), if (it == 1f) "Normal" else "${it}×", selected = it == state.speed) },
            onPick = { o -> o.key?.toFloatOrNull()?.let(connection::setSpeed); panel = null },
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, showLabel: Boolean = true, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, corner = 50.dp, scale = 1.08f, color = Color(0x33FFFFFF)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
            if (showLabel) {
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TvVideoSurface(player: Player?, subtitleScale: Float) {
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.tv_player_view, null) as PlayerView).apply {
                subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setStyle(CaptionStyleCompat(AColor.WHITE, 0x99000000.toInt(), AColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, AColor.WHITE, null))
                }
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * subtitleScale)
        },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}
