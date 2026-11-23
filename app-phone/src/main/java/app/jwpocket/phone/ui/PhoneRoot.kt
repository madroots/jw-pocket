package app.jwpocket.phone.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.phone.ui.details.DetailsSheet
import app.jwpocket.phone.ui.player.FullPlayer
import app.jwpocket.phone.ui.player.MiniPlayer
import app.jwpocket.phone.ui.screens.BrowseScreen
import app.jwpocket.phone.ui.screens.CategoryScreen
import app.jwpocket.phone.ui.screens.HomeScreen
import app.jwpocket.phone.ui.screens.LanguageScreen
import app.jwpocket.phone.ui.screens.LibraryScreen
import app.jwpocket.phone.ui.screens.LibraryTab
import app.jwpocket.phone.ui.screens.SearchScreen
import app.jwpocket.phone.ui.screens.SectionScreen
import app.jwpocket.phone.ui.screens.SettingsScreen
import app.jwpocket.phone.ui.screens.SubtitleLanguageScreen
import app.jwpocket.phone.ui.screens.categoryTitle
import app.jwpocket.phone.ui.theme.Pocket

/** Window-level player state shared with the activity (picture-in-picture, rotation). */
class PhoneUiState {
    var expanded by mutableStateOf(false)
    var inPictureInPicture by mutableStateOf(false)
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Browse("browse", "Browse", Icons.Rounded.GridView),
    Search("search", "Search", Icons.Rounded.Search),
    Library("library", "Library", Icons.Rounded.VideoLibrary),
}

