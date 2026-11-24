package app.jwpocket.phone.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.details.DetailsSheet
import app.jwpocket.phone.ui.player.FullPlayer
import app.jwpocket.phone.ui.player.MiniPlayer
import app.jwpocket.phone.ui.screens.AlbumScreen
import app.jwpocket.phone.ui.screens.BibleBookScreen
import app.jwpocket.phone.ui.screens.BibleScreen
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

/** Window-level state shared with the activity (picture-in-picture, rotation, notification taps). */
class PhoneUiState {
    var expanded by mutableStateOf(false)
    var inPictureInPicture by mutableStateOf(false)
    /** A video whose details a tapped notification asked for; cleared once shown. */
    var openDetails by mutableStateOf<Video?>(null)
}

/** [icon] for the tab you're on, [outlined] for the others. */
private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val outlined: ImageVector) {
    Home("home", R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Browse("browse", R.string.nav_browse, Icons.Rounded.GridView, Icons.Outlined.GridView),
    Bible("bible", R.string.nav_bible, Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    Search("search", R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    Library("library", R.string.nav_library, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
}

/** Routes that draw their own header instead of the top bar. */
private val OWN_HEADER = setOf("home", "search", "library?tab={tab}", "bible")

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

    // A tapped new-video notification: show that video's details (its row is just the video).
    LaunchedEffect(ui.openDetails) {
        ui.openDetails?.let { video ->
            details = video to listOf(video)
            ui.openDetails = null
        }
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
                                if (route != "browse") IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.nav_back)) }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Pocket.Background),
                        )
                    }
                },
                bottomBar = {
                    Column {
                        // Only one picture can show the video: the mini player steps aside while the full one is open.
                        if (playback.active && !ui.expanded) MiniPlayer(playback, onExpand = { ui.expanded = true })
                        PocketTabBar {
                            val activeDownloads = marks.downloads.value.values.count { it.isActive }
                            Tab.entries.forEach { t ->
                                PocketTab(
                                    selected = tab == t,
                                    icon = t.outlined,
                                    selectedIcon = t.icon,
                                    label = stringResource(t.label),
                                    badge = if (t == Tab.Library) activeDownloads else 0,
                                    onClick = {
                                        tab = t
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
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
                title = { Text(stringResource(R.string.update_available_title, update.versionName)) },
                text = { Text(update.notes ?: stringResource(R.string.update_available_message, formatBytes(update.sizeBytes))) },
                confirmButton = { TextButton(onClick = pocket.core.updates::install) { Text(stringResource(R.string.update_install), color = Pocket.Accent) } },
                dismissButton = { TextButton(onClick = pocket.core.updates::dismiss) { Text(stringResource(R.string.update_later)) } },
                containerColor = Pocket.SurfaceHigh,
            )
        }
        updates.downloadProgress?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {
                    Column {
                        LinearProgressIndicator(progress = { p }, color = Pocket.Accent, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.common_percent, (p * 100).toInt()))
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
    route == "browse" -> stringResource(R.string.nav_browse)
    route == "settings" -> stringResource(R.string.nav_settings)
    route == "language" -> stringResource(R.string.nav_library_language)
    route == "subtitles" -> stringResource(R.string.nav_subtitle_language)
    route.startsWith("category/") -> categoryTitle(args?.getString("key").orEmpty())
    route.startsWith("section/") -> categoryTitle(args?.getString("cat").orEmpty(), args?.getString("sec"))
    route.startsWith("album/") -> categoryTitle(LibraryRepository.AUDIO, args?.getString("sec"))
    route.startsWith("bible/") -> LocalPocket.current.let { p ->
        p.core.bible.loadedBooks(p.core.settings.current.languageCode)?.firstOrNull { it.number == args?.getInt("book") }?.name.orEmpty()
    }
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
        composable("browse") { BrowseScreen(padding, onOpenCategory = { openCategory(it.key) }, onOpenAlbum = { nav.navigate("album/$it") }) }
        composable("album/{sec}") { e -> AlbumScreen(e.arguments?.getString("sec").orEmpty(), padding) }
        composable("bible") { BibleScreen(padding) { book -> nav.navigate("bible/$book") } }
        composable("bible/{book}", arguments = listOf(navArgument("book") { type = NavType.IntType })) { e ->
            BibleBookScreen(e.arguments?.getInt("book") ?: 1, padding)
        }
        composable("search") { SearchScreen(padding) { openCategory(it.key) } }
        composable("library?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = LibraryTab.Downloads.name })) { e ->
            val initial = runCatching { LibraryTab.valueOf(e.arguments?.getString("tab").orEmpty()) }.getOrDefault(LibraryTab.Downloads)
            LibraryScreen(
                padding, initial,
                onBrowse = { onTab(Tab.Browse); nav.navigate("browse") { launchSingleTop = true } },
                onOpenBible = { onTab(Tab.Bible); nav.navigate("bible") { launchSingleTop = true } },
            )
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
