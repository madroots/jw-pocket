package app.jwpocket.core.model

import kotlinx.serialization.Serializable

/*
 * Models of the public JW mediator API (b.jw-cdn.org/apis/mediator/v1).
 * Only the fields the apps use are declared; unknown keys are ignored.
 */

@Serializable
data class CategoryResponse(val category: Category)

@Serializable
data class Category(
    val key: String,
    val type: String = "",
    val name: String = "",
    val description: String? = null,
    val images: Map<String, ImageSet>? = null,
    val subcategories: List<Category>? = null,
    val media: List<Video>? = null,
) {
    val imageUrl: String? get() = images.bestUrl(small = true)
}

@Serializable
data class Video(
    val guid: String? = null,
    val naturalKey: String? = null,
    /** Same for every language version, e.g. "pub-jwb-143_1_VIDEO". */
    val languageAgnosticNaturalKey: String? = null,
    val primaryCategory: String? = null,
    val title: String = "",
    val description: String? = null,
    val firstPublished: String? = null,
    val duration: Double? = null,
    val files: List<MediaFile>? = null,
    val images: Map<String, ImageSet>? = null,
    /** Language codes this video is published in (each with its own subtitles). */
    val availableLanguages: List<String>? = null,
) {
    /**
     * Stable id of this language version: used for progress, favorites and
     * downloads. The natural key already includes the language.
     */
    val id: String
        get() = naturalKey
            ?: guid
            ?: files?.firstNotNullOfOrNull { it.progressiveDownloadURL }?.substringBefore('?')?.hashCode()?.toString()
            ?: title.hashCode().toString()

    val durationMs: Long get() = ((duration ?: 0.0) * 1000).toLong()

    /** The video's own subtitles (WebVTT), in the language it was fetched in. */
    val subtitleUrl: String? get() = files?.firstNotNullOfOrNull { it.subtitles?.url }

    val thumbnailUrl: String? get() = images.bestUrl(small = true)
    val posterUrl: String? get() = images.bestUrl(small = false)

    /** Quality labels offered, lowest first ("240p", "360p", "480p", "720p", ...). */
    val qualities: List<String>
        get() = files.orEmpty()
            .filter { !it.progressiveDownloadURL.isNullOrBlank() && it.label != null }
            .sortedBy { it.height }
            .mapNotNull { it.label }
            .distinct()

    fun file(quality: String?): MediaFile? {
        val playable = files.orEmpty().filter { !it.progressiveDownloadURL.isNullOrBlank() }
        if (playable.isEmpty()) return null
        if (quality != null) playable.firstOrNull { it.label.equals(quality, ignoreCase = true) }?.let { return it }
        // Auto: the best quality up to 720p, which suits phones, TVs and data plans.
        return playable.filter { it.height in 1..720 }.maxByOrNull { it.height }
            ?: playable.minByOrNull { it.height }
    }

    /** The smallest playable file, for when only the sound is heard. */
    fun smallestFile(): MediaFile? =
        files.orEmpty().filter { !it.progressiveDownloadURL.isNullOrBlank() && it.height > 0 }.minByOrNull { it.height }
            ?: file(null)

    /** Highest quality label, for "HD"/"4K" chips. */
    val maxHeight: Int get() = files.orEmpty().maxOfOrNull { it.height } ?: 0

    /** Year-month-day of first publication, or null. */
    val publishedDate: String? get() = firstPublished?.take(10)

    /** jw.org link to this video, for sharing. */
    fun shareUrl(languageCode: String): String? =
        languageAgnosticNaturalKey?.let { "https://www.jw.org/finder?srcid=share&wtlocale=$languageCode&lank=$it" }
}

@Serializable
data class MediaFile(
    val progressiveDownloadURL: String? = null,
    val label: String? = null,
    val filesize: Long? = null,
    val subtitles: SubtitleFile? = null,
) {
    val height: Int get() = label?.filter { it.isDigit() }?.toIntOrNull() ?: 0
}

@Serializable
data class SubtitleFile(val url: String? = null)

@Serializable
data class ImageSet(
    val xs: String? = null,
    val sm: String? = null,
    val md: String? = null,
    val lg: String? = null,
    val xl: String? = null,
) {
    fun small(): String? = sm ?: md ?: xs ?: lg ?: xl
    fun large(): String? = lg ?: xl ?: md ?: sm ?: xs
}

private val IMAGE_PREFERENCE = listOf("wss", "lsr", "pnr", "sqr")

internal fun Map<String, ImageSet>?.bestUrl(small: Boolean): String? {
    if (this == null) return null
    for (key in IMAGE_PREFERENCE) {
        val set = this[key] ?: continue
        val url = if (small) set.small() else set.large()
        if (!url.isNullOrBlank()) return url
    }
    return values.firstNotNullOfOrNull { if (small) it.small() else it.large() }
}

@Serializable
data class LanguageResponse(val languages: List<Language> = emptyList())

@Serializable
data class Language(
    val code: String,
    val locale: String? = null,
    val vernacular: String = "",
    val name: String = "",
    /** Sign-language versions have no subtitle files. */
    val isSignLanguage: Boolean = false,
)

@Serializable
data class MediaItemsResponse(val media: List<Video> = emptyList())

/** A titled row of videos: one subcategory of a category. */
data class Section(
    val key: String,
    val title: String,
    val videos: List<Video>,
)

/** A browsable category (e.g. "Children") with its sections. */
data class CategoryContent(
    val category: Category,
    val sections: List<Section>,
)
