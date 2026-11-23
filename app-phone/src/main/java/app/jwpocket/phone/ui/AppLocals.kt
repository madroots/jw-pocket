package app.jwpocket.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.jwpocket.core.CoreGraph
import app.jwpocket.core.data.WatchState
import app.jwpocket.core.model.Video
import app.jwpocket.media.MediaGraph
import app.jwpocket.media.download.DownloadItem

/** The shared graph, for screens and their ViewModels. */
@Stable
class Pocket(val core: CoreGraph, val media: MediaGraph)

val LocalPocket = staticCompositionLocalOf<Pocket> { error("Pocket graph not provided") }

/**
 * Per-video marks shown on every card: progress, download state, favourite.
 * Cards read them through [rememberMarks], so a download ticking only
 * redraws its own card.
 */
@Stable
class VideoMarks(
    val progress: State<Map<String, WatchState>>,
    val downloads: State<Map<String, DownloadItem>>,
    val favorites: State<Set<String>>,
)

val LocalMarks = staticCompositionLocalOf<VideoMarks> { error("Marks not provided") }

data class Marks(val watch: WatchState?, val download: DownloadItem?, val favorite: Boolean)

@Composable
fun rememberMarks(videoId: String): State<Marks> {
    val marks = LocalMarks.current
    return remember(videoId, marks) {
        derivedStateOf {
            Marks(marks.progress.value[videoId], marks.downloads.value[videoId], videoId in marks.favorites.value)
        }
    }
}

/** What tapping a video does, the same on every screen. */
@Stable
class VideoActions(
    /** Opens the details sheet; [row] is the list it came from (for autoplay). */
    val open: (video: Video, row: List<Video>) -> Unit,
    val play: (video: Video, row: List<Video>, fromStart: Boolean) -> Unit,
)

val LocalVideoActions = staticCompositionLocalOf<VideoActions> { error("Actions not provided") }
