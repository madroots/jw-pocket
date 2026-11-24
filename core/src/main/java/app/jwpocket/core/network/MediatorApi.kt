package app.jwpocket.core.network

import app.jwpocket.core.model.Category
import app.jwpocket.core.model.CategoryResponse
import app.jwpocket.core.model.LanguageResponse
import app.jwpocket.core.model.MediaItemsResponse
import app.jwpocket.core.model.PubMediaFile
import app.jwpocket.core.model.PubMediaResponse
import app.jwpocket.core.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The JSON parser every module uses: tolerant of new or missing fields. */
val PocketJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

/**
 * Small client for the public mediator API. Every response is also written
 * to [JsonCache], so screens can show the last copy instantly (and offline)
 * while a fresh one loads.
 */
class MediatorApi(
    private val http: OkHttpClient,
    private val cache: JsonCache,
    private val json: Json = PocketJson,
) {

    suspend fun category(language: String, key: String, fresh: Boolean): Category? {
        val cacheKey = "cat_${language}_$key"
        val url = "$BASE/categories/$language/$key?detailed=true"
        return load(cacheKey, url, fresh, CategoryResponse.serializer())?.category
    }

    suspend fun languages(language: String, fresh: Boolean): LanguageResponse? =
        load("langs_$language", "$BASE/languages/$language/web", fresh, LanguageResponse.serializer())

    /** The [language] version of the video with [languageAgnosticKey]; network only. */
    suspend fun mediaItem(language: String, languageAgnosticKey: String): Video? =
        load(null, "$BASE/media-items/$language/$languageAgnosticKey", true, MediaItemsResponse.serializer())
            ?.media?.firstOrNull()

    /** Age of the cached copy in ms, or null when there is none. */
    suspend fun cacheAge(language: String, key: String): Long? = cache.age("cat_${language}_$key")

    /** The Bible read aloud in [language]: every chapter with its verse timings (about 4 MB, cached). */
    suspend fun bibleAudio(language: String, fresh: Boolean): PubMediaResponse? =
        load(BIBLE_AUDIO + language, "$PUB_MEDIA?output=json&alllangs=0&pub=nwt&fileformat=MP3&langwritten=$language", fresh, PubMediaResponse.serializer())

    suspend fun bibleAudioAge(language: String): Long? = cache.age(BIBLE_AUDIO + language)

    /** The Bible's EPUB in [language] (its text, to follow along); network only. */
    suspend fun bibleEpub(language: String): PubMediaFile? =
        load(null, "$PUB_MEDIA?output=json&alllangs=0&pub=nwt&fileformat=EPUB&langwritten=$language", true, PubMediaResponse.serializer())
            ?.files?.get(language)?.get("EPUB")?.firstOrNull()

    private suspend fun <T> load(cacheKey: String?, url: String, fresh: Boolean, serializer: KSerializer<T>): T? {
        if (!fresh) {
            val text = cacheKey?.let { cache.read(it) } ?: return null
            return runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        }
        val body = getText(url) ?: return null
        val decoded = runCatching { json.decodeFromString(serializer, body) }.getOrNull() ?: return null
        if (cacheKey != null) cache.write(cacheKey, body)
        return decoded
    }

    /** GET [url] as text; null on any network or HTTP error. */
    suspend fun getText(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (response.isSuccessful) withContext(Dispatchers.IO) { response.body?.string() } else null
        }
    }.getOrNull()

    /** Downloads [url] into [target] (written atomically); false on failure. */
    suspend fun download(url: String, target: File): Boolean = runCatching {
        http.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) return@use false
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                val part = File(target.parentFile, target.name + ".part")
                response.body!!.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
                part.renameTo(target)
            }
        }
    }.getOrDefault(false)

    companion object {
        const val BASE = "https://b.jw-cdn.org/apis/mediator/v1"
        /** jw.org's publication media: Bible audio and publication files. */
        const val PUB_MEDIA = "https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS"
        private const val BIBLE_AUDIO = "bible_audio_"
    }
}

/** Suspends on an OkHttp call without blocking a thread; cancels the call with the coroutine. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
