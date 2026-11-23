package app.jwpocket.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** One choice in a side panel. */
data class PanelOption(val key: String?, val label: String, val detail: String? = null, val selected: Boolean = false)

/**
 * A list of choices sliding in from the right, used for subtitles, quality,
 * language and other settings. Focus starts on the selected choice.
 */
@Composable
fun TvSidePanel(title: String, subtitle: String? = null, options: List<PanelOption>, onPick: (PanelOption) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val start = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(440.dp).background(Tv.Surface).padding(top = 40.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 32.dp))
            if (subtitle != null) Text(subtitle, color = Tv.TextSecondary, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
            Spacer(Modifier.height(16.dp))
            LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                itemsIndexed(options) { index, option ->
                    FocusSurface(
                        onClick = { onPick(option) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if (index == start) Modifier.focusRequester(focus) else Modifier),
                        scale = 1.02f,
                        color = Color.Transparent,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(option.label, fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal, color = if (option.selected) Tv.Accent else Color.White)
                                if (option.detail != null) Text(option.detail, style = MaterialTheme.typography.bodySmall, color = Tv.TextSecondary)
                            }
                            if (option.selected) Icon(Icons.Rounded.Check, null, tint = Tv.Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
