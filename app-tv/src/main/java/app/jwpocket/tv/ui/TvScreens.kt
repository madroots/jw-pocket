package app.jwpocket.tv.ui

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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
import app.jwpocket.core.util.formatBytes
import app.jwpocket.media.download.StorageInfo
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.util.qualityBadge
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
        if (continueWatching.isNotEmpty()) item(key = "continue") { TvRow("Continue watching", continueWatching.map { it.video }, 300.dp) }
        if (downloaded.isNotEmpty()) item(key = "downloaded") { TvRow("Ready offline", downloaded) }
        latest.dataOrNull?.drop(1)?.takeIf { it.isNotEmpty() }?.let { item(key = "latest") { TvRow("Latest", it) } }
        featured.dataOrNull?.sections?.take(4)?.forEach { s -> item(key = "f_${s.key}") { TvRow(s.title, s.videos) } }
        if (favorites.isNotEmpty()) item(key = "fav") { TvRow("Your favorites", favorites) }
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
                    if (downloadedCount > 0) "OFFLINE · $downloadedCount DOWNLOADED VIDEOS READY" else "OFFLINE",
                    color = Tv.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (video != null) {
                Text("NEW", color = Tv.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(video.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(formatDuration(video.duration), qualityBadge(video.maxHeight), video.publishedDate.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = Tv.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                TvButton(Icons.Rounded.PlayArrow, "Watch now", Modifier.focusRequester(focus), primary = true) { open(video, row, focus) }
                LaunchedEffect(video.id) { runCatching { focus.requestFocus() } }
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
    TvVideoGrid(
        title = "Downloads",
        detail = "${done.size} ready offline · ${formatBytes(storage?.downloadsBytes ?: 0)} used · ${formatBytes(storage?.freeBytes ?: 0)} free",
        videos = all,
        empty = "Nothing downloaded yet. Open any video and choose Download to watch it without internet.",
        subtitle = { v -> items[v.id]?.let { if (it.isDone) "${it.quality} · ${formatBytes(it.bytes)}" else "Downloading ${(it.progress * 100).toInt()}%" } },
    )
}

@Composable
fun TvFavorites() {
    val favorites by LocalTv.current.core.favorites.favorites.collectAsState()
    TvVideoGrid("Favorites", null, favorites, "Videos you mark with the heart appear here.")
}

@Composable
fun TvHistory() {
    val tv = LocalTv.current
    val history by remember { tv.core.progress.history }.collectAsState(emptyList())
    TvVideoGrid("History", null, history.map { it.video }, "Videos you watch appear here.") { v ->
        history.firstOrNull { it.video.id == v.id }?.let { if (it.finished) "Watched" else null }
    }
}

@Composable
fun TvSearch() {
    val tv = LocalTv.current
    val vm: SearchViewModel = viewModel { SearchViewModel(tv.core) }
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text("Search", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 48.dp))
        Spacer(Modifier.height(16.dp))
        FocusSurface(onClick = { runCatching { focus.requestFocus() } }, modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(0.6f), scale = 1.02f) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Tv.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Box {
                    if (query.isEmpty()) Text("Type a title…", color = Tv.TextSecondary)
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
        when (val state = results) {
            null -> Text("Search every video by title or description.", color = Tv.TextSecondary, modifier = Modifier.padding(48.dp))
            Load.Loading -> Text("Searching the whole library…", color = Tv.TextSecondary, modifier = Modifier.padding(48.dp))
            is Load.Failed -> Text(state.message, color = Tv.TextSecondary, modifier = Modifier.padding(48.dp))
            is Load.Ready -> {
                val videos = state.data.map { it.video }
                TvVideoGrid("${videos.size} results", null, videos, "No results. Try other words.") { v ->
                    state.data.firstOrNull { it.video.id == v.id }?.section
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
