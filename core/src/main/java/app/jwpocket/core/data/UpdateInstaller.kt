package app.jwpocket.core.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.jwpocket.core.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Downloads an update APK and hands it to the system installer. */
class UpdateInstaller(private val context: Context, private val http: OkHttpClient) {

    /** Downloads [url]; [onProgress] gets 0..1. Returns the file, or null on failure. */
    suspend fun download(url: String, onProgress: (Float) -> Unit): File? = runCatching {
        val target = File(context.cacheDir, "updates/update.apk")
        http.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) return null
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                val body = response.body ?: return@withContext null
                val total = body.contentLength()
                var read = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) onProgress(read.toFloat() / total)
                        }
                        output.fd.sync()
                    }
                }
                target
            }
        }
    }.getOrNull()

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        })
    }
}
