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
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

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

    /**
     * Use OkHttp for GitHub release metadata and asset downloads. The library updater
     * uses URL.openStream() without a User-Agent and hides the underlying IOException.
     */
    suspend fun updateYtdlp(onLog: ((String) -> Unit)? = null): Boolean {
        return updateMutex.withLock {
            val engineDir = File(noBackupFilesDir, "\${YoutubeDL.baseName}/\${YoutubeDL.ytdlpDirName}")
            val binary = File(engineDir, YoutubeDL.ytdlpBin)
            val channels = listOf(
                "NIGHTLY" to "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest",
                "STABLE" to "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
            )
            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            for ((channelName, apiUrl) in channels) {
                var tempFile: File? = null
                var backupFile: File? = null
                try {
                    onLog?.invoke("Checking $channelName release metadata...")
                    val apiRequest = Request.Builder()
                        .url(apiUrl)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "TuneVault-Android/1.4")
                        .build()
                    val release = client.newCall(apiRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            val detail = response.body?.string()?.take(500).orEmpty()
                            throw IllegalStateException("GitHub API HTTP \${response.code}: $detail")
                        }
                        JSONObject(response.body?.string() ?: throw IllegalStateException("Empty GitHub API response"))
                    }

                    val tag = release.optString("tag_name")
                    val releaseName = release.optString("name", tag)
                    val assets = release.optJSONArray("assets")
                        ?: throw IllegalStateException("Release $tag has no assets array")
                    var downloadUrl: String? = null
                    var expectedSize = -1L
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name") == YoutubeDL.ytdlpBin) {
                            downloadUrl = asset.optString("browser_download_url")
                            expectedSize = asset.optLong("size", -1L)
                            break
                        }
                    }
                    if (downloadUrl.isNullOrBlank() || expectedSize <= 0L) {
                        throw IllegalStateException("Release $tag has no valid yt-dlp asset")
                    }
                    if (!engineDir.exists() && !engineDir.mkdirs()) {
                        throw IllegalStateException("Cannot create yt-dlp engine directory")
                    }

                    onLog?.invoke("Downloading yt-dlp $tag ($expectedSize bytes) from GitHub...")
                    tempFile = File.createTempFile("yt-dlp-update-", ".tmp", engineDir)
                    val assetRequest = Request.Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "TuneVault-Android/1.4")
                        .build()
                    client.newCall(assetRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("GitHub asset HTTP \${response.code}")
                        }
                        val body = response.body ?: throw IllegalStateException("GitHub asset response is empty")
                        FileOutputStream(tempFile).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    if (tempFile.length() != expectedSize || tempFile.length() < 1_000_000L) {
                        throw IllegalStateException("Downloaded asset size mismatch: \${tempFile.length()} vs $expectedSize")
                    }
                    if (!tempFile.setExecutable(true, false)) {
                        throw IllegalStateException("Could not mark downloaded yt-dlp executable")
                    }

                    backupFile = File(engineDir, "yt-dlp.previous")
                    if (backupFile.exists() && !backupFile.delete()) {
                        throw IllegalStateException("Could not clear previous yt-dlp backup")
                    }
                    if (binary.exists() && !binary.renameTo(backupFile)) {
                        throw IllegalStateException("Could not back up current yt-dlp executable")
                    }
                    if (!tempFile.renameTo(binary)) {
                        if (backupFile.exists()) backupFile.renameTo(binary)
                        throw IllegalStateException("Could not install downloaded yt-dlp executable")
                    }
                    tempFile = null

                    val installedVersion = try {
                        verifyYtdlpEngine()
                    } catch (e: Exception) {
                        binary.delete()
                        if (backupFile.exists()) backupFile.renameTo(binary)
                        throw IllegalStateException("Downloaded $tag is not runnable: \${e.message}", e)
                    }
                    if (backupFile.exists()) backupFile.delete()

                    getSharedPreferences("youtubedl-android", MODE_PRIVATE).edit()
                        .putString("dlpVersion", tag)
                        .putString("dlpVersionName", releaseName)
                        .apply()
                    getSharedPreferences("ytdlp", MODE_PRIVATE).edit()
                        .putLong("last_update", System.currentTimeMillis())
                        .apply()

                    val message = "Installed yt-dlp $tag successfully; executable reports $installedVersion."
                    Log.i(TAG, message)
                    onLog?.invoke(message)
                    return@withLock true
                } catch (e: Exception) {
                    val root = generateSequence(e as Throwable?) { it.cause }
                        .map { "\${it.javaClass.simpleName}: \${it.message}" }
                        .joinToString(" <- ")
                    val message = "yt-dlp $channelName update failed: $root"
                    Log.e(TAG, message, e)
                    onLog?.invoke(message)
                    tempFile?.delete()
                    if (backupFile?.exists() == true && !binary.exists()) backupFile.renameTo(binary)
                }
            }
            onLog?.invoke("Both direct GitHub update channels failed; keeping the last verified engine.")
            false
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
