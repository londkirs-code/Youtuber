package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.network.YouTubeEngine
import com.example.notify.Notifier
import java.util.concurrent.TimeUnit

/**
 * Every ~15 minutes checks the RSS feed of each saved channel.
 * When a video appears that we haven't seen yet -> notification with a "download" button.
 */
class ChannelCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            val dao = AppDatabase.getDatabase(applicationContext).savedMediaDao()
            val channels = dao.getSavedChannelsOnce().filter { it.notifyNewVideos }

            for (ch in channels) {
                var channelId = ch.channelId
                if (channelId.isBlank() && ch.seedVideoId.isNotBlank()) {
                    channelId = YouTubeEngine.resolveChannelIdFromVideo(ch.seedVideoId).orEmpty()
                    if (channelId.isNotBlank()) dao.updateChannelId(ch.channelTitle, channelId)
                }
                if (channelId.isBlank()) continue

                val feed = YouTubeEngine.fetchChannelFeed(channelId)
                if (feed.isEmpty()) continue

                // First time we see this channel: remember the newest video, don't notify
                if (ch.lastSeenVideoId.isBlank()) {
                    dao.updateLastSeen(ch.channelTitle, feed.first().videoId)
                    continue
                }

                val fresh = feed.takeWhile { it.videoId != ch.lastSeenVideoId }
                if (fresh.isEmpty()) continue

                fresh.take(3).reversed().forEach { v ->
                    Notifier.newVideo(
                        applicationContext, v.videoId, v.title, ch.channelTitle,
                        "https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg"
                    )
                }
                dao.updateLastSeen(ch.channelTitle, feed.first().videoId)
            }
        } catch (e: Exception) {
            Log.w("ChannelCheckWorker", "check failed", e)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "channel_check"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<ChannelCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
