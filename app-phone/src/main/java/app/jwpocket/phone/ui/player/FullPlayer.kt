package app.jwpocket.phone.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Settings
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatClock
import app.jwpocket.core.util.formatDuration
import app.jwpocket.media.PlaybackState
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.components.Thumbnail
import app.jwpocket.phone.ui.rememberMarks
import app.jwpocket.phone.ui.theme.Pocket
import kotlinx.coroutines.delay

private enum class Sheet { Subtitles, Quality, Speed, Sleep }

/**
 * The full player. Portrait: video on top, then controls, actions and Up
 * next. Landscape: the video fills the screen. Back or a swipe down shrinks
 * it to the mini player; playback carries on.
 */
@Composable
fun FullPlayer(
    state: PlaybackState,
    landscape: Boolean,
    onCollapse: () -> Unit,
    onPictureInPicture: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val pocket = LocalPocket.current
    val connection = pocket.media.player
    val controller by connection.controller.collectAsState()
    val settings by pocket.core.settings.settings.collectAsState()
    val languages by pocket.core.languages.languages.collectAsState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val video = state.video ?: return
    val marks by rememberMarks(video.id)
    // A download removed while it plays keeps playing, streamed.
    val fromDownload = state.fromDownload && marks.download?.status == DownloadStatus.Done

    BackHandler { if (landscape) onToggleFullscreen() else onCollapse() }

    if (landscape) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(controller, Modifier.fillMaxSize(), settings.subtitleScale * 1.25f)
            VideoOverlay(state, fullscreen = true, title = video.title, onCollapse = onToggleFullscreen,
                onFullscreen = onToggleFullscreen, onSubtitles = { sheet = Sheet.Subtitles }, onQuality = { sheet = Sheet.Quality })
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dragY.coerceAtLeast(0f) }
                .background(Pocket.Background)
                .statusBarsPadding()
        ) {
            // Grab bar and video: swipe down anywhere on them to minimize.
            Column(
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > with(density) { 120.dp.toPx() }) onCollapse()
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    ) { _, delta -> dragY += delta }
                }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Pocket.TextSecondary)
                        Text(
                            if (fromDownload) "From your downloads" else (state.quality ?: "Streaming"),
                            style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                        )
                    }
                    IconButton(onClick = onPictureInPicture) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture") }
                }

                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    VideoSurface(controller, Modifier.fillMaxSize(), settings.subtitleScale)
                    VideoOverlay(state, fullscreen = false, title = null, onCollapse = onCollapse,
                        onFullscreen = onToggleFullscreen, onSubtitles = { sheet = Sheet.Subtitles }, onQuality = { sheet = Sheet.Quality })
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                item { NowPlayingInfo(state, onSheet = { sheet = it }) }
                if (state.queue.size > 1) {
                    item {
                        Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = Pocket.TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(if (settings.autoplayNext) "Up next" else "In this list", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    itemsIndexed(state.queue, key = { _, v -> v.id }) { index, v ->
                        val current = index == state.queueIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { connection.playQueueItem(index) }
                                .background(if (current) Pocket.Surface else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Thumbnail(v, Modifier.width(112.dp), corner = 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                    color = if (current) Pocket.Accent else MaterialTheme.colorScheme.onSurface)
                                Text(formatDuration(v.duration), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(24.dp)) }
            }
        }
    }

    when (sheet) {
        Sheet.Subtitles -> SubtitleSheet(
            ownLanguageName = video.subtitleUrl?.let { pocket.core.languages.name(settings.languageCode) },
            others = pocket.core.subtitles.otherLanguages(video, settings.languageCode, languages, settings.subtitleShortcuts),
            pinned = settings.recentSubtitleLanguages,
            selected = settings.subtitleLanguage,
            showingLanguage = state.subtitleLanguage,
            onPick = { connection.setSubtitles(it); sheet = null },
            onDismiss = { sheet = null },
        )
        Sheet.Quality -> QualitySheet(
            video, "Quality", state.quality, pocket.media.downloads.get(video.id)?.takeIf { it.isDone }?.quality, showAuto = true,
            onPick = { connection.setQuality(it); sheet = null }, onDismiss = { sheet = null },
        )
        Sheet.Speed -> SpeedSheet(state.speed, onPick = { connection.setSpeed(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.Sleep -> SleepSheet(state.sleepAt != null, onPick = { connection.setSleepTimer(it); sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** Title, seek bar, big transport buttons and the action pills. */
@Composable
private fun NowPlayingInfo(state: PlaybackState, onSheet: (Sheet) -> Unit) {
    val pocket = LocalPocket.current
    val connection = pocket.media.player
    val video = state.video ?: return
    val marks by rememberMarks(video.id)
    val settings by pocket.core.settings.settings.collectAsState()
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(video.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(formatDuration(video.duration).ifEmpty { null }, state.quality, video.publishedDate).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        SeekBar(state, onSeek = connection::seekTo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = connection::previous, enabled = state.hasPrevious) { Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { connection.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", modifier = Modifier.size(30.dp)) }
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable(onClick = connection::togglePlay),
                contentAlignment = Alignment.Center,
            ) {
                if (state.buffering && state.playWhenReady) CircularProgressIndicator(color = Pocket.Background, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                else Icon(
                    when { state.ended -> Icons.Rounded.Replay; state.isPlaying -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                    if (state.isPlaying) "Pause" else "Play", tint = Pocket.Background, modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { connection.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", modifier = Modifier.size(30.dp)) }
            IconButton(onClick = connection::next, enabled = state.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(16.dp))

        if (state.subtitleMissing && settings.subtitleLanguage != null && settings.subtitleLanguage != Settings.SUBTITLES_OFF) {
            Text(
                "No ${pocket.core.languages.name(settings.subtitleLanguage!!)} subtitles for this video, showing the original.",
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cc = state.subtitleLanguage
            Pill(if (cc != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                if (cc != null) pocket.core.languages.name(cc) else "Subtitles off", highlighted = cc != null) { onSheet(Sheet.Subtitles) }
            Pill(Icons.Rounded.HighQuality, state.quality ?: "Quality") { onSheet(Sheet.Quality) }
            val download = marks.download
            when {
                download == null -> Pill(Icons.Rounded.Download, "Download") {
                    val quality = settings.downloadQuality ?: state.quality ?: video.file(null)?.label
                    if (quality != null) pocket.media.downloads.download(video, quality, settings.languageCode)
                }
                download.status == DownloadStatus.Done -> Pill(Icons.Rounded.CheckCircle, "Downloaded · ${download.quality}", highlighted = true) { confirmRemove = true }
                else -> Pill(Icons.Rounded.Download, "${(download.progress * 100).toInt()}% · ${formatBytes(download.totalBytes)}") { confirmRemove = true }
            }
            Pill(if (marks.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (marks.favorite) "Saved" else "Favorite", highlighted = marks.favorite) {
                pocket.core.favorites.toggle(video)
            }
            Pill(Icons.Rounded.Speed, if (state.speed == 1f) "Speed" else "${state.speed}×", highlighted = state.speed != 1f) { onSheet(Sheet.Speed) }
            Pill(Icons.Rounded.Bedtime, sleepLabel(state.sleepAt), highlighted = state.sleepAt != null) { onSheet(Sheet.Sleep) }
            video.shareUrl(settings.languageCode)?.let { url ->
                Pill(Icons.Rounded.Share, "Share") {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
                    }, null))
                }
            }
        }
        if (!state.error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }

    val download = marks.download
    if (confirmRemove && download != null) {
        val done = download.status == DownloadStatus.Done
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (done) "Remove download?" else "Cancel download?") },
            text = {
                Text(
                    if (done) "Frees ${formatBytes(download.totalBytes)} on this phone. You can still stream it."
                    else "The part downloaded so far is deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = { pocket.media.downloads.remove(video.id); confirmRemove = false }) {
                    Text(if (done) "Remove" else "Cancel download", color = Pocket.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

private fun sleepLabel(sleepAt: Long?): String = when {
    sleepAt == null -> "Sleep timer"
    sleepAt < 0 -> "After this video"
    else -> "${((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)} min left"
}

@Composable
private fun Pill(icon: ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) Pocket.AccentSoft else Pocket.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (highlighted) Pocket.Accent else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Seek bar with elapsed and total time; dragging previews the time and seeks on release. */
@Composable
fun SeekBar(state: PlaybackState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, light: Boolean = false) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shown = dragging ?: state.progress
    Column(modifier) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Pocket.Accent,
                inactiveTrackColor = if (light) Color(0x55FFFFFF) else Pocket.SurfaceHighest,
            ),
            modifier = Modifier.height(28.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock((shown * duration).toLong()), style = MaterialTheme.typography.labelSmall, color = if (light) Color.White else Pocket.TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(formatClock(state.durationMs), style = MaterialTheme.typography.labelSmall, color = if (light) Color.White else Pocket.TextSecondary)
        }
    }
}

/**
 * Over the video: double-tap a side to skip 10 seconds, tap to show the
 * controls, which hide by themselves while playing. In portrait the controls
 * live below the video, so only the full-screen button shows here; full
 * screen has the whole set.
 */
@Composable
private fun VideoOverlay(
    state: PlaybackState,
    fullscreen: Boolean,
    title: String?,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
    onSubtitles: () -> Unit,
    onQuality: () -> Unit,
) {
    val connection = LocalPocket.current.media.player
    var visible by remember { mutableStateOf(true) }
    var touch by remember { mutableStateOf(0) }
    LaunchedEffect(visible, state.isPlaying, touch) {
        if (visible && state.isPlaying) {
            delay(3_000)
            visible = false
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { visible = !visible; touch++ },
                    onDoubleTap = { offset ->
                        connection.seekBy(if (offset.x < size.width / 2) -10_000 else 10_000)
                        visible = true; touch++
                    },
                )
            }
    ) {
        if (state.buffering && state.playWhenReady && !(visible && fullscreen)) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(40.dp))
        }
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            if (!fullscreen) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Fullscreen, "Full screen", tint = Color.White)
                        }
                    }
                }
                return@AnimatedVisibility
            }
            Box(Modifier.fillMaxSize().background(Color(0x77000000))) {
                Row(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Exit full screen", tint = Color.White) }
                    Text(title.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { touch++; onSubtitles() }) {
                        Icon(if (state.subtitleLanguage != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, "Subtitles", tint = Color.White)
                    }
                    IconButton(onClick = { touch++; onQuality() }) { Icon(Icons.Rounded.HighQuality, "Quality", tint = Color.White) }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    IconButton(onClick = { touch++; connection.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = { touch++; connection.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { touch++; connection.togglePlay() }, contentAlignment = Alignment.Center) {
                        if (state.buffering && state.playWhenReady) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { touch++; connection.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = { touch++; connection.next() }, enabled = state.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeekBar(state, onSeek = { touch++; connection.seekTo(it) }, modifier = Modifier.weight(1f), light = true)
                    IconButton(onClick = onFullscreen) { Icon(Icons.Rounded.FullscreenExit, "Exit full screen", tint = Color.White) }
                }
            }
        }
    }
}
