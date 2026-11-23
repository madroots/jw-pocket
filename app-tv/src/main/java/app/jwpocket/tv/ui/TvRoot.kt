package app.jwpocket.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.delay

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
    // The card or button that opened the details: focus goes back to it when they close.
    var opener by remember { mutableStateOf<FocusRequester?>(null) }
    var playing by remember { mutableStateOf(false) }
    val updates by tv.core.updates.state.collectAsState()
    // Moving focus into the page: after picking a menu entry (which closes the menu) or when the opener is gone.
    val page = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    var focusPage by remember { mutableIntStateOf(0) }
    // The update's release notes, opened from the update banner.
    var whatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(focusPage) {
        if (focusPage == 0) return@LaunchedEffect
        // A new page may still be loading: keep trying briefly until something in it takes focus.
        repeat(40) {
            if (pageFocused) return@LaunchedEffect
            runCatching { page.requestFocus() }
            delay(50)
        }
    }

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

    CompositionLocalProvider(LocalTvMarks provides marks, LocalOpenVideo provides { v, row, from -> details = v to row; opener = from }) {
        Box(Modifier.fillMaxSize().background(Tv.Background)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 84.dp)
                    .focusRequester(page)
                    .onFocusChanged { pageFocused = it.hasFocus }
                    .focusGroup()
            ) {
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
            Sidebar(dests, dest, activeDownloads = downloads.values.count { it.isActive }) { destId = it.id; focusPage++ }
            BackHandler(enabled = dest != Dest.Home && details == null && !playing) { destId = Dest.Home.id; focusPage++ }

            details?.let { (video, row) ->
                TvDetails(
                    video,
                    active = !playing,
                    onPlay = { fromStart, quality ->
                        connection.play(video, row, fromStart, quality)
                        playing = true
                    },
                    onClose = {
                        if (runCatching { opener?.requestFocus() }.isFailure || opener == null) focusPage++
                        details = null
                    },
                )
            }
            if (playing) {
                TvPlayer(onExit = {
                    // Leaving the player stops playback on TV; the place is saved for Resume.
                    connection.stop()
                    playing = false
                })
            }

            // Not on top of details or the player: it waits until you're back on a page.
            if (updates.showDialog && details == null && !playing) {
                val update = updates.available!!
                UpdateBanner(
                    version = update.versionName,
                    size = formatBytes(update.sizeBytes),
                    notesOpen = whatsNew,
                    onUpdate = { tv.core.updates.install(); focusPage++ },
                    onWhatsNew = if (update.notes.isNullOrBlank()) null else ({ whatsNew = true }),
                    onLater = { tv.core.updates.dismiss(); focusPage++ },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                if (whatsNew) TvSidePanel(
                    "What's new in ${update.versionName}",
                    options = listOf(PanelOption("update", "Update now", selected = true), PanelOption("back", "Back")),
                    body = update.notes,
                    onPick = { whatsNew = false; if (it.key == "update") { tv.core.updates.install(); focusPage++ } },
                    onDismiss = { whatsNew = false },
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
 * A short notice across the top of the screen when an update is ready. The
 * release notes only show if you ask for them (What's new). It takes the
 * remote when it appears; Down goes on to the page, Back means Later.
 */
@Composable
private fun UpdateBanner(
    version: String,
    size: String,
    notesOpen: Boolean,
    onUpdate: () -> Unit,
    onWhatsNew: (() -> Unit)?,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = remember { FocusRequester() }
    val notes = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var notesSeen by remember { mutableStateOf(false) }
    BackHandler(enabled = focused, onBack = onLater)
    Row(
        modifier
            .padding(top = 24.dp)
            .background(Tv.SurfaceHigh, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = Tv.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Update available", fontWeight = FontWeight.Bold)
            Text("Version $version · $size", color = Tv.TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(28.dp))
        TvButton(null, "Update", Modifier.focusRequester(update), primary = true, onClick = onUpdate)
        if (onWhatsNew != null) {
            Spacer(Modifier.width(10.dp))
            TvButton(null, "What's new", Modifier.focusRequester(notes)) { notesSeen = true; onWhatsNew() }
        }
        Spacer(Modifier.width(10.dp))
        TvButton(null, "Later", onClick = onLater)
    }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (focused) return@LaunchedEffect
            runCatching { update.requestFocus() }
            delay(50)
        }
    }
    // Back from the notes lands on What's new again.
    LaunchedEffect(notesOpen) { if (!notesOpen && notesSeen) runCatching { notes.requestFocus() } }
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
