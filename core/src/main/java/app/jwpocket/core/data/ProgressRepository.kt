package app.jwpocket.core.data

import app.jwpocket.core.data.db.FavoriteDao
import app.jwpocket.core.data.db.FavoriteEntity
import app.jwpocket.core.data.db.ProgressDao
import app.jwpocket.core.data.db.ProgressEntity
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.PocketJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far the person got in a video. */
data class WatchState(
    val video: Video,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)
    val canResume: Boolean get() = !finished && positionMs > 0
}

internal fun Video.toJson(): String = runCatching { PocketJson.encodeToString(Video.serializer(), this) }.getOrDefault("")
internal fun String.toVideo(): Video? = runCatching { PocketJson.decodeFromString(Video.serializer(), this) }.getOrNull()

/**
 * Resume positions and history. Kept in memory as a map (players read it
 * synchronously when they start) and saved in the database.
 */
class ProgressRepository(private val dao: ProgressDao, private val scope: CoroutineScope) {

    /** videoId -> state, newest first. Each language version (a Bible chapter in Slovak or English) is tracked on its own. */
    val states: StateFlow<Map<String, WatchState>> = dao.observeAll()
        .map { rows ->
            val byId = LinkedHashMap<String, WatchState>()
            for (row in rows) {
                val video = row.videoJson.toVideo() ?: continue
                // By the video's own id: rows 1.12.0 saved under a language-free Bible key count for their language.
                byId.putIfAbsent(video.id, WatchState(video, row.positionMs, row.durationMs, row.finished, row.updatedAt))
            }
            byId
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        // 1.12.0 kept one row per Bible chapter for every language ("bible:1:1"); give each back to its language.
        scope.launch {
            dao.observeAll().first().filter { isSharedBibleKey(it.videoId) }.forEach { row ->
                val video = row.videoJson.toVideo() ?: return@forEach
                if (dao.get(video.id) == null) dao.upsert(row.copy(videoId = video.id))
                dao.delete(row.videoId)
            }
        }
    }

    /** Started but not finished, most recent first. Bible chapters have their own place (the Bible tab). */
    val continueWatching: Flow<List<WatchState>> = states.map { map -> map.values.filter { it.canResume && !it.video.isBible } }

    /** Everything watched or started, most recent first (without Bible chapters, tracked in the Bible tab). */
    val history: Flow<List<WatchState>> = states.map { map -> map.values.filter { !it.video.isBible } }

    fun resumePositionMs(videoId: String): Long = states.value[videoId]?.takeIf { it.canResume }?.positionMs ?: 0L

    /**
     * Records a position. Under 5 s nothing is worth resuming; within the last
     * 5 % (or 20 s) the video counts as finished and leaves Continue Watching.
     * A Bible chapter once heard stays heard: hearing it again leaves no trace
     * (it doesn't become "started" again, nor the chapter to continue with).
     */
    fun save(video: Video, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        if (video.isBible && states.value[video.id]?.finished == true) return
        val finished = positionMs >= durationMs * 0.95 || durationMs - positionMs < 20_000
        if (!finished && positionMs < 5_000 && states.value[video.id] == null) return
        upsert(video, if (finished) 0 else positionMs, durationMs, finished)
    }

    /**
     * Marks [video] as watched (heard) without playing it. It keeps the time it
     * was last played (none if never), so it doesn't become "the last one
     * listened to" or count as heard this week.
     */
    fun markFinished(video: Video) =
        upsert(video, 0, video.durationMs.coerceAtLeast(1), finished = true, updatedAt = states.value[video.id]?.updatedAt ?: 0L)

    private fun upsert(video: Video, positionMs: Long, durationMs: Long, finished: Boolean, updatedAt: Long = System.currentTimeMillis()) {
        val entity = ProgressEntity(
            videoId = video.id,
            videoJson = video.toJson(),
            positionMs = positionMs,
            durationMs = durationMs,
            finished = finished,
            updatedAt = updatedAt,
        )
        scope.launch { dao.upsert(entity) }
    }

    /** Forgets the position: the video leaves Continue Watching and History. */
    fun remove(videoId: String) {
        scope.launch { dao.delete(videoId) }
    }

    fun clearHistory() {
        scope.launch { dao.clear() }
    }

    /** "bible:1:1": a chapter without its language, as 1.12.0 saved them (chapter ids are "bible:<language>:1:1"). */
    private fun isSharedBibleKey(videoId: String): Boolean =
        videoId.startsWith(Video.BIBLE_PREFIX) && videoId.removePrefix(Video.BIBLE_PREFIX).split(':').size == 2
}

class FavoritesRepository(private val dao: FavoriteDao, private val scope: CoroutineScope) {

    val favorites: StateFlow<List<Video>> = dao.observeAll()
        .map { rows -> rows.mapNotNull { it.videoJson.toVideo() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val ids: StateFlow<Set<String>> = favorites.map { list -> list.mapTo(HashSet()) { it.id } as Set<String> }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun toggle(video: Video) {
        scope.launch {
            if (dao.exists(video.id)) dao.delete(video.id)
            else dao.insert(FavoriteEntity(video.id, video.toJson(), System.currentTimeMillis()))
        }
    }
}
