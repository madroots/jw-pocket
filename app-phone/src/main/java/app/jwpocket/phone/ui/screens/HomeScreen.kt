package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Load
import app.jwpocket.core.data.dataOrNull
import app.jwpocket.core.model.Category
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.presentation.HomeViewModel
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.LocalVideoActions
import app.jwpocket.phone.ui.components.ContinueCard
import app.jwpocket.phone.ui.components.PlaceholderRow
import app.jwpocket.phone.ui.components.SectionHeader
import app.jwpocket.phone.ui.components.VideoRow
import app.jwpocket.phone.ui.pocketViewModel
import app.jwpocket.phone.ui.theme.Pocket
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val vm = pocketViewModel { HomeViewModel(it) }
    val pocket = LocalPocket.current
    val latest by vm.latest.collectAsState()
    val categories by vm.categories.collectAsState()
    val featured by vm.featured.collectAsState()
    val continueWatching by remember { pocket.core.progress.continueWatching }.collectAsState(emptyList())
    val favorites by pocket.core.favorites.favorites.collectAsState()
    val downloads by pocket.media.downloads.items.collectAsState()
    val online by pocket.core.network.online.collectAsState()
    // Bible chapters have their own place (Bible tab, Library › Bible).
    val downloaded = remember(downloads) { downloads.values.filter { it.isDone && !it.video.isBible }.map { it.video } }
    val refreshing = (latest as? Load.Ready)?.refreshing == true

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "header") {
                Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("JW", style = MaterialTheme.typography.headlineMedium, color = Pocket.Accent, fontWeight = FontWeight.Black)
                    Text(" Pocket", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.nav_settings)) }
                }
            }
            if (!online) item(key = "offline") { OfflineBanner(downloaded.size, onOpenDownloads) }

            val hero = latest.dataOrNull?.firstOrNull()
            if (hero != null) item(key = "hero") { Hero(hero, latest.dataOrNull.orEmpty()) }
            else if (latest is Load.Loading) item(key = "hero_ph") {
                Box(Modifier.padding(16.dp).fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp)).background(Pocket.Surface))
            }

            if (continueWatching.isNotEmpty()) item(key = "continue") {
                val row = continueWatching.map { it.video }
                SectionHeader(stringResource(R.string.home_continue_watching))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueWatching, key = { it.video.id }) { ContinueCard(it.video, it.remainingMs, row) }
                }
            }
            if (downloaded.isNotEmpty()) item(key = "downloaded") { VideoRow(stringResource(R.string.common_ready_offline), downloaded, onSeeAll = onOpenDownloads) }

            latest.dataOrNull?.drop(1)?.takeIf { it.isNotEmpty() }?.let { videos ->
                item(key = "latest") { VideoRow(stringResource(R.string.home_latest), videos) }
            }
            featured.dataOrNull?.sections?.take(3)?.forEach { section ->
                item(key = "featured_${section.key}") { VideoRow(section.title, section.videos) }
            }
            if (favorites.isNotEmpty()) item(key = "favorites") { VideoRow(stringResource(R.string.home_your_favorites), favorites, onSeeAll = onOpenFavorites) }

            categories.dataOrNull?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "browse") {
                    SectionHeader(stringResource(R.string.nav_browse))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.key }) { CategoryTile(it, Modifier.width(150.dp)) { onOpenCategory(it) } }
                    }
                }
            }

            if (latest is Load.Loading && featured is Load.Loading) items(3) { PlaceholderRow() }
            (latest as? Load.Failed)?.let { failure ->
                item(key = "error") {
                    Text(failure.message, color = Pocket.TextSecondary, modifier = Modifier.padding(24.dp))
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Hero(video: Video, row: List<Video>) {
    val actions = LocalVideoActions.current
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(16.dp))
            .background(Pocket.Surface)
            .clickable { actions.open(video, row) }
    ) {
        AsyncImage(video.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xEE000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(stringResource(R.string.home_new_badge), style = MaterialTheme.typography.labelSmall, color = Pocket.Accent)
            Spacer(Modifier.height(4.dp))
            Text(video.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(formatDuration(video.duration), style = MaterialTheme.typography.bodySmall, color = Color(0xFFDDDDDD))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { actions.play(video, row, false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_play), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OfflineBanner(downloadCount: Int, onOpenDownloads: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Pocket.SurfaceHigh)
            .clickable(onClick = onOpenDownloads)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = Pocket.TextSecondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_offline_title), style = MaterialTheme.typography.titleSmall)
            Text(
                if (downloadCount > 0) pluralStringResource(R.plurals.home_offline_ready, downloadCount, downloadCount)
                else stringResource(R.string.home_offline_empty),
                style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
            )
        }
    }
}

/** Category picture with its name over a dark fade. */
@Composable
fun CategoryTile(category: Category, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(12.dp))
            .background(Pocket.SurfaceHigh)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(category.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xDD000000))))
        Text(
            category.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
