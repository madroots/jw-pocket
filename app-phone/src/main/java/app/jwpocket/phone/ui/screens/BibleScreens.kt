package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import app.jwpocket.core.util.formatClock
import app.jwpocket.media.download.DownloadItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jwpocket.core.data.BibleBook
import app.jwpocket.core.data.BibleChapter
import app.jwpocket.core.data.BibleProgress
import app.jwpocket.core.data.WatchState
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.theme.Pocket

/** The books of the Bible in the Bible language; null while loading. */
@Composable
private fun rememberBooks(): Pair<List<BibleBook>?, Boolean> {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val language = settings.bibleLanguageCode
    var books by remember(language) { mutableStateOf(pocket.core.bible.loadedBooks(language)) }
    var failed by remember(language) { mutableStateOf(false) }
    LaunchedEffect(language) {
        if (books == null) {
            books = pocket.core.bible.books(language)
            failed = books == null
        }
    }
    return books to failed
}

/**
 * The Bible tab: carry on where you stopped, how far you've come, and the
 * books of the Hebrew-Aramaic and Greek Scriptures with their progress.
 */
@Composable
fun BibleScreen(contentPadding: PaddingValues, onOpenBook: (Int) -> Unit) {
    val pocket = LocalPocket.current
    val (books, failed) = rememberBooks()
    val states by pocket.core.progress.states.collectAsState()
    val playback by pocket.media.player.state.collectAsState()
    val all = remember(books) { books.orEmpty().flatMap { it.chapters } }
    val next = remember(states, all) { BibleProgress.next(states, all) }
    var greek by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showGreek = greek ?: ((next?.first?.book ?: 1) > 39)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Text(stringResource(R.string.nav_bible), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.bible_translation), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
            }
        }
        when {
            books == null && failed -> full { EmptyState(Icons.Rounded.CloudOff, stringResource(R.string.bible_error), "") }
            books == null -> full { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pocket.Accent) } }
            else -> {
                next?.let { (chapter, position) -> full { ContinueCard(chapter, position, states, all, playback.video?.id == chapter.id && playback.isPlaying) } }
                full {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(Modifier.weight(1f), "${BibleProgress.heard(states, all)}", "/ ${all.size}", stringResource(R.string.bible_heard))
                        val weekAgo = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60_000 }
                        Stat(Modifier.weight(1f), "${BibleProgress.heardSince(states, all, weekAgo)}", null, stringResource(R.string.bible_this_week))
                    }
                }
                full { OtherLanguageDownloads() }
                full {
                    Segmented(
                        listOf(stringResource(R.string.bible_hebrew), stringResource(R.string.bible_greek)),
                        selected = if (showGreek) 1 else 0,
                        onSelect = { greek = it == 1 },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                val shown = books.filter { it.hebrewAramaic != showGreek }
                items(shown, key = { it.number }) { book -> BookTile(book, states) { onOpenBook(book.number) } }
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

/**
 * Chapters downloaded in a language other than the Bible language: they
 * aren't played now (each language is its own recording), so say so and
 * offer to free the space. Nothing is deleted without asking.
 */
@Composable
private fun OtherLanguageDownloads() {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val downloads by pocket.media.downloads.items.collectAsState()
    val language = settings.bibleLanguageCode
    val others = downloads.values.filter { it.video.isBible }
        .groupBy { it.video.id.removePrefix(Video.BIBLE_PREFIX).substringBefore(':') }
        .filterKeys { it != language }
    var removing by remember { mutableStateOf<String?>(null) }
    Column {
        others.forEach { (code, items) ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Pocket.Surface).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.bible_other_language_downloads, items.size, items.size, pocket.core.languages.name(code)) + " · " + formatBytes(items.sumOf { it.bytes }),
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { removing = code }) { Text(stringResource(R.string.bible_other_language_remove), color = Pocket.Accent) }
            }
        }
    }
    removing?.let { code ->
        val items = others[code].orEmpty()
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.library_bible_delete_title, pocket.core.languages.name(code))) },
            text = { Text(stringResource(R.string.library_bible_delete_body, items.size, formatBytes(items.sumOf { it.bytes }))) },
            confirmButton = {
                TextButton(onClick = { items.forEach { pocket.media.downloads.remove(it.video.id) }; removing = null }) {
                    Text(stringResource(R.string.common_delete_download), color = Pocket.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@Composable
private fun ContinueCard(chapter: BibleChapter, positionMs: Long, states: Map<String, WatchState>, all: List<BibleChapter>, playing: Boolean) {
    val pocket = LocalPocket.current
    val actions = LocalVideoActions.current
    val context = LocalContext.current
    val started = states.isNotEmpty() && all.any { states[it.id] != null }
    val play = {
        if (pocket.media.player.state.value.video?.id == chapter.id) pocket.media.player.togglePlay()
        else actions.play(chapter.video, all.map { it.video }, false)
    }
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pocket.SurfaceHigh)
            .clickable(onClick = play).padding(16.dp)
    ) {
        Text(
            stringResource(if (started) R.string.bible_continue else R.string.bible_start).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = Pocket.Accent, fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(chapter.video.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val verse = chapter.verseAt(positionMs)
                Text(
                    stringResource(R.string.bible_verse_of, verse, chapter.verseCount) +
                        if (positionMs > 0) " · " + formatTimeLeft(context, chapter.video.durationMs - positionMs) else "",
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                )
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Pocket.Accent), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.common_pause else R.string.common_play), tint = Color.White)
            }
        }
        if (positionMs > 0 && chapter.video.durationMs > 0) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Pocket.SurfaceHighest)) {
                Box(Modifier.fillMaxWidth((positionMs.toFloat() / chapter.video.durationMs).coerceIn(0f, 1f)).height(4.dp).background(Pocket.Accent))
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, of: String?, label: String) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Pocket.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (of != null) Text(" $of", style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
    }
}

