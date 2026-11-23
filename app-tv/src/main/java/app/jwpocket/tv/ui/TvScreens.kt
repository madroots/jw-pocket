package app.jwpocket.tv.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.jwpocket.core.data.Load
import app.jwpocket.core.data.dataOrNull
import app.jwpocket.core.model.Video
import app.jwpocket.core.presentation.CategoryViewModel
import app.jwpocket.core.presentation.HomeViewModel
import app.jwpocket.core.presentation.SearchViewModel
import app.jwpocket.core.util.AppLanguage
import app.jwpocket.core.util.formatBytes
import app.jwpocket.media.download.StorageInfo
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.qualityBadge
import app.jwpocket.tv.R
import coil.compose.AsyncImage

@Composable
fun TvHome() {
    val tv = LocalTv.current
    val vm: HomeViewModel = viewModel { HomeViewModel(tv.core) }
    val latest by vm.latest.collectAsState()
    val featured by vm.featured.collectAsState()
    val continueWatching by remember { tv.core.progress.continueWatching }.collectAsState(emptyList())
    val favorites by tv.core.favorites.favorites.collectAsState()
    val downloads by tv.media.downloads.items.collectAsState()
    val online by tv.core.network.online.collectAsState()
    val downloaded = remember(downloads) { downloads.values.filter { it.isDone }.map { it.video } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        val hero = latest.dataOrNull?.firstOrNull()
        item(key = "hero") { TvHero(hero, latest.dataOrNull.orEmpty(), online, downloaded.size) }
        if (continueWatching.isNotEmpty()) item(key = "continue") { TvRow(stringResource(R.string.home_continue_watching), continueWatching.map { it.video }, 300.dp) }
        if (downloaded.isNotEmpty()) item(key = "downloaded") { TvRow(stringResource(R.string.home_ready_offline), downloaded) }
        latest.dataOrNull?.drop(1)?.takeIf { it.isNotEmpty() }?.let { item(key = "latest") { TvRow(stringResource(R.string.home_latest), it) } }
        featured.dataOrNull?.sections?.take(4)?.forEach { s -> item(key = "f_${s.key}") { TvRow(s.title, s.videos) } }
        if (favorites.isNotEmpty()) item(key = "fav") { TvRow(stringResource(R.string.home_your_favorites), favorites) }
        if (latest is Load.Loading) items(3) { TvPlaceholderRow() }
    }
}

@Composable
private fun TvHero(video: Video?, row: List<Video>, online: Boolean, downloadedCount: Int) {
    val open = LocalOpenVideo.current
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        if (video != null) {
            AsyncImage(video.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Tv.Background, 0.55f to Color(0x990B0C10), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Tv.Background)))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 48.dp, top = 40.dp).width(560.dp)) {
            if (!online) {
                Text(
                    if (downloadedCount > 0) pluralStringResource(R.plurals.home_offline_ready, downloadedCount, downloadedCount) else stringResource(R.string.home_offline),
                    color = Tv.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (video != null) {
                Text(stringResource(R.string.home_new), color = Tv.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(video.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(formatDuration(video.duration), qualityBadge(video.maxHeight), video.publishedDate.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = Tv.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                TvButton(Icons.Rounded.PlayArrow, stringResource(R.string.home_watch_now), Modifier.focusRequester(focus), primary = true) { open(video, row, focus) }
                // Only the first time the page shows: the hero leaves the list when you scroll down and
                // comes back on the way up, and grabbing focus then threw you to the top (skipping row 1).
                var focusedOnce by rememberSaveable(video.id) { mutableStateOf(false) }
                LaunchedEffect(video.id) {
                    if (!focusedOnce) {
                        focusedOnce = true
                        runCatching { focus.requestFocus() }
                    }
                }
            }
        }
    }
}

@Composable
fun TvCategory(key: String, name: String) {
    val tv = LocalTv.current
    val vm: CategoryViewModel = viewModel(key = "cat_$key") { CategoryViewModel(tv.core, key) }
    val content by vm.content.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp)) {
        item { Text(name, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)) }
        when (val state = content) {
            is Load.Ready -> items(state.data.sections, key = { it.key }) { TvRow(it.title, it.videos) }
            is Load.Failed -> item { Text(state.message, color = Tv.TextSecondary, modifier = Modifier.padding(48.dp)) }
            Load.Loading -> items(3) { TvPlaceholderRow() }
        }
    }
}

/** A grid of videos with a title and a line of detail, for library pages. */
@Composable
fun TvVideoGrid(
    title: String,
    detail: String?,
    videos: List<Video>,
    empty: String,
    header: (@Composable () -> Unit)? = null,
    subtitle: (Video) -> String? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(title, style = MaterialTheme.typography.displaySmall)
                if (detail != null) Text(detail, color = Tv.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                header?.let { Spacer(Modifier.height(16.dp)); it() }
            }
        }
        if (videos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(empty, color = Tv.TextSecondary, modifier = Modifier.padding(vertical = 32.dp))
        }
        items(videos, key = { it.id }) { TvVideoCard(it, videos, width = null, subtitle = subtitle(it)) }
    }
}

