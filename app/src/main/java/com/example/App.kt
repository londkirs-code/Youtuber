package com.example

import android.app.Application
import android.util.Log
import com.example.download.DownloadManager
import com.example.notify.Notifier
import com.example.work.ChannelCheckWorker
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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
            val currentAppVersion = BuildConfig.VERSION_CODE
            val enginePrefs = getSharedPreferences("ytdlp_engine", MODE_PRIVATE)
            val previousAppVersion = enginePrefs.getInt("engine_app_version", 0)
            val appVersionChanged = previousAppVersion != currentAppVersion

            try {
                if (appVersionChanged) {
                    // youtubedl-android caches yt-dlp in noBackupFilesDir and only copies
                    // the APK resource when the cached executable is absent. Replace it
                    // on app upgrades so an old executable cannot survive a wrapper upgrade.
                    val engineDir = File(
                        noBackupFilesDir,
                        "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}"
                    )
                    if (engineDir.exists() && !engineDir.deleteRecursively()) {
                        throw IllegalStateException("Could not remove the cached yt-dlp engine")
                    }

                    // Force the library's nightly updater to check after the bundled engine
                    // has been replaced. These are the library's actual preference keys.
                    getSharedPreferences("youtubedl-android", MODE_PRIVATE).edit()
                        .remove("dlpVersion")
                        .remove("dlpVersionName")
                        .apply()
                    getSharedPreferences("ytdlp", MODE_PRIVATE).edit()
                        .remove("last_update")
                        .remove("last_check")
                        .apply()

                    Log.i(
                        TAG,
                        "App version changed $previousAppVersion -> $currentAppVersion; resetting cached yt-dlp."
                    )
                }

                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)

                // Exercise the real execution path. The Android wrapper injects
                // --js-runtimes here, so this detects an old or incompatible binary.
                val bundledVersion = verifyYtdlpEngine()
                Log.i(TAG, "Bundled yt-dlp runtime preflight passed: $bundledVersion")

                if (appVersionChanged) {
                    enginePrefs.edit()
                        .putInt("engine_app_version", currentAppVersion)
                        .apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp initialization/preflight failed", e)
                ytdlpReady.complete(false)
                return@launch
            }

            // Check nightly on migration and then at most once every 12 hours.
            val prefs = getSharedPreferences("ytdlp", MODE_PRIVATE)
            val lastCheck = prefs.getLong("last_check", 0L)
            val now = System.currentTimeMillis()
            if (appVersionChanged || now - lastCheck > 12L * 60 * 60 * 1000) {
                val updateSucceeded = updateYtdlp()
                prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()

                if (updateSucceeded) {
                    try {
                        val updatedVersion = verifyYtdlpEngine()
                        Log.i(TAG, "Updated yt-dlp runtime preflight passed: $updatedVersion")
                    } catch (e: Exception) {
                        Log.e(TAG, "Updated yt-dlp is incompatible; restoring APK-bundled engine.", e)
                        try {
                            restoreBundledYtdlp()
                            val fallbackVersion = verifyYtdlpEngine()
                            Log.w(TAG, "Restored compatible bundled yt-dlp: $fallbackVersion")
                        } catch (restoreError: Exception) {
                            Log.e(TAG, "Could not restore the bundled yt-dlp engine", restoreError)
                            ytdlpReady.complete(false)
                            return@launch
                        }
                    }
                } else {
                    // A failed network update must not disable downloads. The verified
                    // APK-bundled engine remains installed and ready to use.
                    Log.w(TAG, "Nightly update unavailable; keeping the verified bundled yt-dlp engine.")
                }
            }

            ytdlpReady.complete(true)
        }
    }

    /** Download code calls this before the first yt-dlp command. */
    suspend fun awaitYtdlp(): Boolean = ytdlpReady.await()

    /** Updates yt-dlp and reports whether the download/install process succeeded. */
    suspend fun updateYtdlp(): Boolean {
        return updateMutex.withLock {
            val previousVersion = YoutubeDL.version(this)
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(
                    this, YoutubeDL.UpdateChannel.NIGHTLY
                )
                val currentVersion = YoutubeDL.version(this)
                val succeeded = status == YoutubeDL.UpdateStatus.DONE ||
                    status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE
                Log.i(
                    TAG,
                    "yt-dlp update status=$status, version=$previousVersion -> $currentVersion"
                )
                if (succeeded) {
                    getSharedPreferences("ytdlp", MODE_PRIVATE)
                        .edit().putLong("last_update", System.currentTimeMillis()).apply()
                }
                succeeded
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed (previous version: $previousVersion): ${e.message}", e)
                false
            }
        }
    }

    /** Verifies the actual installed binary via the wrapper, including its QuickJS argument. */
    private fun verifyYtdlpEngine(): String {
        val response = YoutubeDL.getInstance().execute(
            YoutubeDLRequest(emptyList()).apply { addOption("--version") }
        )
        val version = response.out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        if (response.exitCode != 0 || version.isNullOrBlank()) {
            throw IllegalStateException(
                "yt-dlp preflight failed (exit=${response.exitCode}): ${response.err}"
            )
        }
        return version
    }

    /** Restores the executable shipped in this APK if a downloaded update fails preflight. */
    private fun restoreBundledYtdlp() {
        val engineDir = File(
            noBackupFilesDir,
            "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}"
        )
        if (engineDir.exists() && !engineDir.deleteRecursively()) {
            throw IllegalStateException("Could not remove the incompatible yt-dlp engine")
        }
        YoutubeDL.getInstance().init_ytdlp(this, engineDir)
        getSharedPreferences("youtubedl-android", MODE_PRIVATE).edit()
            .remove("dlpVersion")
            .remove("dlpVersionName")
            .apply()
    }

    companion object {
        private const val TAG = "App"
    }
}
