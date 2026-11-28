package app.jwpocket.phone.ui.screens

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.BibleRepository
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.media.download.DownloadItem
import app.jwpocket.media.download.DownloadStatus
import app.jwpocket.media.download.StorageInfo
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.clips.ClipsList
import app.jwpocket.phone.ui.clips.ClipsRow
import app.jwpocket.phone.ui.components.DownloadBadge
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.components.SectionHeader
import app.jwpocket.phone.ui.components.VideoCard
import app.jwpocket.phone.ui.components.VideoListItem
import app.jwpocket.phone.ui.theme.Pocket

/** What a download is, for the "On this phone" lines of the Library; each has its own list. */
enum class DownloadKind(@StringRes val label: Int, val icon: ImageVector) {
    Videos(R.string.library_kind_videos, Icons.Rounded.Movie),
    Music(R.string.audio_music, Icons.Rounded.MusicNote),
    Bible(R.string.nav_bible, Icons.AutoMirrored.Rounded.MenuBook),
    Dramas(R.string.library_kind_dramas, Icons.Rounded.Headphones),
    ;

    companion object {
        /** Audio dramas and dramatic Bible readings carry their row's key as primaryCategory; other sound-only items are songs. */
        fun of(video: Video): DownloadKind = when {
            video.isBible -> Bible
            !video.isAudio -> Videos
            video.primaryCategory == LibraryRepository.AUDIO_DRAMAS || video.primaryCategory == LibraryRepository.AUDIO_BIBLE_READINGS -> Dramas
            else -> Music
        }

        fun from(name: String?): DownloadKind? = entries.firstOrNull { it.name == name }
    }
}

/** Overview rows show this many items; "See all" has the rest. */
private const val ROW_LIMIT = 20
/** Pending downloads listed on the overview before "N more". */
private const val PENDING_LIMIT = 3

/** Library overview: storage, pending downloads, favorites, what's on this phone, clips and recently played. */
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onBrowse: () -> Unit,
    onOpenDownloads: (DownloadKind) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val pocket = LocalPocket.current
    val downloads = pocket.media.downloads
    val all by downloads.items.collectAsState()
    val favorites by pocket.core.favorites.favorites.collectAsState()
    val clips by pocket.clips.clips.collectAsState()
    val history by remember { pocket.core.progress.history }.collectAsState(emptyList())
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val items = all.values.toList()
    val pending = items.filterNot { it.isDone }
    val kinds = items.groupBy { DownloadKind.of(it.video) }
    LaunchedEffect(all.size) { storage = downloads.storage() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
        item(key = "header") {
            LibraryHeader(canDelete = items.isNotEmpty(), onDeleteAll = { confirmDeleteAll = true })
        }
        if (items.isNotEmpty()) item(key = "storage") {
            Text(
                stringResource(R.string.library_storage, formatBytes(items.sumOf { it.bytes }), formatBytes(storage?.freeBytes ?: 0)),
                style = MaterialTheme.typography.bodyMedium, color = Pocket.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }
        if (pending.isNotEmpty()) item(key = "pending") { PendingCard(pending) }
        if (favorites.isNotEmpty()) item(key = "favorites") {
            RowHeader(Icons.Rounded.FavoriteBorder, stringResource(R.string.library_favorites), stringResource(R.string.common_see_all), onOpenFavorites)
            val row = favorites.take(ROW_LIMIT)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(row, key = { it.id }) { VideoCard(it, row) }
            }
        }
        if (kinds.isNotEmpty()) {
            item(key = "on_phone") { SectionHeader(stringResource(R.string.library_on_phone)) }
            DownloadKind.entries.forEach { kind ->
                val list = kinds[kind] ?: return@forEach
                item(key = "kind_${kind.name}") { KindLine(kind, list, onClick = { onOpenDownloads(kind) }) }
            }
        }
        if (clips.isNotEmpty()) item(key = "clips") {
            RowHeader(Icons.Rounded.ContentCut, stringResource(R.string.library_clips), stringResource(R.string.common_see_all), onOpenClips)
            ClipsRow(clips.take(ROW_LIMIT))
        }
        if (history.isNotEmpty()) item(key = "recent") {
            RowHeader(Icons.Rounded.History, stringResource(R.string.library_recent), stringResource(R.string.library_history), onOpenHistory)
            val row = history.take(ROW_LIMIT).map { it.video }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(row, key = { it.id }) { VideoCard(it, row) }
            }
        }
        if (items.isEmpty() && favorites.isEmpty() && clips.isEmpty() && history.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.FavoriteBorder, stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_body)) {
                TextButton(onClick = onBrowse) { Text(stringResource(R.string.library_find_videos), color = Pocket.Accent) }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.library_delete_all_title)) },
            text = { Text(stringResource(R.string.library_delete_all_message, formatBytes(items.sumOf { it.bytes }))) },
            confirmButton = {
                TextButton(onClick = { items.forEach { downloads.remove(it.video.id) }; confirmDeleteAll = false }) {
                    Text(stringResource(R.string.library_delete_all), color = Pocket.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

/** "Library" and its ⋮ menu: Wi-Fi only downloads, delete every download. */
@Composable
private fun LibraryHeader(canDelete: Boolean, onDeleteAll: () -> Unit) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.nav_library), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.common_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_wifi_only)) },
                    trailingIcon = {
                        Switch(checked = settings.wifiOnlyDownloads, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Pocket.Accent))
                    },
                    onClick = { pocket.core.settings.update { it.copy(wifiOnlyDownloads = !it.wifiOnlyDownloads) } },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_menu_delete_all)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    enabled = canDelete,
                    onClick = { menu = false; onDeleteAll() },
                )
            }
        }
    }
}