@Composable
fun TvDownloads() {
    val tv = LocalTv.current
    val items by tv.media.downloads.items.collectAsState()
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    val done = items.values.filter { it.isDone }
    LaunchedEffect(done.size) { storage = tv.media.downloads.storage() }
    val all = items.values.sortedBy { if (it.isDone) 1 else 0 }.map { it.video }
    val context = LocalContext.current
    TvVideoGrid(
        title = stringResource(R.string.nav_downloads),
        detail = pluralStringResource(R.plurals.downloads_summary, done.size, done.size, formatBytes(storage?.downloadsBytes ?: 0), formatBytes(storage?.freeBytes ?: 0)),
        videos = all,
        empty = stringResource(R.string.downloads_empty),
        subtitle = { v -> items[v.id]?.let { if (it.isDone) "${it.quality} · ${formatBytes(it.bytes)}" else context.getString(R.string.downloads_downloading_progress, (it.progress * 100).toInt()) } },
    )
}

@Composable
fun TvFavorites() {
    val favorites by LocalTv.current.core.favorites.favorites.collectAsState()
    TvVideoGrid(stringResource(R.string.nav_favorites), null, favorites, stringResource(R.string.favorites_empty))
}

@Composable
fun TvHistory() {
    val tv = LocalTv.current
    val history by remember { tv.core.progress.history }.collectAsState(emptyList())
    val watched = stringResource(R.string.common_watched)
    TvVideoGrid(stringResource(R.string.nav_history), null, history.map { it.video }, stringResource(R.string.history_empty)) { v ->
        history.firstOrNull { it.video.id == v.id }?.let { if (it.finished) watched else null }
    }
}

/**
 * Search. [listen] asks it to start the speech recognizer right away (the
 * remote's search key); [onListening] says the request was taken.
 */
@Composable
fun TvSearch(listen: Boolean, onListening: () -> Unit) {
    val tv = LocalTv.current
    val vm: SearchViewModel = viewModel { SearchViewModel(tv.core) }
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val focus = remember { FocusRequester() }
    val mic = remember { FocusRequester() }
    val voice = rememberVoiceSearch { vm.query.value = it }
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(stringResource(R.string.nav_search), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 48.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusSurface(onClick = { runCatching { focus.requestFocus() } }, modifier = Modifier.fillMaxWidth(0.6f), scale = 1.02f) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = Tv.TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Box {
                        if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = Tv.TextSecondary)
                        BasicTextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                            cursorBrush = SolidColor(Tv.Accent),
                            modifier = Modifier.focusRequester(focus).fillMaxWidth(),
                        )
                    }
                }
            }
            if (voice != null) {
                Spacer(Modifier.width(12.dp))
                FocusSurface(onClick = voice, modifier = Modifier.focusRequester(mic), color = Tv.SurfaceHigh) {
                    Icon(Icons.Rounded.Mic, stringResource(R.string.voice_search), tint = Color.White, modifier = Modifier.padding(14.dp))
                }
            }
        }
        when (val state = results) {
            null -> Text(stringResource(R.string.search_intro), color = Tv.TextSecondary, modifier = Modifier.padding(48.dp))
            Load.Loading -> Text(stringResource(R.string.search_searching), color = Tv.TextSecondary, modifier = Modifier.padding(48.dp))
            is Load.Failed -> Text(state.message, color = Tv.TextSecondary, modifier = Modifier.padding(48.dp))
            is Load.Ready -> {
                val videos = state.data.map { it.video }
                TvVideoGrid(pluralStringResource(R.plurals.search_results_count, videos.size, videos.size), null, videos, stringResource(R.string.search_no_results)) { v ->
                    state.data.firstOrNull { it.video.id == v.id }?.section
                }
            }
        }
    }
    // Opened to listen: focus rests on the mic rather than the field, so the keyboard doesn't pop up behind the recognizer.
    LaunchedEffect(Unit) { runCatching { (if (listen && voice != null) mic else focus).requestFocus() } }
    LaunchedEffect(listen) {
        if (listen) {
            onListening()
            voice?.invoke()
        }
    }
}

/**
 * The device's speech recognizer, listening in the app language, handing the
 * first thing it heard to [onText]. Null when no recognizer is installed, so
 * the mic button doesn't show.
 */
@Composable
private fun rememberVoiceSearch(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val settings = LocalTv.current.core.settings
    val prompt = stringResource(R.string.voice_search_prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
    }
    val available = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }
    if (!available) return null
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.effective(settings.current.appLanguage))
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        // The recognizer can go away between the check and the tap (disabled, updating).
        runCatching { launcher.launch(intent) }
    }
}
