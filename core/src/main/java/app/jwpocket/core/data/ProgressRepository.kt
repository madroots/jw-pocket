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

    /**
     * Where to resume [other], another language version of this video (a Bible
     * chapter heard in another language): the same share of the way through,
     * as readings in different languages take different times.
     */
    fun positionFor(other: Video): Long = when {
        !canResume -> 0L
        other.id == video.id || durationMs <= 0 || other.durationMs <= 0 -> positionMs
        else -> (positionMs.toDouble() * other.durationMs / durationMs).toLong()
    }
}

/**
 * Progress by video, where a Bible chapter is the same chapter in every
 * language: looking up "bible:V:1:1" finds Genesis 1 heard in English too.
 */
class ProgressStates internal constructor(private val byKey: Map<String, WatchState>) : Map<String, WatchState> by byKey {
    override fun get(key: String): WatchState? = byKey[ProgressRepository.key(key)]
    override fun containsKey(key: String): Boolean = byKey.containsKey(ProgressRepository.key(key))
}

internal fun Video.toJson(): String = runCatching { PocketJson.encodeToString(Video.serializer(), this) }.getOrDefault("")
internal fun String.toVideo(): Video? = runCatching { PocketJson.decodeFromString(Video.serializer(), this) }.getOrNull()

/**
 * Resume positions and history. Kept in memory as a map (players read it
 * synchronously when they start) and saved in the database.
 */
class ProgressRepository(private val dao: ProgressDao, private val scope: CoroutineScope) {

    /** Saved row ids by progress key (a Bible chapter may have rows from several languages). */
    @Volatile private var rowIds: Map<String, List<String>> = emptyMap()

    /** progress key -> state, newest first; look up by video id (see [ProgressStates]). */
    val states: StateFlow<ProgressStates> = dao.observeAll()
        .map { rows ->
            val byKey = LinkedHashMap<String, WatchState>()
            val ids = HashMap<String, MutableList<String>>()
            for (row in rows) {
                val video = row.videoJson.toVideo() ?: continue
                val key = key(row.videoId)
                ids.getOrPut(key) { mutableListOf() } += row.videoId
                val state = WatchState(video, row.positionMs, row.durationMs, row.finished, row.updatedAt)
                val kept = byKey[key]
                // Newest first; a chapter heard in any language stays heard.
                if (kept == null) byKey[key] = state
                else if (state.finished && !kept.finished) byKey[key] = kept.copy(finished = true, positionMs = 0)
            }
            rowIds = ids
            ProgressStates(byKey)
        }
        .stateIn(scope, SharingStarted.Eagerly, ProgressStates(emptyMap()))

    /** Started but not finished, most recent first. Bible chapters have their own place (the Bible tab). */
    val continueWatching: Flow<List<WatchState>> = states.map { map -> map.values.filter { it.canResume && !it.video.isBible } }

    /** Everything watched or started, most recent first (without Bible chapters, tracked in the Bible tab). */
    val history: Flow<List<WatchState>> = states.map { map -> map.values.filter { !it.video.isBible } }

    /** Where to resume [video] (0 = from the start); a chapter last heard in another language resumes at the same share. */
    fun resumePositionMs(video: Video): Long = states.value[video.id]?.positionFor(video) ?: 0L

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
        val key = key(video.id)
        val entity = ProgressEntity(
            videoId = key,
            videoJson = video.toJson(),
            positionMs = positionMs,
            durationMs = durationMs,
            finished = finished,
            updatedAt = updatedAt,
        )
        // Rows saved per language before 1.12 give way to the one shared row.
        val older = rowIds[key].orEmpty().filter { it != key }
        scope.launch {
            older.forEach { dao.delete(it) }
            dao.upsert(entity)
        }
    }

    /** Forgets the position: the video leaves Continue Watching and History. */
    fun remove(videoId: String) {
        val key = key(videoId)
        val ids = (rowIds[key].orEmpty() + key + videoId).distinct()
        scope.launch { ids.forEach { dao.delete(it) } }
    }

    fun clearHistory() {
        scope.launch { dao.clear() }
    }

    companion object {
        /** The id progress is saved under: a Bible chapter without its language ("bible:1:1"), any other video its own id. */
        fun key(videoId: String): String =
            if (videoId.startsWith(Video.BIBLE_PREFIX)) {
                val parts = videoId.removePrefix(Video.BIBLE_PREFIX).split(':')
                if (parts.size == 3) Video.BIBLE_PREFIX + parts[1] + ":" + parts[2] else videoId
            } else videoId
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
