package com.example.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.App
import com.example.data.model.UserSettings
import com.example.notify.Notifier

/** Handles the buttons inside notifications ("ביטול" and "הורד שמע"). */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        when (intent.action) {
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                app.downloadManager.cancelDownload(taskId)
            }
            ACTION_DOWNLOAD -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return
                app.downloadManager.startDownload(
                    videoId = videoId,
                    title = intent.getStringExtra(EXTRA_TITLE) ?: videoId,
                    channel = intent.getStringExtra(EXTRA_CHANNEL) ?: "",
                    thumbnailUrl = intent.getStringExtra(EXTRA_THUMB)
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    isLive = false,
                    trimRange = null,
                    settings = UserSettings()
                )
                Notifier.cancelNewVideo(context, videoId)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.example.action.CANCEL_DOWNLOAD"
        const val ACTION_DOWNLOAD = "com.example.action.DOWNLOAD_VIDEO"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_THUMB = "thumb"
    }
}
