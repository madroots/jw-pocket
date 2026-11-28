package app.jwpocket.core

import app.jwpocket.core.util.Cue
import app.jwpocket.core.util.WebVtt
import app.jwpocket.core.data.LanguageUse
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.Settings
import app.jwpocket.core.data.UpdateRepository
import app.jwpocket.core.model.Language
import app.jwpocket.core.model.MediaFile
import app.jwpocket.core.model.SeriesOrder
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.PocketJson
import app.jwpocket.core.data.SubtitleRepository
import app.jwpocket.core.network.JsonCache
import app.jwpocket.core.network.MediatorApi
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreLogicTest {

    private fun file(label: String, url: String = "https://cdn/x_$label.mp4") = MediaFile(progressiveDownloadURL = url, label = label, filesize = 100)

    @Test
    fun autoQualityPicksBestUpTo720p() {
        val video = Video(title = "t", files = listOf(file("240p"), file("1080p"), file("720p"), file("480p")))
        assertEquals("720p", video.file(null)?.label)
        assertEquals("1080p", video.file("1080p")?.label)
        assertEquals(listOf("240p", "480p", "720p", "1080p"), video.qualities)
    }

    @Test
    fun autoQualityFallsBackToLowestWhenOnlyHighExists() {
        val video = Video(title = "t", files = listOf(file("2160p"), file("1080p")))
        assertEquals("1080p", video.file(null)?.label)
    }

    @Test
    fun idPrefersNaturalKey() {
        assertEquals("pub-x_E_1_VIDEO", Video(naturalKey = "pub-x_E_1_VIDEO", guid = "g").id)
        assertEquals("g", Video(guid = "g").id)
    }

    @Test
    fun parsesApiShapeAndIgnoresUnknownFields() {
        val json = """{"guid":"g","naturalKey":"n","title":"Hello","duration":61.5,"extra":{"a":1},
            "files":[{"progressiveDownloadURL":"u","label":"360p","filesize":12,"subtitles":{"url":"s.vtt"}}],
            "availableLanguages":["E","S"]}"""
        val video = PocketJson.decodeFromString(Video.serializer(), json)
        assertEquals("Hello", video.title)
        assertEquals(61_500L, video.durationMs)
        assertEquals("s.vtt", video.subtitleUrl)
        assertEquals(listOf("E", "S"), video.availableLanguages)
    }

    @Test
    fun versionComparison() {
        assertTrue(UpdateRepository.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateRepository.isNewer("2.0", "1.99.99"))
        assertFalse(UpdateRepository.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateRepository.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun releaseNotesBecomePlainText() {
        assertEquals("What's new\n• Faster\n• Smoother", UpdateRepository.plainNotes("## What's new\n- **Faster**\n* Smoother"))
        assertNull(UpdateRepository.plainNotes("   "))
    }

    @Test
    fun searchNormalizationIgnoresAccentsAndCase() {
        assertEquals("cancion de jehova", LibraryRepository.normalize("¡Canción de JEHOVÁ!"))
    }

    @Test
    fun subtitleLanguagesPinRecentFirstAndSkipSignLanguages() {
        val repo = SubtitleRepository(MediatorApi(OkHttpClient(), JsonCache(File("build/tmp/test-cache"))))
        val languages = listOf(
            Language("E", vernacular = "English", name = "English"),
            Language("S", vernacular = "español", name = "Spanish"),
            Language("ASL", vernacular = "ASL", name = "American Sign Language", isSignLanguage = true),
            Language("F", vernacular = "Français", name = "French"),
        )
        val video = Video(languageAgnosticNaturalKey = "k", availableLanguages = listOf("E", "S", "F", "ASL"))
        val offered = repo.otherLanguages(video, ownLanguage = "E", languages = languages, pinned = listOf("S"))
        assertEquals(listOf("S", "F"), offered.map { it.code })
    }

    private fun v(title: String, key: String? = null) = Video(naturalKey = title, title = title, languageAgnosticNaturalKey = key)

    @Test
    fun seriesPartsFollowInOrderWhateverTheRowOrder() {
        val p1 = v("True Christians Are Neutral—Part 1")
        val p2 = v("True Christians Are Neutral—Part 2")
        val p3 = v("True Christians Are Neutral—Part 3")
        val other = v("Why Did Jesus Die?—Part 2")
        val row = listOf(p3, other, v("Never Give Up Hope!"), p2, p1)
        assertEquals(listOf(p2, p3), SeriesOrder.nextParts(p1, row))
        // Starting from a later part never goes back.
        assertEquals(listOf(p3), SeriesOrder.nextParts(p2, row))
        assertEquals(emptyList<Video>(), SeriesOrder.nextParts(p3, row))
    }

    @Test
    fun seriesPartsInOtherLanguagesAndRomanNumerals() {
        val sk1 = v("Daniel – príbeh nezlomnej viery (1. časť)")
        val sk2 = v("Daniel – príbeh nezlomnej viery (2. časť)")
        assertEquals(listOf(sk2), SeriesOrder.nextParts(sk1, listOf(sk2, sk1)))
        val r1 = v("Nehemiah: “The Joy of Jehovah Is Your Stronghold”\u200B—Part I")
        val r2 = v("Nehemiah: “The Joy of Jehovah Is Your Stronghold”—Part II")
        val excerpt = v("Nehemiah: “The Joy of Jehovah Is Your Stronghold”—Part II—Excerpt")
        assertEquals(listOf(r2), SeriesOrder.nextParts(r1, listOf(excerpt, r2)))
    }

    @Test
    fun episodesOfOnePublicationFollowByKey() {
        val e1 = v("Episode 1: The True Light of the World", "pub-gnj_1_VIDEO")
        val e2 = v("Episode 2: “This Is My Son”", "pub-gnj_2_VIDEO")
        val e3 = v("Episode 3: “I Am He”", "pub-gnj_3_VIDEO")
        assertEquals(listOf(e2, e3), SeriesOrder.nextParts(e1, listOf(e3, e2, e1)))
        // A video with no number is no series.
        assertFalse(SeriesOrder.mayBePart(v("Never Give Up Hope!", "pub-lffv_11_VIDEO")))
    }

    @Test
    fun webVttCuesWithTagsAndShortTimes() {
        val vtt = "WEBVTT\n\n1\n00:00:05.120 --> 00:00:07.900 align:middle\n<c.white>The Bible says</c> that\nJehovah &amp; his Son\n\n02:03.5 --> 02:04.000\nOnly <i>minutes</i>\n"
        val cues = WebVtt.parse(vtt)
        assertEquals(listOf(Cue(5_120, 7_900, "The Bible says that\nJehovah & his Son"), Cue(123_500, 124_000, "Only minutes")), cues)
    }

    @Test
    fun bibleVersesWithoutNotesAndStoppingAtTheNextBook() {
        val html = """
            <p><span id="chapter5"></span><span id="chapter5_verse1"></span><span class="w_ch"><strong>5</strong> </span>Keď Ježiš videl zástupy.
            <span id="chapter5_verse2"></span><strong><sup>2</sup></strong> A začal&nbsp;rozprávať:</p>
            <p><span id="chapter5_verse3"></span><strong><sup>3</sup></strong> „Šťastní sú,<span id="footnotesource38"></span><a epub:type="noteref" href="#footnote38">*</a> lebo…</p>
            <aside epub:type="footnote">poznámka</aside>
            <p><span id="chapter5_verse1"></span>Next book, chapter 5 again</p>
        """.trimIndent()
        val verses = app.jwpocket.core.data.BibleText.parse(html, 5)
        assertEquals(listOf(1, 2, 3), verses.keys.toList())
        assertEquals("Keď Ježiš videl zástupy.", verses[1])
        assertEquals("A začal rozprávať:", verses[2])
        assertEquals("„Šťastní sú, lebo…", verses[3])
    }

    @Test
    fun bibleContinuesAfterAFinishedChapter() {
        fun chapter(n: Int) = app.jwpocket.core.data.BibleChapter("V", 40, n, "Matúš",
            Video(naturalKey = "${Video.BIBLE_PREFIX}V:40:$n", title = "Matúš $n"), emptyList())
        val chapters = (1..3).map(::chapter)
        fun state(c: app.jwpocket.core.data.BibleChapter, pos: Long, finished: Boolean, at: Long) =
            c.id to app.jwpocket.core.data.WatchState(c.video, pos, 100_000, finished, at)
        assertEquals(chapters[0] to 0L, app.jwpocket.core.data.BibleProgress.next(emptyMap(), chapters))
        val listened = mapOf(state(chapters[0], 100_000, true, 1), state(chapters[1], 30_000, false, 2))
        assertEquals(chapters[1] to 30_000L, app.jwpocket.core.data.BibleProgress.next(listened, chapters))
        val finishedLast = mapOf(state(chapters[1], 30_000, false, 2), state(chapters[0], 100_000, true, 3))
        // After a finished chapter: the next one not heard, where it was left.
        assertEquals(chapters[1] to 30_000L, app.jwpocket.core.data.BibleProgress.next(finishedLast, chapters))
        val skipHeard = mapOf(state(chapters[1], 100_000, true, 1), state(chapters[0], 100_000, true, 2))
        assertEquals(chapters[2] to 0L, app.jwpocket.core.data.BibleProgress.next(skipHeard, chapters))
        val allHeard = chapters.associate { state(it, 100_000, true, 1) }
        assertNull(app.jwpocket.core.data.BibleProgress.next(allHeard, chapters))
        assertEquals(62_345L, app.jwpocket.core.data.BibleRepository.parseTime("00:01:02.345"))
    }

    @Test
    fun theTwoMostUsedLanguagesArePinnedEnglishUntilTwoAreUsed() {
        // Most recent first: S once, then F and D three times each (F more recent).
        val uses = listOf(LanguageUse("S", 1), LanguageUse("F", 3), LanguageUse("D", 3))
        assertEquals(listOf("F", "D"), Settings(languageUses = uses).pinnedLanguages)
        assertEquals(listOf("S", "E"), Settings(languageUses = listOf(LanguageUse("S", 1))).pinnedLanguages)
        assertEquals(listOf("S"), Settings(languageUses = listOf(LanguageUse("S", 1))).mostUsedLanguages)
    }
}
