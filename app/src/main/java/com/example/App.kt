package com.example

import android.app.Application
import android.util.Log
import com.example.download.DownloadManager
import com.example.notify.Notifier
import com.example.work.ChannelCheckWorker
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ytdlpReady = CompletableDeferred<Boolean>()
    private val updateMutex = Mutex()

    // One shared DownloadManager for the whole process (UI, notifications, receivers)
    val downloadManager: DownloadManager by lazy { DownloadManager(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ChannelCheckWorker.schedule(this)

        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp init failed", e)
                ytdlpReady.complete(false)
                return@launch
            }
            // Update yt-dlp at most once every 12 hours (YouTube changes constantly)
            val prefs = getSharedPreferences("ytdlp", MODE_PRIVATE)
            val last = prefs.getLong("last_update", 0L)
            if (System.currentTimeMillis() - last > 12L * 60 * 60 * 1000) {
                updateYtdlp()
            }
            ytdlpReady.complete(true)
        }
    }

    /** Download code calls this before the first yt-dlp command. */
    suspend fun awaitYtdlp(): Boolean = ytdlpReady.await()

    /** Updates the yt-dlp binary. Safe to call from several places. */
    suspend fun updateYtdlp() {
        updateMutex.withLock {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.NIGHTLY)
                Log.i(TAG, "yt-dlp update status: $status")
                getSharedPreferences("ytdlp", MODE_PRIVATE)
                    .edit().putLong("last_update", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "App"
    }
}
