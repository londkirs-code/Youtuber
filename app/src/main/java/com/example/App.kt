package com.example

import android.app.Application
import android.util.Log
import com.example.download.DownloadManager
import com.example.engine.EngineRuntime
import com.example.notify.Notifier
import com.example.work.ChannelCheckWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ytdlpReady = CompletableDeferred<Boolean>()

    // One shared DownloadManager for the whole process (UI, notifications, receivers).
    val downloadManager: DownloadManager by lazy { DownloadManager(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ChannelCheckWorker.schedule(this)

        appScope.launch {
            try {
                EngineRuntime.initialize(this@App)
                Log.i(TAG, "Embedded Python/yt-dlp/FFmpeg engine initialized.")
                ytdlpReady.complete(true)
            } catch (error: Exception) {
                Log.e(TAG, "Embedded yt-dlp engine initialization failed", error)
                ytdlpReady.complete(false)
            }
        }
    }

    suspend fun awaitYtdlp(): Boolean = ytdlpReady.await()

    companion object {
        private const val TAG = "App"
    }
}
