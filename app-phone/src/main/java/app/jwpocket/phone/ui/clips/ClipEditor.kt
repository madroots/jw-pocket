package app.jwpocket.phone.ui.clips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.Cue
import app.jwpocket.core.util.WebVtt
import app.jwpocket.core.util.formatClock
import app.jwpocket.media.PlaybackState
import app.jwpocket.phone.R
import app.jwpocket.phone.clips.Clip
import app.jwpocket.phone.clips.ClipMaker
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.Pocket
import app.jwpocket.phone.ui.player.VideoSurface
import app.jwpocket.phone.ui.theme.Pocket as Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Longest clip, so files stay small enough to send. */
private const val MAX_CLIP_MS = 5 * 60_000L
/** Shortest clip. */
private const val MIN_CLIP_MS = 500L
/** A little air before the first and after the last word. */
private const val PAD_MS = 250L
/** One tap on a nudge arrow. */
private const val NUDGE_MS = 250L

/** Who set the current start and end: nobody yet (the default), the trim bar or the sentences. */
private enum class Source { None, Timeline, Sentences }

/** A change waiting for "switch method?" to be answered. */
private enum class Switch { ToTimeline, ToSentences }

/**
 * "Share a moment", full screen: the video on top to preview the selection,
 * a trim bar with a start and an end handle (like a video editor), and the
 * subtitle lines below, where tapping the first and the last sentence does the
 * same. Both edit the same start and end; changing method with a selection
 * already made asks first. The video pauses while this is open and is back
 * where it was afterwards.
 */
