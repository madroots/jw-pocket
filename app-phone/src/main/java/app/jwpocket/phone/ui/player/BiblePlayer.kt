package app.jwpocket.phone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jwpocket.core.data.BibleChapter
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.media.PlaybackState
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.theme.Pocket
import kotlinx.coroutines.launch

/**
 * The full player for a Bible chapter: the words follow the reading verse by
 * verse (like subtitles), tap a verse to jump to it, and the buttons step a
 * verse or a chapter back and forward. The words come from the Bible's EPUB,
 * downloaded once (by itself on Wi-Fi, after asking on mobile data).
 */
@Composable
internal fun BiblePlayerBody(state: PlaybackState, video: Video, onSheet: (Sheet) -> Unit, modifier: Modifier = Modifier) {
    val pocket = LocalPocket.current
    val bible = pocket.core.bible
    val connection = pocket.media.player
    val scope = rememberCoroutineScope()
    val chapter by produceState(bible.chapter(video.id), video.id) { value = bible.chapterOf(video) }
    val language = chapter?.language
    var textVersion by remember { mutableIntStateOf(0) } // bumped when the text arrives
    val verses by produceState<Map<Int, String>?>(null, chapter, textVersion) { value = chapter?.let { bible.verseText(it) } }
    val downloading by bible.textDownloading.collectAsState()
    var textFailed by remember { mutableStateOf(false) }

    // The text downloads by itself where data is free; on mobile data it waits for a tap.
    LaunchedEffect(language) {
        if (language != null && !bible.hasText(language) && !pocket.core.network.isMetered()) {
            textFailed = !bible.downloadText(language)
            textVersion++
        }
    }

    val c = chapter
    val verse = c?.verseAt(state.positionMs) ?: 1
    val book = language?.let { bible.loadedBooks(it) }?.firstOrNull { it.number == c?.book }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(video.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (c != null && book != null) {
                Text(
                    stringResource(R.string.bible_chapter_of, c.chapter, book.chapters.size) + " · " + stringResource(R.string.bible_verse_of, verse, c.verseCount),
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val text = verses
            when {
                c != null && text != null -> VerseList(c, text, verse) { v -> c.startOf(v)?.let(connection::seekTo) }
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.bible_verse).uppercase(), style = MaterialTheme.typography.labelMedium, color = Pocket.TextSecondary)
                    Text("$verse", fontSize = 64.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(16.dp))
                    when {
                        language == null -> Unit
                        downloading == language -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Pocket.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.bible_text_loading), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
                        }
                        !bible.hasText(language) -> {
                            val size by produceState<Long?>(null, language) { value = bible.textSize(language) }
                            Text(
                                stringResource(R.string.bible_text_get),
                                style = MaterialTheme.typography.labelLarge, color = Pocket.Accent,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Pocket.AccentSoft)
                                    .clickable { scope.launch { textFailed = !bible.downloadText(language); textVersion++ } }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                            size?.let { Text(stringResource(R.string.bible_text_size, formatBytes(it)), style = MaterialTheme.typography.bodySmall, color = Pocket.TextMuted, modifier = Modifier.padding(top = 6.dp)) }
                            if (textFailed) Text(stringResource(R.string.bible_text_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            SeekBar(state, onSeek = connection::seekTo)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = connection::previous, enabled = state.hasPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.bible_prev_chapter), modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { c?.let { previousVerse(it, state.positionMs)?.let(connection::seekTo) } }) {
                    Icon(Icons.Rounded.FastRewind, stringResource(R.string.bible_prev_verse), modifier = Modifier.size(30.dp))
                }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable(onClick = connection::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.buffering && state.playWhenReady) CircularProgressIndicator(color = Pocket.Background, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    else Icon(
                        when { state.ended -> Icons.Rounded.Replay; state.isPlaying -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                        stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play), tint = Pocket.Background, modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { c?.nextVerse(verse)?.let { c.startOf(it) }?.let(connection::seekTo) }) {
                    Icon(Icons.Rounded.FastForward, stringResource(R.string.bible_next_verse), modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = connection::next, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.bible_next_chapter), modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            PlayerActions(state, onSheet)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(16.dp))
        }
    }
}

/** Back to the start of this verse, or to the previous one when it just began. */
private fun previousVerse(chapter: BibleChapter, positionMs: Long): Long? {
    val current = chapter.verseAt(positionMs)
    val start = chapter.startOf(current) ?: return 0L
    if (positionMs - start > 1_500) return start
    return chapter.verses.lastOrNull { it.startMs < start }?.startMs ?: 0L
}

/**
 * The chapter's verses; the one being read is bright and kept in view (with
 * any verses read together with it), the rest dimmed. Tap a verse to hear it.
 */
@Composable
private fun VerseList(chapter: BibleChapter, verses: Map<Int, String>, current: Int, onPick: (Int) -> Unit) {
    val numbers = remember(verses) { verses.keys.sorted() }
    // Verses the audio reads together with the current one (no timing of their own).
    val until = chapter.nextVerse(current) ?: Int.MAX_VALUE
    val listState = rememberLazyListState()
    LaunchedEffect(current, numbers) {
        val index = numbers.indexOf(current).takeIf { it >= 0 } ?: return@LaunchedEffect
        listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        itemsIndexed(numbers, key = { _, n -> n }) { _, n ->
            val reading = n in current until until
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(n) }.padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Text(
                    "$n",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reading) Pocket.Accent else Pocket.TextMuted,
                    modifier = Modifier.width(28.dp).padding(top = if (reading) 5.dp else 3.dp),
                )
                Text(
                    verses.getValue(n),
                    style = if (reading) MaterialTheme.typography.titleLarge.copy(lineHeight = 30.sp) else MaterialTheme.typography.bodyLarge,
                    color = if (reading) Pocket.TextPrimary else Pocket.TextMuted,
                    fontWeight = if (reading) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}
