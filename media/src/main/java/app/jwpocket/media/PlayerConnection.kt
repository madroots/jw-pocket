package app.jwpocket.media

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.jwpocket.core.CoreGraph
import app.jwpocket.core.data.BibleProgress
import app.jwpocket.core.model.SeriesOrder
import app.jwpocket.core.model.Video
import app.jwpocket.media.download.DownloadsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Everything the screens need to draw the player. */
data class PlaybackState(
    val video: Video? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val bufferedMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queue: List<Video> = emptyList(),
    val queueIndex: Int = 0,
    /** Quality actually playing ("720p"). */
    val quality: String? = null,
    /** Language of the subtitles shown, or null when off. */
    val subtitleLanguage: String? = null,
    /** The wanted subtitle language isn't published for this video. */
    val subtitleMissing: Boolean = false,
    val fromDownload: Boolean = false,
    val speed: Float = 1f,
    /** Sleep timer end (epoch ms), or -1 for "end of this video", or null. */
    val sleepAt: Long? = null,
    val error: String? = null,
    /** Nothing loaded yet: a session brought back after a restart, waiting for play. */
    val idle: Boolean = false,
    /** Streaming the smallest quality because only the sound is heard. */
    val listening: Boolean = false,
    /** The person turned listening on with the Listen button. */
    val listenByHand: Boolean = false,
    /** The language heard when another one was picked for this video (null = the library language). */
    val spokenLanguage: String? = null,
    /** That language version's title. */
    val spokenTitle: String? = null,
    /** A language picked for this video that couldn't be loaded (the library version plays). */
    val spokenMissing: String? = null,
) {
    val active: Boolean get() = video != null
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    /** Title in the language heard. */
    val title: String? get() = spokenTitle ?: video?.title
}

/**
 * The app's handle on the player service. Screens read [state] and call the
 * commands; the player itself lives in [PlaybackService], so closing a screen
 * never stops playback.
 */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
