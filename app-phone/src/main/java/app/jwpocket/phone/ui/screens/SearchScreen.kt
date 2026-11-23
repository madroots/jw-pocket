package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Load
import app.jwpocket.core.data.dataOrNull
import app.jwpocket.core.model.Category
import app.jwpocket.core.util.formatDuration
import app.jwpocket.core.presentation.SearchViewModel
import app.jwpocket.phone.ui.components.EmptyState
import app.jwpocket.phone.ui.components.VideoListItem
import app.jwpocket.phone.ui.pocketViewModel
import app.jwpocket.phone.ui.theme.Pocket

/** Searches the whole library by title and description. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(contentPadding: PaddingValues, onOpenCategory: (Category) -> Unit) {
    val vm = pocketViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val categories by vm.categories.collectAsState()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
        TextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text("Videos, series, songs…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Pocket.SurfaceHigh,
                unfocusedContainerColor = Pocket.SurfaceHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        when (val state = results) {
            null -> Column(Modifier.padding(16.dp)) {
                Text("Browse categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.dataOrNull.orEmpty().forEach { c -> AssistChip(onClick = { onOpenCategory(c) }, label = { Text(c.name) }) }
                }
            }
            Load.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Pocket.Accent)
                    Text("Searching the whole library…", color = Pocket.TextSecondary, modifier = Modifier.padding(top = 12.dp))
                }
            }
            is Load.Ready -> if (state.data.isEmpty()) {
                EmptyState(Icons.Rounded.SearchOff, "No results", "Try other words, or check the spelling.")
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    item { Text("${state.data.size} videos", style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    val row = state.data.map { it.video }
                    items(state.data, key = { it.video.id }) { hit ->
                        VideoListItem(hit.video, row, detail = listOf(formatDuration(hit.video.duration), hit.section).filter { it.isNotEmpty() }.joinToString(" · "))
                    }
                }
            }
            is Load.Failed -> EmptyState(Icons.Rounded.SearchOff, "Search failed", state.message)
        }
    }
}
