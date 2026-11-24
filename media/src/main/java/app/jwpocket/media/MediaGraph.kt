package app.jwpocket.media

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import app.jwpocket.core.CoreGraph
import app.jwpocket.media.download.DownloadsRepository
import java.io.File

/** Implemented by each app's Application, so services can reach the shared graph. */
interface PocketGraphOwner {
    val core: CoreGraph
    val media: MediaGraph
}

val Context.pocketGraph: PocketGraphOwner get() = applicationContext as PocketGraphOwner

/**
 * The playback engine, one per process: the download store, the data sources
 * that read from it, the download manager and the connection to the player
 * service.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
class MediaGraph(context: Context, val core: CoreGraph) {

    private val appContext = context.applicationContext

    internal val databaseProvider by lazy { StandaloneDatabaseProvider(appContext) }

    /** Downloaded videos. Never evicted: they stay until the person deletes them. */
    internal val downloadCache by lazy {
        SimpleCache(File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)
    }

    internal val httpDataSource by lazy { OkHttpDataSource.Factory(core.http) }

    /**
     * Playback reads from downloads first, then the network (and local files,
     * for saved subtitles). Streaming doesn't write into the download store.
     * The phone's clip maker reads through it too, so a downloaded video is cut offline.
     */
    val playbackDataSource by lazy {
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext, httpDataSource))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Saved subtitle files: subtitles/<video>/<language>.vtt */
    internal val subtitleDir = File(appContext.filesDir, "subtitles")

    val downloads by lazy { DownloadsRepository(appContext, this) }
    val player by lazy { PlayerConnection(appContext, core, downloads) }
}
