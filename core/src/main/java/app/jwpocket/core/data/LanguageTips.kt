package app.jwpocket.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a tip suggests making the default. */
enum class TipKind { VideoLanguage, Subtitles }

/** "You keep picking [language] for [kind]: make it the default?" ([language] may be [Settings.SUBTITLES_OFF]). */
data class LanguageTip(val kind: TipKind, val language: String)

/**
 * Notices when someone keeps changing the language (or subtitles) of one
 * video after another to the same choice, and offers once to make it the
 * default instead. Picks in the player are never remembered, so without
 * this a person who always wants another language would pick it every time.
 *
 * The tip comes after [PICKS] different videos within [WINDOW_MS], and only
 * once per choice: shown is shown, whatever the answer.
 */
class LanguageTips(context: Context, private val settings: SettingsRepository) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _tip = MutableStateFlow<LanguageTip?>(null)
    /** The tip to show now, or null. */
    val tip: StateFlow<LanguageTip?> = _tip.asStateFlow()

    /** A pick for one video in the player ([language] = what it was set to). */
    fun record(kind: TipKind, language: String, videoId: String, now: Long = System.currentTimeMillis()) {
        if (isDefault(kind, language)) return
        val key = kind.name + "|" + language
        if (key in told()) return
        val picks = picks().filter { now - it.at < WINDOW_MS } + Pick(key, videoId, now)
        savePicks(picks)
        if (picks.filter { it.key == key }.map { it.videoId }.distinct().size >= PICKS) _tip.value = LanguageTip(kind, language)
    }

    /** Make the tip's choice the default (the caller reloads what depends on the video language). */
    fun accept(tip: LanguageTip, languageName: String) {
        when (tip.kind) {
            TipKind.VideoLanguage -> settings.setLanguage(tip.language, languageName)
            TipKind.Subtitles -> settings.setSubtitleLanguage(tip.language)
        }
        dismiss(tip)
    }

    /** Put away; this choice is never suggested again. */
    fun dismiss(tip: LanguageTip) {
        val key = tip.kind.name + "|" + tip.language
        prefs.edit().putStringSet(KEY_TOLD, told() + key).apply()
        savePicks(picks().filter { it.key != key })
        if (_tip.value == tip) _tip.value = null
    }

    private fun isDefault(kind: TipKind, language: String): Boolean {
        val s = settings.current
        return when (kind) {
            TipKind.VideoLanguage -> language == s.languageCode
            TipKind.Subtitles -> language == (s.subtitleLanguage ?: s.languageCode)
        }
    }

    private data class Pick(val key: String, val videoId: String, val at: Long)

    private fun told(): Set<String> = prefs.getStringSet(KEY_TOLD, emptySet()).orEmpty()

    /** Saved as "key\tvideoId\tat" lines. */
    private fun picks(): List<Pick> = prefs.getString(KEY_PICKS, "").orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size == 3) parts[2].toLongOrNull()?.let { Pick(parts[0], parts[1], it) } else null
    }

    private fun savePicks(picks: List<Pick>) {
        prefs.edit().putString(KEY_PICKS, picks.takeLast(MAX_PICKS).joinToString("\n") { "${it.key}\t${it.videoId}\t${it.at}" }).apply()
    }

    companion object {
        private const val PREFS = "jw_pocket_tips"
        private const val KEY_PICKS = "language_picks"
        private const val KEY_TOLD = "language_told"
        /** Different videos given the same choice before the tip: clearly a habit, not a one-off. */
        const val PICKS = 4
        const val WINDOW_MS = 21L * 24 * 60 * 60_000
        private const val MAX_PICKS = 60
    }
}
