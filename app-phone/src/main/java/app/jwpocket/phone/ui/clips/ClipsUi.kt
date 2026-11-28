package app.jwpocket.phone.ui.clips

import android.annotation.SuppressLint
import java.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.jwpocket.core.util.formatClock
import app.jwpocket.phone.R
import app.jwpocket.phone.clips.Clip
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.theme.Pocket
import coil.compose.AsyncImage

/**
 * Before a clip leaves the phone: a reminder that it's for personal sharing
 * only, and an optional link to the whole video (off unless ticked).
 */
@Composable
fun ClipShareDialog(clip: Clip, justSaved: Boolean, onDismiss: () -> Unit) {
    val pocket = LocalPocket.current
    val context = LocalContext.current
    var withLink by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clip_share_title)) },
        text = {
            Column {
                if (justSaved) {
                    Text(stringResource(R.string.clip_saved), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Pocket.SurfaceHighest).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Info, null, tint = Pocket.Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.clip_share_warning), style = MaterialTheme.typography.bodySmall)
                }
                if (clip.videoUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { withLink = !withLink },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(withLink, onCheckedChange = { withLink = it }, colors = CheckboxDefaults.colors(checkedColor = Pocket.Accent))
                        Text(stringResource(R.string.clip_share_link), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !preparing,
                onClick = {
                    preparing = true
                    scope.launch {
                        context.startActivity(pocket.clips.shareIntent(clip, withLink))
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.clip_share_action), color = Pocket.Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        containerColor = Pocket.SurfaceHigh,
    )
}

/** Plays a saved clip full screen, on its own player; the main video pauses meanwhile. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun ClipPlayerDialog(clip: Clip, onDismiss: () -> Unit) {
    val pocket = LocalPocket.current
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(pocket.clips.video(clip).toURI().toString()))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        pocket.media.player.pause()
        onDispose { player.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; setShowSubtitleButton(false) } },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = Color.White)
            }
        }
    }
}

/** Library › Clips: the clips made with "Share a moment", newest first. */
@Composable
fun ClipsList(padding: PaddingValues) {
    val pocket = LocalPocket.current
    val clips by pocket.clips.clips.collectAsState()
    var playing by remember { mutableStateOf<Clip?>(null) }
    var sharing by remember { mutableStateOf<Clip?>(null) }
    var deleting by remember { mutableStateOf<Clip?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (clips.isEmpty()) item {
            EmptyState(Icons.Rounded.ContentCut, stringResource(R.string.clips_empty_title), stringResource(R.string.clips_empty_body))
        }
        items(clips, key = { it.id }) { clip -> ClipRow(clip, onPlay = { playing = clip }, onShare = { sharing = clip }, onDelete = { deleting = clip }) }
    }
    playing?.let { ClipPlayerDialog(it, onDismiss = { playing = null }) }
    sharing?.let { ClipShareDialog(it, justSaved = false, onDismiss = { sharing = null }) }
    deleting?.let { clip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.clip_delete_title)) },
            text = { Text(clip.title) },
            confirmButton = { TextButton(onClick = { pocket.clips.delete(clip); deleting = null }) { Text(stringResource(R.string.clip_delete_confirm), color = Pocket.Accent) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

/** Library overview: saved clips side by side; a tap plays one. */
@Composable
fun ClipsRow(clips: List<Clip>) {
    var playing by remember { mutableStateOf<Clip?>(null) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(clips, key = { it.id }) { clip ->
            Column(Modifier.width(168.dp).clip(RoundedCornerShape(10.dp)).clickable { playing = clip }) {
                ClipThumbnail(clip, Modifier.fillMaxWidth(), corner = 10.dp)
                Spacer(Modifier.height(8.dp))
                Text(clip.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
    }
    playing?.let { ClipPlayerDialog(it, onDismiss = { playing = null }) }
}

/** 16:9 picture of a clip with its length. */
@Composable
private fun ClipThumbnail(clip: Clip, modifier: Modifier, corner: Dp) {
    val pocket = LocalPocket.current
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(corner)).background(Pocket.SurfaceHigh)) {
        AsyncImage(model = pocket.clips.thumbnail(clip), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Text(
            formatClock(clip.durationMs),
            style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ClipRow(clip: Clip, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipThumbnail(clip, Modifier.width(112.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(clip.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val locale = LocalConfiguration.current.locales[0]
            Text(
                "${formatClock(clip.startMs)} – ${formatClock(clip.endMs)} · " +
                    remember(clip.createdAt, locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(clip.createdAt) },
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, maxLines = 1,
            )
        }
        IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.common_share), tint = Pocket.TextSecondary) }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.clip_delete), tint = Pocket.TextSecondary) }
    }
}