/** Two or more choices in a pill, one highlighted. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Pocket.Surface).border(1.dp, Pocket.SurfaceHighest, RoundedCornerShape(50)).padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (i == selected) Pocket.Accent else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Long names ("Christian Greek Scriptures" in some languages) wrap rather than being cut.
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (i == selected) Color.White else Pocket.TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun BookTile(book: BibleBook, states: Map<String, WatchState>, onClick: () -> Unit) {
    val heard = book.chapters.count { states[it.id]?.finished == true }
    val started = heard > 0 || book.chapters.any { states[it.id]?.canResume == true }
    Box(
        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(Pocket.SurfaceHigh).clickable(onClick = onClick).padding(8.dp)
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(book.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.bible_chapters, book.chapters.size, book.chapters.size), style = MaterialTheme.typography.labelSmall, color = Pocket.TextMuted)
        }
        when {
            heard == book.chapters.size -> Icon(Icons.Rounded.CheckCircle, null, tint = Pocket.Success, modifier = Modifier.align(Alignment.TopEnd).size(14.dp))
            started -> ProgressRing(heard.toFloat() / book.chapters.size, Modifier.align(Alignment.TopEnd).size(14.dp))
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val arc = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(Pocket.SurfaceHighest, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
        drawArc(Pocket.Accent, -90f, 360f * fraction.coerceAtLeast(0.06f), false, topLeft, arc, style = Stroke(stroke))
    }
}

/**
 * One book: every chapter at a glance, the chapter to continue with, and
 * "Download all". Each mark means one thing: a filled tile with a check is
 * heard, a bar along the bottom shows how far a started chapter got, a small
 * arrow is downloaded, moving bars are playing now. Hold a chapter for more.
 */