class PlayerConnection internal constructor(
    private val context: Context,
    private val core: CoreGraph,
    private val downloads: DownloadsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sessionClosed(closed: Boolean) {
        if (prefs.getBoolean(KEY_CLOSED, false) != closed) prefs.edit().putBoolean(KEY_CLOSED, closed).apply()
    }
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    /** For video surfaces (PlayerView); null until connected. */
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _cues = MutableStateFlow<String?>(null)
    /** Subtitle text on screen now, for views that draw it themselves (Listen mode). */
    val cues: StateFlow<String?> = _cues.asStateFlow()

    private var connecting: CompletableDeferred<MediaController>? = null
    private var ticker: Job? = null
    private var sleepJob: Job? = null

    /** The Listen button: smallest quality whatever the setting says. */
    private var listenByHand = false
    /** The app is out of sight while playing, so only the sound is heard. */
    private var inBackground = false

    /** Whether new and reloaded items should stream the smallest quality. */
    private fun listening(): Boolean =
        listenByHand || (inBackground && core.settings.current.listenSaver.applies(core.network.isMetered()))

    /** The Listen button in the player. */
    fun setListen(on: Boolean) {
        listenByHand = on
        _state.update { it.copy(listenByHand = on) }
        applyListening()
    }

    /**
     * The app went out of sight (true) or came back (false) while the video
     * keeps playing as sound only; with the data-saving setting on, the stream
     * drops to the smallest quality meanwhile.
     */
    fun setInBackground(background: Boolean) {
        inBackground = background
        applyListening()
    }

    /** Reloads the queue when its quality no longer matches; downloads are untouched. */
    private fun applyListening() {
        val c = _controller.value ?: return
        val current = c.currentMediaItem?.let { PlayRequest.from(it) } ?: return
        // Audio has a single file and no picture: nothing to switch.
        if (current.video.isAudio) return
        val listening = listening()
        if (current.listening == listening) return
        reload { it.copy(listening = listening && !it.video.isAudio) }
    }

    /** Connects to the service ahead of time, so the first tap plays at once. */
    fun warmUp() {
        scope.launch { controller() }
    }

    private suspend fun controller(): MediaController {
        _controller.value?.let { return it }
        connecting?.let { return it.await() }
        val deferred = CompletableDeferred<MediaController>()
        connecting = deferred
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { c ->
                    c.addListener(listener)
                    _controller.value = c
                    sync(c)
                    deferred.complete(c)
                }
                .onFailure {
                    connecting = null
                    deferred.completeExceptionally(it)
                }
        }, ContextCompat.getMainExecutor(context))
        return deferred.await()
    }

    /**
     * Plays [video]; with autoplay on, the next parts of its series follow it,
     * then the next videos of [row]. Starts where the person left off unless [fromStart].
     */
    fun play(video: Video, row: List<Video> = emptyList(), fromStart: Boolean = false, quality: String? = null) {
        sessionClosed(false)
        scope.launch {
            val c = controller()
            val settings = core.settings.current
            val queue = if (settings.autoplayNext) upNext(video, row, row) else listOf(video)
            val items = queue.map { request(it, if (it.id == video.id) quality else null).toMediaItem() }
            val position = if (fromStart) 0L else core.progress.resumePositionMs(video.id)
            c.setMediaItems(items, 0, position)
            c.prepare()
            c.play()
            if (settings.autoplayNext) addSeriesParts(c, video, row, queue)
        }
    }

    /**
     * After the app was closed: the last thing played comes back in the mini
     * player, paused where it was left (a finished Bible chapter gives the
     * next one). Nothing loads until play is pressed. Not after the person
     * closed the player.
     */
    fun restoreLastSession() {
        if (prefs.getBoolean(KEY_CLOSED, false)) return
        scope.launch {
            val c = controller()
            if (c.mediaItemCount > 0) return@launch
            val states = withTimeoutOrNull(3_000) { core.progress.states.first { it.isNotEmpty() } } ?: return@launch
            val last = states.values.maxByOrNull { it.updatedAt } ?: return@launch
            val (video, position, row) = if (last.video.isBible) {
                val chapter = core.bible.chapterOf(last.video) ?: return@launch
                val all = core.bible.allChapters(chapter.language)
                val (next, at) = BibleProgress.next(states, all) ?: return@launch
                Triple(next.video, at, all.map { it.video })
            } else {
                if (last.finished) return@launch
                Triple(last.video, last.positionMs, listOf(last.video))
            }
            if (c.mediaItemCount > 0) return@launch // something started meanwhile
            val queue = if (core.settings.current.autoplayNext) upNext(video, row, row) else listOf(video)
            c.setMediaItems(queue.map { request(it, null).toMediaItem() }, 0, position)
        }
    }

    /** [video], the later parts of its series found in [pool], then what follows it in [row]. */
    private fun upNext(video: Video, row: List<Video>, pool: List<Video>): List<Video> {
        val start = row.indexOfFirst { it.id == video.id }
        val rest = if (start >= 0) row.drop(start + 1) else emptyList()
        // Albums and the Bible go on track after track, chapter after chapter.
        val ahead = if (video.isAudio) AUDIO_QUEUE_AHEAD else QUEUE_AHEAD
        return (listOf(video) + SeriesOrder.nextParts(video, pool) + rest).distinctBy { it.id }.take(1 + ahead)
    }

    /**
     * The row a video was opened from rarely holds its whole series (Latest,
     * search, Favorites): look in the video's own category and, if that changes
     * Up next, replace what follows the video, which is already playing.
     */
    private suspend fun addSeriesParts(c: MediaController, video: Video, row: List<Video>, queued: List<Video>) {
        val category = video.primaryCategory ?: return
        if (video.isAudio || !SeriesOrder.mayBePart(video)) return
        val own = runCatching { core.library.row(core.settings.current.languageCode, category) }.getOrNull() ?: return
        val better = upNext(video, row, row + own)
        if (better.map { it.id } == queued.map { it.id }) return
        if (c.currentMediaItem?.mediaId != video.id || c.currentMediaItemIndex != 0) return
        if (c.mediaItemCount > 1) c.removeMediaItems(1, c.mediaItemCount)
        c.addMediaItems(better.drop(1).map { request(it, null).toMediaItem() })
    }

    fun togglePlay() = command {
        if (it.isPlaying) it.pause()
        else {
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0)
            startIfRestored(it)
            it.play()
        }
    }
    fun pause() = command { it.pause() }
    fun resume() = command { startIfRestored(it); it.play() }
    fun seekTo(positionMs: Long) = command { it.seekTo(positionMs); refreshPosition(it) }
    fun seekBy(deltaMs: Long) = command { it.seekTo((it.currentPosition + deltaMs).coerceIn(0, it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)); refreshPosition(it) }
    fun next() = command { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = command { if (it.currentPosition > 5_000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun playQueueItem(index: Int) = command { it.seekTo(index, 0); startIfRestored(it); it.play() }

    /** A session brought back by [restoreLastSession] loads only when played. */
    private fun startIfRestored(player: Player) {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
    }
    fun setSpeed(speed: Float) = command { it.playbackParameters = PlaybackParameters(speed) }

    /** Stops playback, closes the notification and forgets the queue. */
    fun stop() = command {
        sessionClosed(true)
        sleepJob?.cancel()
        it.stop()
        it.clearMediaItems()
        _state.value = PlaybackState()
    }

    /** Subtitle choice for this and later videos: null = original, "off", or a language code. */
    fun setSubtitles(choice: String?) {
        core.settings.setSubtitleLanguage(choice)
        reload { it.copy(subtitleChoice = core.settings.current.subtitleLanguage) }
    }

    /**
     * Plays the current video in another of its languages ([language] null or
     * the library language = back to it), from the same moment. Only this
     * video: the rest of the queue stays in the library language.
     */
    fun setSpokenLanguage(language: String?) = command { c ->
        val current = c.currentMediaItem?.let { PlayRequest.from(it) } ?: return@command
        val wanted = language?.takeIf { it != current.language }
        if (wanted == current.spoken) return@command
        if (wanted != null) core.settings.recordLanguageUse(wanted)
        val id = current.video.id
        reload { if (it.video.id == id) it.copy(spoken = wanted) else it }
    }

    /** A book introduction playing before a chapter: (intro id, chapter id, where the chapter was). */
    private var resumeAfterIntro: Triple<String, String, Long>? = null

    /**
     * Plays [intro] (a Bible book's introduction video, published in [language])
     * before the chapter now playing; when it ends, the chapter goes on from
     * where it was. A language other than the video language is heard as that
     * video's language, like a pick in the player.
     */
    fun playIntroduction(intro: Video, language: String) = command { c ->
        val chapterId = c.currentMediaItem?.mediaId ?: return@command
        val index = c.currentMediaItemIndex
        resumeAfterIntro = Triple(intro.id, chapterId, c.currentPosition)
        c.addMediaItem(index, request(intro, null).copy(spoken = language.takeIf { it != core.settings.current.languageCode }).toMediaItem())
        c.seekTo(index, 0)
        c.play()
    }

    /** Switches quality of the current video, keeping the position; picking one ends Listen. */
    fun setQuality(quality: String?) {
        listenByHand = false
        _state.update { it.copy(listenByHand = false) }
        val listening = listening()
        val currentId = _controller.value?.currentMediaItem?.mediaId
        reload { if (it.video.id == currentId) it.copy(quality = quality, listening = listening) else it.copy(listening = listening) }
    }

    /** Pauses after [minutes]; 0 = at the end of this video; null = off. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _state.update { it.copy(sleepAt = null) }
        when {
            minutes == null -> Unit
            minutes == 0 -> _state.update { it.copy(sleepAt = END_OF_VIDEO) }
            else -> {
                val at = System.currentTimeMillis() + minutes * 60_000L
                _state.update { it.copy(sleepAt = at) }
                sleepJob = scope.launch {
                    delay(minutes * 60_000L)
                    pause()
                    _state.update { it.copy(sleepAt = null) }
                }
            }
        }
    }

    private fun request(video: Video, quality: String?): PlayRequest {
        val s = core.settings.current
        // A copy loaded in this session may carry fields an older saved copy lacks.
        val fresh = core.library.findLoaded(video.id) ?: video
        return PlayRequest(fresh, if (fresh.isBible) s.bibleLanguageCode else s.languageCode, quality ?: s.streamQuality, s.subtitleLanguage, listening() && !fresh.isAudio)
    }

    /** Rebuilds every queued item with [change] (e.g. a new subtitle language). */
    private fun reload(change: (PlayRequest) -> PlayRequest) = command { c ->
        if (c.mediaItemCount == 0) return@command
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        val wasPlaying = c.playWhenReady
        val items = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            PlayRequest.from(item)?.let { change(it).toMediaItem() } ?: item
        }
        c.setMediaItems(items, index, position)
        c.prepare()
        if (wasPlaying) c.play()
    }

    private fun command(block: (MediaController) -> Unit) {
        val c = _controller.value
        if (c != null) block(c) else scope.launch { block(controller()) }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // Sleep timer "end of this video": stop before the next one starts.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.sleepAt == END_OF_VIDEO) {
                _controller.value?.pause()
                _state.update { it.copy(sleepAt = null) }
            }
            // Back from a book introduction: the chapter goes on where it was.
            resumeAfterIntro?.let { (introId, chapterId, position) ->
                when (mediaItem?.mediaId) {
                    introId -> Unit
                    chapterId -> { _controller.value?.seekTo(position); resumeAfterIntro = null }
                    else -> resumeAfterIntro = null
                }
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            _cues.value = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n").ifBlank { null }
        }

        override fun onPlayerError(error: PlaybackException) {
            val offline = !core.network.online.value
            _state.update {
                it.copy(error = if (offline) "You're offline. Download videos to watch them without internet." else "Can't play this video right now (${error.errorCodeName}).")
            }
        }
    }

    private fun sync(player: Player) {
        val item = player.currentMediaItem
        val extras = player.mediaMetadata.extras ?: item?.mediaMetadata?.extras
        // The language asked for comes from the item itself, which changes at once; the
        // service's extras can lag behind a switch (or never arrive when the rest of the
        // metadata is unchanged), so they only say whether the asked language failed.
        val requested = item?.let { PlayRequest.from(it)?.spoken }
        val spokenMissing = extras?.getString(PlayRequest.KEY_SPOKEN_MISSING)?.takeIf { it == requested }
        val spoken = requested?.takeIf { spokenMissing == null }
        val queue = (0 until player.mediaItemCount).mapNotNull { PlayRequest.from(player.getMediaItemAt(it))?.video }
        val video = item?.let { PlayRequest.from(it)?.video }
        _state.update { old ->
            old.copy(
                video = video,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                ended = player.playbackState == Player.STATE_ENDED,
                idle = player.playbackState == Player.STATE_IDLE,
                positionMs = player.currentPosition.coerceAtLeast(0),
                bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (video?.durationMs ?: 0),
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem() || player.currentPosition > 5_000,
                queue = queue,
                queueIndex = player.currentMediaItemIndex,
                quality = extras?.getString(PlayRequest.KEY_PLAYING_QUALITY),
                subtitleLanguage = extras?.getString(PlayRequest.KEY_SUBTITLE_LANGUAGE),
                subtitleMissing = extras?.getBoolean(PlayRequest.KEY_SUBTITLE_MISSING) == true,
                fromDownload = extras?.getBoolean(PlayRequest.KEY_FROM_DOWNLOAD) == true,
                listening = item?.let { PlayRequest.from(it)?.listening } == true && extras?.getBoolean(PlayRequest.KEY_FROM_DOWNLOAD) != true,
                spokenLanguage = spoken,
                spokenTitle = extras?.getString(PlayRequest.KEY_SPOKEN_TITLE)?.takeIf { spoken != null && extras.getString(PlayRequest.KEY_SPOKEN_LANGUAGE) == spoken },
                spokenMissing = spokenMissing,
                speed = player.playbackParameters.speed,
                error = if (player.playerError == null) null else old.error,
                sleepAt = if (old.sleepAt == END_OF_VIDEO && player.playbackState == Player.STATE_ENDED) null else old.sleepAt,
            )
        }
        if (player.isPlaying) startTicker(player) else ticker?.cancel()
    }

    private fun refreshPosition(player: Player) {
        _state.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0), bufferedMs = player.bufferedPosition.coerceAtLeast(0)) }
    }

    /** Position updates while playing, for seek bars. */
    private fun startTicker(player: Player) {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                refreshPosition(player)
                delay(500)
            }
        }
    }

    private companion object {
        /** How many videos of the row follow the chosen one. */
        const val QUEUE_AHEAD = 10
        /** Songs of an album, chapters of the Bible. */
        const val AUDIO_QUEUE_AHEAD = 60
        private const val PREFS = "jw_pocket_player"
        /** The person closed the player: nothing comes back at the next start. */
        private const val KEY_CLOSED = "closed"
        const val END_OF_VIDEO = -1L
    }
}
