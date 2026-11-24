package app.jwpocket.core

import android.app.ActivityManager
import android.content.Context
import app.jwpocket.core.data.BibleRepository
import app.jwpocket.core.data.FavoritesRepository
import app.jwpocket.core.data.LanguageRepository
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.ProgressRepository
import app.jwpocket.core.data.Settings
import app.jwpocket.core.data.SettingsRepository
import app.jwpocket.core.data.SubtitleRepository
import app.jwpocket.core.data.UpdateController
import app.jwpocket.core.data.UpdateInstaller
import app.jwpocket.core.data.UpdateRepository
import app.jwpocket.core.data.db.PocketDatabase
import app.jwpocket.core.network.JsonCache
import app.jwpocket.core.network.MediatorApi
import app.jwpocket.core.util.AppLanguage
import app.jwpocket.core.util.AppText
import app.jwpocket.core.util.NetworkMonitor
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Everything the apps share, created once per process. Plain constructor
 * wiring: no reflection, nothing to slow down start-up.
 *
 * @param updateRepo GitHub "owner/name" the app updates itself from.
 * @param updateApk  release asset name of this app's APK.
 * @param versionName the running app's version, to compare with releases.
 */
class CoreGraph(context: Context, updateRepo: String, updateApk: String, versionName: String) {

    private val appContext = context.applicationContext

    /** Lives as long as the process: saves progress and favourites even as screens close. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(appContext.cacheDir, "http"), 50L shl 20))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val api = MediatorApi(http, JsonCache(File(appContext.filesDir, "json")))
    private val database by lazy { PocketDatabase.create(appContext) }

    private val text = AppText(appContext)
    val settings = SettingsRepository(appContext)
    val library = LibraryRepository(api, text)
    val languages = LanguageRepository(api, appScope)
    val subtitles = SubtitleRepository(api)
    val bible = BibleRepository(api, appContext.filesDir)
    val progress by lazy { ProgressRepository(database.progress(), appScope) }
    val favorites by lazy { FavoritesRepository(database.favorites(), appScope) }
    val updates = UpdateController(
        UpdateRepository(api, updateRepo, updateApk), UpdateInstaller(appContext, http), text, appScope, versionName,
    )
    val network = NetworkMonitor(appContext)

    init {
        // First start: videos in the device's language when the library has it. The app's own
        // text already follows the device (no app language chosen), and subtitles follow the videos.
        if (settings.firstRun) appScope.launch {
            val device = AppLanguage.device() ?: return@launch
            val list = languages.languages.first { it.isNotEmpty() }
            val match = languages.forLocale(device, list) ?: return@launch
            if (match.code != settings.current.languageCode && settings.current.languageCode == Settings.ENGLISH) {
                settings.setLanguage(match.code, match.vernacular)
                library.clearMemory()
                languages.load(match.code)
            }
        }
    }

    /** Thumbnails: memory + disk cached; 16-bit and no fades on low-RAM devices (TV sticks). */
    fun imageLoader(): ImageLoader {
        val lowRam = appContext.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        return ImageLoader.Builder(appContext)
            .okHttpClient(http)
            .memoryCache { MemoryCache.Builder(appContext).maxSizePercent(if (lowRam) 0.15 else 0.25).build() }
            .diskCache { DiskCache.Builder().directory(appContext.cacheDir.resolve("images")).maxSizeBytes(256L shl 20).build() }
            .crossfade(if (lowRam) 0 else 150)
            .allowRgb565(lowRam)
            .respectCacheHeaders(false)
            .build()
    }
}