/** Routes that draw their own header instead of the top bar. */
private val OWN_HEADER = setOf("home", "search", "library?tab={tab}")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneRoot(ui: PhoneUiState, onPictureInPicture: () -> Unit, onToggleFullscreen: (Boolean) -> Unit) {
    val pocket = LocalPocket.current
    val connection = pocket.media.player
    val playback by connection.state.collectAsState()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var details by remember { mutableStateOf<Pair<Video, List<Video>>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    val progressState = pocket.core.progress.states.collectAsState()
    val downloadsState = pocket.media.downloads.items.collectAsState()
    val favoritesState = pocket.core.favorites.ids.collectAsState()
    val marks = remember(progressState, downloadsState, favoritesState) { VideoMarks(progressState, downloadsState, favoritesState) }
    val actions = remember(connection) {
        VideoActions(
            open = { video, row -> details = video to row },
            play = { video, row, fromStart ->
                connection.play(video, row, fromStart)
                details = null
                ui.expanded = true
            },
        )
    }

    // Download notifications need permission on Android 13+: ask at the first download.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val downloadCount = marks.downloads.value.size
    LaunchedEffect(downloadCount > 0) {
        if (downloadCount > 0 && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Full-screen video in landscape: hide the system bars.
    val view = LocalView.current
    val immersive = ui.expanded && landscape && playback.active
    DisposableEffect(immersive) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    LaunchedEffect(playback.active) { if (!playback.active) ui.expanded = false }

    val updates by pocket.core.updates.state.collectAsState()
    LaunchedEffect(updates.message) {
        updates.message?.let {
            snackbar.showSnackbar(it)
            pocket.core.updates.consumeMessage()
        }
    }

    // Text and icons default to light colours everywhere, also on the full player layer.
    CompositionLocalProvider(LocalMarks provides marks, LocalVideoActions provides actions, LocalContentColor provides Pocket.TextPrimary) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                containerColor = Pocket.Background,
                topBar = {
                    if (route != null && route !in OWN_HEADER) {
                        TopAppBar(
                            title = { Text(titleFor(route, entry?.arguments), maxLines = 1) },
                            navigationIcon = {
                                if (route != "browse") IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Pocket.Background),
                        )
                    }
                },
                bottomBar = {
                    Column {
                        // Only one picture can show the video: the mini player steps aside while the full one is open.
                        if (playback.active && !ui.expanded) MiniPlayer(playback, onExpand = { ui.expanded = true })
                        NavigationBar(containerColor = Pocket.Surface, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = {
                                        tab = t
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        val active = marks.downloads.value.values.count { it.isActive }
                                        if (t == Tab.Library && active > 0) BadgedBox(badge = { Badge(containerColor = Pocket.Accent) { Text("$active") } }) { Icon(t.icon, t.label) }
                                        else Icon(t.icon, t.label)
                                    },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = Pocket.SurfaceHighest,
                                        unselectedIconColor = Pocket.TextMuted,
                                        unselectedTextColor = Pocket.TextMuted,
                                    ),
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                PocketNavHost(nav, padding, onTab = { tab = it })
            }

            AnimatedVisibility(
                visible = ui.expanded && playback.active,
                enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
            ) {
                FullPlayer(
                    state = playback,
                    landscape = landscape,
                    onCollapse = { ui.expanded = false; onToggleFullscreen(false) },
                    onPictureInPicture = onPictureInPicture,
                    onToggleFullscreen = { onToggleFullscreen(!landscape) },
                )
            }
        }

        details?.let { (video, row) ->
            DetailsSheet(video, onPlay = { fromStart -> actions.play(video, row, fromStart) }, onDismiss = { details = null })
        }

        if (updates.showDialog) {
            val update = updates.available!!
            AlertDialog(
                onDismissRequest = pocket.core.updates::dismiss,
                title = { Text("Update available: ${update.versionName}") },
                text = { Text(update.notes ?: "A new version of JW Pocket is ready (${formatBytes(update.sizeBytes)}).") },
                confirmButton = { TextButton(onClick = pocket.core.updates::install) { Text("Update", color = Pocket.Accent) } },
                dismissButton = { TextButton(onClick = pocket.core.updates::dismiss) { Text("Later") } },
                containerColor = Pocket.SurfaceHigh,
            )
        }
        updates.downloadProgress?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading update") },
                text = {
                    Column {
                        LinearProgressIndicator(progress = { p }, color = Pocket.Accent, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${(p * 100).toInt()}%")
                    }
                },
                confirmButton = {},
                containerColor = Pocket.SurfaceHigh,
            )
        }
    }
}

@Composable
private fun titleFor(route: String, args: android.os.Bundle?): String = when {
    route == "browse" -> "Browse"
    route == "settings" -> "Settings"
    route == "language" -> "Library language"
    route == "subtitles" -> "Subtitle language"
    route.startsWith("category/") -> categoryTitle(args?.getString("key").orEmpty())
    route.startsWith("section/") -> categoryTitle(args?.getString("cat").orEmpty(), args?.getString("sec"))
    else -> ""
}

@Composable
private fun PocketNavHost(nav: NavHostController, padding: androidx.compose.foundation.layout.PaddingValues, onTab: (Tab) -> Unit) {
    val openCategory: (String) -> Unit = { key -> nav.navigate("category/$key") }
    NavHost(
        nav,
        startDestination = "home",
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
    ) {
        composable("home") {
            HomeScreen(
                contentPadding = padding,
                onOpenSettings = { nav.navigate("settings") },
                onOpenCategory = { openCategory(it.key) },
                onOpenDownloads = { onTab(Tab.Library); nav.navigate("library?tab=${LibraryTab.Downloads.name}") { launchSingleTop = true } },
                onOpenFavorites = { onTab(Tab.Library); nav.navigate("library?tab=${LibraryTab.Favorites.name}") { launchSingleTop = true } },
            )
        }
        composable("browse") { BrowseScreen(padding) { openCategory(it.key) } }
        composable("search") { SearchScreen(padding) { openCategory(it.key) } }
        composable("library?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = LibraryTab.Downloads.name })) { e ->
            val initial = runCatching { LibraryTab.valueOf(e.arguments?.getString("tab").orEmpty()) }.getOrDefault(LibraryTab.Downloads)
            LibraryScreen(padding, initial, onBrowse = { onTab(Tab.Browse); nav.navigate("browse") { launchSingleTop = true } })
        }
        composable("settings") {
            SettingsScreen(
                padding,
                onOpenLanguage = { nav.navigate("language") },
                onOpenSubtitles = { nav.navigate("subtitles") },
                onOpenDownloads = { onTab(Tab.Library); nav.navigate("library?tab=${LibraryTab.Downloads.name}") { launchSingleTop = true } },
            )
        }
        composable("language") { LanguageScreen(padding) { nav.popBackStack() } }
        composable("subtitles") { SubtitleLanguageScreen(padding) { nav.popBackStack() } }
        composable("category/{key}") { e ->
            val key = e.arguments?.getString("key").orEmpty()
            CategoryScreen(key, padding) { section -> nav.navigate("section/$key/$section") }
        }
        composable("section/{cat}/{sec}") { e ->
            SectionScreen(e.arguments?.getString("cat").orEmpty(), e.arguments?.getString("sec").orEmpty(), padding)
        }
    }
}
