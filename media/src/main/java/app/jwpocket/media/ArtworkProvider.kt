package app.jwpocket.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Hands thumbnails to Android Auto, which only shows browse artwork from
 * content:// URIs. content://<app>.artwork/<host>/<path> serves
 * https://<host>/<path>, downloaded once into the cache. Only JW CDN hosts are
 * served, so the exported provider can't fetch anything else.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException(uri.toString())
        val host = uri.pathSegments.firstOrNull()
        if (mode != "r" || host == null || !isCdn(host)) throw FileNotFoundException(uri.toString())
        val remote = "https:/" + uri.encodedPath + (uri.encodedQuery?.let { "?$it" } ?: "")
        // Check the URL OkHttp will actually fetch, not just the first path segment.
        val parsed = remote.toHttpUrlOrNull()
        if (parsed == null || !parsed.isHttps || !isCdn(parsed.host)) throw FileNotFoundException(uri.toString())
        val file = File(File(context.cacheDir, "artwork"), sha1(remote))
        if (!file.exists()) download(context, remote, file)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun download(context: Context, url: String, target: File) {
        val request = Request.Builder().url(url).build()
        runCatching {
            context.pocketGraph.core.http.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use false
                target.parentFile?.mkdirs()
                val part = File.createTempFile(target.name, ".part", target.parentFile)
                part.outputStream().use { body.byteStream().copyTo(it) }
                if (!part.renameTo(target)) part.delete()
                target.exists()
            }
        }.getOrNull()?.takeIf { it } ?: throw FileNotFoundException(url)
    }

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val CDN = "jw-cdn.org"

        private fun isCdn(host: String) = host == CDN || host.endsWith(".$CDN")

        /** The content:// form of [url] when it is a JW CDN image, else [url] itself. */
        fun uri(context: Context, url: String): Uri {
            val remote = url.toUri()
            val host = remote.host
            if (remote.scheme != "https" || host == null || !isCdn(host)) return remote
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.artwork")
                .encodedPath("/$host${remote.encodedPath.orEmpty()}")
                .encodedQuery(remote.encodedQuery)
                .build()
        }

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