@Composable
fun ClipEditor(state: PlaybackState, video: Video, onClose: () -> Unit, onCreated: (Clip) -> Unit) {
    val pocket = LocalPocket.current
    val context = LocalContext.current
    val connection = pocket.media.player
    val controller by connection.controller.collectAsState()
    val settings = pocket.core.settings.current
    val scope = rememberCoroutineScope()
    val maker = remember { ClipMaker(context.applicationContext, pocket.media, pocket.clips) }
    val durationMs = state.durationMs.coerceAtLeast(1)

    // Pause on the way in (and show the picture even in Listen mode); back to the same spot on the way out.
    val anchorMs = remember { state.positionMs }
    val wasListening = remember { state.listenByHand }
    DisposableEffect(Unit) {
        connection.pause()
        if (wasListening) connection.setListen(false)
        onDispose {
            connection.pause()
            connection.seekTo(anchorMs)
            if (wasListening) connection.setListen(true)
        }
    }
    BackHandler(onBack = onClose)

    var cues by remember { mutableStateOf<List<Cue>?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(video.id) {
        cues = withContext(Dispatchers.IO) { loadCues(pocket, video, state.subtitleLanguage ?: settings.languageCode, settings.languageCode) }
            ?.takeIf { it.isNotEmpty() }
        loading = false
    }

    var source by remember { mutableStateOf(Source.None) }
    var startMs by remember { mutableLongStateOf((anchorMs - 10_000).coerceAtLeast(0)) }
    var endMs by remember { mutableLongStateOf(anchorMs.coerceAtLeast(startMs + 5_000).coerceAtMost(durationMs)) }
    var first by remember { mutableStateOf<Int?>(null) }
    var last by remember { mutableStateOf<Int?>(null) }
    var window by remember { mutableStateOf(windowFor(startMs, endMs, durationMs)) }
    var asking by remember { mutableStateOf<Switch?>(null) }
    var pendingCue by remember { mutableStateOf<Int?>(null) }

    var previewing by remember { mutableStateOf(false) }
    var burnSubtitles by remember { mutableStateOf(state.subtitleLanguage != null) }
    var making by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    // The preview stops at the end of the selection.
    LaunchedEffect(previewing, state.positionMs) {
        if (previewing && state.positionMs >= endMs) {
            connection.pause()
            previewing = false
        }
    }

    fun pickSentence(i: Int) {
        val list = cues ?: return
        previewing = false
        if (first == null || last != null) { first = i; last = null } else { val a = first!!; first = minOf(a, i); last = maxOf(a, i) }
        val a = first!!; val b = last ?: a
        startMs = (list[a].startMs - PAD_MS).coerceAtLeast(0)
        endMs = (list[b].endMs + PAD_MS).coerceAtMost(durationMs)
        source = Source.Sentences
        window = windowFor(startMs, endMs, durationMs)
        connection.seekTo(startMs)
    }

    /** Moves one end of the selection by [deltaMs] (the arrows), asking first if sentences were picked. */
    fun nudge(handle: Handle, deltaMs: Long) {
        if (!switchTo(source, Switch.ToTimeline) { asking = it }) return
        previewing = false
        if (handle == Handle.Start) startMs = (startMs + deltaMs).coerceIn((endMs - MAX_CLIP_MS).coerceAtLeast(0), endMs - MIN_CLIP_MS)
        else endMs = (endMs + deltaMs).coerceIn(startMs + MIN_CLIP_MS, (startMs + MAX_CLIP_MS).coerceAtMost(durationMs))
        source = Source.Timeline
        first = null; last = null
        if (startMs < window.first || endMs > window.second) window = windowFor(startMs, endMs, durationMs)
        connection.seekTo(if (handle == Handle.Start) startMs else endMs)
    }

    val tooLong = endMs - startMs > MAX_CLIP_MS
    val ready = endMs - startMs >= MIN_CLIP_MS && !tooLong && making == null

    Column(Modifier.fillMaxSize().background(Theme.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close)) }
            Text(stringResource(R.string.moment_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }

        // Preview: tap to play the selection or pause.
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                .clickable {
                    if (state.isPlaying) { connection.pause(); previewing = false }
                    else {
                        if (state.positionMs !in startMs until endMs - 300) connection.seekTo(startMs)
                        connection.resume(); previewing = true
                    }
                },
        ) {
            VideoSurface(controller, Modifier.fillMaxSize(), settings.subtitleScale)
            if (!state.isPlaying) {
                Box(Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.common_play), tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Nudge(stringResource(R.string.clip_start), startMs, onEarlier = { nudge(Handle.Start, -NUDGE_MS) }, onLater = { nudge(Handle.Start, NUDGE_MS) })
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.clip_length, tenths(endMs - startMs)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tooLong) MaterialTheme.colorScheme.error else Theme.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Nudge(stringResource(R.string.clip_end), endMs, onEarlier = { nudge(Handle.End, -NUDGE_MS) }, onLater = { nudge(Handle.End, NUDGE_MS) })
            }
            Spacer(Modifier.height(8.dp))
            TrimBar(
                windowStart = window.first, windowEnd = window.second,
                startMs = startMs, endMs = endMs, positionMs = state.positionMs,
                onGrab = { switchTo(source, Switch.ToTimeline) { asking = it }.also { ok -> if (ok) { previewing = false; connection.pause() } } },
                onMove = { handle, ms ->
                    if (handle == Handle.Start) startMs = ms.coerceIn((endMs - MAX_CLIP_MS).coerceAtLeast(0), endMs - MIN_CLIP_MS)
                    else endMs = ms.coerceIn(startMs + MIN_CLIP_MS, (startMs + MAX_CLIP_MS).coerceAtMost(durationMs))
                    source = Source.Timeline
                    first = null; last = null
                    connection.seekTo(if (handle == Handle.Start) startMs else endMs)
                },
                onRelease = { window = windowFor(startMs, endMs, durationMs) },
                onScrub = { ms -> previewing = false; connection.pause(); connection.seekTo(ms) },
            )
            Text(
                stringResource(R.string.moment_hint_range),
                style = MaterialTheme.typography.bodySmall, color = Theme.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(
            stringResource(R.string.editor_sentences),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Text(stringResource(R.string.moment_hint), style = MaterialTheme.typography.bodySmall, color = Theme.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = cues
            when {
                loading -> CircularProgressIndicator(color = Theme.Accent, modifier = Modifier.align(Alignment.Center))
                list == null -> Text(stringResource(R.string.editor_no_sentences), style = MaterialTheme.typography.bodyMedium, color = Theme.TextMuted, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> {
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (list.indexOfLast { it.startMs <= anchorMs } - 2).coerceAtLeast(0))
                    // Follow the selection made on the video.
                    LaunchedEffect(startMs, source) {
                        if (source == Source.Timeline) scrollTo(listState, list, startMs)
                    }
                    SentenceList(list, listState, startMs, endMs, source, first, last, state.positionMs) { i ->
                        if (source == Source.Timeline) { pendingCue = i; asking = Switch.ToSentences } else pickSentence(i)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            if (cues != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { burnSubtitles = !burnSubtitles },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(burnSubtitles, onCheckedChange = { burnSubtitles = it }, colors = CheckboxDefaults.colors(checkedColor = Theme.Accent))
                    Text(stringResource(R.string.moment_subtitles), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (tooLong) Text(stringResource(R.string.moment_too_long, MAX_CLIP_MS / 60_000), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (failed) Text(stringResource(R.string.moment_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (making != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.moment_creating, (progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, color = Theme.Accent, trackColor = Theme.SurfaceHighest, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            } else {
                Button(
                    onClick = {
                        connection.pause(); previewing = false
                        failed = false; progress = 0f
                        val from = startMs; val to = endMs
                        val burn = if (burnSubtitles) cues?.filter { it.endMs > from && it.startMs < to } else null
                        making = scope.launch {
                            try {
                                val clip = maker.make(video, video.shareUrl(settings.languageCode), from, to, burn) { progress = it }
                                onCreated(clip)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("ClipMaker", "Clip failed", e)
                                failed = true
                            } finally {
                                making = null
                            }
                        }
                    },
                    enabled = ready,
                    colors = ButtonDefaults.buttonColors(containerColor = Theme.Accent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.moment_create)) }
            }
        }
    }

    when (asking) {
        Switch.ToTimeline -> SwitchDialog(
            title = stringResource(R.string.switch_to_video_title),
            body = stringResource(R.string.switch_to_video_body),
            onConfirm = { source = Source.Timeline; first = null; last = null; asking = null },
            onDismiss = { asking = null },
        )
        Switch.ToSentences -> SwitchDialog(
            title = stringResource(R.string.switch_to_sentences_title),
            body = stringResource(R.string.switch_to_sentences_body),
            onConfirm = { source = Source.None; first = null; last = null; asking = null; pendingCue?.let(::pickSentence); pendingCue = null },
            onDismiss = { asking = null; pendingCue = null },
        )
        null -> Unit
    }
}

/**
 * Whether an edit on the trim bar may go ahead now. With sentences picked,
 * [ask] is called instead and the edit waits for the answer.
 */
private fun switchTo(source: Source, target: Switch, ask: (Switch) -> Unit): Boolean {
    if (target == Switch.ToTimeline && source == Source.Sentences) { ask(target); return false }
    return true
}

@Composable
private fun SwitchDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.switch_confirm), color = Theme.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        containerColor = Theme.SurfaceHigh,
    )
}

/** "Start 1:42.3" with arrows that move it a quarter of a second. */
@Composable
private fun Nudge(label: String, ms: Long, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEarlier, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.editor_earlier, label))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Theme.TextMuted)
            Text(tenths(ms), style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onLater, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.editor_later, label))
        }
    }
}

