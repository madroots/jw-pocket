package app.jwpocket.core

import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.UpdateRepository
import app.jwpocket.core.model.Language
import app.jwpocket.core.model.MediaFile
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
}
