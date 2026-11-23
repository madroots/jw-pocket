package app.jwpocket.core.data

import app.jwpocket.core.model.Category
import app.jwpocket.core.model.CategoryContent
import app.jwpocket.core.model.Section
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.MediatorApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** What a screen shows while content loads: the last copy first, then fresh data. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    /** [refreshing]: a fresh copy is still on its way. [offline]: the fresh copy failed. */
    data class Ready<T>(val data: T, val refreshing: Boolean = false, val offline: Boolean = false) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

val <T> Load<T>.dataOrNull: T? get() = (this as? Load.Ready<T>)?.data

/** A search hit: the video and the row it was found in. */
data class SearchHit(val video: Video, val section: String, val category: String)

/**
 * The video library. Each top category (Children, Dramas, ...) comes back from
 * the API with all its rows and videos in a single request, so a category is
 * one request (the old apps made one per row), and the whole library - for
 * search - is about fifteen.
 */
class LibraryRepository(private val api: MediatorApi) {

    private val memory = ConcurrentHashMap<String, CategoryContent>()
    private val indexLock = Mutex()

    /** Top categories of the video library. */
    fun rootCategories(language: String): Flow<Load<List<Category>>> =
        cachedThenFresh(language, ROOT) { it.subcategories.orEmpty() }

    /** Newest videos, for Home. */
    fun latest(language: String): Flow<Load<List<Video>>> =
        cachedThenFresh(language, LATEST) { it.media.orEmpty() }

    /** One category with its rows. */
    fun category(language: String, key: String): Flow<Load<CategoryContent>> = flow {
        memory["$language|$key"]?.let { emit(Load.Ready(it, refreshing = true)) }
            ?: run {
                val cached = api.category(language, key, fresh = false)?.let { toContent(language, it, fresh = false) }
                if (cached != null) emit(Load.Ready(cached, refreshing = true)) else emit(Load.Loading)
            }
        val fresh = api.category(language, key, fresh = true)?.let { toContent(language, it, fresh = true) }
        when {
            fresh != null -> {
                memory["$language|$key"] = fresh
                emit(Load.Ready(fresh))
            }
            memory["$language|$key"] != null -> emit(Load.Ready(memory.getValue("$language|$key"), offline = true))
            else -> {
                val cached = api.category(language, key, fresh = false)?.let { toContent(language, it, fresh = false) }
                emit(if (cached != null) Load.Ready(cached, offline = true) else Load.Failed("Couldn't load this category. Check your connection."))
            }
        }
    }

    /**
     * Searches every video title and description of the library, loading the
     * categories not seen yet (cached copies first). Accents and case don't matter.
     */
    suspend fun search(language: String, query: String): List<SearchHit> {
        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val index = index(language)
        val seen = HashSet<String>()
        val titleHits = ArrayList<SearchHit>()
        val otherHits = ArrayList<SearchHit>()
        for (hit in index) {
            if (!seen.add(hit.video.id)) continue
            val title = normalize(hit.video.title)
            when {
                terms.all { it in title } -> titleHits += hit
                terms.all { it in title || it in normalize(hit.video.description.orEmpty()) } -> otherHits += hit
            }
        }
        return (titleHits.sortedBy { if (normalize(it.video.title).startsWith(terms.first())) 0 else 1 } + otherHits).take(200)
    }

    /** Every video of the library, loading what isn't loaded yet. */
    suspend fun index(language: String): List<SearchHit> = indexLock.withLock {
        val roots = api.category(language, ROOT, fresh = false) ?: api.category(language, ROOT, fresh = true)
        val keys = roots?.subcategories.orEmpty().map { it.key }.filter { it !in NOT_SEARCHED }
        val gate = Semaphore(4)
        coroutineScope {
            keys.map { key ->
                async {
                    gate.withPermit {
                        memory["$language|$key"]
                            ?: (api.category(language, key, fresh = false) ?: api.category(language, key, fresh = true))
                                ?.let { toContent(language, it, fresh = false) }
                                ?.also { memory["$language|$key"] = it }
                    }
                }
            }.awaitAll().filterNotNull()
        }.flatMap { content ->
            content.sections.flatMap { section -> section.videos.map { SearchHit(it, section.title, content.category.name) } }
        }
    }

    /** The freshest copy of a video loaded in this session (older saved copies may lack fields). */
    fun findLoaded(videoId: String): Video? =
        memory.values.asSequence().flatMap { it.sections.asSequence() }.flatMap { it.videos.asSequence() }
            .firstOrNull { it.id == videoId }

    fun clearMemory() = memory.clear()

    private fun <T> cachedThenFresh(language: String, key: String, map: (Category) -> T): Flow<Load<T>> = flow {
        val cached = api.category(language, key, fresh = false)
        if (cached != null) emit(Load.Ready(map(cached), refreshing = true)) else emit(Load.Loading)
        val fresh = api.category(language, key, fresh = true)
        when {
            fresh != null -> emit(Load.Ready(map(fresh)))
            cached != null -> emit(Load.Ready(map(cached), offline = true))
            else -> emit(Load.Failed("You're offline and nothing is saved yet."))
        }
    }

    /**
     * Rows of a category. Rows normally arrive with their videos; a row that
     * arrives empty (a deeper container) is loaded on its own.
     */
    private suspend fun toContent(language: String, category: Category, fresh: Boolean): CategoryContent {
        val subs = category.subcategories.orEmpty()
        if (subs.isEmpty()) {
            return CategoryContent(category, listOfNotNull(
                category.media?.takeIf { it.isNotEmpty() }?.let { Section(category.key, category.name, it) }
            ))
        }
        val gate = Semaphore(4)
        val sections = coroutineScope {
            subs.map { sub ->
                async {
                    if (!sub.media.isNullOrEmpty()) listOf(Section(sub.key, sub.name, sub.media))
                    else gate.withPermit {
                        val loaded = api.category(language, sub.key, fresh) ?: api.category(language, sub.key, !fresh)
                        when {
                            loaded == null -> emptyList()
                            !loaded.media.isNullOrEmpty() -> listOf(Section(sub.key, sub.name, loaded.media))
                            else -> loaded.subcategories.orEmpty().filter { !it.media.isNullOrEmpty() }
                                .map { Section(it.key, it.name, it.media!!) }
                        }
                    }
                }
            }.awaitAll().flatten()
        }
        return CategoryContent(category, sections)
    }

    companion object {
        const val ROOT = "VideoOnDemand"
        const val LATEST = "LatestVideos"
        /** Audio-described copies of videos found elsewhere; left out of search to avoid doubles. */
        private val NOT_SEARCHED = setOf("VODAudioDescriptions")

        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
    }
}
