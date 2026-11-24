package app.jwpocket.core.model

import kotlinx.serialization.Serializable

/*
 * jw.org's publication-media API (b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS):
 * files of publications by language and format. Used for the Bible read aloud.
 */

@Serializable
data class PubMediaResponse(
    /** language -> format ("MP3", "EPUB") -> files. */
    val files: Map<String, Map<String, List<PubMediaFile>>> = emptyMap(),
)

@Serializable
data class PubMediaFile(
    val title: String = "",
    val file: PubMediaUrl? = null,
    val filesize: Long? = null,
    val duration: Double? = null,
    val booknum: Int? = null,
    val track: Int? = null,
    val mimetype: String? = null,
    val markers: PubMediaMarkers? = null,
)

@Serializable
data class PubMediaUrl(val url: String = "")

@Serializable
data class PubMediaMarkers(val markers: List<VerseMarker> = emptyList())

/** Where a verse starts in a chapter's audio ("00:01:02.345"). */
@Serializable
data class VerseMarker(val verseNumber: Int = 0, val startTime: String = "")
