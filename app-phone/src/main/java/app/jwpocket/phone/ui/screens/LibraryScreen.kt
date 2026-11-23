package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.media.download.DownloadItem
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.media.download.StorageInfo
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.components.DownloadBadge
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.components.VideoListItem
import app.jwpocket.phone.ui.theme.Pocket

enum class LibraryTab(val label: String) { Downloads("Downloads"), Favorites("Favorites"), History("History") }

/** Downloads, favourites and history, all in one place. */
@Composable
fun LibraryScreen(contentPadding: PaddingValues, initialTab: LibraryTab = LibraryTab.Downloads, onBrowse: () -> Unit) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text(t.label) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pocket.Accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
        val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp, top = 8.dp)
        when (tab) {
            LibraryTab.Downloads -> DownloadsTab(bottom, onBrowse)
            LibraryTab.Favorites -> FavoritesTab(bottom, onBrowse)
            LibraryTab.History -> HistoryTab(bottom, onBrowse)
        }
    }
}

@Composable
private fun DownloadsTab(padding: PaddingValues, onBrowse: () -> Unit) {
    val pocket = LocalPocket.current
    val downloads = pocket.media.downloads
    val items by downloads.items.collectAsState()
    val settings by pocket.core.settings.settings.collectAsState()
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val done = items.values.filter { it.isDone }
    val pending = items.values.filterNot { it.isDone }
    LaunchedEffect(done.size) { storage = downloads.storage() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pocket.Surface).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${done.size} ${if (done.size == 1) "video" else "videos"} · ${formatBytes(storage?.downloadsBytes ?: 0)}", style = MaterialTheme.typography.titleMedium)
                        Text("${formatBytes(storage?.freeBytes ?: 0)} free on this phone", style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
                    }
                    if (items.isNotEmpty()) TextButton(onClick = { confirmDeleteAll = true }) { Text("Delete all", color = Pocket.TextSecondary) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = { on -> pocket.core.settings.update { it.copy(wifiOnlyDownloads = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pocket.Accent),
                    )
                }
            }
        }
        if (items.isEmpty()) item {
            EmptyState(Icons.Rounded.DownloadForOffline, "No downloads yet", "Tap Download on any video to watch it later without internet. Downloaded videos get a green mark everywhere.") {
                TextButton(onClick = onBrowse) { Text("Find videos", color = Pocket.Accent) }
            }
        }
        if (pending.isNotEmpty()) {
            item { Header("In progress") }
            items(pending, key = { "p_" + it.video.id }) { DownloadRow(it) }
        }
        if (done.isNotEmpty()) {
            item { Header("Ready offline") }
            items(done, key = { it.video.id }) { DownloadRow(it) }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This frees ${formatBytes(storage?.downloadsBytes ?: 0)}. You can download videos again later.") },
            confirmButton = { TextButton(onClick = { downloads.removeAll(); confirmDeleteAll = false }) { Text("Delete all", color = Pocket.Accent) } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun DownloadRow(item: DownloadItem) {
    val pocket = LocalPocket.current
    val downloads = pocket.media.downloads
    val actions = LocalVideoActions.current
    val allDone by downloads.items.collectAsState()
    val row = allDone.values.filter { it.isDone }.map { it.video }
    var menu by remember { mutableStateOf(false) }
    Column {
        VideoListItem(
            item.video, row,
            detail = when (item.status) {
                DownloadStatus.Done -> "${formatDuration(item.video.duration)} · ${item.quality} · ${formatBytes(item.bytes)}"
                DownloadStatus.Downloading -> "${(item.progress * 100).toInt()}% of ${formatBytes(item.totalBytes)}"
                DownloadStatus.Queued -> "Waiting to start"
                DownloadStatus.Waiting -> "Waiting for Wi-Fi"
                DownloadStatus.Paused -> "Paused at ${(item.progress * 100).toInt()}%"
                DownloadStatus.Failed -> "Failed. Tap ⋮ to retry"
            },
            onClick = if (item.isDone) ({ actions.play(item.video, row, false) }) else null,
            trailing = {
                Box {
                    IconButton(onClick = { menu = true }) {
                        if (item.isDone) Icon(Icons.Rounded.MoreVert, "More") else DownloadBadge(item, size = 30.dp)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (item.isDone) DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }, onClick = { menu = false; actions.play(item.video, row, false) })
                        if (item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Queued || item.status == DownloadStatus.Waiting)
                            DropdownMenuItem(text = { Text("Pause") }, leadingIcon = { Icon(Icons.Rounded.Pause, null) }, onClick = { menu = false; downloads.pause(item.video.id) })
                        if (item.status == DownloadStatus.Paused)
                            DropdownMenuItem(text = { Text("Resume") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }, onClick = { menu = false; downloads.resume(item.video.id) })
                        if (item.status == DownloadStatus.Failed)
                            DropdownMenuItem(text = { Text("Retry") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; downloads.retry(item.video.id) })
                        DropdownMenuItem(
                            text = { Text(if (item.isDone) "Delete download" else "Cancel download") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; downloads.remove(item.video.id) },
                        )
                    }
                }
            },
        )
        if (!item.isDone && item.status != DownloadStatus.Failed) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp),
                color = Pocket.Accent, trackColor = Pocket.SurfaceHigh,
            )
        }
    }
}

@Composable
private fun FavoritesTab(padding: PaddingValues, onBrowse: () -> Unit) {
    val pocket = LocalPocket.current
    val favorites by pocket.core.favorites.favorites.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (favorites.isEmpty()) item {
            EmptyState(Icons.Rounded.FavoriteBorder, "No favorites yet", "Tap the heart on a video to keep it here.") {
                TextButton(onClick = onBrowse) { Text("Find videos", color = Pocket.Accent) }
            }
        }
        items(favorites, key = { it.id }) { VideoListItem(it, favorites) }
    }
}

@Composable
private fun HistoryTab(padding: PaddingValues, onBrowse: () -> Unit) {
    val pocket = LocalPocket.current
    val history by remember { pocket.core.progress.history }.collectAsState(emptyList())
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (history.isEmpty()) item {
            EmptyState(Icons.Rounded.History, "Nothing watched yet", "Videos you start appear here, so you can pick up where you left off.") {
                TextButton(onClick = onBrowse) { Text("Find videos", color = Pocket.Accent) }
            }
        } else item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirm = true }) { Text("Clear history", color = Pocket.TextSecondary) }
            }
        }
        val row = history.map { it.video }
        items(history, key = { it.video.id }) { w ->
            VideoListItem(w.video, row, detail = if (w.finished) "Watched" else formatTimeLeft(w.remainingMs))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear history?") },
            text = { Text("Resume points and watched marks are removed.") },
            confirmButton = { TextButton(onClick = { pocket.core.progress.clearHistory(); confirm = false }) { Text("Clear", color = Pocket.Accent) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}
