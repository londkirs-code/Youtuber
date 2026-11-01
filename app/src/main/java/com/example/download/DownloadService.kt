package com.example.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.notify.Notifier

/**
 * Keeps the process alive while downloads run in the background.
 * Started by DownloadManager when the first download starts and stopped when the last one ends.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifier.foregroundNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this, Notifier.FG_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifier.FG_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException when started from background
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DownloadService::class.java))
        }
    }
}
