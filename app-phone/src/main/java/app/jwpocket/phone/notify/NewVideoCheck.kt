package app.jwpocket.phone.notify

import android.content.Context
import androidx.annotation.StringRes
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Video
import app.jwpocket.media.pocketGraph
import app.jwpocket.phone.R
import java.util.concurrent.TimeUnit

/** The library rows the phone watches for new videos, each with its own switch in Settings. */
enum class NewVideoKind(val categoryKey: String, @StringRes val line: Int) {
    Broadcasting("StudioMonthlyPrograms", R.string.notify_new_broadcasting),
    /** "News and Announcements": nearly all Governing Body updates, plus a few other announcements. */
    News("StudioNewsReports", R.string.notify_new_news);

    fun enabled(settings: Settings): Boolean = when (this) {
        Broadcasting -> settings.notifyBroadcasting
        News -> settings.notifyNews
    }
}

/** Schedules the daily look for new videos while any notification switch is on. */
object NewVideoCheck {

    private const val WORK = "new_videos"

    /**
     * Matches the scheduled work to [settings]. A kind turned off forgets what it
     * saw, so turning it back on starts quietly instead of announcing the backlog.
     */
    fun sync(context: Context, settings: Settings) {
        val seen = SeenVideos(context)
        NewVideoKind.entries.filterNot { it.enabled(settings) }.forEach(seen::clear)
        val work = WorkManager.getInstance(context)
        if (NewVideoKind.entries.any { it.enabled(settings) }) {
            val request = PeriodicWorkRequestBuilder<NewVideoWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK)
        }
    }
}

/**
 * Loads each watched row fresh in the library language and announces videos
 * published after the newest one seen last time. The first look only records
 * that video, so installing the app (or turning a switch on) never floods.
 */
class NewVideoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val core = applicationContext.pocketGraph.core
        val settings = core.settings.current
        val seen = SeenVideos(applicationContext)
        var failed = false
        for (kind in NewVideoKind.entries.filter { it.enabled(settings) }) {
            val media = core.api.category(settings.languageCode, kind.categoryKey, fresh = true)?.media
            if (media == null) {
                failed = true
                continue
            }
            val newest = media.filter { it.firstPublished != null }.maxByOrNull { it.firstPublished!! } ?: continue
            val last = seen.get(kind)
            if (last != null) NewVideoNotifier.post(applicationContext, kind, newerThan(media, last).take(MAX_PER_CHECK))
            seen.set(kind, newest)
        }
        return if (failed) Result.retry() else Result.success()
    }

    private companion object {
        /** More than this at once means a long gap offline; the rest wait in the library. */
        const val MAX_PER_CHECK = 3
    }
}

/** The newest video seen in a row: its language-independent key and publication time. */
internal data class SeenVideo(val key: String, val published: String)

/** Stable across library languages, so switching language doesn't make old videos new. */
internal val Video.seenKey: String get() = languageAgnosticNaturalKey ?: id

/**
 * Videos of [media] published after [seen], newest first. The mediator's
 * timestamps are ISO-8601 UTC in one format, so comparing the text orders them by time.
 */
internal fun newerThan(media: List<Video>, seen: SeenVideo): List<Video> =
    media.filter { val published = it.firstPublished; published != null && published > seen.published && it.seenKey != seen.key }
        .sortedByDescending { it.firstPublished }

/** Per kind, the newest video already looked at. Kept apart from Settings: it is state, not a choice. */
private class SeenVideos(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jw_pocket_new_videos", Context.MODE_PRIVATE)

    fun get(kind: NewVideoKind): SeenVideo? {
        val key = prefs.getString("${kind.name}_key", null) ?: return null
        return SeenVideo(key, prefs.getString("${kind.name}_published", null).orEmpty())
    }

    fun set(kind: NewVideoKind, video: Video) {
        prefs.edit().putString("${kind.name}_key", video.seenKey).putString("${kind.name}_published", video.firstPublished).apply()
    }

    fun clear(kind: NewVideoKind) {
        prefs.edit().remove("${kind.name}_key").remove("${kind.name}_published").apply()
    }
}
