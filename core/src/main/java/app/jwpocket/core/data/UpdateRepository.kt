package app.jwpocket.core.data

import app.jwpocket.core.network.MediatorApi
import app.jwpocket.core.network.PocketJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
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

data class AppUpdate(val versionName: String, val notes: String?, val apkUrl: String, val sizeBytes: Long)

/**
 * Self-update from the GitHub releases of [repo]: one API call reads the
 * recent releases; the newest one gives the APK named [apkAsset], and the
 * notes of every release newer than the installed version are listed, so
 * skipping versions doesn't hide what changed.
 */
class UpdateRepository(
    private val api: MediatorApi,
    private val repo: String,
    private val apkAsset: String,
) {
    /** The update when the latest release is newer than [currentVersion]; null otherwise or on error. */
    suspend fun check(currentVersion: String): Result<AppUpdate?> = runCatching {
        val text = api.getText("https://api.github.com/repos/$repo/releases?per_page=30")
            ?: error("Couldn't reach GitHub.")
        val releases = PocketJson.decodeFromString(ListSerializer(GithubRelease.serializer()), text)
        val newer = releases.filter { !it.prerelease && isNewer(it.version, currentVersion) }.sortedWith { a, b ->
            when { isNewer(a.version, b.version) -> -1; isNewer(b.version, a.version) -> 1; else -> 0 }
        }
        val latest = newer.firstOrNull() ?: return@runCatching null
        val asset = latest.assets.firstOrNull { it.name == apkAsset } ?: return@runCatching null
        AppUpdate(latest.version, changes(newer), asset.url, asset.size)
    }

    /** Each release's notes, newest first; headed by its version when there are several. */
    private fun changes(newer: List<GithubRelease>): String? {
        val notes = newer.mapNotNull { release -> plainNotes(release.body)?.let { release.version to bullets(it) } }
        return when (notes.size) {
            0 -> null
            1 -> notes.single().second
            else -> notes.joinToString("\n\n") { (version, text) -> "$version\n$text" }
        }
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

        /** Every line of a release's notes as a bullet (one-line notes too). */
        fun bullets(notes: String): String = notes.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { if (it.startsWith("• ")) it else "• $it" }
    }
}
