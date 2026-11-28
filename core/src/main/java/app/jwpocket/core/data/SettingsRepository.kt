package app.jwpocket.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Every choice the person makes in Settings (and a few made in the player). */
data class Settings(
    /** Language of the app's own text, a tag from AppLanguage; null = the device's language. */
    val appLanguage: String? = null,
    /** Library (video) language, JW code ("E" = English). */
    val languageCode: String = ENGLISH,
    val languageName: String = "English",
    /** Streaming quality; null = Auto. */
    val streamQuality: String? = null,
    /** Download quality; null = ask every time. */
    val downloadQuality: String? = null,
    val wifiOnlyDownloads: Boolean = false,
    /** Default subtitles: null = follow the video language, [SUBTITLES_OFF], or a language picked (kept even if the videos later use it too). */
    val subtitleLanguage: String? = null,
    /** Other languages picked (subtitles, or a video's language in the player) and how often, most recent first. */
    val languageUses: List<LanguageUse> = emptyList(),
    /** Language of the Bible tab and its reading; null = follow the video language, else a language picked (kept). */
    val bibleLanguage: String? = null,
    val subtitleScale: Float = 1f,
    /** Keep playing (as audio) when the app goes to the background or the screen locks. */
    val backgroundPlayback: Boolean = true,
    /** Shrink to a floating window when leaving the app during a video. */
    val autoPictureInPicture: Boolean = true,
    /** Play the next video of the row when one ends. */
    val autoplayNext: Boolean = true,
    /** Phone: notify when a new monthly JW Broadcasting program appears. */
    val notifyBroadcasting: Boolean = true,
    /** Phone: notify when a new Governing Body update (or other news video) appears. */
    val notifyNews: Boolean = true,
    /** When only the sound plays (background, the car), stream the smallest quality. */
    val listenSaver: ListenSaver = ListenSaver.MobileData,
) {
    /** The two languages picked most (the more recent first on a tie), always at the top of language lists; English until two are used. */
    val pinnedLanguages: List<String>
        get() = (languageUses.withIndex().sortedWith(compareByDescending<IndexedValue<LanguageUse>> { it.value.count }.thenBy { it.index })
            .map { it.value.code } + ENGLISH).distinct().take(2)

    /** Of [pinnedLanguages], those really picked before (labelled "most used" in lists). */
    val mostUsedLanguages: List<String> get() = pinnedLanguages.filter { code -> languageUses.any { it.code == code } }

    /** The language the Bible is read in. */
    val bibleLanguageCode: String get() = bibleLanguage ?: languageCode

    companion object {
        const val ENGLISH = "E"
        const val SUBTITLES_OFF = "off"
    }
}

/** A language picked [count] times. */
data class LanguageUse(val code: String, val count: Int)

/** When to stream the smallest quality while only the sound is heard. */
enum class ListenSaver {
    Off,
    /** Only on metered connections (mobile data, hotspots). */
    MobileData,
    Always;

