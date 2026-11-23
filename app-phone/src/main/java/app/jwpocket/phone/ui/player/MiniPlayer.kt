package app.jwpocket.phone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.media.PlaybackState
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.components.ProgressLine
import app.jwpocket.phone.ui.theme.Pocket

/** The minimized player above the tab bar: the video keeps playing in it. */
@Composable
fun MiniPlayer(state: PlaybackState, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val pocket = LocalPocket.current
    val connection = pocket.media.player
    val controller by connection.controller.collectAsState()
    val video = state.video ?: return
    Column(
        modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Pocket.SurfaceHigh)
            .clickable(onClick = onExpand)
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Color.Black)) {
                VideoSurface(controller, Modifier.fillMaxHeight().fillMaxWidth(), subtitles = false)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        state.error != null -> "Can't play"
                        state.buffering && state.playWhenReady -> "Loading…"
                        state.isPlaying -> if (state.fromDownload) "Playing offline" else "Playing"
                        state.ended -> "Finished"
                        else -> "Paused"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, maxLines = 1,
                )
            }
            IconButton(onClick = connection::togglePlay) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play")
            }
            if (state.hasNext) IconButton(onClick = connection::next) { Icon(Icons.Rounded.SkipNext, "Next") }
            IconButton(onClick = connection::stop) { Icon(Icons.Rounded.Close, "Close player") }
        }
        ProgressLine(state.progress, height = 2.dp)
    }
}
