package app.jwpocket.phone.ui.details

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatClock
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.core.util.qualityBadge
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.components.ProgressLine
import app.jwpocket.phone.ui.player.QualitySheet
import app.jwpocket.phone.ui.rememberMarks
import app.jwpocket.phone.ui.theme.Pocket
import coil.compose.AsyncImage

/** Everything about one video, and what can be done with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(video: Video, onPlay: (fromStart: Boolean) -> Unit, onDismiss: () -> Unit) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val marks by rememberMarks(video.id)
    val context = LocalContext.current
    var chooseQuality by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val watch = marks.watch
    val download = marks.download

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pocket.Surface,
        dragHandle = null,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(video.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(BottomFadeSurface))
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), modifier = Modifier.background(Pocket.Scrim, RoundedCornerShape(50)).padding(4.dp))
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(video.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        formatDuration(video.duration).ifEmpty { null },
                        qualityBadge(video.maxHeight).ifEmpty { null },
                        video.publishedDate,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = Pocket.TextSecondary,
                )

                if (watch != null && watch.canResume) {
                    Spacer(Modifier.height(14.dp))
                    ProgressLine(watch.fraction, Modifier.clip(RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.details_stopped_at, formatClock(watch.positionMs), formatTimeLeft(context, watch.remainingMs)), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
                } else if (watch?.finished == true) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.common_watched), style = MaterialTheme.typography.bodySmall, color = Pocket.Success)
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onPlay(false) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pocket.Accent),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (watch?.canResume == true) R.string.common_resume else R.string.common_play), style = MaterialTheme.typography.titleMedium)
                }
                if (watch?.canResume == true) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onPlay(true) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.Replay, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.details_start_over))
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val downloads = pocket.media.downloads
                    when {
                        download == null -> Action(Icons.Rounded.Download, stringResource(R.string.common_download)) {
                            val q = settings.downloadQuality?.takeIf { it in video.qualities }
                            if (q != null) downloads.download(video, q, settings.languageCode) else chooseQuality = true
                        }
                        download.status == DownloadStatus.Done -> Action(Icons.Rounded.CheckCircle, stringResource(R.string.common_downloaded), tint = Pocket.Success) {}
                        download.status == DownloadStatus.Failed -> Action(Icons.Rounded.Refresh, stringResource(R.string.common_retry)) { downloads.retry(video.id) }
                        download.status == DownloadStatus.Paused -> Action(Icons.Rounded.Download, stringResource(R.string.common_resume)) { downloads.resume(video.id) }
                        else -> Action(Icons.Rounded.Pause, stringResource(R.string.common_pause)) { downloads.pause(video.id) }
                    }
                    Action(if (marks.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (marks.favorite) R.string.common_saved else R.string.common_favorite),
                        tint = if (marks.favorite) Pocket.Accent else null) { pocket.core.favorites.toggle(video) }
                    video.shareUrl(settings.languageCode)?.let { url ->
                        Action(Icons.Rounded.Share, stringResource(R.string.common_share)) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
                            }, null))
                        }
                    }
                }

                if (download != null) {
                    Spacer(Modifier.height(16.dp))
                    DownloadPanel(video, onRemove = { pocket.media.downloads.remove(video.id) })
                }

                if (watch != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (watch.canResume) R.string.details_remove_continue_watching else R.string.details_remove_history),
                        style = MaterialTheme.typography.labelLarge, color = Pocket.TextSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { pocket.core.progress.remove(video.id) }.padding(vertical = 10.dp),
                    )
                }

                val description = video.description?.takeIf { it.isNotBlank() }
                if (description != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pocket.TextSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (chooseQuality) {
        QualitySheet(video, stringResource(R.string.details_download_quality), null, null, showAuto = false, onPick = { q ->
            if (q != null) pocket.media.downloads.download(video, q, settings.languageCode)
            chooseQuality = false
        }, onDismiss = { chooseQuality = false })
    }
}

private val BottomFadeSurface = androidx.compose.ui.graphics.Brush.verticalGradient(
    0.5f to androidx.compose.ui.graphics.Color.Transparent, 1f to Pocket.Surface,
)

/** Progress or size of the download, with delete. */
@Composable
private fun DownloadPanel(video: Video, onRemove: () -> Unit) {
    val marks by rememberMarks(video.id)
    val item = marks.download ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pocket.SurfaceHigh).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val label = when (item.status) {
                DownloadStatus.Done -> stringResource(R.string.details_download_done, item.quality, formatBytes(item.bytes))
                DownloadStatus.Downloading -> stringResource(R.string.details_download_progress, item.quality, (item.progress * 100).toInt(), formatBytes(item.totalBytes))
                DownloadStatus.Queued -> stringResource(R.string.details_download_queued, item.quality)
                DownloadStatus.Waiting -> stringResource(R.string.details_download_waiting_wifi, item.quality)
                DownloadStatus.Paused -> stringResource(R.string.details_download_paused, (item.progress * 100).toInt())
                DownloadStatus.Failed -> stringResource(R.string.details_download_failed)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.status == DownloadStatus.Failed) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            if (item.status != DownloadStatus.Done) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)), color = Pocket.Accent, trackColor = Pocket.SurfaceHighest)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, stringResource(if (item.isDone) R.string.common_delete_download else R.string.common_cancel_download), tint = Pocket.TextSecondary) }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Pocket.TextSecondary)
    }
}
