package app.jwpocket.media.download

import android.annotation.SuppressLint
import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import app.jwpocket.media.R
import app.jwpocket.media.pocketGraph

/** Foreground service that runs the downloads and shows their progress. */
@SuppressLint("UnsafeOptInUsageError")
@UnstableApi
class PocketDownloadService : DownloadService(
    FOREGROUND_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadsRepository.CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val repository get() = pocketGraph.media.downloads

    override fun getDownloadManager(): DownloadManager = repository.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        repository.notifications.buildProgressNotification(
            this, R.drawable.ic_stat_download, null, getString(R.string.downloads_running), downloads, notMetRequirements,
        )

    private companion object {
        const val FOREGROUND_ID = 1001
    }
}