@Composable
fun BibleBookScreen(bookNumber: Int, contentPadding: PaddingValues) {
    val pocket = LocalPocket.current
    val actions = LocalVideoActions.current
    val context = LocalContext.current
    val (books, _) = rememberBooks()
    val book = books?.firstOrNull { it.number == bookNumber }
    val states by pocket.core.progress.states.collectAsState()
    val playback by pocket.media.player.state.collectAsState()
    val downloads by pocket.media.downloads.items.collectAsState()
    var confirmDownload by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<BibleChapter?>(null) }
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pocket.Accent) }
        return
    }
    val all = remember(books) { books.flatMap { it.chapters }.map { it.video } }
    val heard = book.chapters.count { states[it.id]?.finished == true }
    // Continue with what was heard last in this book; once it's all heard, the next book.
    val resume = BibleProgress.next(states, book.chapters)
    val nextBook = books.getOrNull(books.indexOf(book) + 1)?.chapters?.firstOrNull()
    val target = resume?.first ?: nextBook ?: book.chapters.first()
    val label = when {
        resume != null && resume.second > 0 ->
            stringResource(R.string.bible_continue_chapter, resume.first.chapter, formatTimeLeft(context, resume.first.video.durationMs - resume.second))
        resume != null -> stringResource(R.string.bible_play_chapter, resume.first.chapter)
        nextBook != null -> stringResource(R.string.bible_next_book, nextBook.video.title)
        else -> stringResource(R.string.bible_play_chapter, 1)
    }
    val allDownloaded = book.chapters.all { downloads[it.id] != null }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.bible_book_progress, heard, book.chapters.size) + " · " + formatDuration(book.durationMs / 1000.0),
                        style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.weight(1f),
                    )
                    // The whole book; single chapters download from their options (hold a chapter).
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !allDownloaded) { confirmDownload = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (allDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download, null, tint = if (allDownloaded) Pocket.Success else Pocket.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (allDownloaded) R.string.common_downloaded else R.string.bible_download_all), style = MaterialTheme.typography.labelLarge,
                            color = if (allDownloaded) Pocket.TextSecondary else Pocket.Accent)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Pocket.SurfaceHighest)) {
                    Box(Modifier.fillMaxWidth(heard.toFloat() / book.chapters.size).height(5.dp).background(Pocket.Accent))
                }
            }
        }
        full {
            Row(
                Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(Pocket.Accent)
                    .clickable { actions.play(target.video, all, false) }.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        items(book.chapters, key = { it.id }) { chapter ->
            ChapterTile(
                chapter = chapter,
                state = states[chapter.id],
                download = downloads[chapter.id],
                current = playback.video?.id == chapter.id,
                playing = playback.isPlaying,
                onClick = { actions.play(chapter.video, all, false) },
                onLongClick = { options = chapter },
            )
        }
        full {
            Column(Modifier.padding(top = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(stringResource(R.string.bible_legend_heard)) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Pocket.AccentSoft)) }
                    Legend(stringResource(R.string.bible_legend_started)) { Box(Modifier.width(12.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Pocket.Accent)) }
                    Legend(stringResource(R.string.bible_legend_downloaded)) { Icon(Icons.Rounded.Download, null, tint = Pocket.TextMuted, modifier = Modifier.size(12.dp)) }
                    Legend(stringResource(R.string.bible_legend_playing)) { Icon(Icons.Rounded.GraphicEq, null, tint = Pocket.Accent, modifier = Modifier.size(12.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.bible_hold_hint), style = MaterialTheme.typography.bodySmall, color = Pocket.TextMuted)
            }
        }
    }

    options?.let { chapter -> ChapterSheet(chapter, states[chapter.id], downloads[chapter.id], all) { options = null } }

    if (confirmDownload) {
        val missing = book.chapters.filter { downloads[it.id] == null }
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text(stringResource(R.string.bible_download_title, book.name)) },
            text = { Text(stringResource(R.string.bible_download_body, missing.size, formatBytes(missing.sumOf { it.video.files?.firstOrNull()?.filesize ?: 0 }))) },
            confirmButton = {
                TextButton(onClick = {
                    val language = pocket.core.settings.current.bibleLanguageCode
                    missing.forEach { pocket.media.downloads.download(it.video, it.video.audioQuality ?: return@forEach, language) }
                    confirmDownload = false
                }) { Text(stringResource(R.string.common_download), color = Pocket.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterTile(chapter: BibleChapter, state: WatchState?, download: DownloadItem?, current: Boolean, playing: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val heard = state?.finished == true
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (heard) Pocket.AccentSoft else Pocket.SurfaceHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            current && playing -> Icon(Icons.Rounded.GraphicEq, null, tint = Pocket.Accent, modifier = Modifier.size(20.dp))
            // In the player but paused: its number in the accent colour.
            current -> Text("${chapter.chapter}", fontSize = 15.sp, color = Pocket.Accent, fontWeight = FontWeight.Bold)
            else -> Text("${chapter.chapter}", fontSize = 15.sp, color = if (state != null) Pocket.TextPrimary else Pocket.TextSecondary)
        }
        if (heard) Icon(Icons.Rounded.Check, null, tint = Pocket.Accent, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(11.dp))
        if (download != null) Icon(
            Icons.Rounded.Download, null,
            tint = if (download.isDone) Pocket.TextMuted else Pocket.Accent,
            modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(11.dp),
        )
        // How far a started chapter got.
        if (state?.canResume == true) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Pocket.SurfaceHighest)) {
                Box(Modifier.fillMaxWidth(state.fraction.coerceAtLeast(0.05f)).height(3.dp).background(Pocket.Accent))
            }
        }
    }
}

