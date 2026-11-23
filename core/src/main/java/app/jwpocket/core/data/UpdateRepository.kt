package app.jwpocket.core.data

import app.jwpocket.core.network.MediatorApi
import app.jwpocket.core.network.PocketJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
internal data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

data class AppUpdate(val versionName: String, val notes: String?, val apkUrl: String, val sizeBytes: Long)

/**
 * Self-update from the GitHub releases of [repo]: one API call reads the
 * latest release's tag, notes and the APK named [apkAsset].
 */
class UpdateRepository(
    private val api: MediatorApi,
    private val repo: String,
    private val apkAsset: String,
) {
    /** The update when the latest release is newer than [currentVersion]; null otherwise or on error. */
    suspend fun check(currentVersion: String): Result<AppUpdate?> = runCatching {
        val text = api.getText("https://api.github.com/repos/$repo/releases/latest")
            ?: error("Couldn't reach GitHub.")
        val release = PocketJson.decodeFromString(GithubRelease.serializer(), text)
        val version = release.tagName.removePrefix("v")
        val asset = release.assets.firstOrNull { it.name == apkAsset }
        if (release.prerelease || asset == null || !isNewer(version, currentVersion)) null
        else AppUpdate(version, plainNotes(release.body), asset.url, asset.size)
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
    }
}