    fun applies(metered: Boolean): Boolean = this == Always || (this == MobileData && metered)
}

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Nothing saved yet: the first start after installing. */
    val firstRun: Boolean = !prefs.contains("language_code")

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
        write(_settings.value)
    }

    /**
     * The default language of videos and audio. Subtitles and the Bible that
     * follow it change with it; a language picked for them stays.
     */
    fun setLanguage(code: String, name: String) = update { it.copy(languageCode = code, languageName = name) }

    /** null = follow the video language. */
    fun setBibleLanguage(code: String?) = update { it.copy(bibleLanguage = code) }

    /** Default subtitles: null = follow the video language, [Settings.SUBTITLES_OFF], or a language. */
    fun setSubtitleLanguage(choice: String?) = update {
        val uses = if (choice != null && choice != Settings.SUBTITLES_OFF) withUse(it, choice) else it.languageUses
        it.copy(subtitleLanguage = choice, languageUses = uses)
    }

    /** A language picked for one video in the player: counts toward the languages pinned at the top, like subtitle picks. */
    fun recordLanguageUse(code: String) = update {
        if (code == it.languageCode) it else it.copy(languageUses = withUse(it, code))
    }

    private fun withUse(s: Settings, code: String): List<LanguageUse> {
        val count = s.languageUses.firstOrNull { it.code == code }?.count ?: 0
        return (listOf(LanguageUse(code, count + 1)) + s.languageUses.filter { it.code != code }).take(MAX_USES)
    }

    /** [tag] from AppLanguage, or null to follow the device. Screens must be recreated to show it. */
    fun setAppLanguage(tag: String?) = update { it.copy(appLanguage = tag) }

    private fun read() = Settings(
        appLanguage = prefs.getString(KEY_APP_LANGUAGE, null),
        languageCode = prefs.getString("language_code", null) ?: Settings.ENGLISH,
        languageName = prefs.getString("language_name", null) ?: "English",
        streamQuality = prefs.getString("stream_quality", null),
        downloadQuality = prefs.getString("download_quality", null),
        wifiOnlyDownloads = prefs.getBoolean("wifi_only_downloads", false),
        subtitleLanguage = prefs.getString("subtitle_language", null),
        languageUses = readUses(),
        bibleLanguage = prefs.getString("bible_language", null),
        subtitleScale = prefs.getFloat("subtitle_scale", 1f),
        backgroundPlayback = prefs.getBoolean("background_playback", true),
        autoPictureInPicture = prefs.getBoolean("auto_pip", true),
        autoplayNext = prefs.getBoolean("autoplay_next", true),
        notifyBroadcasting = prefs.getBoolean("notify_broadcasting", true),
        notifyNews = prefs.getBoolean("notify_news", true),
        listenSaver = prefs.getString("listen_saver", null)?.let { name -> ListenSaver.entries.firstOrNull { it.name == name } } ?: ListenSaver.MobileData,
    )

    private fun write(s: Settings) {
        prefs.edit()
            .putString(KEY_APP_LANGUAGE, s.appLanguage)
            .putString("language_code", s.languageCode)
            .putString("language_name", s.languageName)
            .putString("stream_quality", s.streamQuality)
            .putString("download_quality", s.downloadQuality)
            .putBoolean("wifi_only_downloads", s.wifiOnlyDownloads)
            .putString("subtitle_language", s.subtitleLanguage)
            .putString("language_uses", s.languageUses.joinToString(",") { "${it.code}:${it.count}" })
            .remove("subtitle_recent")
            .putString("bible_language", s.bibleLanguage)
            .putFloat("subtitle_scale", s.subtitleScale)
            .putBoolean("background_playback", s.backgroundPlayback)
            .putBoolean("auto_pip", s.autoPictureInPicture)
            .putBoolean("autoplay_next", s.autoplayNext)
            .putBoolean("notify_broadcasting", s.notifyBroadcasting)
            .putBoolean("notify_news", s.notifyNews)
            .putString("listen_saver", s.listenSaver.name)
            .apply()
    }

    /** "S:3,F:1"; before 1.11 only the recent codes were kept ("S,F"), each counted once. */
    private fun readUses(): List<LanguageUse> {
        prefs.getString("language_uses", null)?.let { saved ->
            return saved.split(',').mapNotNull { entry ->
                val code = entry.substringBefore(':')
                val count = entry.substringAfter(':', "").toIntOrNull() ?: return@mapNotNull null
                LanguageUse(code, count).takeIf { code.isNotBlank() }
            }
        }
        return prefs.getString("subtitle_recent", "").orEmpty().split(',').filter { it.isNotBlank() }.map { LanguageUse(it, 1) }
    }

    companion object {
        const val PREFS = "jw_pocket_settings"
        const val KEY_APP_LANGUAGE = "app_language"
        /** Languages remembered for the counts; enough that a once-a-year pick doesn't push out a favorite. */
        private const val MAX_USES = 12
    }
}