/** Held chapter: continue or start over, mark heard or not, download or remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(chapter: BibleChapter, state: WatchState?, download: DownloadItem?, all: List<app.jwpocket.core.model.Video>, onDismiss: () -> Unit) {
    val pocket = LocalPocket.current
    val actions = LocalVideoActions.current
    val video = chapter.video
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Pocket.Surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                when {
                    state?.finished == true -> stringResource(R.string.bible_status_heard)
                    state?.canResume == true -> stringResource(R.string.bible_status_started, formatClock(state.positionMs), formatClock(state.durationMs))
                    else -> stringResource(R.string.bible_status_new)
                },
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (state?.canResume == true) {
                SheetAction(Icons.Rounded.PlayArrow, stringResource(R.string.bible_continue_from, formatClock(state.positionMs))) { actions.play(video, all, false); onDismiss() }
                SheetAction(Icons.Rounded.Replay, stringResource(R.string.bible_play_start)) { actions.play(video, all, true); onDismiss() }
            } else {
                SheetAction(Icons.Rounded.PlayArrow, stringResource(R.string.common_play)) { actions.play(video, all, true); onDismiss() }
            }
            if (state?.finished == true) SheetAction(Icons.Rounded.RemoveDone, stringResource(R.string.bible_mark_not_heard)) { pocket.core.progress.remove(video.id); onDismiss() }
            else SheetAction(Icons.Rounded.Check, stringResource(R.string.bible_mark_heard)) { pocket.core.progress.markFinished(video); onDismiss() }
            when {
                download == null -> SheetAction(Icons.Rounded.Download, stringResource(R.string.bible_download_chapter, formatBytes(video.files?.firstOrNull()?.filesize ?: 0))) {
                    video.audioQuality?.let { pocket.media.downloads.download(video, it, pocket.core.settings.current.bibleLanguageCode) }
                    onDismiss()
                }
                download.isDone -> SheetAction(Icons.Rounded.Delete, stringResource(R.string.bible_remove_download)) { pocket.media.downloads.remove(video.id); onDismiss() }
                else -> SheetAction(Icons.Rounded.Close, stringResource(R.string.common_cancel_download)) { pocket.media.downloads.remove(video.id); onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Pocket.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Legend(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
    }
}
