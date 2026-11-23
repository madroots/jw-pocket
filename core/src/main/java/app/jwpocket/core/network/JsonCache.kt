package app.jwpocket.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Last API responses on disk, one file per key. Never expires by itself:
 * repositories decide when a copy is old enough to refresh, and an old copy
 * is still better than nothing when offline.
 */
class JsonCache(private val dir: File) {

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { file(key).takeIf { it.exists() }?.readText() }.getOrNull()
    }

    suspend fun write(key: String, text: String) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val target = file(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(target)
        }
    }

    suspend fun age(key: String): Long? = withContext(Dispatchers.IO) {
        file(key).takeIf { it.exists() }?.let { System.currentTimeMillis() - it.lastModified() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dir.deleteRecursively() }
}