private enum class Handle { Start, End }

/**
 * The trim bar: a stretch of the video around the selection (zoomed so the
 * handles are easy to place), the selection between two handles and a line
 * where the preview is. Drag a handle to move it; drag anywhere else to look
 * through the video. The stretch re-centres on the selection when a handle is let go.
 */
@Composable
private fun TrimBar(
    windowStart: Long,
    windowEnd: Long,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    onGrab: () -> Boolean,
    onMove: (Handle, Long) -> Unit,
    onRelease: () -> Unit,
    onScrub: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val grabRadius = with(density) { 28.dp.toPx() }
    val handleWidth = with(density) { 14.dp.toPx() }
    val current = rememberUpdatedState(Triple(windowStart to windowEnd, startMs to endMs, Triple(onGrab, onMove, onRelease)))
    val scrub = rememberUpdatedState(onScrub)
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val (win, sel, callbacks) = current.value
                        val span = (win.second - win.first).coerceAtLeast(1)
                        fun toMs(x: Float) = win.first + (x / size.width * span).toLong()
                        fun toX(ms: Long) = (ms - win.first).toFloat() / span * size.width
                        val distStart = abs(down.position.x - toX(sel.first))
                        val distEnd = abs(down.position.x - toX(sel.second))
                        val handle = when {
                            minOf(distStart, distEnd) > grabRadius -> null
                            distStart <= distEnd -> Handle.Start
                            else -> Handle.End
                        }
                        if (handle != null && !callbacks.first()) {
                            // Waiting for "switch method?": swallow this gesture.
                            do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        down.consume()
                        if (handle == null) scrub.value(toMs(down.position.x).coerceIn(win.first, win.second))
                        var lastSeek = 0L
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val ms = toMs(change.position.x).coerceIn(win.first, win.second)
                            val now = System.currentTimeMillis()
                            if (handle != null) callbacks.second(handle, ms)
                            else if (now - lastSeek > 80) { scrub.value(ms); lastSeek = now }
                        }
                        if (handle != null) callbacks.third()
                    }
                },
        ) {
            val span = (windowEnd - windowStart).coerceAtLeast(1).toFloat()
            fun x(ms: Long) = ((ms - windowStart) / span * size.width).coerceIn(0f, size.width)
            val r = CornerRadius(8.dp.toPx())
            drawRoundRect(Theme.SurfaceHigh, size = size, cornerRadius = r)
            val sx = x(startMs); val ex = x(endMs)
            drawRect(Theme.AccentSoft, topLeft = Offset(sx, 0f), size = Size((ex - sx).coerceAtLeast(0f), size.height))
            val edge = 3.dp.toPx()
            drawRect(Theme.Accent, topLeft = Offset(sx, 0f), size = Size(ex - sx, edge))
            drawRect(Theme.Accent, topLeft = Offset(sx, size.height - edge), size = Size(ex - sx, edge))
            for (hx in listOf(sx - handleWidth / 2, ex - handleWidth / 2)) {
                drawRoundRect(Theme.Accent, topLeft = Offset(hx, 0f), size = Size(handleWidth, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                drawRect(Color.White, topLeft = Offset(hx + handleWidth / 2 - 1.dp.toPx(), size.height * 0.3f), size = Size(2.dp.toPx(), size.height * 0.4f))
            }
            if (positionMs in windowStart..windowEnd) {
                drawRect(Color.White, topLeft = Offset(x(positionMs) - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(formatClock(windowStart), style = MaterialTheme.typography.labelSmall, color = Theme.TextMuted)
            Spacer(Modifier.weight(1f))
            Text(formatClock((windowStart + windowEnd) / 2), style = MaterialTheme.typography.labelSmall, color = Theme.TextMuted)
            Spacer(Modifier.weight(1f))
            Text(formatClock(windowEnd), style = MaterialTheme.typography.labelSmall, color = Theme.TextMuted)
        }
    }
}

/**
 * Subtitle lines; picked sentences are highlighted, and lines inside a
 * selection made on the video get a lighter mark. The line playing now has a dot.
 */
@Composable
private fun SentenceList(
    cues: List<Cue>,
    listState: LazyListState,
    startMs: Long,
    endMs: Long,
    source: Source,
    first: Int?,
    last: Int?,
    positionMs: Long,
    onTap: (Int) -> Unit,
) {
    val now = cues.indexOfLast { it.startMs <= positionMs }
    val picked = first?.let { a -> a..(last ?: a) }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(cues) { i, cue ->
            val strong = source == Source.Sentences && picked != null && i in picked
            val inside = !strong && source != Source.None && cue.endMs > startMs && cue.startMs < endMs
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            strong -> Theme.AccentSoft
                            inside -> Theme.SurfaceHigh
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onTap(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    formatClock(cue.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (strong || inside) Theme.Accent else Theme.TextMuted,
                    modifier = Modifier.width(44.dp).padding(top = 2.dp),
                )
                Text(
                    cue.text.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (strong || inside || i == now) Theme.TextPrimary else Theme.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (i == now) Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(Theme.Accent))
            }
        }
    }
}

private suspend fun scrollTo(listState: LazyListState, cues: List<Cue>, ms: Long) {
    val i = cues.indexOfFirst { it.endMs > ms }.takeIf { it >= 0 } ?: return
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isNotEmpty() && i in (visible.first().index + 1) until visible.last().index) return
    listState.animateScrollToItem((i - 1).coerceAtLeast(0))
}

/** The stretch of video the trim bar shows: about three times the selection, between one and six minutes. */
private fun windowFor(startMs: Long, endMs: Long, durationMs: Long): Pair<Long, Long> {
    val span = ((endMs - startMs) * 3).coerceIn(60_000, 360_000).coerceAtMost(durationMs)
    val from = ((startMs + endMs) / 2 - span / 2).coerceIn(0, (durationMs - span).coerceAtLeast(0))
    return from to from + span
}

/** "1:42.3" */
private fun tenths(ms: Long): String = "${formatClock(ms)}.${(ms % 1000) / 100}"

/** The video's subtitle lines in [language]: a saved copy first, then the network; null if there are none. */
private suspend fun loadCues(pocket: Pocket, video: Video, language: String, libraryLanguage: String): List<Cue>? {
    if (language == Settings.SUBTITLES_OFF) return null
    val text = pocket.media.downloads.savedSubtitle(video.id, language)?.readText() ?: run {
        val fresh = pocket.core.library.findLoaded(video.id) ?: video
        val url = if (language == libraryLanguage) fresh.subtitleUrl else pocket.core.subtitles.url(fresh, language)
        url?.let { pocket.core.api.getText(it) }
    }
    return text?.let(WebVtt::parse)
}
