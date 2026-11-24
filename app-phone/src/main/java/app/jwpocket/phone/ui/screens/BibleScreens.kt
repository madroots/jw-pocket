package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.Canvas
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
import app.jwpocket.core.util.formatBytes
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.formatTimeLeft
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.theme.Pocket

/** The books of the Bible in the library language; null while loading. */
@Composable
private fun rememberBooks(): Pair<List<BibleBook>?, Boolean> {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val language = settings.languageCode
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

/** One book: every chapter at a glance (heard, started), continue, download. */
@Composable
fun BibleBookScreen(bookNumber: Int, contentPadding: PaddingValues) {
    val pocket = LocalPocket.current
    val actions = LocalVideoActions.current
    val (books, _) = rememberBooks()
    val book = books?.firstOrNull { it.number == bookNumber }
    val states by pocket.core.progress.states.collectAsState()
    val playback by pocket.media.player.state.collectAsState()
    val downloads by pocket.media.downloads.items.collectAsState()
    var confirmDownload by remember { mutableStateOf(false) }
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pocket.Accent) }
        return
    }
    val all = remember(books) { books.flatMap { it.chapters }.map { it.video } }
    val heard = book.chapters.count { states[it.id]?.finished == true }
    // Continue in this book: a started chapter, else the one after the last heard, else the first.
    val resume = book.chapters.firstOrNull { states[it.id]?.canResume == true }
        ?: book.chapters.lastOrNull { states[it.id]?.finished == true }?.let { last -> book.chapters.getOrNull(book.chapters.indexOf(last) + 1) }
        ?: book.chapters.first()
    val allDownloaded = book.chapters.all { downloads[it.id] != null }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full {
            Text(
                pluralStringResource(R.plurals.bible_chapters, book.chapters.size, book.chapters.size) + " · " +
                    formatDuration(book.durationMs / 1000.0) + " · " + stringResource(R.string.bible_book_heard, heard),
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
            )
        }
        full {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Pocket.Accent)
                        .clickable { actions.play(resume.video, all, false) }.padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bible_play_chapter, resume.chapter), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Pocket.SurfaceHigh)
                        .clickable(enabled = !allDownloaded) { confirmDownload = true }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (allDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download, null, tint = if (allDownloaded) Pocket.Success else Pocket.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (allDownloaded) R.string.common_downloaded else R.string.common_download), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        items(book.chapters, key = { it.id }) { chapter ->
            val state = states[chapter.id]
            val current = playback.video?.id == chapter.id
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (state?.finished == true) Pocket.AccentSoft else Pocket.SurfaceHigh)
                    .then(if (state?.canResume == true || current) Modifier.border(1.5.dp, Pocket.Accent, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { actions.play(chapter.video, all, false) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${chapter.chapter}", fontSize = 15.sp,
                    color = if (state != null || current) Pocket.TextPrimary else Pocket.TextSecondary,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        full {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Modifier.background(Pocket.AccentSoft), stringResource(R.string.bible_legend_heard))
                Legend(Modifier.border(1.5.dp, Pocket.Accent, RoundedCornerShape(3.dp)), stringResource(R.string.bible_legend_started))
            }
        }
    }

    if (confirmDownload) {
        val missing = book.chapters.filter { downloads[it.id] == null }
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text(stringResource(R.string.bible_download_title, book.name)) },
            text = { Text(stringResource(R.string.bible_download_body, missing.size, formatBytes(missing.sumOf { it.video.files?.firstOrNull()?.filesize ?: 0 }))) },
            confirmButton = {
                TextButton(onClick = {
                    val language = pocket.core.settings.current.languageCode
                    missing.forEach { pocket.media.downloads.download(it.video, it.video.audioQuality ?: return@forEach, language) }
                    confirmDownload = false
                }) { Text(stringResource(R.string.common_download), color = Pocket.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text(stringResource(R.string.common_cancel)) } },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

@Composable
private fun Legend(swatch: Modifier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).then(swatch))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
    }
}
