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
    /** null = the video's own language, [SUBTITLES_OFF], or a language code. */
    val subtitleLanguage: String? = null,
    /** Other languages picked before (subtitles, or a video's language in the player), most recent first. */
    val recentSubtitleLanguages: List<String> = emptyList(),
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
    /** Languages pinned at the top of subtitle lists: used before, then English. */
    val subtitleShortcuts: List<String> get() = (recentSubtitleLanguages + ENGLISH).distinct()

    companion object {
        const val ENGLISH = "E"
        const val SUBTITLES_OFF = "off"
    }
}

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

    fun setLanguage(code: String, name: String) = update {
        // A subtitle preference equal to the new library language means "original".
        it.copy(languageCode = code, languageName = name,
            subtitleLanguage = it.subtitleLanguage.takeUnless { s -> s == code })
    }

    fun setSubtitleLanguage(choice: String?) = update {
        val normalized = choice.takeUnless { c -> c == it.languageCode }
        val recent = if (normalized != null && normalized != Settings.SUBTITLES_OFF) withRecent(it, normalized) else it.recentSubtitleLanguages
        it.copy(subtitleLanguage = normalized, recentSubtitleLanguages = recent)
    }

    /** A language picked for one video in the player: offered first next time, like subtitle picks. */
    fun addRecentLanguage(code: String) = update {
        if (code == it.languageCode) it else it.copy(recentSubtitleLanguages = withRecent(it, code))
    }

    private fun withRecent(s: Settings, code: String) = (listOf(code) + s.recentSubtitleLanguages - code).distinct().take(4)

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
        recentSubtitleLanguages = prefs.getString("subtitle_recent", "").orEmpty()
            .split(',').filter { it.isNotBlank() },
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
            .putString("subtitle_recent", s.recentSubtitleLanguages.joinToString(","))
            .putFloat("subtitle_scale", s.subtitleScale)
            .putBoolean("background_playback", s.backgroundPlayback)
            .putBoolean("auto_pip", s.autoPictureInPicture)
            .putBoolean("autoplay_next", s.autoplayNext)
            .putBoolean("notify_broadcasting", s.notifyBroadcasting)
            .putBoolean("notify_news", s.notifyNews)
            .putString("listen_saver", s.listenSaver.name)
            .apply()
    }

    companion object {
        const val PREFS = "jw_pocket_settings"
        const val KEY_APP_LANGUAGE = "app_language"
    }
}
