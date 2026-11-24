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

    /** The library language spoken in [locale] ("sk"), not a sign language; null when the library has none. */
    fun forLocale(locale: String, list: List<Language> = _languages.value): Language? =
        list.firstOrNull { !it.isSignLanguage && it.locale.equals(locale, ignoreCase = true) }

    private fun sorted(list: List<Language>) = list.sortedBy { it.vernacular.lowercase() }
}

/**
 * The other language versions of a video: their subtitles, and their own
 * files when a video is played in another language. Every language version
 * of a JW video is the same edit, so another version's WebVTT file lines up
 * with the video being watched, and the same moment is the same scene.
 */
class SubtitleRepository(private val api: MediatorApi) {

    /** "lang|key" -> that language's version. */
    private val versions = ConcurrentHashMap<String, Video>()

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
                val url = withTimeoutOrNull(timeoutMs) { version(video, choice)?.subtitleUrl }
                if (url != null) ResolvedSubtitle(url, choice)
                else ResolvedSubtitle(video.subtitleUrl, videoLanguage.takeIf { video.subtitleUrl != null }, missing = true)
            }
        }
    }

    /** [video] as published in [language], or null when it isn't or can't be reached (not cached: tried again next time). */
    suspend fun version(video: Video, language: String): Video? {
        val key = video.languageAgnosticNaturalKey ?: return null
        val cacheKey = "$language|$key"
        versions[cacheKey]?.let { return it }
        val version = api.mediaItem(language, key) ?: return null
        versions[cacheKey] = version
        return version
    }

    /** Subtitle URL of [video]'s [language] version, or null when it has none (or can't be reached). */
    suspend fun url(video: Video, language: String): String? = version(video, language)?.subtitleUrl

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
