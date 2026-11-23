package app.jwpocket.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import app.jwpocket.core.CoreGraph
import app.jwpocket.core.data.WatchState
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.media.MediaGraph
import app.jwpocket.media.download.DownloadItem
import app.jwpocket.media.download.DownloadStatus
import coil.compose.AsyncImage

object Tv {
    val Background = Color(0xFF0B0C10)
    val Surface = Color(0xFF15171D)
    val SurfaceHigh = Color(0xFF1F222A)
    val Accent = Color(0xFFFF3D5A)
    val Success = Color(0xFF3DDC97)
    val TextSecondary = Color(0xFFA3A8B4)
    val Scrim = Color(0xCC000000)
}

val TvColors = darkColorScheme(
    primary = Tv.Accent,
    onPrimary = Color.White,
    background = Tv.Background,
    surface = Tv.Surface,
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Tv.SurfaceHigh,
    onSurfaceVariant = Tv.TextSecondary,
    border = Color(0xFF3A3F4B),
)

@Stable
class TvPocket(val core: CoreGraph, val media: MediaGraph)

val LocalTv = staticCompositionLocalOf<TvPocket> { error("not provided") }

@Stable
class TvMarks(
    val progress: State<Map<String, WatchState>>,
    val downloads: State<Map<String, DownloadItem>>,
    val favorites: State<Set<String>>,
)

val LocalTvMarks = staticCompositionLocalOf<TvMarks> { error("not provided") }

/** Opens a video's details; [row] is the list it came from, for autoplay. */
val LocalOpenVideo = staticCompositionLocalOf<(Video, List<Video>) -> Unit> { { _, _ -> } }

data class CardMarks(val watch: WatchState?, val download: DownloadItem?, val favorite: Boolean)

@Composable
fun rememberCardMarks(id: String): State<CardMarks> {
    val marks = LocalTvMarks.current
    return remember(id, marks) { derivedStateOf { CardMarks(marks.progress.value[id], marks.downloads.value[id], id in marks.favorites.value) } }
}

/** A focusable surface that grows and gets a white outline when the remote lands on it. */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    scale: Float = 1.06f,
    color: Color = Tv.Surface,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(corner)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = scale),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = Tv.SurfaceHigh),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(corner))),
        content = content,
    )
}

/** Video card for rows: picture with marks, title under it. */
@Composable
fun TvVideoCard(video: Video, row: List<Video>, width: Dp? = 240.dp, subtitle: String? = null) {
    val open = LocalOpenVideo.current
    val marks by rememberCardMarks(video.id)
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        FocusSurface(onClick = { open(video, row) }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(video.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (video.duration != null) {
                Text(
                    formatDuration(video.duration), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Tv.Scrim, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            marks.download?.let { TvDownloadBadge(it, Modifier.align(Alignment.TopEnd).padding(8.dp)) }
            val watch = marks.watch
            if (watch?.finished == true && marks.download == null) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Tv.Scrim, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Watched", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            if (watch != null && watch.canResume) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxWidth(watch.fraction).height(4.dp).background(Tv.Accent))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = subtitle ?: marks.watch?.takeIf { it.canResume }?.let { formatTimeLeft(it.remainingMs) }
        if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = Tv.TextSecondary, maxLines = 1)
    }
}

@Composable
fun TvDownloadBadge(item: DownloadItem, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Tv.Scrim, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint, label) = when (item.status) {
            DownloadStatus.Done -> Triple(Icons.Rounded.CheckCircle, Tv.Success, "Downloaded")
            DownloadStatus.Downloading, DownloadStatus.Queued -> Triple(null, Color.White, "${(item.progress * 100).toInt()}%")
            DownloadStatus.Waiting -> Triple(Icons.Rounded.Schedule, Color.White, "Waiting")
            DownloadStatus.Paused -> Triple(Icons.Rounded.Pause, Color.White, "Paused")
            DownloadStatus.Failed -> Triple(Icons.Rounded.ErrorOutline, Color(0xFFFF6B6B), "Failed")
        }
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** A titled row of cards. */
@Composable
fun TvRow(title: String, videos: List<Video>, cardWidth: Dp = 240.dp) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(videos, key = { it.id }) { TvVideoCard(it, videos, cardWidth) }
        }
    }
}

/** Focusable button with an icon, for dialogs and the details screen. */
@Composable
fun TvButton(icon: ImageVector?, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, corner = 10.dp, scale = 1.04f, color = if (primary) Tv.Accent else Tv.SurfaceHigh) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Grey card shapes shown while a row loads. */
@Composable
fun TvPlaceholderRow() {
    Column(Modifier.padding(start = 48.dp, top = 24.dp)) {
        Box(Modifier.width(220.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(Tv.Surface))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(5) { Box(Modifier.width(240.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Tv.Surface)) }
        }
    }
}
