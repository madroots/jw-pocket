package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Load
import app.jwpocket.core.data.dataOrNull
import app.jwpocket.core.model.Category
import app.jwpocket.core.presentation.CategoryViewModel
import app.jwpocket.core.presentation.HomeViewModel
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.components.PlaceholderRow
import app.jwpocket.phone.ui.components.VideoCard
import app.jwpocket.phone.ui.components.VideoRow
import app.jwpocket.phone.ui.pocketViewModel
import app.jwpocket.phone.ui.theme.Pocket

/** All categories as a picture grid. */
@Composable
fun BrowseScreen(contentPadding: PaddingValues, onOpenCategory: (Category) -> Unit) {
    val vm = pocketViewModel { HomeViewModel(it) }
    val categories by vm.categories.collectAsState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val state = categories) {
            is Load.Ready -> items(state.data, key = { it.key }) { CategoryTile(it) { onOpenCategory(it) } }
            is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.CloudOff, "Can't load the library", state.message) }
            Load.Loading -> items(8) { CategoryTile(Category(key = "", name = "")) {} }
        }
    }
}

/** One category: a row per section, "See all" opens the section as a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(key: String, contentPadding: PaddingValues, onOpenSection: (sectionKey: String) -> Unit) {
    val vm = pocketViewModel(key = "category_$key") { CategoryViewModel(it, key) }
    val content by vm.content.collectAsState()
    val ready = content as? Load.Ready
    PullToRefreshBox(isRefreshing = ready?.refreshing == true, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            if (ready?.offline == true) item {
                Text("Offline: showing the last saved copy.", style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(16.dp))
            }
            when (val state = content) {
                is Load.Ready -> items(state.data.sections, key = { it.key }) { section ->
                    VideoRow(section.title, section.videos, onSeeAll = if (section.videos.size > 4) ({ onOpenSection(section.key) }) else null)
                }
                is Load.Failed -> item { EmptyState(Icons.Rounded.CloudOff, "Can't load this category", state.message) }
                Load.Loading -> items(4) { PlaceholderRow() }
            }
        }
    }
}

/** Every video of one section, as a grid. */
@Composable
fun SectionScreen(categoryKey: String, sectionKey: String, contentPadding: PaddingValues) {
    val vm = pocketViewModel(key = "category_$categoryKey") { CategoryViewModel(it, categoryKey) }
    val content by vm.content.collectAsState()
    val section = content.dataOrNull?.sections?.firstOrNull { it.key == sectionKey }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val videos = section?.videos.orEmpty()
        items(videos, key = { it.id }) { VideoCard(it, videos, width = null) }
    }
}

/** Title for the top bar of a category/section route. */
@Composable
fun categoryTitle(categoryKey: String, sectionKey: String? = null): String {
    val vm = pocketViewModel(key = "category_$categoryKey") { CategoryViewModel(it, categoryKey) }
    val content by vm.content.collectAsState()
    val data = content.dataOrNull ?: return ""
    return if (sectionKey == null) data.category.name else data.sections.firstOrNull { it.key == sectionKey }?.title.orEmpty()
}