/** Title of an overview row with an icon and a link to its full list. */
@Composable
private fun RowHeader(icon: ImageVector, title: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Pocket.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(action, style = MaterialTheme.typography.labelLarge, color = Pocket.TextSecondary)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Pocket.TextSecondary)
    }
}

/** One "On this phone" line: what kind, how many and how big. */
@Composable
private fun KindLine(kind: DownloadKind, items: List<DownloadItem>, onClick: () -> Unit) {
    val size = formatBytes(items.sumOf { it.bytes })
    val summary = when (kind) {
        DownloadKind.Videos -> pluralStringResource(R.plurals.library_downloads_summary, items.size, items.size, size)
        DownloadKind.Music -> pluralStringResource(R.plurals.album_songs, items.size, items.size) + " · " + size
        DownloadKind.Bible -> pluralStringResource(R.plurals.bible_chapters, items.size, items.size) + " · " + size
        DownloadKind.Dramas -> pluralStringResource(R.plurals.library_recordings, items.size, items.size) + " · " + size
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(Pocket.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            Icon(kind.icon, null, tint = Pocket.TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(stringResource(kind.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, maxLines = 1)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Pocket.TextSecondary)
    }
}

/** Downloads still on their way, compactly; all of them are in their own lists too. */
@Composable
private fun PendingCard(pending: List<DownloadItem>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pocket.Surface).padding(vertical = 8.dp)) {
        Text(stringResource(R.string.library_in_progress), style = MaterialTheme.typography.labelLarge, color = Pocket.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        pending.take(PENDING_LIMIT).forEach { item -> key(item.video.id) { PendingRow(item) } }
        val more = pending.size - PENDING_LIMIT
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.library_pending_more, more, more),
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PendingRow(item: DownloadItem) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusText(item), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DownloadMenu(item, emptyList())
        }
        if (item.status != DownloadStatus.Failed) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp).height(2.dp),
                color = Pocket.Accent, trackColor = Pocket.SurfaceHigh,
            )
        }
    }
}

/** Padding of a Library list page: below the top bar, above the tab bar. */
private fun pagePadding(contentPadding: PaddingValues) =
    PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp)

/** Library › On this phone › one kind: its downloads, pending first. Bible chapters are listed by language and book. */
@Composable
fun LibraryDownloadsScreen(kind: DownloadKind, contentPadding: PaddingValues, onBrowse: () -> Unit, onOpenBible: () -> Unit) {
    if (kind == DownloadKind.Bible) BibleDownloads(pagePadding(contentPadding), onOpenBible)
    else KindDownloads(kind, pagePadding(contentPadding), onBrowse)
}

