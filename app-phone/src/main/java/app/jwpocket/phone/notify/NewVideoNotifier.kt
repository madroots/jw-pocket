package app.jwpocket.phone.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import app.jwpocket.core.model.Video
import app.jwpocket.core.network.PocketJson
import app.jwpocket.core.util.withAppLanguage
import app.jwpocket.media.R as MediaR
import app.jwpocket.phone.MainActivity
import app.jwpocket.phone.R
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/** Posts "new video" notifications; tapping one opens that video's details in the app. */
object NewVideoNotifier {

    private const val CHANNEL = "new_videos"
    private const val TAG = "new_video"
    private const val EXTRA_VIDEO = "app.jwpocket.NEW_VIDEO"
    private const val THUMBNAIL_PX = 256

    /** Announces [videos] (newest first), unless the person has notifications turned off for the app. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled is false without the Android 13 permission
    suspend fun post(context: Context, kind: NewVideoKind, videos: List<Video>) {
        val manager = NotificationManagerCompat.from(context)
        if (videos.isEmpty() || !manager.areNotificationsEnabled()) return
        val text = context.withAppLanguage()
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(text.getString(R.string.notify_channel))
                .build()
        )
        // Oldest first, so the newest ends up on top.
        for (video in videos.asReversed()) {
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(MediaR.drawable.ic_stat_pocket)
                .setContentTitle(video.title)
                .setContentText(text.getString(kind.line))
                .setLargeIcon(thumbnail(context, video))
                .setContentIntent(openDetails(context, video))
                .setAutoCancel(true)
                .build()
            manager.notify(TAG, video.seenKey.hashCode(), notification)
        }
    }

    /** The video a tapped notification asks to show, or null for any other launch. */
    fun videoFrom(intent: Intent?): Video? {
        val json = intent?.getStringExtra(EXTRA_VIDEO) ?: return null
        return runCatching { PocketJson.decodeFromString(Video.serializer(), json) }.getOrNull()
    }

    private fun openDetails(context: Context, video: Video): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_VIDEO, PocketJson.encodeToString(Video.serializer(), video))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // One request code per video, so each notification keeps its own video.
        return PendingIntent.getActivity(context, video.seenKey.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The video's thumbnail through the app's image cache; none when it can't be loaded. */
    private suspend fun thumbnail(context: Context, video: Video): Bitmap? {
        val url = video.thumbnailUrl ?: return null
        val request = ImageRequest.Builder(context).data(url).size(THUMBNAIL_PX).allowHardware(false).build()
        return (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }
}
