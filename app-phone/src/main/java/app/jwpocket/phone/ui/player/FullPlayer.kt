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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Settings
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatClock
import app.jwpocket.core.util.formatDuration
import app.jwpocket.media.PlaybackState
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.components.Thumbnail
import app.jwpocket.phone.ui.rememberMarks
import app.jwpocket.phone.ui.theme.Pocket
import app.jwpocket.phone.clips.Clip
import app.jwpocket.phone.ui.clips.ClipShareDialog
import app.jwpocket.phone.ui.clips.ClipEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Sheet { Subtitles, Quality, Speed, Sleep, Moment }

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
    val cue by connection.cues.collectAsState()
    val languages by pocket.core.languages.languages.collectAsState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var savedClip by remember { mutableStateOf<Clip?>(null) }
    val drag = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val video = state.video ?: return
    val marks by rememberMarks(video.id)
    // A download removed while it plays keeps playing, streamed.
    val fromDownload = state.fromDownload && marks.download?.status == DownloadStatus.Done

    BackHandler { if (landscape) onToggleFullscreen() else onCollapse() }

    // "Share a moment" takes the whole screen; the player comes back when it closes.
    if (sheet == Sheet.Moment) {
        ClipEditor(state, video, onClose = { sheet = null }, onCreated = { sheet = null; savedClip = it })
        return
    }

    if (landscape) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(controller, Modifier.fillMaxSize(), settings.subtitleScale * 1.25f)
            if (state.listening) ListenVisual(video, state.playWhenReady && !state.ended, cue, Modifier.fillMaxSize(), subtitleScale = settings.subtitleScale * 1.25f)
            VideoOverlay(state, fullscreen = true, title = video.title, onCollapse = onToggleFullscreen,
                onFullscreen = onToggleFullscreen, onSubtitles = { sheet = Sheet.Subtitles }, onQuality = { sheet = Sheet.Quality })
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = drag.value.coerceAtLeast(0f) }
                .background(Pocket.Background)
                .statusBarsPadding()
        ) {
            // Grab bar and video: swipe down anywhere on them to minimize.
            Column(
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            // Far enough: shrink from where the finger left it (the exit
                            // slide carries on from here); otherwise glide back up.
                            if (drag.value > with(density) { 120.dp.toPx() }) onCollapse()
                            else dragScope.launch { drag.animateTo(0f) }
                        },
                        onDragCancel = { dragScope.launch { drag.animateTo(0f) } },
                    ) { _, delta -> dragScope.launch { drag.snapTo(drag.value + delta) } }
                }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_minimize), modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.player_now_playing), style = MaterialTheme.typography.labelSmall, color = Pocket.TextSecondary)
                        Text(
                            if (fromDownload) stringResource(R.string.player_from_downloads) else (state.quality ?: stringResource(R.string.player_streaming)),
                            style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                        )
                    }
                    IconButton(onClick = onPictureInPicture) { Icon(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.player_picture_in_picture)) }
                }

                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    VideoSurface(controller, Modifier.fillMaxSize(), settings.subtitleScale)
                    if (state.listening) ListenVisual(video, state.playWhenReady && !state.ended, cue, Modifier.fillMaxSize(), subtitleScale = settings.subtitleScale)
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
                            Text(stringResource(if (settings.autoplayNext) R.string.player_up_next else R.string.player_in_this_list), style = MaterialTheme.typography.titleMedium)
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
            video, stringResource(R.string.player_quality), state.quality, pocket.media.downloads.get(video.id)?.takeIf { it.isDone }?.quality, showAuto = true,
            onPick = { connection.setQuality(it); sheet = null }, onDismiss = { sheet = null },
        )
        Sheet.Speed -> SpeedSheet(state.speed, onPick = { connection.setSpeed(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.Sleep -> SleepSheet(state.sleepAt != null, onPick = { connection.setSleepTimer(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.Moment -> Unit // full screen, above
        null -> Unit
    }
    savedClip?.let { ClipShareDialog(it, justSaved = true, onDismiss = { savedClip = null }) }
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
            IconButton(onClick = connection::previous, enabled = state.hasPrevious) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { connection.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, stringResource(R.string.player_back_10), modifier = Modifier.size(30.dp)) }
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable(onClick = connection::togglePlay),
                contentAlignment = Alignment.Center,
            ) {
                if (state.buffering && state.playWhenReady) CircularProgressIndicator(color = Pocket.Background, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                else Icon(
                    when { state.ended -> Icons.Rounded.Replay; state.isPlaying -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                    stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play), tint = Pocket.Background, modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { connection.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, stringResource(R.string.player_forward_10), modifier = Modifier.size(30.dp)) }
            IconButton(onClick = connection::next, enabled = state.hasNext) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(16.dp))

        if (state.subtitleMissing && settings.subtitleLanguage != null && settings.subtitleLanguage != Settings.SUBTITLES_OFF) {
            Text(
                stringResource(R.string.player_subtitles_missing, pocket.core.languages.name(settings.subtitleLanguage!!)),
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cc = state.subtitleLanguage
            Pill(if (cc != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                if (cc != null) pocket.core.languages.name(cc) else stringResource(R.string.player_subtitles_off), highlighted = cc != null) { onSheet(Sheet.Subtitles) }
            Pill(Icons.Rounded.HighQuality, state.quality ?: stringResource(R.string.player_quality)) { onSheet(Sheet.Quality) }
            if (!state.fromDownload) {
                Pill(Icons.Rounded.Headphones, stringResource(R.string.listen_button), highlighted = state.listening) {
                    connection.setListen(!state.listenByHand)
                }
            }
            val download = marks.download
            when {
                download == null -> Pill(Icons.Rounded.Download, stringResource(R.string.common_download)) {
                    val quality = settings.downloadQuality ?: state.quality ?: video.file(null)?.label
                    if (quality != null) pocket.media.downloads.download(video, quality, settings.languageCode)
                }
                download.status == DownloadStatus.Done -> Pill(Icons.Rounded.CheckCircle, stringResource(R.string.player_pill_downloaded, download.quality), highlighted = true) { confirmRemove = true }
                else -> Pill(Icons.Rounded.Download, stringResource(R.string.player_pill_download_progress, (download.progress * 100).toInt(), formatBytes(download.totalBytes))) { confirmRemove = true }
            }
            Pill(if (marks.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (marks.favorite) R.string.common_saved else R.string.common_favorite), highlighted = marks.favorite) {
                pocket.core.favorites.toggle(video)
            }
            Pill(Icons.Rounded.Speed, if (state.speed == 1f) stringResource(R.string.player_speed) else "${state.speed}×", highlighted = state.speed != 1f) { onSheet(Sheet.Speed) }
            Pill(Icons.Rounded.Bedtime, sleepLabel(state.sleepAt), highlighted = state.sleepAt != null) { onSheet(Sheet.Sleep) }
            Box {
                var shareMenu by remember { mutableStateOf(false) }
                Pill(Icons.Rounded.Share, stringResource(R.string.common_share)) { shareMenu = true }
                DropdownMenu(shareMenu, onDismissRequest = { shareMenu = false }, containerColor = Pocket.SurfaceHigh) {
                    video.shareUrl(settings.languageCode)?.let { url ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_video)) },
                            leadingIcon = { Icon(Icons.Rounded.Link, null) },
                            onClick = {
                                shareMenu = false
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
                                }, null))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_moment)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCut, null) },
                        onClick = { shareMenu = false; onSheet(Sheet.Moment) },
                    )
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
            title = { Text(stringResource(if (done) R.string.player_remove_download_title else R.string.player_cancel_download_title)) },
            text = {
                Text(
                    if (done) stringResource(R.string.player_remove_download_message, formatBytes(download.totalBytes))
                    else stringResource(R.string.player_cancel_download_message)
                )
            },
            confirmButton = {
                TextButton(onClick = { pocket.media.downloads.remove(video.id); confirmRemove = false }) {
                    Text(stringResource(if (done) R.string.player_remove_download_confirm else R.string.common_cancel_download), color = Pocket.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.player_keep_download)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@Composable
private fun sleepLabel(sleepAt: Long?): String = when {
    sleepAt == null -> stringResource(R.string.player_sleep_timer)
    sleepAt < 0 -> stringResource(R.string.player_sleep_after_video)
    else -> ((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(1).toInt().let { pluralStringResource(R.plurals.player_sleep_minutes_left, it, it) }
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
 * Over the video: tap to show the controls, which hide by themselves while
 * playing. In portrait the controls live below the video (with their own
 * skip buttons), so only the full-screen button shows here; full screen has
 * the whole set, and there a double-tap on a side skips 10 seconds.
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
            .pointerInput(fullscreen) {
                detectTapGestures(
                    onTap = { visible = !visible; touch++ },
                    onDoubleTap = if (!fullscreen) null else { offset ->
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
                            Icon(Icons.Rounded.Fullscreen, stringResource(R.string.player_full_screen), tint = Color.White)
                        }
                    }
                }
                return@AnimatedVisibility
            }
            Box(Modifier.fillMaxSize().background(Color(0x77000000))) {
                Row(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_exit_full_screen), tint = Color.White) }
                    Text(title.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { touch++; onSubtitles() }) {
                        Icon(if (state.subtitleLanguage != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, stringResource(R.string.player_subtitles), tint = Color.White)
                    }
                    IconButton(onClick = { touch++; onQuality() }) { Icon(Icons.Rounded.HighQuality, stringResource(R.string.player_quality), tint = Color.White) }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    IconButton(onClick = { touch++; connection.previous() }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = { touch++; connection.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, stringResource(R.string.player_back_10), tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { touch++; connection.togglePlay() }, contentAlignment = Alignment.Center) {
                        if (state.buffering && state.playWhenReady) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play), tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { touch++; connection.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, stringResource(R.string.player_forward_10), tint = Color.White, modifier = Modifier.size(34.dp)) }
                    IconButton(onClick = { touch++; connection.next() }, enabled = state.hasNext) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeekBar(state, onSeek = { touch++; connection.seekTo(it) }, modifier = Modifier.weight(1f), light = true)
                    IconButton(onClick = onFullscreen) { Icon(Icons.Rounded.FullscreenExit, stringResource(R.string.player_exit_full_screen), tint = Color.White) }
                }
            }
        }
    }
}