@Composable
private fun KindDownloads(kind: DownloadKind, padding: PaddingValues, onBrowse: () -> Unit) {
    val all by LocalPocket.current.media.downloads.items.collectAsState()
    val items = all.values.filter { DownloadKind.of(it.video) == kind }
    val done = items.filter { it.isDone }
    val pending = items.filterNot { it.isDone }
    val row = done.map { it.video }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (items.isEmpty()) item {
            EmptyState(Icons.Rounded.DownloadForOffline, stringResource(R.string.library_downloads_empty_title), stringResource(R.string.library_downloads_empty_body)) {
                TextButton(onClick = onBrowse) { Text(stringResource(R.string.library_find_videos), color = Pocket.Accent) }
            }
        }
        if (pending.isNotEmpty()) {
            item { Header(stringResource(R.string.library_in_progress)) }
            items(pending, key = { "p_" + it.video.id }) { DownloadRow(it, row) }
        }
        if (done.isNotEmpty()) {
            item { Header(stringResource(R.string.common_ready_offline)) }
            items(done, key = { it.video.id }) { DownloadRow(it, row) }
        }
    }
}

/** Language code of a Bible chapter's video id ("bible:<language>:<book>:<chapter>"). */
private fun bibleLanguage(video: Video): String = video.id.removePrefix(Video.BIBLE_PREFIX).substringBefore(':')

/**
 * Downloaded Bible chapters by book (each book can be deleted at once); with
 * chapters of several languages, each language gets its own heading, the
 * Bible's current language first.
 */
