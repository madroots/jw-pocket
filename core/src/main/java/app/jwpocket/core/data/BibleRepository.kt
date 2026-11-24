package app.jwpocket.core.data

import app.jwpocket.core.model.MediaFile
import app.jwpocket.core.model.PubMediaFile
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.MediatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Where a verse starts in a chapter's audio. */
data class VerseMark(val verse: Int, val startMs: Long)

/** One chapter read aloud: played as a [video] (so progress, downloads and the player work as for any video). */
class BibleChapter(
    val language: String,
    val book: Int,
    val chapter: Int,
    val bookName: String,
    val video: Video,
    val verses: List<VerseMark>,
) {
    val id: String get() = video.id
    val verseCount: Int get() = verses.maxOfOrNull { it.verse } ?: 0

    /** The verse being read at [positionMs] (the short introduction belongs to the first verse). */
    fun verseAt(positionMs: Long): Int = verses.lastOrNull { it.startMs <= positionMs }?.verse ?: verses.firstOrNull()?.verse ?: 1

    /** The verse read after [verse] (the audio sometimes reads two verses together). */
    fun nextVerse(verse: Int): Int? = verses.firstOrNull { it.verse > verse }?.verse

    fun startOf(verse: Int): Long? = verses.firstOrNull { it.verse == verse }?.startMs
}

class BibleBook(val number: Int, val name: String, val chapters: List<BibleChapter>) {
    /** Genesis to Malachi; the rest are the Christian Greek Scriptures. */
    val hebrewAramaic: Boolean get() = number <= 39
    val durationMs: Long get() = chapters.sumOf { it.video.durationMs }
    val sizeBytes: Long get() = chapters.sumOf { it.video.files?.firstOrNull()?.filesize ?: 0 }
}

/**
 * The Bible read aloud (New World Translation): chapters with their verse
 * timings from jw.org's publication media, and the words from the Bible's
 * EPUB, downloaded once per language, to follow along.
 */
class BibleRepository(private val api: MediatorApi, filesDir: File) {

    private val dir = File(filesDir, "bible")
    private val books = ConcurrentHashMap<String, List<BibleBook>>()
    private val chapters = ConcurrentHashMap<String, BibleChapter>()
    private val texts = ConcurrentHashMap<String, BibleText>()
    private val loadLock = Mutex()
    private val textLock = Mutex()

    private val _textDownloading = MutableStateFlow<String?>(null)
    /** Language whose text is being downloaded, or null. */
    val textDownloading: StateFlow<String?> = _textDownloading.asStateFlow()

    /** The 66 books in [language], each with its chapters; null when neither a saved copy nor the network has them. */
    suspend fun books(language: String): List<BibleBook>? = books[language] ?: loadLock.withLock {
        books[language] ?: load(language)?.also { list ->
            books[language] = list
            list.forEach { book -> book.chapters.forEach { chapters[it.id] = it } }
        }
    }

    /** The books of [language] if already loaded (for titles drawn before anything loads). */
    fun loadedBooks(language: String): List<BibleBook>? = books[language]

    /** A chapter already loaded (the Bible tab or the player loads its language first). */
    fun chapter(videoId: String): BibleChapter? = chapters[videoId]

    /** The chapter a Bible video plays, loading its language when needed. */
    suspend fun chapterOf(video: Video): BibleChapter? {
        if (!video.isBible) return null
        chapters[video.id]?.let { return it }
        val language = video.id.removePrefix(Video.BIBLE_PREFIX).substringBefore(':')
        books(language)
        return chapters[video.id]
    }

    /** Every chapter in reading order, Genesis 1 to Revelation 22. */
    fun allChapters(language: String): List<BibleChapter> = books[language].orEmpty().flatMap { it.chapters }

    private suspend fun load(language: String): List<BibleBook>? {
        val age = api.bibleAudioAge(language)
        val response = (if (age == null || age > REFRESH_MS) api.bibleAudio(language, fresh = true) else null)
            ?: api.bibleAudio(language, fresh = false)
        val files = response?.files?.get(language)?.get("MP3")?.takeIf { it.isNotEmpty() } ?: return null
        return files.mapNotNull { toChapter(language, it) }
            .groupBy { it.book }
            .toSortedMap()
            .map { (number, list) -> BibleBook(number, list.first().bookName, list.sortedBy { it.chapter }) }
    }

