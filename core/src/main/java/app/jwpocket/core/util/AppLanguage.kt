package app.jwpocket.core.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import app.jwpocket.core.data.SettingsRepository
import java.util.Locale

/** The languages the app's own text (menus, buttons, messages) is translated into. */
object AppLanguage {

    /** Language tag to its own name, in the order Settings lists them. */
    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "cs" to "Čeština",
        "sk" to "Slovenčina",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "pt" to "Português",
        "pl" to "Polski",
        "nl" to "Nederlands",
        "hu" to "Magyar",
        "ro" to "Română",
        "ru" to "Русский",
        "uk" to "Українська",
    )

    /** The device's own language ("sk"), whether or not the app is translated into it. */
    fun device(): String? = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language?.takeIf { it.isNotEmpty() }

    /** The app language in use: the chosen one, else the device's when the app speaks it, else English. */
    fun effective(chosen: String?): String {
        val supportedTags = supported.map { it.first }
        return chosen?.takeIf { it in supportedTags } ?: device()?.takeIf { it in supportedTags } ?: "en"
    }

    fun name(tag: String): String = supported.firstOrNull { it.first == tag }?.second ?: tag
}

/**
 * This context with the app's own text in the app language. Activities,
 * services and the application wrap their base context with it; it reads the
 * setting straight from storage because it runs before anything else exists.
 */
fun Context.withAppLanguage(): Context {
    val chosen = getSharedPreferences(SettingsRepository.PREFS, Context.MODE_PRIVATE).getString(SettingsRepository.KEY_APP_LANGUAGE, null)
    val locale = Locale.forLanguageTag(AppLanguage.effective(chosen))
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}

/** Text in the app language for messages made outside the screens (library errors, update status). */
class AppText(private val context: Context) {
    fun get(@StringRes id: Int, vararg args: Any): String = context.withAppLanguage().getString(id, *args)
}
