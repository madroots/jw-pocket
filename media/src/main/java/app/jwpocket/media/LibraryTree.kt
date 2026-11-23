package app.jwpocket.media

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import app.jwpocket.core.data.Load
import app.jwpocket.core.model.CategoryContent
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * The browse tree media browsers see (Android Auto above all): Continue
 * watching, Downloads, Favorites, Latest, then the library's categories,
 * those that work as sound only first. Browsers send back nothing but media
 * ids, so a video's id carries its folder ("<folder>#<video>"): playing it
 * queues that folder, and next/previous work in the car.
 *
 * [context] gives the folder names, so it should speak the app language.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
internal class LibraryTree(private val context: Context) {

    private val graph get() = context.pocketGraph

    /** "<language>|<folder>" -> the videos last listed there, to map a played id back to its folder. */
    private val listed = ConcurrentHashMap<String, List<Video>>()

    /** Root children each browser can show (Android Auto: about four tabs), by package. */
    private val rootLimits = ConcurrentHashMap<String, Int>()

    /** "<language>|<category>" -> (loaded at, content). */
    private val categories = ConcurrentHashMap<String, Pair<Long, CategoryContent>>()

    private val language get() = graph.core.settings.current.languageCode

    fun root(browser: MediaSession.ControllerInfo, params: LibraryParams?): MediaItem {
        params?.extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 0)?.takeIf { it > 0 }
            ?.let { rootLimits[browser.packageName] = it }
        val style = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        return folder(ROOT, "JW Pocket", extras = style)
    }

    /** Children of [parentId], or null when there is no such folder. */
    suspend fun children(browser: MediaSession.ControllerInfo, parentId: String): List<MediaItem>? {
        val language = language
        return when {
            parentId == ROOT -> topFolders(language).let { all ->
                val limit = rootLimit(browser, all.size) ?: return@let all
                all.take(limit - 1) + folder(BROWSE, context.getString(R.string.auto_browse))
            }
            // Root entries that didn't fit as tabs.
            parentId == BROWSE -> topFolders(language).drop((rootLimit(browser, Int.MAX_VALUE) ?: (FIXED.size + 1)) - 1)
            parentId.startsWith(CATEGORY) -> {
                val key = parentId.removePrefix(CATEGORY)
                val content = category(language, key) ?: return null
                val single = content.sections.singleOrNull()
                if (single != null) single.videos.also { listed["$language|$parentId"] = it }.map { leaf(parentId, it) }
                else content.sections.map { folder(row(key, it.key), it.title, it.videos.firstOrNull()?.thumbnailUrl) }
            }
            else -> videos(parentId, language)?.map { leaf(parentId, it) }
        }
    }

    suspend fun item(browser: MediaSession.ControllerInfo, mediaId: String): MediaItem? {
        if (LEAF in mediaId) {
            val (videos, index) = locate(mediaId) ?: return null
            return leaf(mediaId.substringBefore(LEAF), videos[index])
        }
        return when {
            mediaId == ROOT -> folder(ROOT, "JW Pocket")
            mediaId == BROWSE -> folder(BROWSE, context.getString(R.string.auto_browse))
            mediaId in FIXED -> fixedFolder(mediaId)
            mediaId.startsWith(CATEGORY) -> topFolders(language).firstOrNull { it.mediaId == mediaId }
            mediaId.startsWith(ROW) -> {
                val categoryId = CATEGORY + mediaId.removePrefix(ROW).substringBefore(':')
                children(browser, categoryId)?.firstOrNull { it.mediaId == mediaId }
            }
            else -> null
        }
    }

    /**
     * What playing [mediaId] queues: videos of its folder around it, and the
     * index of the chosen one. A bare video id gives just that video.
     */
    suspend fun queue(mediaId: String): Pair<List<PlayRequest>, Int>? {
        val (videos, index) = locate(mediaId) ?: return null
        val from = (index - QUEUE_AROUND).coerceAtLeast(0)
        val window = videos.subList(from, minOf(videos.size, index + 1 + QUEUE_AROUND))
        return window.map { request(it) } to index - from
    }

    /** One video by its media id, for browsers that add items one by one. */
    suspend fun request(mediaId: String): PlayRequest? =
        locate(mediaId)?.let { (videos, index) -> request(videos[index]) }

    /** For "play" with nothing queued (e.g. the car starting): the Continue watching list. */
    suspend fun resumeQueue(): Pair<List<PlayRequest>, Int>? {
        val videos = videos(CONTINUE, language)?.takeIf { it.isNotEmpty() } ?: return null
        return queue(CONTINUE + LEAF + videos.first().id)
    }

    /** The folder's videos and the index of the one [mediaId] names. */
    private suspend fun locate(mediaId: String): Pair<List<Video>, Int>? {
        val folder = mediaId.substringBefore(LEAF, missingDelimiterValue = "")
        val videoId = mediaId.substringAfter(LEAF)
        if (folder.isNotEmpty()) {
            val language = language
            val videos = listed["$language|$folder"] ?: videos(folder, language)
            val index = videos?.indexOfFirst { it.id == videoId } ?: -1
            if (videos != null && index >= 0) return videos to index
        }
        return find(videoId)?.let { listOf(it) to 0 }
    }

    /** A video anywhere the app keeps one, for ids whose folder is gone. */
    private fun find(videoId: String): Video? =
        graph.core.progress.states.value[videoId]?.video
            ?: graph.core.favorites.favorites.value.firstOrNull { it.id == videoId }
            ?: graph.media.downloads.get(videoId)?.video
            ?: graph.core.library.findLoaded(videoId)
            ?: listed.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == videoId } }

    private fun request(video: Video): PlayRequest {
        val settings = graph.core.settings.current
        return PlayRequest(graph.core.library.findLoaded(video.id) ?: video, settings.languageCode, null, settings.subtitleLanguage)
    }

    /** Videos of a folder that lists videos, or null for any other id. */
    private suspend fun videos(folder: String, language: String): List<Video>? {
        val core = graph.core
        val videos = when {
            folder == CONTINUE -> core.progress.states.loaded { it.isEmpty() }.values.filter { it.canResume }.map { it.video }
            folder == DOWNLOADS -> graph.media.downloads.items.loaded { it.isEmpty() }.values.filter { it.isDone }.map { it.video }
            folder == FAVORITES -> core.favorites.favorites.loaded { it.isEmpty() }
            folder == LATEST -> core.library.latest(language).settle()
            folder.startsWith(CATEGORY) -> category(language, folder.removePrefix(CATEGORY))?.sections?.singleOrNull()?.videos
            folder.startsWith(ROW) -> {
                val (categoryKey, rowKey) = folder.removePrefix(ROW).split(':', limit = 2).takeIf { it.size == 2 } ?: return null
                category(language, categoryKey)?.sections?.firstOrNull { it.key == rowKey }?.videos
            }
            else -> null
        }
        return videos?.also { listed["$language|$folder"] = it }
    }

    /** Kept a while: each step into a category or row shouldn't wait for the network again. */
    private suspend fun category(language: String, key: String): CategoryContent? {
        val cacheKey = "$language|$key"
        categories[cacheKey]?.takeIf { (at, _) -> System.currentTimeMillis() - at < CATEGORY_TTL_MS }?.let { return it.second }
        return graph.core.library.category(language, key).settle()
            ?.also { categories[cacheKey] = System.currentTimeMillis() to it }
    }

    /** Continue watching, Downloads, Favorites, Latest, then the categories. */
    private suspend fun topFolders(language: String): List<MediaItem> {
        val roots = graph.core.library.rootCategories(language).settle().orEmpty()
        // Governing Body Updates sit in a row of JW Broadcasting: given a folder of their own.
        val gbUpdates = (GB_UPDATES to folder(CATEGORY + GB_UPDATES, context.getString(R.string.auto_gb_updates))).takeIf { roots.isNotEmpty() }
        val folders = (roots.map { it.key to folder(CATEGORY + it.key, it.name, it.imageUrl) } + listOfNotNull(gbUpdates))
            .sortedBy { (key, _) -> AUDIO_FIRST.indexOf(key).takeIf { it >= 0 } ?: AUDIO_FIRST.size }
            .map { it.second }
        return FIXED.map { fixedFolder(it) } + folders
    }

    /** The browser's root limit when the root holds more than it shows; null when all fit. */
    private fun rootLimit(browser: MediaSession.ControllerInfo, count: Int): Int? =
        rootLimits[browser.packageName]?.takeIf { it in 2 until count }

    private fun fixedFolder(id: String): MediaItem = folder(
        id,
        context.getString(
            when (id) {
                CONTINUE -> R.string.auto_continue_watching
                DOWNLOADS -> R.string.auto_downloads
                FAVORITES -> R.string.auto_favorites
                else -> R.string.auto_latest
            }
        ),
    )

    private fun folder(id: String, title: String, imageUrl: String? = null, extras: Bundle? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(artwork(imageUrl))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_VIDEOS)
                    .apply { if (extras != null) setExtras(extras) }
                    .build()
            )
            .build()

    /** A playable video, with how far it was watched (the car shows it). */
    private fun leaf(folder: String, video: Video): MediaItem {
        val state = graph.core.progress.states.value[video.id]
        val extras = Bundle().apply {
            when {
                state?.finished == true -> putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED)
                state?.canResume == true -> {
                    putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED)
                    putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, state.fraction.toDouble())
                }
            }
        }
        return MediaItem.Builder()
            .setMediaId(folder + LEAF + video.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .setSubtitle(formatDuration(video.duration).ifEmpty { null })
                    .setArtworkUri(artwork(video.thumbnailUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun artwork(url: String?): Uri? = url?.let { ArtworkProvider.uri(context, it) }

    private fun row(categoryKey: String, rowKey: String) = "$ROW$categoryKey:$rowKey"

    /** The fresh copy, or the saved one when the network is slow or down. */
    private suspend fun <T> Flow<Load<T>>.settle(): T? {
        var latest: T? = null
        withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            transformWhile { load ->
                emit(load)
                load is Load.Loading || (load is Load.Ready && load.refreshing)
            }.collect { load -> if (load is Load.Ready) latest = load.data }
        }
        return latest
    }

    /** Stored lists fill in just after start-up: give them a moment before calling them empty. */
    private suspend fun <T> StateFlow<T>.loaded(isEmpty: (T) -> Boolean): T =
        withTimeoutOrNull(STORE_WAIT_MS) { first { !isEmpty(it) } } ?: value

    private companion object {
        const val ROOT = "root"
        const val CONTINUE = "continue"
        const val DOWNLOADS = "downloads"
        const val FAVORITES = "favorites"
        const val LATEST = "latest"
        /** Holds the root entries a browser can't show as tabs. */
        const val BROWSE = "browse"
        const val CATEGORY = "cat:"
        /** "row:<category>:<row>" */
        const val ROW = "row:"
        const val LEAF = '#'
        val FIXED = listOf(CONTINUE, DOWNLOADS, FAVORITES, LATEST)

        /** "News and Announcements" of JW Broadcasting: nearly all Governing Body Updates. */
        const val GB_UPDATES = "StudioNewsReports"
        /** Categories that work as sound only, first in the car. */
        val AUDIO_FIRST = listOf("VODMusicVideos", "VODAudioDescriptions", "VODMovies", GB_UPDATES, "VODStudio")

        /** Videos queued before and after the chosen one. */
        const val QUEUE_AROUND = 20
        const val LOAD_TIMEOUT_MS = 10_000L
        const val STORE_WAIT_MS = 500L
        const val CATEGORY_TTL_MS = 10 * 60_000L
    }
}
