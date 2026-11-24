package app.jwpocket.phone.clips

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.jwpocket.core.util.formatClock
import org.json.JSONObject
import java.io.File

/** A cut-out part of a video, saved on the phone. */
data class Clip(
    val id: String,
    val videoId: String,
    val title: String,
    /** jw.org link to the whole video, offered when sharing. */
    val videoUrl: String?,
    /** Where in the video the clip starts and ends. */
    val startMs: Long,
    val endMs: Long,
    val createdAt: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Clips live in the app's own storage (files/clips): <id>.mp4, <id>.jpg (a
 * frame for the list) and <id>.json. Nothing else can read them except
 * through a share, which hands out a temporary link to the one file.
 */
class ClipStore private constructor(private val context: Context) {

    private val dir = File(context.filesDir, "clips")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _clips = MutableStateFlow<List<Clip>>(emptyList())
    /** Newest first. */
    val clips: StateFlow<List<Clip>> = _clips.asStateFlow()

    init {
        scope.launch { _clips.value = load() }
    }

    fun video(clip: Clip) = File(dir, "${clip.id}.mp4")
    fun thumbnail(clip: Clip) = File(dir, "${clip.id}.jpg")

    /** Where a new clip with [id] is written: [partFile] while it's being made, then [videoFile]. */
    internal fun partFile(id: String): File = File(dir.apply { mkdirs() }, "$id.part")
    internal fun videoFile(id: String): File = File(dir.apply { mkdirs() }, "$id.mp4")
    internal fun thumbnailFile(id: String): File = File(dir.apply { mkdirs() }, "$id.jpg")

    internal fun add(clip: Clip) {
        File(dir, "${clip.id}.json").writeText(
            JSONObject()
                .put("videoId", clip.videoId)
                .put("title", clip.title)
                .put("videoUrl", clip.videoUrl ?: JSONObject.NULL)
                .put("startMs", clip.startMs)
                .put("endMs", clip.endMs)
                .put("createdAt", clip.createdAt)
                .toString()
        )
        _clips.update { listOf(clip) + it }
    }

    fun delete(clip: Clip) {
        _clips.update { list -> list.filterNot { it.id == clip.id } }
        scope.launch { listOf("mp4", "jpg", "json").forEach { File(dir, "${clip.id}.$it").delete() } }
    }

    /**
     * A share sheet for [clip]; with [withLink], the jw.org link to the whole
     * video goes along. The file goes out as a copy named after the video and
     * the moment ("Title – 3.50.mp4"), not the clip's internal id.
     */
    suspend fun shareIntent(clip: Clip, withLink: Boolean): Intent {
        val copy = withContext(Dispatchers.IO) {
            val shared = File(context.cacheDir, "shared")
            val now = System.currentTimeMillis()
            shared.listFiles().orEmpty().filter { now - it.lastModified() > SHARED_KEEP_MS }.forEach { it.deleteRecursively() }
            val name = clip.title.replace(UNSAFE, " ").replace(Regex("\\s+"), " ").trim().take(80) +
                " – " + formatClock(clip.startMs).replace(':', '.') + ".mp4"
            video(clip).copyTo(File(File(shared, now.toString()).apply { mkdirs() }, name), overwrite = true)
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (withLink && clip.videoUrl != null) putExtra(Intent.EXTRA_TEXT, "${clip.title}\n${clip.videoUrl}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun load(): List<Clip> {
        // A clip still being made when the app was closed never finished.
        dir.listFiles { f -> f.extension == "part" }.orEmpty().forEach { it.delete() }
        return dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                val id = file.nameWithoutExtension
                if (!File(dir, "$id.mp4").exists()) return@mapNotNull null
                Clip(
                    id = id,
                    videoId = json.getString("videoId"),
                    title = json.getString("title"),
                    videoUrl = json.optString("videoUrl").takeIf { !json.isNull("videoUrl") && it.isNotBlank() },
                    startMs = json.getLong("startMs"),
                    endMs = json.getLong("endMs"),
                    createdAt = json.getLong("createdAt"),
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    companion object {
        @Volatile private var instance: ClipStore? = null
        fun get(context: Context): ClipStore =
            instance ?: synchronized(this) { instance ?: ClipStore(context.applicationContext).also { instance = it } }
        /** Characters file names can't carry on some systems. */
        private val UNSAFE = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        /** Shared copies stay a day, in case the receiving app reads them late. */
        private const val SHARED_KEEP_MS = 24 * 60 * 60_000L
    }
}
