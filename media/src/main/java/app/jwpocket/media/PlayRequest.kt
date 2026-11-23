package app.jwpocket.media

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.PocketJson

/**
 * What the app asks the player service to play. The app only says which
 * video, in which language, quality and subtitle choice; the service turns
 * it into a playable item (download or stream, which subtitle file).
 */
data class PlayRequest(
    val video: Video,
    /** Library language the video was fetched in. */
    val language: String,
    /** null = Auto. */
    val quality: String?,
    /** See Settings.subtitleLanguage. */
    val subtitleChoice: String?,
) {
    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString(KEY_VIDEO, PocketJson.encodeToString(Video.serializer(), video))
            putString(KEY_LANGUAGE, language)
            putString(KEY_QUALITY, quality)
            putString(KEY_SUBTITLE_CHOICE, subtitleChoice)
        }
        return MediaItem.Builder()
            .setMediaId(video.id)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .setMediaMetadata(video.metadata())
            .build()
    }

    companion object {
        internal const val KEY_VIDEO = "pocket.video"
        internal const val KEY_LANGUAGE = "pocket.language"
        internal const val KEY_QUALITY = "pocket.quality"
        internal const val KEY_SUBTITLE_CHOICE = "pocket.subtitle_choice"

        /** Filled in by the service on the playable item. */
        const val KEY_PLAYING_QUALITY = "pocket.playing_quality"
        const val KEY_SUBTITLE_LANGUAGE = "pocket.subtitle_language"
        const val KEY_SUBTITLE_MISSING = "pocket.subtitle_missing"
        const val KEY_FROM_DOWNLOAD = "pocket.from_download"

        fun from(item: MediaItem): PlayRequest? {
            val extras = item.requestMetadata.extras ?: return null
            val video = extras.getString(KEY_VIDEO)?.let {
                runCatching { PocketJson.decodeFromString(Video.serializer(), it) }.getOrNull()
            } ?: return null
            return PlayRequest(
                video = video,
                language = extras.getString(KEY_LANGUAGE) ?: "E",
                quality = extras.getString(KEY_QUALITY),
                subtitleChoice = extras.getString(KEY_SUBTITLE_CHOICE),
            )
        }
    }
}

internal fun Video.metadata(extras: Bundle? = null): MediaMetadata = MediaMetadata.Builder()
    .setTitle(title)
    .setDisplayTitle(title)
    .setArtist("JW Pocket")
    .setArtworkUri(posterUrl?.toUri())
    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
    .setIsPlayable(true)
    .setIsBrowsable(false)
    .apply { if (extras != null) setExtras(extras) }
    .build()
