package app.jwpocket.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.jwpocket.core.data.dataOrNull
import app.jwpocket.core.model.Video
import app.jwpocket.core.presentation.HomeViewModel
import app.jwpocket.core.util.formatBytes

/** Where the sidebar can take you. Categories come from the library. */
sealed interface Dest {
    val id: String
    data object Search : Dest { override val id = "search" }
    data object Home : Dest { override val id = "home" }
    data object Downloads : Dest { override val id = "downloads" }
    data object Favorites : Dest { override val id = "favorites" }
    data object History : Dest { override val id = "history" }
    data object Settings : Dest { override val id = "settings" }
    data class Category(val key: String, val name: String) : Dest { override val id = "cat_$key" }
}

@Composable
fun TvRoot(openPlayer: Boolean, onPlayerOpened: () -> Unit) {
    val tv = LocalTv.current
    val connection = tv.media.player
    val homeVm: HomeViewModel = viewModel { HomeViewModel(tv.core) }
    val categories by homeVm.categories.collectAsState()
    val downloads by tv.media.downloads.items.collectAsState()
    var destId by rememberSaveable { mutableStateOf(Dest.Home.id) }
    var details by remember { mutableStateOf<Pair<Video, List<Video>>?>(null) }
    var playing by remember { mutableStateOf(false) }
    val updates by tv.core.updates.state.collectAsState()

    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            if (connection.state.value.active) playing = true
            onPlayerOpened()
        }
    }

    val progressState = tv.core.progress.states.collectAsState()
    val favoritesState = tv.core.favorites.ids.collectAsState()
    val downloadsState = tv.media.downloads.items.collectAsState()
    val marks = remember(progressState, favoritesState, downloadsState) { TvMarks(progressState, downloadsState, favoritesState) }

    val categoryDests = categories.dataOrNull.orEmpty().map { Dest.Category(it.key, it.name) }
    val dests = listOf(Dest.Search, Dest.Home, Dest.Downloads, Dest.Favorites, Dest.History) + categoryDests + Dest.Settings
    val dest = dests.firstOrNull { it.id == destId } ?: Dest.Home

    CompositionLocalProvider(LocalTvMarks provides marks, LocalOpenVideo provides { v, row -> details = v to row }) {
        Box(Modifier.fillMaxSize().background(Tv.Background)) {
            Box(Modifier.fillMaxSize().padding(start = 84.dp)) {
                when (dest) {
                    Dest.Search -> TvSearch()
                    Dest.Home -> TvHome()
                    Dest.Downloads -> TvDownloads()
                    Dest.Favorites -> TvFavorites()
                    Dest.History -> TvHistory()
                    Dest.Settings -> TvSettings()
                    is Dest.Category -> TvCategory(dest.key, dest.name)
                }
            }
            Sidebar(dests, dest, activeDownloads = downloads.values.count { it.isActive }) { destId = it.id }
            BackHandler(enabled = dest != Dest.Home && details == null && !playing) { destId = Dest.Home.id }

            details?.let { (video, row) ->
                TvDetails(
                    video,
                    onPlay = { fromStart, quality ->
                        connection.play(video, row, fromStart, quality)
                        playing = true
                    },
                    onClose = { details = null },
                )
            }
            if (playing) {
                TvPlayer(onExit = {
                    // Leaving the player stops playback on TV; the place is saved for Resume.
                    connection.stop()
                    playing = false
                })
            }

            if (updates.showDialog) {
                val update = updates.available!!
                TvSidePanel(
                    "Update available: ${update.versionName}",
                    update.notes ?: "A new version of JW Pocket is ready (${formatBytes(update.sizeBytes)}).",
                    listOf(PanelOption("update", "Update now", selected = true), PanelOption("later", "Later")),
                    onPick = { if (it.key == "update") tv.core.updates.install() else tv.core.updates.dismiss() },
                    onDismiss = tv.core.updates::dismiss,
                )
            }
            updates.downloadProgress?.let { p ->
                Text(
                    "Downloading update… ${(p * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp).background(Tv.Surface).padding(16.dp),
                )
            }
        }
    }
}

/**
 * Icons on the left; it widens to show names while the remote is on it,
 * drawn over the content so the page underneath doesn't move.
 */
@Composable
private fun Sidebar(dests: List<Dest>, current: Dest, activeDownloads: Int, onSelect: (Dest) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (focused) 260.dp else 84.dp, tween(180), label = "sidebar")
    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (focused) Brush.horizontalGradient(0f to Color(0xF20B0C10), 0.85f to Color(0xE60B0C10), 1f to Color.Transparent)
                else Brush.horizontalGradient(0f to Tv.Background, 1f to Tv.Background)
            )
            .onFocusChanged { focused = it.hasFocus }
    ) {
        LazyColumn(Modifier.fillMaxHeight().padding(vertical = 24.dp)) {
            item {
                Row(Modifier.padding(start = 26.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("JW", color = Tv.Accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    if (focused) Text(" Pocket", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            items(dests, key = { it.id }) { d ->
                val selected = d.id == current.id
                FocusSurface(
                    onClick = { onSelect(d) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth(),
                    scale = 1.0f,
                    color = if (selected) Color(0x33FF3D5A) else Color.Transparent,
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconFor(d), null, tint = if (selected) Tv.Accent else Color(0xFFD0D3DA), modifier = Modifier.size(22.dp))
                        if (focused) {
                            Spacer(Modifier.width(16.dp))
                            Text(labelFor(d), maxLines = 1, color = Color.White, modifier = Modifier.weight(1f))
                            if (d == Dest.Downloads && activeDownloads > 0) Text("$activeDownloads", color = Tv.Accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (d == Dest.History) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun labelFor(d: Dest) = when (d) {
    Dest.Search -> "Search"
    Dest.Home -> "Home"
    Dest.Downloads -> "Downloads"
    Dest.Favorites -> "Favorites"
    Dest.History -> "History"
    Dest.Settings -> "Settings"
    is Dest.Category -> d.name
}

private fun iconFor(d: Dest): ImageVector = when (d) {
    Dest.Search -> Icons.Rounded.Search
    Dest.Home -> Icons.Rounded.Home
    Dest.Downloads -> Icons.Rounded.DownloadForOffline
    Dest.Favorites -> Icons.Rounded.Favorite
    Dest.History -> Icons.Rounded.History
    Dest.Settings -> Icons.Rounded.Settings
    is Dest.Category -> when {
        "Studio" in d.key -> Icons.Rounded.LiveTv
        "Children" in d.key -> Icons.Rounded.ChildCare
        "Teen" in d.key -> Icons.Rounded.Groups
        "Family" in d.key -> Icons.Rounded.FamilyRestroom
        "Program" in d.key -> Icons.Rounded.Event
        "Bible" in d.key -> Icons.Rounded.AutoStories
        "Movies" in d.key -> Icons.Rounded.Movie
        "Music" in d.key -> Icons.Rounded.MusicNote
        "IntExp" in d.key || "Audio" in d.key -> Icons.Rounded.RecordVoiceOver
        "Ministry" in d.key || "Activities" in d.key -> Icons.Rounded.VolunteerActivism
        else -> Icons.Rounded.VideoLibrary
    }
}
