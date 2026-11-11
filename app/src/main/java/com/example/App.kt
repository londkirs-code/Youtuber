package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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

class App : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ytdlpReady = CompletableDeferred<Boolean>()

    // One shared DownloadManager for the whole process (UI, notifications, receivers).
    val downloadManager: DownloadManager by lazy { DownloadManager(this) }

    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Restrict memory cache: 12% on low-RAM/emulator devices, 20% on normal
                    .maxSizePercent(if (isLowRam) 0.12 else 0.20)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowRam) 25L * 1024 * 1024 else 50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false) // YouTube thumbnails are immutable; disk cache prevents repeated network fetches
            .crossfade(!isLowRam) // Disable crossfade on low-RAM devices to eliminate GPU/CPU blending overhead
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_UI_HIDDEN) {
            try {
                coil.Coil.imageLoader(this).memoryCache?.clear()
            } catch (_: Exception) {}
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        } catch (_: Exception) {}
    }

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
                getSharedPreferences("yt-dlp-engine", MODE_PRIVATE).edit()
                    .putString("engine_error", error.stackTraceToString()).apply()
                Log.e(TAG, "Embedded yt-dlp engine initialization failed", error)
                ytdlpReady.complete(false)
            }
        }
    }

    suspend fun awaitYtdlp(): Boolean = ytdlpReady.await()

    suspend fun updateYtdlp(logMessage: (String) -> Unit = {}): Boolean {
        if (!awaitYtdlp()) {
            logMessage("The embedded yt-dlp engine is not ready.")
            return false
        }
        return try {
            val callback = object : YtDlpBridgeCallback {
                override fun onLog(level: String, message: String) {
                    logMessage("[" + level + "] " + message)
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
            logMessage(result)
            true
        } catch (error: Exception) {
            Log.e(TAG, "yt-dlp update failed", error)
            logMessage("Update failed: " + (error.message ?: error.javaClass.simpleName))
            false
        }
    }

    companion object {
        private const val TAG = "App"
    }
}
