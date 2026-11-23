package app.jwpocket.core.data

import app.jwpocket.core.model.Language
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.MediatorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** The 100+ library languages, sorted by their own name. */
class LanguageRepository(private val api: MediatorApi, private val scope: CoroutineScope) {

    private val _languages = MutableStateFlow<List<Language>>(emptyList())
    val languages: StateFlow<List<Language>> = _languages.asStateFlow()

    fun load(uiLanguage: String) {
        scope.launch {
            api.languages(uiLanguage, fresh = false)?.let { _languages.value = sorted(it.languages) }
            api.languages(uiLanguage, fresh = true)?.let { _languages.value = sorted(it.languages) }
        }
    }

    fun name(code: String): String = _languages.value.firstOrNull { it.code == code }?.vernacular ?: code

    private fun sorted(list: List<Language>) = list.sortedBy { it.vernacular.lowercase() }
}

/**
 * Subtitles in any language a video is published in. Every language version
 * of a JW video is the same edit, so another version's WebVTT file lines up
 * with the video being watched.
 */
class SubtitleRepository(private val api: MediatorApi) {

    /** "lang|key" -> url, or "" when that version has no subtitles. */
    private val cache = ConcurrentHashMap<String, String>()

    fun supportsOtherLanguages(video: Video): Boolean =
        video.languageAgnosticNaturalKey != null && !video.availableLanguages.isNullOrEmpty()

    /**
     * Subtitle URL for [choice] (see [Settings.subtitleLanguage]) over [video],
     * fetched in [videoLanguage]. Falls back to the video's own subtitles when
     * the other language has none or can't be reached within [timeoutMs].
     */
    suspend fun resolve(video: Video, videoLanguage: String, choice: String?, timeoutMs: Long = 3_000): ResolvedSubtitle {
        return when {
            choice == Settings.SUBTITLES_OFF -> ResolvedSubtitle(null, null)
            choice == null || choice == videoLanguage -> ResolvedSubtitle(video.subtitleUrl, videoLanguage.takeIf { video.subtitleUrl != null })
            choice !in video.availableLanguages.orEmpty() -> ResolvedSubtitle(video.subtitleUrl, videoLanguage.takeIf { video.subtitleUrl != null }, missing = true)
            else -> {
                val url = withTimeoutOrNull(timeoutMs) { url(video, choice) }
                if (url != null) ResolvedSubtitle(url, choice)
                else ResolvedSubtitle(video.subtitleUrl, videoLanguage.takeIf { video.subtitleUrl != null }, missing = true)
            }
        }
    }

    suspend fun url(video: Video, language: String): String? {
        val key = video.languageAgnosticNaturalKey ?: return null
        val cacheKey = "$language|$key"
        cache[cacheKey]?.let { return it.ifEmpty { null } }
        val version = api.mediaItem(language, key) ?: return null // not cached: try again later
        val url = version.subtitleUrl
        cache[cacheKey] = url.orEmpty()
        return url
    }

    /**
     * Languages to offer over [video]: those it exists in, minus sign languages
     * and its own language, [pinned] first, then by name.
     */
    fun otherLanguages(video: Video, ownLanguage: String, languages: List<Language>, pinned: List<String>): List<Language> {
        val available = video.availableLanguages?.toHashSet() ?: return emptyList()
        return pinnedFirst(languages.filter { it.code in available && it.code != ownLanguage && !it.isSignLanguage }, pinned)
    }

    /** Every language that can have subtitles, [pinned] first. */
    fun subtitleLanguages(languages: List<Language>, pinned: List<String>): List<Language> =
        pinnedFirst(languages.filterNot { it.isSignLanguage }, pinned)

    private fun pinnedFirst(languages: List<Language>, pinned: List<String>): List<Language> {
        val rank = pinned.withIndex().associate { (i, code) -> code to i }
        return languages.sortedBy { it.vernacular.lowercase() }.sortedBy { rank[it.code] ?: Int.MAX_VALUE }
    }
}

/** [url] null = no subtitles. [missing]: the wanted language wasn't available. */
data class ResolvedSubtitle(val url: String?, val language: String?, val missing: Boolean = false)
