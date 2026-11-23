package app.jwpocket.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatClock
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.core.util.qualityBadge
import app.jwpocket.media.download.DownloadStatus
import coil.compose.AsyncImage

private enum class DetailsPanel { PlayQuality, DownloadQuality }

/**
 * Full-screen details: play, resume or start over, quality, download, favourite.
 * [active] is false while the player covers it; focus returns to Play when it's back.
 */
@Composable
fun TvDetails(video: Video, active: Boolean, onPlay: (fromStart: Boolean, quality: String?) -> Unit, onClose: () -> Unit) {
    val tv = LocalTv.current
    val marks by rememberCardMarks(video.id)
    val settings by tv.core.settings.settings.collectAsState()
    var panel by remember { mutableStateOf<DetailsPanel?>(null) }
    var quality by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val qualityButton = remember { FocusRequester() }
    // First button of the download row: whichever one the download's state shows.
    val downloadButton = remember { FocusRequester() }
    // Where focus goes when the details get it back (after a panel); Play otherwise.
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    // A button that disappears when pressed (Download becomes Pause, …) would leave nothing focused.
    var hadFocus by remember { mutableStateOf(false) }
    var lostFocus by remember { mutableIntStateOf(0) }
    val watch = marks.watch
    val download = marks.download
    val downloads = tv.media.downloads

    BackHandler(enabled = panel == null, onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Tv.Background)
            .onFocusChanged { if (it.hasFocus) hadFocus = true else if (hadFocus && active && panel == null) lostFocus++ }
            .focusTrap()
    ) {
        AsyncImage(video.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Tv.Background, 0.5f to Color(0xEE0B0C10), 1f to Color(0x330B0C10))))
        Column(Modifier.padding(start = 64.dp, top = 64.dp, end = 64.dp).width(680.dp)) {
            Text(video.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text(
                listOf(formatDuration(video.duration), qualityBadge(video.maxHeight), video.publishedDate.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                color = Tv.TextSecondary,
            )
            if (download != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    when (download.status) {
                        DownloadStatus.Done -> "Downloaded · ${download.quality} · ${formatBytes(download.bytes)} · plays without internet"
                        DownloadStatus.Downloading, DownloadStatus.Queued -> "Downloading ${download.quality} · ${(download.progress * 100).toInt()}% of ${formatBytes(download.totalBytes)}"
                        DownloadStatus.Waiting -> "Waiting for Wi-Fi to download"
                        DownloadStatus.Paused -> "Download paused at ${(download.progress * 100).toInt()}%"
                        DownloadStatus.Failed -> "Download failed"
                    },
                    color = if (download.isDone) Tv.Success else Tv.TextSecondary,
                )
            }
            video.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Tv.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (watch != null && watch.canResume) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(240.dp).height(4.dp).background(Color(0x44FFFFFF))) {
                        Box(Modifier.fillMaxWidth(watch.fraction).height(4.dp).background(Tv.Accent))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("${formatClock(watch.positionMs)} · ${formatTimeLeft(watch.remainingMs)}", color = Tv.TextSecondary, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            // One row: Play (or Resume) on the left, then the same round button for every other action.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                PlayButton(if (watch?.canResume == true) "Resume" else "Play", Modifier.focusRequester(focus)) { onPlay(false, quality) }
                Spacer(Modifier.width(12.dp))
                if (watch?.canResume == true) DetailAction(Icons.Rounded.Replay, "Start over") { onPlay(true, quality) }
                if (download?.isDone != true) DetailAction(Icons.Rounded.HighQuality, quality ?: settings.streamQuality ?: "Auto", Modifier.focusRequester(qualityButton)) {
                    returnTo = qualityButton
                    panel = DetailsPanel.PlayQuality
                }
                val inDownloadRow = Modifier.focusRequester(downloadButton)
                when {
                    download == null -> DetailAction(Icons.Rounded.Download, "Download", inDownloadRow) {
                        val q = settings.downloadQuality?.takeIf { it in video.qualities }
                        if (q != null) downloads.download(video, q, settings.languageCode) else { returnTo = downloadButton; panel = DetailsPanel.DownloadQuality }
                    }
                    download.status == DownloadStatus.Done -> DetailAction(Icons.Rounded.Delete, "Delete download", inDownloadRow) { downloads.remove(video.id) }
                    download.status == DownloadStatus.Failed -> DetailAction(Icons.Rounded.Refresh, "Retry download", inDownloadRow) { downloads.retry(video.id) }
                    download.status == DownloadStatus.Paused -> DetailAction(Icons.Rounded.Download, "Resume download", inDownloadRow) { downloads.resume(video.id) }
                    else -> {
                        DetailAction(Icons.Rounded.Pause, "Pause download", inDownloadRow) { downloads.pause(video.id) }
                        DetailAction(Icons.Rounded.Close, "Cancel download") { downloads.remove(video.id) }
                    }
                }
                DetailAction(
                    if (marks.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (marks.favorite) "In favorites" else "Favorite",
                    tint = if (marks.favorite) Tv.Accent else Color.White,
                ) { tv.core.favorites.toggle(video) }
                if (watch != null) DetailAction(Icons.Rounded.RemoveDone, "Mark unwatched") { tv.core.progress.remove(video.id) }
            }
        }
    }

    when (panel) {
        DetailsPanel.PlayQuality -> TvSidePanel(
            "Quality",
            options = listOf(PanelOption(null, "Auto", "Up to 720p", quality == null)) +
                video.qualities.reversed().map { PanelOption(it, it, video.file(it)?.filesize?.let(::formatBytes), quality == it) },
            onPick = { quality = it.key; panel = null },
            onDismiss = { panel = null },
        )
        DetailsPanel.DownloadQuality -> TvSidePanel(
            "Download quality", "Smaller qualities save space.",
            options = video.qualities.reversed().map { PanelOption(it, it, video.file(it)?.filesize?.let(::formatBytes)) },
            onPick = { o -> o.key?.let { downloads.download(video, it, settings.languageCode) }; panel = null },
            onDismiss = { panel = null },
        )
        null -> Unit
    }
    LaunchedEffect(video.id, active, panel) {
        if (active && panel == null) {
            runCatching { (returnTo ?: focus).requestFocus() }.onFailure { runCatching { focus.requestFocus() } }
            returnTo = null
        }
    }
    LaunchedEffect(lostFocus) {
        if (lostFocus == 0) return@LaunchedEffect
        withFrameNanos { } // let the row settle into its new buttons
        runCatching { downloadButton.requestFocus() }.onFailure { runCatching { focus.requestFocus() } }
    }
}

/** The main action: a wide pill the same height as the round actions beside it. */
@Composable
private fun PlayButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.height(ActionSize), corner = ActionSize / 2, scale = 1.06f, color = Tv.Accent) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

/** A round action button with its name under it; every secondary action looks the same. */
@Composable
private fun DetailAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(onClick = onClick, modifier = modifier.size(ActionSize), corner = ActionSize / 2, scale = 1.1f, color = Tv.SurfaceHigh) {
            Icon(icon, label, tint = tint, modifier = Modifier.align(Alignment.Center).size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Tv.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val ActionSize = 56.dp
