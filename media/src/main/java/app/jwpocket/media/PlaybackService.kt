package app.jwpocket.media

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import app.jwpocket.core.util.withAppLanguage
import app.jwpocket.media.download.DownloadsRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the one player of the app. Because it is a media session service and
 * not part of a screen, playback carries on with the screen locked or the app
 * in the background, and the system shows lock-screen and notification
 * controls. Progress is saved here too, so it is right whichever screen is open.
 * It is also a media library: Android Auto browses [LibraryTree] and plays
 * the sound of the videos through this same player.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private val graph get() = pocketGraph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    private val tree by lazy { LibraryTree(this) }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(base.withAppLanguage())

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(graph.media.playbackDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)  // keep streaming with the screen off
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(progressSaver(player))

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra(EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        session = MediaLibrarySession.Builder(this, player, Callback())
            .apply {
                if (openApp != null) setSessionActivity(
                    PendingIntent.getActivity(this@PlaybackService, 0, openApp, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            .build()

        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_stat_pocket)
        })

        // Save the position every few seconds while playing.
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) saveProgress(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Swiping the app away keeps playback only while something is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            player?.let { saveProgress(it) }
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            saveProgress(player)
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private fun saveProgress(player: Player, item: MediaItem? = player.currentMediaItem, position: Long = player.currentPosition) {
        val request = item?.let { PlayRequest.from(it) } ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: request.video.durationMs
        graph.core.progress.save(request.video, position, duration)
    }

    private fun progressSaver(player: Player) = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveProgress(player)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            // Leaving a video for the next one: record where the old one ended.
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                saveProgress(player, oldPosition.mediaItem, oldPosition.positionMs)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) saveProgress(player, position = player.duration.coerceAtLeast(0))
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(tree.root(browser, params), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val children = tree.children(browser, parentId)
                ?: return@future LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_BAD_VALUE)
            val paged = if (page >= 0 && pageSize in 1 until Int.MAX_VALUE) {
                children.drop((page.toLong() * pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize)
            } else children
            LibraryResult.ofItemList(paged, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            tree.item(browser, mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE)
        }

        /** Turns requests from the app, or bare media ids from browsers, into playable items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            resolveAll(mediaItems).toMutableList()
        }

        /**
         * A browser playing one video by its id (Android Auto) gets the
         * video's folder queued around it, starting where it was left off.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()?.takeIf { PlayRequest.from(it) == null }
            val folder = single?.let { tree.queue(it.mediaId) }
            if (folder != null) {
                val (requests, index) = folder
                val position = if (startPositionMs == C.TIME_UNSET) graph.core.progress.resumePositionMs(requests[index].video.id) else startPositionMs
                MediaItemsWithStartPosition(resolveAll(requests.map { it.toMediaItem() }), index, position)
            } else {
                MediaItemsWithStartPosition(resolveAll(mediaItems), startIndex, startPositionMs)
            }
        }

        /** "Play" with nothing queued (e.g. in the car): carry on with Continue watching. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val (requests, index) = tree.resumeQueue() ?: throw UnsupportedOperationException("Nothing to resume")
            val position = graph.core.progress.resumePositionMs(requests[index].video.id)
            MediaItemsWithStartPosition(resolveAll(requests.map { it.toMediaItem() }), index, position)
        }
    }

    /** Items the player can't play (an unknown id) are left out. */
    private suspend fun resolveAll(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        items.map { item ->
            async {
                PlayRequest.from(item)?.let { resolve(it, item) }
                    ?: tree.request(item.mediaId)?.let { resolve(it, it.toMediaItem()) }
            }
        }.awaitAll().filterNotNull()
    }

    /** Download if there is one, otherwise the stream; plus the subtitle file for the choice. */
    private suspend fun resolve(request: PlayRequest, item: MediaItem): MediaItem {
        val video = request.video
        val downloads = graph.media.downloads
        val download = downloads.get(video.id)?.takeIf { it.isDone }

        val quality: String?
        val uri: Uri
        if (download != null) {
            quality = download.quality
            uri = (video.file(download.quality)?.progressiveDownloadURL ?: "").toUri()
        } else {
            val file = if (request.listening) video.smallestFile() else video.file(request.quality)
            quality = file?.label
            uri = (file?.progressiveDownloadURL ?: "").toUri()
        }

        val online = graph.core.network.online.value
        val resolved = if (online) graph.core.subtitles.resolve(video, request.language, request.subtitleChoice) else null
        val wanted = resolved?.language ?: when (request.subtitleChoice) {
            app.jwpocket.core.data.Settings.SUBTITLES_OFF -> null
            null -> request.language
            else -> request.subtitleChoice
        }
        // A saved copy beats the network: works offline and starts faster.
        val saved = wanted?.let { downloads.savedSubtitle(video.id, it) }
            ?: if (!online && request.subtitleChoice != app.jwpocket.core.data.Settings.SUBTITLES_OFF) downloads.savedSubtitle(video.id, request.language) else null
        val subtitleUri: Uri? = saved?.let { Uri.fromFile(it) } ?: resolved?.url?.toUri()
        val subtitleLanguage = when {
            saved != null -> saved.nameWithoutExtension
            else -> resolved?.language
        }
        if (download != null && resolved?.url != null && resolved.language != null && saved == null) {
            // Downloaded video watched with a new subtitle language: keep that file too.
            scope.launch { downloads.saveSubtitle(video, resolved.language!!, resolved.url!!) }
        }

        val extras = Bundle().apply {
            putString(PlayRequest.KEY_PLAYING_QUALITY, quality)
            putString(PlayRequest.KEY_SUBTITLE_LANGUAGE, subtitleLanguage)
            putBoolean(PlayRequest.KEY_SUBTITLE_MISSING, resolved?.missing == true)
            putBoolean(PlayRequest.KEY_FROM_DOWNLOAD, download != null)
        }
        return item.buildUpon()
            .setUri(uri)
            .setMimeType(MimeTypes.VIDEO_MP4)
            // Only a finished download is read by its key; a stream must never
            // read the bytes of a download of another quality still in progress.
            .setCustomCacheKey(if (download != null) DownloadsRepository.cacheKey(video.id) else null)
            .setMediaMetadata(video.metadata(extras))
            .setSubtitleConfigurations(
                listOfNotNull(subtitleUri?.let {
                    MediaItem.SubtitleConfiguration.Builder(it)
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(subtitleLanguage)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                })
            )
            .build()
    }

    companion object {
        /** Set on the intent that opens the app from the notification. */
        const val EXTRA_OPEN_PLAYER = "app.jwpocket.OPEN_PLAYER"
    }
}
