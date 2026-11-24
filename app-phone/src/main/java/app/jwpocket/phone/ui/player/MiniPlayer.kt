package app.jwpocket.phone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.media.PlaybackState
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.components.ProgressLine
import app.jwpocket.phone.ui.rememberMarks
import app.jwpocket.phone.ui.theme.Pocket

/** The minimized player above the tab bar: the video keeps playing in it. */
@Composable
fun MiniPlayer(state: PlaybackState, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val pocket = LocalPocket.current
    val connection = pocket.media.player
    val controller by connection.controller.collectAsState()
    val video = state.video ?: return
    val marks by rememberMarks(video.id)
    // A download removed while it plays keeps playing, streamed.
    val fromDownload = state.fromDownload && marks.download?.status == DownloadStatus.Done
    Column(
        modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Pocket.SurfaceHigh)
            .clickable(onClick = onExpand)
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Color.Black)) {
                if (!video.isAudio) VideoSurface(controller, Modifier.fillMaxHeight().fillMaxWidth(), subtitles = false)
                // Brought back after a restart and not played yet: no picture until play, so its cover.
                if (!video.isAudio && state.idle) AsyncImage(video.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (state.listening || video.isAudio) ListenVisual(video, state.playWhenReady && !state.ended, cue = null, Modifier.fillMaxSize(), compact = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(when {
                        state.error != null -> R.string.player_status_error
                        state.buffering && state.playWhenReady -> R.string.player_status_loading
                        state.isPlaying -> if (fromDownload) R.string.player_status_playing_offline else R.string.player_status_playing
                        state.ended -> R.string.player_status_finished
                        else -> R.string.player_status_paused
                    }),
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, maxLines = 1,
                )
            }
            IconButton(onClick = connection::togglePlay) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play))
            }
            if (state.hasNext) IconButton(onClick = connection::next) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next)) }
            IconButton(onClick = connection::stop) { Icon(Icons.Rounded.Close, stringResource(R.string.player_close)) }
        }
        ProgressLine(state.progress, height = 2.dp)
    }
}
