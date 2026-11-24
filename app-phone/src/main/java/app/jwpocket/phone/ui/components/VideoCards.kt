package app.jwpocket.phone.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.media.download.DownloadItem
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.rememberMarks
import app.jwpocket.phone.ui.theme.Pocket
import coil.compose.AsyncImage

/** 16:9 thumbnail with duration, progress and download marks. */
@Composable
fun Thumbnail(
    video: Video,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
    corner: Dp = 10.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val marks by rememberMarks(video.id)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(Pocket.SurfaceHigh)
    ) {
        // Bible chapters (and audio without artwork) have no picture: an icon instead.
        if (video.thumbnailUrl == null && video.isAudio) {
            Icon(
                if (video.isBible) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.MusicNote, null,
                tint = Pocket.TextMuted, modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        } else AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (showDuration && video.duration != null) {
            Text(
                formatDuration(video.duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Pocket.Scrim, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        marks.download?.let { DownloadBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        val watch = marks.watch
        if (watch != null && watch.finished && marks.download == null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Pocket.Scrim, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, stringResource(R.string.common_watched), tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        if (watch != null && watch.canResume) {
            ProgressLine(watch.fraction, Modifier.align(Alignment.BottomCenter))
        }
        overlay()
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.fillMaxWidth().height(height).background(Color(0x66FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction).height(height).background(Pocket.Accent))
    }
}

/** Small round mark: downloaded (check), downloading (ring), paused, waiting or failed. */
@Composable
fun DownloadBadge(item: DownloadItem, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(modifier.size(size).background(Pocket.Scrim, CircleShape), contentAlignment = Alignment.Center) {
        when (item.status) {
            DownloadStatus.Done -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.common_downloaded), tint = Pocket.Success, modifier = Modifier.size(size - 4.dp))
            DownloadStatus.Downloading, DownloadStatus.Queued -> CircularProgressIndicator(
                progress = { item.progress.coerceAtLeast(0.03f) },
                modifier = Modifier.size(size - 6.dp),
                strokeWidth = 2.5.dp,
                color = Color.White,
                trackColor = Color(0x44FFFFFF),
            )
            DownloadStatus.Waiting -> Icon(Icons.Rounded.Schedule, stringResource(R.string.common_waiting_for_wifi), tint = Color.White, modifier = Modifier.size(size - 8.dp))
            DownloadStatus.Paused -> Icon(Icons.Rounded.Pause, stringResource(R.string.common_paused), tint = Color.White, modifier = Modifier.size(size - 8.dp))
            DownloadStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, stringResource(R.string.common_download_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(size - 6.dp))
        }
    }
}

/** Grid/row card: thumbnail and two lines of title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(video: Video, row: List<Video>, modifier: Modifier = Modifier, width: Dp? = 168.dp) {
    val actions = LocalVideoActions.current
    Column(
        (if (width != null) modifier.width(width) else modifier)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { actions.open(video, row) },
                onLongClick = { actions.play(video, row, false) },
            )
    ) {
        Thumbnail(video, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Continue Watching card: bigger, with time left and a play button. */
@Composable
fun ContinueCard(video: Video, remainingMs: Long, row: List<Video>, modifier: Modifier = Modifier) {
    val actions = LocalVideoActions.current
    Column(modifier.width(260.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.play(video, row, false) }) {
        Thumbnail(video, Modifier.fillMaxWidth(), showDuration = false, corner = 12.dp) {
            Box(
                Modifier.align(Alignment.Center).size(44.dp).background(Pocket.Scrim, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.common_resume), tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatTimeLeft(LocalContext.current, remainingMs), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
    }
}

/** List row: thumbnail left, title and a detail line right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: Video,
    row: List<Video>,
    modifier: Modifier = Modifier,
    detail: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val actions = LocalVideoActions.current
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick ?: { actions.open(video, row) },
                onLongClick = { actions.play(video, row, false) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(video, Modifier.width(132.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = detail ?: formatDuration(video.duration)
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(line, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onSeeAll != null) Modifier.clickable(onClick = onSeeAll) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onSeeAll != null) {
            Text(stringResource(R.string.common_see_all), style = MaterialTheme.typography.labelLarge, color = Pocket.TextSecondary)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Pocket.TextSecondary)
        }
    }
}

/** A titled horizontal row of video cards. */
@Composable
fun VideoRow(title: String, videos: List<Video>, onSeeAll: (() -> Unit)? = null) {
    SectionHeader(title, onSeeAll = onSeeAll)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos, key = { it.id }, contentType = { "video" }) { VideoCard(it, videos) }
    }
}

/** Grey blocks shown while a row loads. */
@Composable
fun PlaceholderRow() {
    Column {
        Box(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp).width(140.dp).height(20.dp).clip(RoundedCornerShape(6.dp)).background(Pocket.SurfaceHigh))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Pocket.Surface))
            }
        }
    }
}

/** Fades the bottom of a picture into the background. */
val BottomFade = Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0x66000000), 1f to Pocket.Background)

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(Pocket.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Pocket.TextSecondary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Pocket.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Download icon for list rows: tap target lives with the caller. */
@Composable
fun DownloadStateIcon(item: DownloadItem?) {
    if (item == null) Icon(Icons.Rounded.CloudDownload, stringResource(R.string.common_download), tint = Pocket.TextSecondary)
    else DownloadBadge(item, size = 28.dp)
}
