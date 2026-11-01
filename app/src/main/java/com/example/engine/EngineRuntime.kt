package com.example.engine

import android.content.Context
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

interface YtDlpBridgeCallback {
    fun onLog(level: String, message: String)
    fun onProgress(percent: Double, downloadedBytes: Long, totalBytes: Long, speed: String, etaSeconds: Long)
    fun isCancelled(): Boolean
}

data class EngineVersions(
    val python: String,
    val ytDlp: String,
    val ejs: String,
    val ffmpeg: String,
    val ffprobe: String,
    val quickJs: String,
    val curlCffi: Boolean,
    val extractorCount: Int
)

class EngineCancelledException : Exception("Download cancelled by user")
class EngineExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object EngineRuntime {
    private const val TAG = "EngineRuntime"
    private const val BUNDLED_FFMPEG_VERSION = "9.0.2"
    private const val FFMPEG_RELEASE_API = "https://api.github.com/repos/Tyrrrz/FFmpegBin/releases/latest"
    private const val FFMPEG_ASSET = "ffmpeg-android-arm64.zip"

    private val updateMutex = Mutex()
    private val http = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    @Volatile private var appContext: Context? = null
    @Volatile private var ffmpegDirectory: File? = null
    @Volatile private var quickJsExecutable: File? = null
    @Volatile private var initialized = false
    private val activeCancellations = ConcurrentHashMap<String, AtomicBoolean>()

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        appContext = app
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))

        val runtimeRoot = File(app.noBackupFilesDir, "tunevault-runtime")
        val ffmpegDir = File(runtimeRoot, "ffmpeg")
        val ffmpeg = File(ffmpegDir, "ffmpeg")
        val ffprobe = File(ffmpegDir, "ffprobe")
        if (!ffmpeg.exists() || !ffprobe.exists() ||
            !isVersionAtLeast(readVersion(ffmpeg), BUNDLED_FFMPEG_VERSION)
        ) {
            installBundledFfmpeg(app, runtimeRoot)
        }
        if (!ffmpeg.setExecutable(true, false) || !ffprobe.setExecutable(true, false)) {
            throw IllegalStateException("Could not mark FFmpeg/FFprobe executable")
        }

        val quickJs = File(app.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (!quickJs.exists()) throw IllegalStateException("QuickJS executable libqjs.so is missing from the APK")
        quickJs.setExecutable(true, false)

        ffmpegDirectory = ffmpegDir
        quickJsExecutable = quickJs
        val userPackages = File(app.filesDir, "yt-dlp-python")
        val cache = File(app.cacheDir, "yt-dlp")
        Python.getInstance().getModule("tunevault_engine").callAttr(
            "configure", ffmpegDir.absolutePath, quickJs.absolutePath,
            cache.absolutePath, userPackages.absolutePath
        )

        val version = getVersionsInternal()
        val pythonParts = version.python.split(".").take(2).mapNotNull { it.toIntOrNull() }
        if (pythonParts.size < 2 || pythonParts[0] < 3 ||
            (pythonParts[0] == 3 && pythonParts[1] < 11)
        ) throw IllegalStateException("Python ${version.python} is too old for current yt-dlp")
        if (version.ytDlp.isBlank() || version.ytDlp == "unknown") {
            throw IllegalStateException("yt-dlp Python package did not load")
        }
        initialized = true
        Log.i(TAG, "Embedded engine ready: Python ${version.python}, yt-dlp ${version.ytDlp}, FFmpeg ${version.ffmpeg}")
    }

    fun isReady(): Boolean = initialized

    suspend fun getVersions(context: Context): EngineVersions = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        getVersionsInternal()
    }

    suspend fun runYtDlp(
        context: Context,
        taskId: String,
        args: List<String>,
        callback: YtDlpBridgeCallback
    ): Int = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val cancellation = AtomicBoolean(false)
        activeCancellations[taskId] = cancellation
        val bridge = object : YtDlpBridgeCallback {
            override fun onLog(level: String, message: String) = callback.onLog(level, message)
            override fun onProgress(percent: Double, downloadedBytes: Long, totalBytes: Long, speed: String, etaSeconds: Long) =
                callback.onProgress(percent, downloadedBytes, totalBytes, speed, etaSeconds)
            override fun isCancelled(): Boolean = cancellation.get() || callback.isCancelled()
        }
        try {
            Python.getInstance().getModule("tunevault_engine")
                .callAttr("run_cli", args.toTypedArray(), bridge)
                .toJava(Int::class.javaObjectType)
        } catch (error: PyException) {
            if (bridge.isCancelled() || error.message.orEmpty().contains("TUNEVault_CANCELLED")) {
                throw EngineCancelledException()
            }
            throw EngineExecutionException(error.message ?: "yt-dlp execution failed", error)
        } finally {
            activeCancellations.remove(taskId)
        }
    }

    fun cancel(taskId: String) {
        activeCancellations[taskId]?.set(true)
    }

    suspend fun runCommand(context: Context, command: String, callback: YtDlpBridgeCallback): Int =
        withContext(Dispatchers.IO) {
            ensureInitialized(context)
            try {
                Python.getInstance().getModule("tunevault_engine")
                    .callAttr("run_cli_text", command, callback)
                    .toJava(Int::class.javaObjectType)
            } catch (error: PyException) {
                if (callback.isCancelled() || error.message.orEmpty().contains("TUNEVault_CANCELLED")) {
                    throw EngineCancelledException()
                }
                throw EngineExecutionException(error.message ?: "yt-dlp command failed", error)
            }
        }

    suspend fun inspectUrl(
        context: Context, url: String, playerClient: String, callback: YtDlpBridgeCallback
    ): String = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        try {
            Python.getInstance().getModule("tunevault_engine")
                .callAttr("inspect_url", url, playerClient, callback)
                .toJava(String::class.java)
        } catch (error: PyException) {
            if (callback.isCancelled() || error.message.orEmpty().contains("TUNEVault_CANCELLED")) {
                throw EngineCancelledException()
            }
            throw EngineExecutionException(error.message ?: "YouTube extraction test failed", error)
        }
    }

    suspend fun updateYtDlp(context: Context, callback: YtDlpBridgeCallback): String =
        updateMutex.withLock {
            withContext(Dispatchers.IO) {
                ensureInitialized(context)
                val app = context.applicationContext
                val target = File(app.filesDir, "yt-dlp-python")
                val temp = File(app.filesDir, "yt-dlp-python.tmp")
                val backup = File(app.filesDir, "yt-dlp-python.backup")
                temp.deleteRecursively()
                if (!temp.mkdirs()) throw IllegalStateException("Cannot create temporary Python package directory")
                try {
                    callback.onLog("INFO", "Installing latest yt-dlp and yt-dlp-ejs wheels from PyPI...")
                    val result = Python.getInstance().getModule("tunevault_engine")
                        .callAttr("update_packages", temp.absolutePath, callback)
                        .toJava(Int::class.javaObjectType)
                    if (result != 0) throw IllegalStateException("pip exited with code $result")
                    val versionFile = File(temp, "yt_dlp/version.py")
                    if (!versionFile.isFile || !File(temp, "yt_dlp_ejs").exists()) {
                        throw IllegalStateException("Downloaded packages are incomplete")
                    }
                    val match = Regex("""__version__\s*=\s*['"]([^'"]+)['"]""").find(versionFile.readText())
                        ?: throw IllegalStateException("Could not read installed yt-dlp version")
                    val newVersion = match.groupValues[1]
                    backup.deleteRecursively()
                    if (target.exists() && !target.renameTo(backup)) {
                        throw IllegalStateException("Could not back up previous yt-dlp packages")
                    }
                    if (!temp.renameTo(target)) {
                        if (backup.exists()) backup.renameTo(target)
                        throw IllegalStateException("Could not atomically install updated yt-dlp packages")
                    }
                    backup.deleteRecursively()
                    app.getSharedPreferences("yt-dlp-engine", Context.MODE_PRIVATE).edit()
                        .putString("python_package_version", newVersion).apply()
                    callback.onLog("SUCCESS", "yt-dlp ${newVersion} and yt-dlp-ejs installed. Restart TuneVault to load them.")
                    "yt-dlp ${newVersion} הותקן. יש להפעיל מחדש את TuneVault כדי לטעון את הגרסה החדשה."
                } catch (error: Exception) {
                    temp.deleteRecursively()
                    if (backup.exists() && !target.exists()) backup.renameTo(target)
                    callback.onLog("ERROR", error.message ?: error.javaClass.simpleName)
                    throw error
                }
            }
        }

    suspend fun updateFfmpeg(context: Context, callback: YtDlpBridgeCallback): String =
        updateMutex.withLock {
            withContext(Dispatchers.IO) {
                ensureInitialized(context)
                val app = context.applicationContext
                val runtimeRoot = File(app.noBackupFilesDir, "tunevault-runtime")
                val currentDir = File(runtimeRoot, "ffmpeg")
                val tempDir = File(runtimeRoot, "ffmpeg-update.tmp")
                val backupDir = File(runtimeRoot, "ffmpeg-backup")
                val zipFile = File(app.cacheDir, "ffmpeg-android-update.zip")
                tempDir.deleteRecursively()
                backupDir.deleteRecursively()
                try {
                    callback.onLog("INFO", "Checking the latest Android FFmpeg release...")
                    val release = http.newCall(
                        Request.Builder().url(FFMPEG_RELEASE_API)
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("User-Agent", "TuneVault-Android").build()
                    ).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("GitHub API HTTP ${response.code}")
                        JSONObject(response.body?.string() ?: throw IllegalStateException("Empty FFmpeg release response"))
                    }
                    val tag = release.optString("tag_name")
                    val assets = release.optJSONArray("assets") ?: throw IllegalStateException("FFmpeg release has no assets")
                    var url: String? = null
                    var digest = ""
                    var expectedSize = 0L
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        if (asset.optString("name") == FFMPEG_ASSET) {
                            url = asset.optString("browser_download_url")
                            digest = asset.optString("digest").removePrefix("sha256:")
                            expectedSize = asset.optLong("size")
                            break
                        }
                    }
                    if (url.isNullOrBlank() || digest.isBlank() || expectedSize <= 0L) {
                        throw IllegalStateException("Latest release does not contain a verifiable ${FFMPEG_ASSET}")
                    }
                    val currentVersion = readVersion(File(currentDir, "ffmpeg"))
                    if (currentVersion.contains(tag)) {
                        callback.onLog("SUCCESS", "FFmpeg ${currentVersion} is already current.")
                        return@withContext "FFmpeg ${currentVersion} כבר מעודכן."
                    }

                    callback.onLog("INFO", "Downloading FFmpeg ${tag} (${expectedSize} bytes)...")
                    http.newCall(Request.Builder().url(url).header("User-Agent", "TuneVault-Android").build())
                        .execute().use { response ->
                            if (!response.isSuccessful) throw IllegalStateException("FFmpeg download HTTP ${response.code}")
                            val body = response.body ?: throw IllegalStateException("Empty FFmpeg download")
                            FileOutputStream(zipFile).use { output ->
                                body.byteStream().use { input -> input.copyTo(output) }
                                output.fd.sync()
                            }
                        }
                    if (zipFile.length() != expectedSize) {
                        throw IllegalStateException("FFmpeg archive size mismatch: ${zipFile.length()} vs ${expectedSize}")
                    }
                    if (!sha256(zipFile).equals(digest, ignoreCase = true)) {
                        throw IllegalStateException("FFmpeg SHA-256 mismatch")
                    }

                    if (!tempDir.mkdirs()) throw IllegalStateException("Cannot create FFmpeg staging directory")
                    ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val baseName = File(entry.name).name
                            if (!entry.isDirectory && (baseName == "ffmpeg" || baseName == "ffprobe")) {
                                FileOutputStream(File(tempDir, baseName)).use { output -> zip.copyTo(output) }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                    val newFfmpeg = File(tempDir, "ffmpeg")
                    val newFfprobe = File(tempDir, "ffprobe")
                    if (!newFfmpeg.isFile || !newFfprobe.isFile || newFfmpeg.length() < 1_000_000L) {
                        throw IllegalStateException("FFmpeg archive did not contain valid ffmpeg and ffprobe executables")
                    }
                    newFfmpeg.setExecutable(true, false)
                    newFfprobe.setExecutable(true, false)
                    val detected = readVersion(newFfmpeg)
                    if (!detected.contains(tag)) {
                        throw IllegalStateException("Downloaded FFmpeg version '$detected' does not match release '$tag'")
                    }
                    if (currentDir.exists() && !currentDir.renameTo(backupDir)) {
                        throw IllegalStateException("Could not back up the current FFmpeg runtime")
                    }
                    if (!tempDir.renameTo(currentDir)) {
                        if (backupDir.exists()) backupDir.renameTo(currentDir)
                        throw IllegalStateException("Could not install the new FFmpeg runtime")
                    }
                    backupDir.deleteRecursively()
                    callback.onLog("SUCCESS", "FFmpeg and FFprobe ${tag} installed and executable.")
                    "FFmpeg ו־FFprobe עודכנו בהצלחה לגרסה ${tag}."
                } catch (error: Exception) {
                    tempDir.deleteRecursively()
                    if (backupDir.exists() && !currentDir.exists()) backupDir.renameTo(currentDir)
                    callback.onLog("ERROR", error.message ?: error.javaClass.simpleName)
                    throw error
                } finally {
                    zipFile.delete()
                }
            }
        }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        Python.getInstance().getModule("tunevault_engine").callAttr("clear_cache")
    }

    private fun ensureInitialized(context: Context) {
        if (!initialized) throw IllegalStateException("Embedded yt-dlp engine is not initialized")
    }

    private fun getVersionsInternal(): EngineVersions {
        val app = appContext ?: throw IllegalStateException("Engine context is unavailable")
        val info = JSONObject(
            Python.getInstance().getModule("tunevault_engine").callAttr("versions").toJava(String::class.java)
        )
        val ffmpegDir = ffmpegDirectory ?: File(app.noBackupFilesDir, "tunevault-runtime/ffmpeg")
        val quickJs = quickJsExecutable ?: File(app.applicationInfo.nativeLibraryDir, "libqjs.so")
        return EngineVersions(
            python = info.optString("python", "unknown"),
            ytDlp = info.optString("yt_dlp", "unknown"),
            ejs = info.optString("ejs", "not installed"),
            ffmpeg = safeVersion(File(ffmpegDir, "ffmpeg")),
            ffprobe = safeVersion(File(ffmpegDir, "ffprobe")),
            quickJs = safeVersion(quickJs),
            curlCffi = info.optBoolean("curl_cffi", false),
            extractorCount = info.optInt("extractors", 0)
        )
    }

    private fun installBundledFfmpeg(context: Context, runtimeRoot: File) {
        val tempDir = File(runtimeRoot, "ffmpeg-bundled.tmp")
        val targetDir = File(runtimeRoot, "ffmpeg")
        tempDir.deleteRecursively()
        if (!tempDir.mkdirs()) throw IllegalStateException("Cannot create bundled FFmpeg directory")
        copyAsset(context, "ffmpeg/ffmpeg", File(tempDir, "ffmpeg"))
        copyAsset(context, "ffmpeg/ffprobe", File(tempDir, "ffprobe"))
        File(tempDir, "ffmpeg").setExecutable(true, false)
        File(tempDir, "ffprobe").setExecutable(true, false)
        val version = readVersion(File(tempDir, "ffmpeg"))
        if (!version.contains(BUNDLED_FFMPEG_VERSION)) {
            tempDir.deleteRecursively()
            throw IllegalStateException("Bundled FFmpeg version mismatch: ${version}")
        }
        val backup = File(runtimeRoot, "ffmpeg-old")
        backup.deleteRecursively()
        if (targetDir.exists() && !targetDir.renameTo(backup)) {
            tempDir.deleteRecursively()
            throw IllegalStateException("Could not back up previous FFmpeg directory")
        }
        if (!tempDir.renameTo(targetDir)) {
            if (backup.exists()) backup.renameTo(targetDir)
            throw IllegalStateException("Could not install bundled FFmpeg")
        }
        backup.deleteRecursively()
    }

    private fun copyAsset(context: Context, asset: String, destination: File) {
        destination.parentFile?.mkdirs()
        context.assets.open(asset).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        destination.setExecutable(true, false)
    }

    private fun safeVersion(file: File): String = try {
        readVersion(file)
    } catch (error: Exception) {
        "unavailable: ${error.message ?: error.javaClass.simpleName}"
    }

    private fun readVersion(executable: File): String {
        if (!executable.exists()) throw IllegalStateException("${executable.name} is missing")
        executable.setExecutable(true, false)
        val output = runBinary(executable, "-version")
        return output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: throw IllegalStateException("${executable.name} returned no version output")
    }

    private fun runBinary(executable: File, vararg args: String): String {
        val process = ProcessBuilder(listOf(executable.absolutePath) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("${executable.name} version check timed out")
        }
        if (process.exitValue() != 0 && output.isBlank()) {
            throw IllegalStateException("${executable.name} exited with ${process.exitValue()}")
        }
        return output
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isVersionAtLeast(currentLine: String, expected: String): Boolean {
        val current = Regex("""(\d+(?:\.\d+)+)""").find(currentLine)?.value ?: return false
        val left = current.split(".").mapNotNull { it.toIntOrNull() }
        val right = expected.split(".").mapNotNull { it.toIntOrNull() }
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return true
    }
}
