package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Load
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.dataOrNull
import app.jwpocket.core.model.Section
import app.jwpocket.core.model.Video
import app.jwpocket.core.presentation.CategoryViewModel
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatDuration
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.components.PlaceholderRow
import app.jwpocket.phone.ui.components.SectionHeader
import app.jwpocket.phone.ui.pocketViewModel
import app.jwpocket.phone.ui.theme.Pocket
import coil.compose.AsyncImage

/** Browse › Audio: the music collections as albums, then audio dramas and dramatic Bible readings. */
@Composable
fun AudioBrowse(contentPadding: PaddingValues, onOpenAlbum: (sectionKey: String) -> Unit) {
    val music = pocketViewModel(key = "category_${LibraryRepository.AUDIO}") { CategoryViewModel(it, LibraryRepository.AUDIO) }
    val dramas = pocketViewModel(key = "category_${LibraryRepository.AUDIO_DRAMAS}") { CategoryViewModel(it, LibraryRepository.AUDIO_DRAMAS) }
    val readings = pocketViewModel(key = "category_${LibraryRepository.AUDIO_BIBLE_READINGS}") { CategoryViewModel(it, LibraryRepository.AUDIO_BIBLE_READINGS) }
    val musicContent by music.content.collectAsState()
    val dramaContent by dramas.content.collectAsState()
    val readingContent by readings.content.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
        item {
            SectionHeader(stringResource(R.string.audio_music))
            when (val state = musicContent) {
                is Load.Ready -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.data.sections, key = { it.key }) { section -> AlbumTile(section) { onOpenAlbum(section.key) } }
                }
                is Load.Failed -> EmptyState(Icons.Rounded.CloudOff, stringResource(R.string.browse_error_category), state.message)
                Load.Loading -> PlaceholderRow()
            }
        }
        listOf(dramaContent, readingContent).forEach { content ->
            when (val state = content) {
                is Load.Ready -> state.data.sections.forEach { section ->
                    item(key = section.key) {
                        SectionHeader(state.data.category.name)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(section.videos, key = { it.id }) { AudioCard(it, section.videos) }
                        }
                    }
                }
                is Load.Failed -> Unit
                Load.Loading -> item { PlaceholderRow() }
            }
        }
    }
}

@Composable
private fun AlbumTile(section: Section, onClick: () -> Unit) {
    Column(Modifier.width(124.dp).clickable(onClick = onClick)) {
        Cover(section.imageUrl ?: section.videos.firstOrNull()?.thumbnailUrl, 124.dp)
        Text(section.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(pluralStringResource(R.plurals.album_songs, section.videos.size, section.videos.size), style = MaterialTheme.typography.labelSmall, color = Pocket.TextMuted)
    }
}

/** A drama or reading: tap to play it, the rest of its row follows. */
@Composable
private fun AudioCard(video: Video, row: List<Video>) {
    val actions = LocalVideoActions.current
    Column(Modifier.width(140.dp).clickable { actions.play(video, row, false) }) {
        Cover(video.posterUrl, 140.dp)
        Text(video.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(formatDuration(video.duration), style = MaterialTheme.typography.labelSmall, color = Pocket.TextMuted)
    }
}

@Composable
private fun Cover(url: String?, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Pocket.SurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = Pocket.TextMuted)
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** A songbook: Play all, Shuffle, Download all, and the songs, found by number or title. */
@Composable
fun AlbumScreen(sectionKey: String, contentPadding: PaddingValues) {
    val pocket = LocalPocket.current
    val actions = LocalVideoActions.current
    val vm = pocketViewModel(key = "category_${LibraryRepository.AUDIO}") { CategoryViewModel(it, LibraryRepository.AUDIO) }
    val content by vm.content.collectAsState()
    val section = content.dataOrNull?.sections?.firstOrNull { it.key == sectionKey } ?: return
    val playback by pocket.media.player.state.collectAsState()
    val downloads by pocket.media.downloads.items.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDownload by remember { mutableStateOf(false) }
    val tracks = section.videos
    val q = LibraryRepository.normalize(query)
    val shown = if (q.isBlank()) tracks else tracks.filter { track ->
        val (number, title) = splitNumber(track.title)
        number == q || LibraryRepository.normalize(title).contains(q)
    }
    val allDownloaded = tracks.all { downloads[it.id] != null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(section.imageUrl, 112.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        pluralStringResource(R.plurals.album_songs, tracks.size, tracks.size) + " · " + formatDuration(tracks.sumOf { it.duration ?: 0.0 }),
                        style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(Modifier.weight(1f), Icons.Rounded.PlayArrow, stringResource(R.string.album_play_all), primary = true) {
                    tracks.firstOrNull()?.let { actions.play(it, tracks, true) }
                }
                PillButton(Modifier.weight(1f), Icons.Rounded.Shuffle, stringResource(R.string.album_shuffle)) {
                    val shuffled = tracks.shuffled()
                    shuffled.firstOrNull()?.let { actions.play(it, shuffled, true) }
                }
                PillButton(Modifier, if (allDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download, null, enabled = !allDownloaded) { confirmDownload = true }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.album_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pocket.Accent, unfocusedBorderColor = Pocket.SurfaceHighest),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(shown, key = { it.id }) { track ->
            val (number, title) = splitNumber(track.title)
            val current = playback.video?.id == track.id
            Row(
                Modifier.fillMaxWidth().clickable { actions.play(track, tracks, false) }
                    .background(if (current) Pocket.Surface else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(36.dp)) {
                    if (current) Icon(Icons.Rounded.GraphicEq, null, tint = Pocket.Accent, modifier = Modifier.size(18.dp))
                    else Text(number, style = MaterialTheme.typography.bodyMedium, color = Pocket.TextMuted)
                }
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (current) Pocket.Accent else Pocket.TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (downloads[track.id]?.isDone == true) Icon(Icons.Rounded.CheckCircle, null, tint = Pocket.Success, modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
                Text(formatDuration(track.duration), style = MaterialTheme.typography.bodySmall, color = Pocket.TextMuted)
            }
        }
    }

    if (confirmDownload) {
        val missing = tracks.filter { downloads[it.id] == null }
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text(stringResource(R.string.album_download_title, missing.size)) },
            text = { Text(stringResource(R.string.album_download_body, formatBytes(missing.sumOf { it.files?.firstOrNull()?.filesize ?: 0 }))) },
            confirmButton = {
                TextButton(onClick = {
                    val language = pocket.core.settings.current.languageCode
                    missing.forEach { pocket.media.downloads.download(it, it.audioQuality ?: return@forEach, language) }
                    confirmDownload = false
                }) { Text(stringResource(R.string.common_download), color = Pocket.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@Composable
private fun PillButton(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (primary) Pocket.Accent else Pocket.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (primary) Color.White else if (!enabled) Pocket.Success else Pocket.TextPrimary, modifier = Modifier.size(18.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (primary) Color.White else Pocket.TextPrimary)
        }
    }
}

/** "151. Title" -> ("151", "Title"); titles without a number keep it empty. */
private fun splitNumber(title: String): Pair<String, String> {
    val match = Regex("^(\\d+)\\.\\s*(.*)$").find(title) ?: return "" to title
    return match.groupValues[1] to match.groupValues[2]
}
