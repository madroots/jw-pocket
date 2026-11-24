package app.jwpocket.media.download

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.PocketJson
import app.jwpocket.media.MediaGraph
import app.jwpocket.media.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.Executors

/** Saved with each download, so the Library can show it without a network. */
@Serializable
internal data class DownloadMeta(
    val videoJson: String,
    val quality: String,
    val language: String,
    val savedAt: Long,
)

enum class DownloadStatus { Queued, Downloading, Waiting, Paused, Done, Failed }

/** One download as the screens see it. */
data class DownloadItem(
    val video: Video,
    val quality: String,
    val language: String,
    val status: DownloadStatus,
    /** 0..1 */
    val progress: Float,
    val bytes: Long,
    val totalBytes: Long,
    val savedAt: Long,
) {
    val isDone: Boolean get() = status == DownloadStatus.Done
    val isActive: Boolean get() = status == DownloadStatus.Queued || status == DownloadStatus.Downloading || status == DownloadStatus.Waiting
}

data class StorageInfo(val downloadsBytes: Long, val freeBytes: Long)

/**
 * Downloads, built on Media3's download manager: they run in a service with
 * a progress notification (also with the app closed), resume after network
 * drops, can wait for Wi-Fi, and play back through the same player as streams.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
class DownloadsRepository internal constructor(
    private val context: Context,
    private val graph: MediaGraph,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val core = graph.core

    internal val manager: DownloadManager by lazy {
        DownloadManager(context, graph.databaseProvider, graph.downloadCache, graph.httpDataSource, Executors.newFixedThreadPool(3)).apply {
            maxParallelDownloads = 2
            requirements = requirementsFor(core.settings.current)
            addListener(listener)
        }
    }

    internal val notifications by lazy { DownloadNotificationHelper(context, CHANNEL_ID) }

    private val _items = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    /** videoId -> download, every state. */
    val items: StateFlow<Map<String, DownloadItem>> = _items.asStateFlow()

    private var ticker: Job? = null
    private val metaCache = HashMap<String, DownloadMeta?>()

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            refresh()
            val meta = meta(download) ?: return
            val title = meta.videoJson.toVideo()?.title ?: return
            when (download.state) {
                Download.STATE_COMPLETED -> notify(download.request.id, notifications.buildDownloadCompletedNotification(context, R.drawable.ic_stat_download, null, title))
                Download.STATE_FAILED -> notify(download.request.id, notifications.buildDownloadFailedNotification(context, R.drawable.ic_stat_download, null, title))
                else -> Unit
            }
        }
        override fun onDownloadRemoved(manager: DownloadManager, download: Download) = refresh()
        override fun onRequirementsStateChanged(manager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = refresh()
        override fun onDownloadsPausedChanged(manager: DownloadManager, downloadsPaused: Boolean) = refresh()
    }

    init {
        main.post {
            manager // create on the main thread
            refresh()
        }
        scope.launch {
            core.settings.settings.distinctUntilChangedBy { it.wifiOnlyDownloads }.collect {
                manager.requirements = requirementsFor(it)
            }
        }
    }

    fun get(videoId: String): DownloadItem? = _items.value[videoId]

    fun isDownloaded(videoId: String): Boolean = _items.value[videoId]?.isDone == true

    /**
     * Starts downloading [video] in [quality]. Its subtitles (own language, and
     * the preferred subtitle language) are saved too, for watching offline.
     */
    fun download(video: Video, quality: String, language: String) {
        val file = video.file(quality) ?: return
        val url = file.progressiveDownloadURL ?: return
        val meta = DownloadMeta(video.toJson(), file.label ?: quality, language, System.currentTimeMillis())
        val request = DownloadRequest.Builder(video.id, android.net.Uri.parse(url))
            .setMimeType(file.mimetype ?: MimeTypes.VIDEO_MP4)
            .setCustomCacheKey(cacheKey(video.id))
            .setData(PocketJson.encodeToString(DownloadMeta.serializer(), meta).toByteArray())
            .build()
        DownloadService.sendAddDownload(context, PocketDownloadService::class.java, request, false)
        _items.value = _items.value + (video.id to DownloadItem(video, meta.quality, language, DownloadStatus.Queued, 0f, 0, file.filesize ?: 0, meta.savedAt))
        scope.launch { saveSubtitles(video, language) }
    }

    fun pause(videoId: String) =
        DownloadService.sendSetStopReason(context, PocketDownloadService::class.java, videoId, STOP_PAUSED, false)

    fun resume(videoId: String) =
        DownloadService.sendSetStopReason(context, PocketDownloadService::class.java, videoId, Download.STOP_REASON_NONE, false)

    /** Retries a failed download from where it stopped. */
    fun retry(videoId: String) {
        val item = get(videoId) ?: return
        download(item.video, item.quality, item.language)
    }

    fun remove(videoId: String) {
        DownloadService.sendRemoveDownload(context, PocketDownloadService::class.java, videoId, false)
        _items.value = _items.value - videoId
        scope.launch(Dispatchers.IO) { File(graph.subtitleDir, safe(videoId)).deleteRecursively() }
    }

    fun removeAll() {
        DownloadService.sendRemoveAllDownloads(context, PocketDownloadService::class.java, false)
        _items.value = emptyMap()
        scope.launch(Dispatchers.IO) { graph.subtitleDir.deleteRecursively() }
    }

    suspend fun storage(): StorageInfo = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        StorageInfo(runCatching { graph.downloadCache.cacheSpace }.getOrDefault(0), dir.usableSpace)
    }

    /** Saved subtitle file for [language], or null. */
    fun savedSubtitle(videoId: String, language: String): File? =
        File(File(graph.subtitleDir, safe(videoId)), "$language.vtt").takeIf { it.exists() }

    /** Saves a subtitle file for a downloaded video (when a new language is picked offline-to-be). */
    suspend fun saveSubtitle(video: Video, language: String, url: String) {
        val target = File(File(graph.subtitleDir, safe(video.id)), "$language.vtt")
        if (!target.exists()) core.api.download(url, target)
    }

    private suspend fun saveSubtitles(video: Video, language: String) {
        video.subtitleUrl?.let { saveSubtitle(video, language, it) }
        val preferred = core.settings.current.subtitleLanguage
        if (preferred != null && preferred != Settings.SUBTITLES_OFF && preferred != language) {
            core.subtitles.url(video, preferred)?.let { saveSubtitle(video, preferred, it) }
        }
    }

    /** Re-reads every download; polls progress while any is running. */
    internal fun refresh() {
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                buildList {
                    runCatching {
                        manager.downloadIndex.getDownloads().use { cursor ->
                            while (cursor.moveToNext()) add(cursor.download)
                        }
                    }
                }
            }
            val live = manager.currentDownloads.associateBy { it.request.id }
            val notMet = manager.notMetRequirements != 0
            _items.value = stored.mapNotNull { stored ->
                val d = live[stored.request.id] ?: stored
                toItem(d, notMet)?.let { d.request.id to it }
            }.sortedByDescending { it.second.savedAt }.toMap(LinkedHashMap())
            if (_items.value.values.any { it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued }) startTicker() else ticker?.cancel()
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(800)
                val live = manager.currentDownloads.associateBy { it.request.id }
                if (live.isEmpty()) { refresh(); break }
                val notMet = manager.notMetRequirements != 0
                _items.value = _items.value.mapValues { (id, item) -> live[id]?.let { toItem(it, notMet) } ?: item }
            }
        }
    }

    private fun toItem(d: Download, notMet: Boolean): DownloadItem? {
        if (d.state == Download.STATE_REMOVING) return null
        val meta = meta(d) ?: return null
        val video = meta.videoJson.toVideo() ?: return null
        val status = when (d.state) {
            Download.STATE_COMPLETED -> DownloadStatus.Done
            Download.STATE_FAILED -> DownloadStatus.Failed
            Download.STATE_STOPPED -> DownloadStatus.Paused
            Download.STATE_DOWNLOADING -> if (notMet) DownloadStatus.Waiting else DownloadStatus.Downloading
            else -> if (notMet) DownloadStatus.Waiting else DownloadStatus.Queued
        }
        val total = d.contentLength.takeIf { it > 0 } ?: video.file(meta.quality)?.filesize ?: 0
        val progress = when {
            status == DownloadStatus.Done -> 1f
            d.percentDownloaded >= 0 -> d.percentDownloaded / 100f
            total > 0 -> d.bytesDownloaded.toFloat() / total
            else -> 0f
        }
        return DownloadItem(video, meta.quality, meta.language, status, progress.coerceIn(0f, 1f), d.bytesDownloaded, total, meta.savedAt)
    }

    private fun meta(d: Download): DownloadMeta? = metaCache.getOrPut(d.request.id) {
        runCatching { PocketJson.decodeFromString(DownloadMeta.serializer(), String(d.request.data)) }.getOrNull()
    }

    private fun notify(id: String, notification: android.app.Notification) {
        NotificationUtil.setNotification(context, NOTIFICATION_BASE + (id.hashCode() and 0xFFFF), notification)
    }

    private fun requirementsFor(s: Settings) =
        Requirements(if (s.wifiOnlyDownloads) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val STOP_PAUSED = 1
        private const val NOTIFICATION_BASE = 2000

        fun cacheKey(videoId: String) = "video:$videoId"
        internal fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }
}

internal fun Video.toJson(): String = PocketJson.encodeToString(Video.serializer(), this)
internal fun String.toVideo(): Video? = runCatching { PocketJson.decodeFromString(Video.serializer(), this) }.getOrNull()