@Composable
private fun BibleDownloads(padding: PaddingValues, onOpenBible: () -> Unit) {
    val pocket = LocalPocket.current
    val downloads = pocket.media.downloads
    val all by downloads.items.collectAsState()
    val settings by pocket.core.settings.settings.collectAsState()
    val known by pocket.core.languages.languages.collectAsState()
    var deleting by remember { mutableStateOf<Pair<String, List<DownloadItem>>?>(null) }
    // Genesis to Revelation, chapter by chapter, per language.
    val chapters = all.values.mapNotNull { item -> BibleRepository.bookAndChapter(item.video.id)?.let { it to item } }
        .sortedWith(compareBy({ it.first.first }, { it.first.second }))
    val byLanguage = chapters.groupBy { bibleLanguage(it.second.video) }
    val codes = byLanguage.keys.toList()
    val languageNames = remember(known, codes) { codes.associateWith { pocket.core.languages.name(it) } }
    val languages = codes.sortedWith(compareBy({ it != settings.bibleLanguageCode }, { languageNames[it] }))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (chapters.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.library_bible_empty_title), stringResource(R.string.library_bible_empty_body)) {
                TextButton(onClick = onOpenBible) { Text(stringResource(R.string.library_bible_open), color = Pocket.Accent) }
            }
        }
        languages.forEach { language ->
            val inLanguage = byLanguage.getValue(language)
            val row = inLanguage.filter { it.second.isDone }.map { it.second.video }
            val books = inLanguage.groupBy({ it.first.first }, { it.second })
            val names = pocket.core.bible.loadedBooks(language)
            // A heading whenever chapters of another language than the Bible's are here.
            if (languages.size > 1 || language != settings.bibleLanguageCode) item(key = "lang_$language") {
                Text(
                    languageNames[language] ?: language, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            books.forEach { (number, items) ->
                val name = names?.firstOrNull { it.number == number }?.name ?: items.first().video.title.substringBeforeLast(' ')
                item(key = "book_${language}_$number") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.bible_chapters, items.size, items.size) + " · " + formatBytes(items.sumOf { it.bytes }),
                                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                            )
                        }
                        IconButton(onClick = { deleting = name to items }) { Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete_download), tint = Pocket.TextSecondary) }
                    }
                }
                items(items, key = { it.video.id }) { DownloadRow(it, row) }
            }
        }
    }

    deleting?.let { (name, items) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.library_bible_delete_title, name)) },
            text = { Text(stringResource(R.string.library_bible_delete_body, items.size, formatBytes(items.sumOf { it.bytes }))) },
            confirmButton = {
                TextButton(onClick = { items.forEach { downloads.remove(it.video.id) }; deleting = null }) {
                    Text(stringResource(R.string.common_delete_download), color = Pocket.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

/** Where a download stands, in one line. */
@Composable
private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.Done -> "${formatDuration(item.video.duration)} · ${item.quality} · ${formatBytes(item.bytes)}"
    DownloadStatus.Downloading -> stringResource(R.string.library_status_downloading, (item.progress * 100).toInt(), formatBytes(item.totalBytes))
    DownloadStatus.Queued -> stringResource(R.string.library_status_queued)
    DownloadStatus.Waiting -> stringResource(R.string.common_waiting_for_wifi)
    DownloadStatus.Paused -> stringResource(R.string.library_status_paused, (item.progress * 100).toInt())
    DownloadStatus.Failed -> stringResource(R.string.library_status_failed)
}

/** A download in a list; [row] is what plays after it. */
@Composable
private fun DownloadRow(item: DownloadItem, row: List<Video>) {
    val actions = LocalVideoActions.current
    Column {
        VideoListItem(
            item.video, row,
            detail = statusText(item),
            onClick = if (item.isDone) ({ actions.play(item.video, row, false) }) else null,
            trailing = { DownloadMenu(item, row) },
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

/** ⋮ (or the progress badge while pending) with play, pause, resume, retry and delete. */
@Composable
private fun DownloadMenu(item: DownloadItem, row: List<Video>) {
    val downloads = LocalPocket.current.media.downloads
    val actions = LocalVideoActions.current
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            if (item.isDone) Icon(Icons.Rounded.MoreVert, stringResource(R.string.common_more)) else DownloadBadge(item, size = 30.dp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item.isDone) DropdownMenuItem(text = { Text(stringResource(R.string.common_play)) }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }, onClick = { menu = false; actions.play(item.video, row, false) })
            if (item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Queued || item.status == DownloadStatus.Waiting)
                DropdownMenuItem(text = { Text(stringResource(R.string.common_pause)) }, leadingIcon = { Icon(Icons.Rounded.Pause, null) }, onClick = { menu = false; downloads.pause(item.video.id) })
            if (item.status == DownloadStatus.Paused)
                DropdownMenuItem(text = { Text(stringResource(R.string.common_resume)) }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }, onClick = { menu = false; downloads.resume(item.video.id) })
            if (item.status == DownloadStatus.Failed)
                DropdownMenuItem(text = { Text(stringResource(R.string.common_retry)) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; downloads.retry(item.video.id) })
            DropdownMenuItem(
                text = { Text(stringResource(if (item.isDone) R.string.common_delete_download else R.string.common_cancel_download)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; downloads.remove(item.video.id) },
            )
        }
    }
}

/** Library › Favorites. */
@Composable
fun LibraryFavoritesScreen(contentPadding: PaddingValues, onBrowse: () -> Unit) {
    val pocket = LocalPocket.current
    val favorites by pocket.core.favorites.favorites.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(contentPadding)) {
        if (favorites.isEmpty()) item {
            EmptyState(Icons.Rounded.FavoriteBorder, stringResource(R.string.library_favorites_empty_title), stringResource(R.string.library_favorites_empty_body)) {
                TextButton(onClick = onBrowse) { Text(stringResource(R.string.library_find_videos), color = Pocket.Accent) }
            }
        }
        items(favorites, key = { it.id }) { VideoListItem(it, favorites) }
    }
}

/** Library › Clips. */
@Composable
fun LibraryClipsScreen(contentPadding: PaddingValues) {
    ClipsList(pagePadding(contentPadding))
}

/** Library › Recently played › History, which can be cleared. */
@Composable
fun LibraryHistoryScreen(contentPadding: PaddingValues, onBrowse: () -> Unit) {
    val pocket = LocalPocket.current
    val history by remember { pocket.core.progress.history }.collectAsState(emptyList())
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(contentPadding)) {
        if (history.isEmpty()) item {
            EmptyState(Icons.Rounded.History, stringResource(R.string.library_history_empty_title), stringResource(R.string.library_history_empty_body)) {
                TextButton(onClick = onBrowse) { Text(stringResource(R.string.library_find_videos), color = Pocket.Accent) }
            }
        } else item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.library_clear_history), color = Pocket.TextSecondary) }
            }
        }
        val row = history.map { it.video }
        items(history, key = { it.video.id }) { w ->
            VideoListItem(w.video, row, detail = if (w.finished) stringResource(R.string.common_watched) else formatTimeLeft(LocalContext.current, w.remainingMs))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.library_clear_history_title)) },
            text = { Text(stringResource(R.string.library_clear_history_message)) },
            confirmButton = { TextButton(onClick = { pocket.core.progress.clearHistory(); confirm = false }) { Text(stringResource(R.string.library_clear_history_confirm), color = Pocket.Accent) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}
