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

    /** videoId -> state, newest first. */
    val states: StateFlow<Map<String, WatchState>> = dao.observeAll()
        .map { rows ->
            rows.mapNotNull { row ->
                row.videoJson.toVideo()?.let { row.videoId to WatchState(it, row.positionMs, row.durationMs, row.finished, row.updatedAt) }
            }.toMap(LinkedHashMap())
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Started but not finished, most recent first. Bible chapters have their own place (the Bible tab). */
    val continueWatching: Flow<List<WatchState>> = states.map { map -> map.values.filter { it.canResume && !it.video.isBible } }

    /** Everything watched or started, most recent first (without Bible chapters, tracked in the Bible tab). */
    val history: Flow<List<WatchState>> = states.map { map -> map.values.filter { !it.video.isBible } }

    fun resumePositionMs(videoId: String): Long = states.value[videoId]?.takeIf { it.canResume }?.positionMs ?: 0L

    /**
     * Records a position. Under 5 s nothing is worth resuming; within the last
     * 5 % (or 20 s) the video counts as finished and leaves Continue Watching.
     */
    fun save(video: Video, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val finished = positionMs >= durationMs * 0.95 || durationMs - positionMs < 20_000
        if (!finished && positionMs < 5_000 && states.value[video.id] == null) return
        val entity = ProgressEntity(
            videoId = video.id,
            videoJson = video.toJson(),
            positionMs = if (finished) 0 else positionMs,
            durationMs = durationMs,
            finished = finished,
            updatedAt = System.currentTimeMillis(),
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
