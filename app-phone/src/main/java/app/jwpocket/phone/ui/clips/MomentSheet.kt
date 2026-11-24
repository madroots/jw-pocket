package app.jwpocket.phone.ui.clips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import app.jwpocket.phone.ui.theme.Pocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Longest clip, so files stay small enough to send. */
private const val MAX_CLIP_MS = 5 * 60_000L
/** A little air before the first and after the last word. */
private const val PAD_MS = 250L

/**
 * "Share a moment": the subtitle lines around where the person is; tap the
 * first and the last sentence, preview, make the clip. Without subtitles, two
 * handles on a short stretch of the timeline do the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentSheet(state: PlaybackState, video: Video, onDismiss: () -> Unit, onCreated: (Clip) -> Unit) {
    val pocket = LocalPocket.current
    val context = LocalContext.current
    val connection = pocket.media.player
    val settings = pocket.core.settings.current
    val scope = rememberCoroutineScope()
    val maker = remember { ClipMaker(context.applicationContext, pocket.media, pocket.clips) }
    val anchorMs = remember { state.positionMs }
    val durationMs = state.durationMs.coerceAtLeast(1)

    // Subtitle lines: the language shown now, else the video's own.
    var cues by remember { mutableStateOf<List<Cue>?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(video.id) {
        cues = withContext(Dispatchers.IO) { loadCues(pocket, video, state.subtitleLanguage ?: settings.languageCode, settings.languageCode) }
            ?.filter { it.endMs > anchorMs - 180_000 && it.startMs < anchorMs + 60_000 }
            ?.takeIf { it.isNotEmpty() }
        loading = false
    }

    var first by remember { mutableStateOf<Int?>(null) }
    var last by remember { mutableStateOf<Int?>(null) }
    // Without subtitles: a range on the timeline.
    val windowStart = (anchorMs - 90_000).coerceAtLeast(0)
    val windowEnd = (anchorMs + 30_000).coerceAtMost(durationMs)
    var range by remember { mutableStateOf((anchorMs - 15_000).coerceAtLeast(windowStart).toFloat()..anchorMs.coerceAtLeast(windowStart + 1_000).toFloat()) }

    val list = cues
    val startMs: Long?
    val endMs: Long?
    if (list != null) {
        val a = first; val b = last ?: first
        startMs = a?.let { (list[minOf(a, b!!)].startMs - PAD_MS).coerceAtLeast(0) }
        endMs = a?.let { (list[maxOf(a, b!!)].endMs + PAD_MS).coerceAtMost(durationMs) }
    } else {
        startMs = range.start.toLong()
        endMs = range.endInclusive.toLong()
    }
    val tooLong = startMs != null && endMs != null && endMs - startMs > MAX_CLIP_MS

    var burnSubtitles by remember { mutableStateOf(state.subtitleLanguage != null) }
    var previewing by remember { mutableStateOf(false) }
    var making by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    // The preview stops at the end of the selection.
    LaunchedEffect(previewing, state.positionMs) {
        if (previewing && endMs != null && state.positionMs >= endMs) {
            connection.pause()
            previewing = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = { making?.cancel(); if (previewing) connection.pause(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pocket.Surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.moment_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (list != null || loading) R.string.moment_hint else R.string.moment_hint_range),
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pocket.Accent)
                }
                list != null -> CueList(list, anchorMs, first, last) { i ->
                    previewing = false
                    when {
                        first == null || last != null -> { first = i; last = null }
                        else -> { val a = first!!; first = minOf(a, i); last = maxOf(a, i) }
                    }
                }
                else -> Column {
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it; previewing = false },
                        valueRange = windowStart.toFloat()..windowEnd.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pocket.Accent, inactiveTrackColor = Pocket.SurfaceHighest),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatClock(windowStart), style = MaterialTheme.typography.labelSmall, color = Pocket.TextSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(formatClock(windowEnd), style = MaterialTheme.typography.labelSmall, color = Pocket.TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (list != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { burnSubtitles = !burnSubtitles }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(burnSubtitles, onCheckedChange = { burnSubtitles = it }, colors = CheckboxDefaults.colors(checkedColor = Pocket.Accent))
                    Text(stringResource(R.string.moment_subtitles), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (tooLong) Text(stringResource(R.string.moment_too_long, MAX_CLIP_MS / 60_000), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (failed) Text(stringResource(R.string.moment_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            val ready = startMs != null && endMs != null && endMs > startMs && !tooLong
            if (making != null) {
                Text(stringResource(R.string.moment_creating, (progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, color = Pocket.Accent, trackColor = Pocket.SurfaceHighest, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
            } else {
                OutlinedButton(
                    onClick = {
                        if (previewing) { connection.pause(); previewing = false }
                        else if (startMs != null) { connection.seekTo(startMs); connection.resume(); previewing = true }
                    },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (ready) Pocket.TextPrimary else Pocket.TextMuted)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            previewing -> stringResource(R.string.moment_stop_preview)
                            startMs == null || endMs == null -> stringResource(R.string.moment_preview_empty)
                            else -> stringResource(R.string.moment_preview, formatClock(startMs), formatClock(endMs), formatClock(endMs - startMs))
                        },
                        color = if (ready) Pocket.TextPrimary else Pocket.TextMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (previewing) { connection.pause(); previewing = false }
                        failed = false
                        progress = 0f
                        val from = startMs!!; val to = endMs!!
                        val burn = if (burnSubtitles) list?.filter { it.endMs > from && it.startMs < to } else null
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
                    colors = ButtonDefaults.buttonColors(containerColor = Pocket.Accent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.moment_create)) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Subtitle lines with their times; the selected stretch is highlighted, the line playing now marked. */
@Composable
private fun CueList(cues: List<Cue>, anchorMs: Long, first: Int?, last: Int?, onTap: (Int) -> Unit) {
    val now = cues.indexOfLast { it.startMs <= anchorMs }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (now - 2).coerceAtLeast(0))
    val selected = first?.let { a -> a..(last ?: a) }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), state = listState) {
        itemsIndexed(cues) { i, cue ->
            val inRange = selected != null && i in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (inRange) Pocket.AccentSoft else Color.Transparent)
                    .clickable { onTap(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    formatClock(cue.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (inRange) Pocket.Accent else Pocket.TextMuted,
                    modifier = Modifier.width(44.dp).padding(top = 2.dp),
                )
                Text(
                    cue.text.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        inRange -> Pocket.TextPrimary
                        i == now -> Pocket.TextPrimary
                        else -> Pocket.TextSecondary
                    },
                    modifier = Modifier.weight(1f),
                )
                if (i == now) Box(Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Pocket.Accent))
            }
        }
    }
}

/** The video's subtitle lines in [language]: a saved copy first, then the network; null if there are none. */
private suspend fun loadCues(pocket: app.jwpocket.phone.ui.Pocket, video: Video, language: String, libraryLanguage: String): List<Cue>? {
    if (language == Settings.SUBTITLES_OFF) return null
    val saved: File? = pocket.media.downloads.savedSubtitle(video.id, language)
    val text = saved?.readText() ?: run {
        val fresh = pocket.core.library.findLoaded(video.id) ?: video
        val url = if (language == libraryLanguage) fresh.subtitleUrl else pocket.core.subtitles.url(fresh, language)
        url?.let { pocket.core.api.getText(it) }
    }
    return text?.let(WebVtt::parse)
}
