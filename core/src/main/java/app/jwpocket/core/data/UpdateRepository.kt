package app.jwpocket.core.data

import app.jwpocket.core.network.MediatorApi
import app.jwpocket.core.network.PocketJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
    val version: String get() = tagName.removePrefix("v")
}

@Serializable
internal data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

/** What one release changed: its version and one line per change. */
data class ReleaseNotes(val version: String, val changes: List<String>)

data class AppUpdate(val versionName: String, val releases: List<ReleaseNotes>, val apkUrl: String, val sizeBytes: Long) {
    /** Every change as text, headed by its version when there are several (plain-text views). */
    val notes: String?
        get() = when (releases.size) {
            0 -> null
            1 -> releases.single().changes.joinToString("\n") { "• $it" }
            else -> releases.joinToString("\n\n") { r -> r.version + "\n" + r.changes.joinToString("\n") { "• $it" } }
        }
}

/**
 * Self-update from the GitHub releases of [repo]: one API call reads the
 * recent releases; the newest one gives the APK named [apkAsset], and the
 * notes of every release newer than the installed version are listed, so
 * skipping versions doesn't hide what changed. A release may carry its notes
 * translated in a [NOTES_ASSET] ({"en": "- …", "sk": "- …"}); the release
 * text (English) is used otherwise.
 */
class UpdateRepository(
    private val api: MediatorApi,
    private val repo: String,
    private val apkAsset: String,
) {
    /** The update when the latest release is newer than [currentVersion]; null otherwise or on error. Notes in [language] (an app language tag) where translated. */
    suspend fun check(currentVersion: String, language: String): Result<AppUpdate?> = runCatching {
        val text = api.getText("https://api.github.com/repos/$repo/releases?per_page=30")
            ?: error("Couldn't reach GitHub.")
        val releases = PocketJson.decodeFromString(ListSerializer(GithubRelease.serializer()), text)
        val newer = releases.filter { !it.prerelease && isNewer(it.version, currentVersion) }.sortedWith { a, b ->
            when { isNewer(a.version, b.version) -> -1; isNewer(b.version, a.version) -> 1; else -> 0 }
        }
        val latest = newer.firstOrNull() ?: return@runCatching null
        val asset = latest.assets.firstOrNull { it.name == apkAsset } ?: return@runCatching null
        val notes = coroutineScope { newer.map { async { notesOf(it, language) } }.awaitAll() }.filterNotNull()
        AppUpdate(latest.version, notes, asset.url, asset.size)
    }

    /** One release's changes in [language] if its notes file has them, else its English text. */
    private suspend fun notesOf(release: GithubRelease, language: String): ReleaseNotes? {
        val translated = release.assets.firstOrNull { it.name == NOTES_ASSET }?.let { notesFile ->
            api.getText(notesFile.url)?.let { json -> runCatching { PocketJson.decodeFromString<Map<String, String>>(json) }.getOrNull() }
        }
        val text = plainNotes(translated?.get(language) ?: translated?.get("en") ?: release.body) ?: return null
        return ReleaseNotes(release.version, changeLines(text))
    }

    companion object {
        /** Compares dotted versions numerically: "1.10.0" > "1.9.3". */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** Release notes without markdown markers, lists as bullets. */
        fun plainNotes(body: String?): String? = body?.lineSequence()
            ?.map { line ->
                val t = line.trim().trimStart('#').trim().replace("**", "")
                if (t.startsWith("- ") || t.startsWith("* ")) "• " + t.substring(2).trim() else t
            }
            ?.joinToString("\n")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()
            ?.ifBlank { null }

        /** One change per non-empty line, bullets removed. */
        fun changeLines(notes: String): List<String> = notes.lineSequence()
            .map { it.trim().removePrefix("• ").trim() }
            .filter { it.isNotEmpty() }
            .toList()

        /** Release asset with the notes translated into the app languages. */
        const val NOTES_ASSET = "notes.json"
    }
}