    private fun toChapter(language: String, file: PubMediaFile): BibleChapter? {
        val book = file.booknum ?: return null
        val chapter = file.track ?: return null
        val url = file.file?.url?.takeIf { it.isNotBlank() } ?: return null
        val bookName = decodeEntities(file.title).substringBeforeLast(" - ").trim().ifEmpty { "$book" }
        val video = Video(
            naturalKey = "${Video.BIBLE_PREFIX}$language:$book:$chapter",
            languageAgnosticNaturalKey = "${Video.BIBLE_PREFIX}$book:$chapter",
            primaryCategory = CATEGORY,
            title = "$bookName $chapter",
            duration = file.duration,
            files = listOf(MediaFile(progressiveDownloadURL = url, label = Video.AUDIO_QUALITY, filesize = file.filesize, mimetype = file.mimetype ?: "audio/mpeg")),
        )
        val verses = file.markers?.markers.orEmpty()
            .mapNotNull { m -> parseTime(m.startTime)?.let { VerseMark(m.verseNumber, it) } }
            .filter { it.verse > 0 }
            .sortedBy { it.startMs }
        return BibleChapter(language, book, chapter, bookName, video, verses)
    }

    // ---- The words ----

    private fun epub(language: String) = File(dir, "nwt_$language.epub")

    fun hasText(language: String): Boolean = epub(language).exists()

    /** Size of the text download for [language], or null if it can't be found. */
    suspend fun textSize(language: String): Long? = api.bibleEpub(language)?.filesize

    /** Downloads the Bible's text for [language] (once); false when it couldn't. */
    suspend fun downloadText(language: String): Boolean = textLock.withLock {
        if (hasText(language)) return@withLock true
        _textDownloading.value = language
        try {
            val url = api.bibleEpub(language)?.file?.url ?: return@withLock false
            api.download(url, epub(language))
        } finally {
            _textDownloading.value = null
        }
    }

    /** Verse number -> words of [chapter], or null while the text isn't downloaded. */
    suspend fun verseText(chapter: BibleChapter): Map<Int, String>? = withContext(Dispatchers.IO) {
        val file = epub(chapter.language).takeIf { it.exists() } ?: return@withContext null
        val text = texts.getOrPut(chapter.language) { BibleText(file) }
        runCatching { text.verses(chapter.book, chapter.chapter) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        /** primaryCategory of Bible chapters. */
        const val CATEGORY = "bible"
        /** The audio list is fetched again after a month (links can move). */
        private const val REFRESH_MS = 30L * 24 * 60 * 60_000

        /** "00:01:02.345" -> 62345 */
        internal fun parseTime(value: String): Long? {
            val parts = value.split(':')
            if (parts.size != 3) return null
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].toDoubleOrNull() ?: return null
            return ((h * 3600 + m * 60) * 1000 + s * 1000).toLong()
        }

        private fun decodeEntities(text: String) = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("\u00A0", " ")

        /** (book, chapter) of a Bible chapter's video id ("bible:<language>:<book>:<chapter>"), or null. */
        fun bookAndChapter(videoId: String): Pair<Int, Int>? {
            if (!videoId.startsWith(Video.BIBLE_PREFIX)) return null
            val parts = videoId.removePrefix(Video.BIBLE_PREFIX).split(':')
            if (parts.size != 3) return null
            val book = parts[1].toIntOrNull() ?: return null
            val chapter = parts[2].toIntOrNull() ?: return null
            return book to chapter
        }
    }
}

/** How far the person is through the Bible, from the saved chapter positions. */
object BibleProgress {

    /** The chapter to continue with and where in it: the last one listened to, or the next one if it was finished. */
    fun next(states: Map<String, WatchState>, chapters: List<BibleChapter>): Pair<BibleChapter, Long>? {
        if (chapters.isEmpty()) return null
        val index = chapters.withIndex().associate { (i, c) -> c.id to i }
        val last = states.values.filter { it.video.id in index }.maxByOrNull { it.updatedAt }
            ?: return chapters.first() to 0L
        val i = index.getValue(last.video.id)
        return if (last.finished) (chapters.getOrNull(i + 1) ?: chapters.first()) to 0L else chapters[i] to last.positionMs
    }

    fun heard(states: Map<String, WatchState>, chapters: Collection<BibleChapter>): Int =
        chapters.count { states[it.id]?.finished == true }

    fun heardSince(states: Map<String, WatchState>, chapters: Collection<BibleChapter>, sinceMs: Long): Int =
        chapters.count { c -> states[c.id]?.let { it.finished && it.updatedAt >= sinceMs } == true }
}
