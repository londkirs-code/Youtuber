package com.example.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.download.DownloadActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URL

object Notifier {
    const val CH_PROGRESS = "download_progress"
    const val CH_DONE = "download_done"
    const val CH_NEW = "new_videos"
    const val CH_SERVICE = "download_service"
    const val FG_ID = 7001

    const val EXTRA_OPEN_VIDEO_ID = "open_video_id"
    const val EXTRA_PLAY_TRACK_ID = "play_track_id"
    const val EXTRA_PLAY_FILE_PATH = "play_file_path"

    private val lastProgressAt = HashMap<String, Long>()
    private val thumbCache = LruCache<String, Bitmap>(30)
    private val thumbUrls = HashMap<String, String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "התקדמות הורדה", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "הורדה הסתיימה", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NEW, "סרטונים חדשים בערוצים שמורים", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "שירות הורדות", NotificationManager.IMPORTANCE_MIN)
        )
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notifyId(key: String) = key.hashCode()

    private fun openAppIntent(
        ctx: Context,
        videoId: String? = null,
        playTrackId: String? = null,
        playFilePath: String? = null,
        requestCode: Int = 0
    ): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (videoId != null) putExtra(EXTRA_OPEN_VIDEO_ID, videoId)
            if (playTrackId != null) putExtra(EXTRA_PLAY_TRACK_ID, playTrackId)
            if (playFilePath != null) putExtra(EXTRA_PLAY_FILE_PATH, playFilePath)
        }
        return PendingIntent.getActivity(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cacheThumbnail(key: String, url: String) {
        if (url.isBlank()) return
        thumbUrls[key] = url
        if (thumbCache.get(url) != null) return
        scope.launch {
            try {
                val stream = URL(url).openStream()
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    thumbCache.put(url, bmp)
                }
            } catch (_: Exception) {}
        }
    }

    private fun getCachedBitmap(key: String): Bitmap? {
        val url = thumbUrls[key] ?: return null
        return thumbCache.get(url)
    }

    /** Notification shown by the foreground service while downloads are running. */
    fun foregroundNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Youtuber מכין הורדה...")
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun downloadStarted(ctx: Context, taskId: String, title: String, thumbnailUrl: String = "") {
        if (!canNotify(ctx)) return
        if (thumbnailUrl.isNotBlank()) cacheThumbnail(taskId, thumbnailUrl)
        val n = progressBuilder(ctx, taskId, title, 0)
            .setContentText("ההורדה התחילה...")
            .setProgress(0, 0, true)
            .build()
        nm(ctx).notify(FG_ID, n)
    }

    /** Percent directly in title: [${percent}%] Title */
    fun downloadProgress(ctx: Context, taskId: String, title: String, percent: Int, text: String, thumbnailUrl: String = "") {
        if (!canNotify(ctx)) return
        if (thumbnailUrl.isNotBlank()) cacheThumbnail(taskId, thumbnailUrl)
        val now = System.currentTimeMillis()
        synchronized(lastProgressAt) {
            val last = lastProgressAt[taskId] ?: 0L
            if (now - last < 600 && percent < 100) return   // avoid notification rate limiting
            lastProgressAt[taskId] = now
        }
        val b = progressBuilder(ctx, taskId, title, percent).setContentText(text)
        if (percent >= 0) b.setProgress(100, percent.coerceIn(0, 100), false)
        else b.setProgress(0, 0, true)
        nm(ctx).notify(FG_ID, b.build())
    }

    private fun progressBuilder(ctx: Context, taskId: String, title: String, percent: Int): NotificationCompat.Builder {
        val cancel = PendingIntent.getBroadcast(
            ctx, notifyId(taskId),
            Intent(ctx, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_CANCEL
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleWithPercent = if (percent >= 0) "[$percent%] $title" else "[הורדה...] $title"

        val builder = NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(titleWithPercent)
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, "ביטול", cancel)

        getCachedBitmap(taskId)?.let {
            builder.setLargeIcon(it)
        }

        return builder
    }

    fun downloadFinished(ctx: Context, taskId: String, title: String, filePath: String = "") {
        synchronized(lastProgressAt) { lastProgressAt.remove(taskId) }
        if (!canNotify(ctx)) return

        val builder = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("[100%] הושלם: $title")
            .setContentText("לחיצה להפעלת הקובץ בספרייה")
            .setContentIntent(openAppIntent(ctx, playTrackId = taskId, playFilePath = filePath, requestCode = notifyId(taskId)))
            .setAutoCancel(true)

        getCachedBitmap(taskId)?.let {
            builder.setLargeIcon(it)
        }

        nm(ctx).notify(notifyId(taskId), builder.build())   // replaces progress notification
        thumbUrls.remove(taskId)
    }

    fun downloadFailed(ctx: Context, taskId: String, title: String, reason: String) {
        synchronized(lastProgressAt) { lastProgressAt.remove(taskId) }
        if (!canNotify(ctx)) return
        val builder = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("[נכשל] $title")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$reason"))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)

        getCachedBitmap(taskId)?.let {
            builder.setLargeIcon(it)
        }

        nm(ctx).notify(notifyId(taskId), builder.build())
        thumbUrls.remove(taskId)
    }

    fun cancel(ctx: Context, key: String) {
        synchronized(lastProgressAt) { lastProgressAt.remove(key) }
        thumbUrls.remove(key)
        nm(ctx).cancel(notifyId(key))
        nm(ctx).cancel(FG_ID)
    }

    fun newVideo(ctx: Context, videoId: String, title: String, channel: String, thumbnailUrl: String) {
        if (!canNotify(ctx)) return
        val download = PendingIntent.getBroadcast(
            ctx, notifyId("dl_$videoId"),
            Intent(ctx, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_DOWNLOAD
                putExtra(DownloadActionReceiver.EXTRA_VIDEO_ID, videoId)
                putExtra(DownloadActionReceiver.EXTRA_TITLE, title)
                putExtra(DownloadActionReceiver.EXTRA_CHANNEL, channel)
                putExtra(DownloadActionReceiver.EXTRA_THUMB, thumbnailUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_NEW)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("סרטון חדש ב-$channel")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(openAppIntent(ctx, videoId = videoId, requestCode = notifyId("open_$videoId")))
            .addAction(0, "הורד שמע", download)
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(notifyId("new_$videoId"), n)
    }

    fun cancelNewVideo(ctx: Context, videoId: String) {
        nm(ctx).cancel(notifyId("new_$videoId"))
    }

    fun autoDownloadStarted(ctx: Context, videoId: String, title: String, channel: String, keyword: String, thumbnailUrl: String) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_NEW)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("הורדה אוטומטית החלה: $channel")
            .setContentText("זוהתה מילת המפתח '$keyword': $title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("זוהתה מילת המפתח '$keyword' בסרטון חדש:\n$title\nההורדה החלה באופן אוטומטי."))
            .setContentIntent(openAppIntent(ctx, videoId = videoId, requestCode = notifyId("autodl_$videoId")))
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(notifyId("auto_dl_$videoId"), n)
    }
}
