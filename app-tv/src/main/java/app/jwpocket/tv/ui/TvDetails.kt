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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        Column(Modifier.padding(start = 64.dp, top = 56.dp, end = 64.dp).width(620.dp)) {
            Text(video.title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text(
                listOf(formatDuration(video.duration), qualityBadge(video.maxHeight), video.publishedDate.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                color = Tv.TextSecondary,
            )
            if (watch != null && watch.canResume) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.width(420.dp).height(4.dp).background(Color(0x44FFFFFF))) {
                    Box(Modifier.fillMaxWidth(watch.fraction).height(4.dp).background(Tv.Accent))
                }
                Spacer(Modifier.height(6.dp))
                Text("Stopped at ${formatClock(watch.positionMs)} · ${formatTimeLeft(watch.remainingMs)}", color = Tv.TextSecondary)
            }
            if (download != null) {
                Spacer(Modifier.height(12.dp))
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
                Text(it, color = Tv.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(Icons.Rounded.PlayArrow, if (watch?.canResume == true) "Resume" else "Play", Modifier.focusRequester(focus), primary = true) { onPlay(false, quality) }
                if (watch?.canResume == true) TvButton(Icons.Rounded.Replay, "Start over") { onPlay(true, quality) }
                if (download?.isDone != true) TvButton(Icons.Rounded.HighQuality, quality ?: settings.streamQuality ?: "Auto", Modifier.focusRequester(qualityButton)) {
                    returnTo = qualityButton
                    panel = DetailsPanel.PlayQuality
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    download == null -> TvButton(Icons.Rounded.Download, "Download", Modifier.focusRequester(downloadButton)) {
                        val q = settings.downloadQuality?.takeIf { it in video.qualities }
                        if (q != null) downloads.download(video, q, settings.languageCode) else { returnTo = downloadButton; panel = DetailsPanel.DownloadQuality }
                    }
                    download.status == DownloadStatus.Done -> TvButton(Icons.Rounded.Delete, "Delete download", Modifier.focusRequester(downloadButton)) { downloads.remove(video.id) }
                    download.status == DownloadStatus.Failed -> TvButton(Icons.Rounded.Refresh, "Retry download", Modifier.focusRequester(downloadButton)) { downloads.retry(video.id) }
                    download.status == DownloadStatus.Paused -> TvButton(Icons.Rounded.Download, "Resume download", Modifier.focusRequester(downloadButton)) { downloads.resume(video.id) }
                    else -> {
                        TvButton(Icons.Rounded.Pause, "Pause download", Modifier.focusRequester(downloadButton)) { downloads.pause(video.id) }
                        TvButton(Icons.Rounded.Close, "Cancel") { downloads.remove(video.id) }
                    }
                }
                TvButton(if (marks.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (marks.favorite) "In favorites" else "Add to favorites") {
                    tv.core.favorites.toggle(video)
                }
                if (watch != null) TvButton(Icons.Rounded.CheckCircle, if (watch.canResume) "Remove from Continue watching" else "Remove from History") {
                    tv.core.progress.remove(video.id)
                }
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
