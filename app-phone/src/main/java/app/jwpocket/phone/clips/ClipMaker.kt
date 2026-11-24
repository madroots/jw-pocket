package app.jwpocket.phone.clips

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.TrackOutput
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.Cue
import app.jwpocket.media.MediaGraph
import app.jwpocket.media.download.DownloadsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Cuts [startMs]..[endMs] out of a video into an MP4 on the phone. A
 * downloaded video is read from the download; otherwise only the needed part
 * of the stream is fetched. With [cues], the subtitles are drawn into the picture.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
class ClipMaker(private val context: Context, private val media: MediaGraph, private val store: ClipStore) {

    suspend fun make(
        video: Video,
        videoUrl: String?,
        startMs: Long,
        endMs: Long,
        cues: List<Cue>?,
        onProgress: (Float) -> Unit,
    ): Clip = withContext(Dispatchers.Main) { // Transformer runs on a thread with a Looper
        val id = UUID.randomUUID().toString()
        val download = media.downloads.get(video.id)?.takeIf { it.isDone }
        val file = (if (download != null) video.file(download.quality) else video.file(null))
            ?: error("No playable file")
        val item = MediaItem.Builder()
            .setUri(file.progressiveDownloadURL)
            .setCustomCacheKey(if (download != null) DownloadsRepository.cacheKey(video.id) else null)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
            .build()
        val effects = if (cues.isNullOrEmpty()) Effects.EMPTY
        else Effects(emptyList(), listOf(OverlayEffect(listOf(SubtitleOverlay(cues, startMs)))))
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(
                DefaultAssetLoaderFactory(
                    context, DefaultDecoderFactory(context), Clock.DEFAULT,
                    DefaultMediaSourceFactory(media.playbackDataSource, SdrColorExtractors), DataSourceBitmapLoader(context),
                )
            )
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        val temp = store.partFile(id)
        try {
            coroutineScope {
                val progress = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        delay(250)
                    }
                }
                suspendCancellableCoroutine { cont ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    cont.invokeOnCancellation { transformer.cancel() }
                    transformer.start(EditedMediaItem.Builder(item).setEffects(effects).build(), temp.path)
                }
                progress.cancel()
            }
            onProgress(1f)
            withContext(Dispatchers.IO) {
                val target = store.videoFile(id)
                check(temp.renameTo(target)) { "Couldn't save the clip" }
                saveThumbnail(target, store.thumbnailFile(id))
            }
        } finally {
            temp.delete()
        }
        Clip(id, video.id, video.title, videoUrl, startMs, endMs, System.currentTimeMillis()).also(store::add)
    }

    /** A frame from the middle of the clip, small, for the list. */
    private fun saveThumbnail(video: File, target: File) = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.path)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) * 1000
            val frame = retriever.getFrameAtTime(durationUs / 2) ?: return@runCatching
            val width = 480
            val scaled = Bitmap.createScaledBitmap(frame, width, (frame.height * width / frame.width.toFloat()).roundToInt(), true)
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } finally {
            retriever.release()
        }
    }
}

/**
 * The subtitle line of each moment, drawn near the bottom of the picture.
 * Times are counted from the first frame, which is the clip's start.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
private class SubtitleOverlay(private val cues: List<Cue>, private val clipStartMs: Long) : TextOverlay() {

    private var firstUs = C.TIME_UNSET
    private var textSizePx = 36

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        textSizePx = (videoSize.height * 0.052f).roundToInt().coerceAtLeast(14)
    }

    override fun getText(presentationTimeUs: Long): SpannableString {
        if (firstUs == C.TIME_UNSET) firstUs = presentationTimeUs
        val at = clipStartMs + (presentationTimeUs - firstUs) / 1000
        val cue = cues.firstOrNull { at >= it.startMs && at < it.endMs }
        // An empty bitmap isn't allowed: a space stands in when nothing is said.
        val text = SpannableString(cue?.text ?: " ")
        val all = Spannable.SPAN_INCLUSIVE_INCLUSIVE
        text.setSpan(AbsoluteSizeSpan(textSizePx), 0, text.length, all)
        text.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, all)
        text.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, text.length, all)
        if (cue != null) text.setSpan(BackgroundColorSpan(0xB3000000.toInt()), 0, text.length, all)
        return text
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = SETTINGS

    companion object {
        // The text's bottom edge sits a little above the bottom of the picture.
        private val SETTINGS = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, -0.86f)
            .setOverlayFrameAnchor(0f, -1f)
            .build()
    }
}

/**
 * jw.org tags its videos' colour as gamma 2.2 ("bt470m"), which the effects
 * pipeline refuses as input, though the picture is plain SDR BT.709. The clip
 * maker reads the video with that tag replaced; nothing else changes.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
private object SdrColorExtractors : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> = defaults.createExtractors().map(::SdrColorExtractor).toTypedArray()

    private class SdrColorExtractor(private val inner: Extractor) : Extractor by inner {
        override fun init(output: ExtractorOutput) = inner.init(object : ExtractorOutput by output {
            override fun track(id: Int, type: Int): TrackOutput {
                val track = output.track(id, type)
                if (type != C.TRACK_TYPE_VIDEO) return track
                return object : TrackOutput by track {
                    override fun format(format: Format) = track.format(sdr(format))
                }
            }
        })

        override fun getUnderlyingImplementation(): Extractor = inner.underlyingImplementation
    }

    private fun sdr(format: Format): Format {
        val color = format.colorInfo
        if (color != null && (ColorInfo.isTransferHdr(color) || color.colorTransfer == C.COLOR_TRANSFER_SDR)) return format
        return format.buildUpon()
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT709)
                    .setColorRange(color?.colorRange?.takeIf { it != Format.NO_VALUE } ?: C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_SDR)
                    .build()
            )
            .build()
    }
}
