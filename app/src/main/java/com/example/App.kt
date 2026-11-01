package com.example

import android.app.Application
import android.util.Log
import com.example.download.DownloadManager
import com.example.engine.EngineRuntime
import com.example.engine.YtDlpBridgeCallback
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

    suspend fun updateYtdlp(onLog: (String) -> Unit = {}): Boolean {
        if (!awaitYtdlp()) {
            onLog("The embedded yt-dlp engine is not ready.")
            return false
        }
        return try {
            val callback = object : YtDlpBridgeCallback {
                override fun onLog(level: String, message: String) {
                    onLog("[" + level + "] " + message)
                }

                override fun onProgress(
                    percent: Double,
                    downloadedBytes: Long,
                    totalBytes: Long,
                    speed: String,
                    etaSeconds: Long
                ) = Unit

                override fun isCancelled(): Boolean = false
            }
            val result = EngineRuntime.updateYtDlp(this, callback)
            onLog(result)
            true
        } catch (error: Exception) {
            Log.e(TAG, "yt-dlp update failed", error)
            onLog("Update failed: " + (error.message ?: error.javaClass.simpleName))
            false
        }
    }

    companion object {
        private const val TAG = "App"
    }
}
