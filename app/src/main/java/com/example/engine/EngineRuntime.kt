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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
    val architecture: String,
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

        val nativeDir = File(app.applicationInfo.nativeLibraryDir)
        val ffmpegBinary = File(nativeDir, "libffmpeg.so")
        val ffprobeBinary = File(nativeDir, "libffprobe.so")
        if (!ffmpegBinary.isFile || !ffprobeBinary.isFile) {
            Log.w(TAG, "One or more bundled FFmpeg native libraries are missing from $nativeDir; continuing engine initialization.")
        }

        try {
            Log.i(TAG, "FFmpeg: " + readVersion(ffmpegBinary))
        } catch (error: Exception) {
            Log.w(TAG, "FFmpeg version check unavailable; continuing.", error)
        }
        try {
            Log.i(TAG, "FFprobe: " + readVersion(ffprobeBinary))
        } catch (error: Exception) {
            Log.w(TAG, "FFprobe version check unavailable; continuing.", error)
        }

        val runtimeRoot = File(app.noBackupFilesDir, "tunevault-runtime")
        val ffmpegDir = File(runtimeRoot, "bin")
        if (!ffmpegDir.exists() && !ffmpegDir.mkdirs()) {
            Log.w(TAG, "Cannot create private FFmpeg link directory; passing the native FFmpeg path directly.")
        }
        val ffmpegLink = File(ffmpegDir, "ffmpeg")
        val ffprobeLink = File(ffmpegDir, "ffprobe")
        var ffmpegLocation = ffmpegDir.absolutePath
        try {
            if (!ffmpegDir.isDirectory) throw IllegalStateException("Private FFmpeg link directory is unavailable")
            ffmpegLink.delete()
            ffprobeLink.delete()
            android.system.Os.symlink(ffmpegBinary.absolutePath, ffmpegLink.absolutePath)
            android.system.Os.symlink(ffprobeBinary.absolutePath, ffprobeLink.absolutePath)
            if (!ffmpegLink.exists() || !ffprobeLink.exists()) {
                throw IllegalStateException("FFmpeg symbolic links are not usable")
            }
        } catch (error: Exception) {
            Log.w(TAG, "FFmpeg symlink setup failed; passing libffmpeg.so directly.", error)
            ffmpegLink.delete()
            ffprobeLink.delete()
            ffmpegLocation = ffmpegBinary.absolutePath
        }

        val quickJs = File(nativeDir, "libqjs.so")
        try {
            if (!quickJs.exists()) throw IllegalStateException("QuickJS executable is missing")
            Log.i(TAG, "QuickJS: " + runBinary(quickJs, "-v").lineSequence().firstOrNull().orEmpty())
        } catch (error: Exception) {
            Log.w(TAG, "QuickJS version check unavailable; continuing.", error)
        }

        ffmpegDirectory = File(ffmpegLocation)
        quickJsExecutable = quickJs
        val userPackages = File(app.filesDir, "yt-dlp-python")
        val cache = File(app.cacheDir, "yt-dlp")
        Python.getInstance().getModule("tunevault_engine").callAttr(
            "configure", ffmpegLocation, quickJs.absolutePath,
            cache.absolutePath, userPackages.absolutePath
        )

        // versions() imports yt_dlp; if it succeeds, Python and yt-dlp are ready.
        // FFmpeg/FFprobe/QuickJS checks above are diagnostics and must not block startup.
        val version = getVersionsInternal()
        initialized = true
        Log.i(TAG, "Embedded engine ready: Python ${version.python}, yt-dlp ${version.ytDlp}")
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

    suspend fun downloadLiveHls(
        context: Context,
        taskId: String,
        url: String,
        playerClient: String,
        outBase: String,
        callback: YtDlpBridgeCallback
    ): String = withContext(Dispatchers.IO) {
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
                .callAttr("download_live_hls", url, playerClient, outBase, bridge)
                .toJava(String::class.java)
        } catch (error: PyException) {
            if (bridge.isCancelled() || error.message.orEmpty().contains("TUNEVault_CANCELLED")) {
                throw EngineCancelledException()
            }
            throw EngineExecutionException(error.message ?: "Live HLS download failed", error)
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
                val hadPreviousPackages = target.exists()
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
                    val activeInfo = JSONObject(
                        Python.getInstance().getModule("tunevault_engine")
                            .callAttr("activate_packages", target.absolutePath)
                            .toJava(String::class.java)
                    )
                    val activeVersion = activeInfo.optString("yt_dlp", "")
                    val activeEjs = activeInfo.optString("ejs", "")
                    if (activeVersion != newVersion || activeEjs.isBlank() || activeEjs == "not installed") {
                        throw IllegalStateException("New yt-dlp/EJS packages failed activation in the running Python process")
                    }
                    backup.deleteRecursively()
                    app.getSharedPreferences("yt-dlp-engine", Context.MODE_PRIVATE).edit()
                        .putString("python_package_version", newVersion).apply()
                    callback.onLog("SUCCESS", "yt-dlp " + newVersion + " and yt-dlp-ejs installed and activated.")
                    "yt-dlp " + newVersion + " ו־yt-dlp-ejs הותקנו ונטענו בהצלחה ללא הפעלה מחדש."
                } catch (error: Exception) {
                    temp.deleteRecursively()
                    if (backup.exists()) {
                        target.deleteRecursively()
                        if (backup.renameTo(target)) {
                            try {
                                Python.getInstance().getModule("tunevault_engine")
                                    .callAttr("activate_packages", target.absolutePath)
                            } catch (_: Exception) {
                                Log.e(TAG, "Could not reactivate the previous yt-dlp package set", error)
                            }
                        }
                    } else if (!hadPreviousPackages) {
                        target.deleteRecursively()
                    }
                    callback.onLog("ERROR", error.message ?: error.javaClass.simpleName)
                    throw error
                }
            }
        }

    suspend fun updateFfmpeg(context: Context, callback: YtDlpBridgeCallback): String =
        withContext(Dispatchers.IO) {
            ensureInitialized(context)
            val nativeDir = File(context.applicationContext.applicationInfo.nativeLibraryDir)
            val ffmpeg = File(nativeDir, "libffmpeg.so")
            val ffprobe = File(nativeDir, "libffprobe.so")
            try {
                Log.i(TAG, "FFmpeg version: " + readVersion(ffmpeg))
            } catch (error: Exception) {
                Log.w(TAG, "FFmpeg version check unavailable.", error)
            }
            try {
                Log.i(TAG, "FFprobe version: " + readVersion(ffprobe))
            } catch (error: Exception) {
                Log.w(TAG, "FFprobe version check unavailable.", error)
            }
            callback.onLog("INFO", "Using FFmpeg/FFprobe bundled in APK native libraries.")
            "FFmpeg ו־FFprobe המובנים באפליקציה מוכנים לשימוש."
        }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        Python.getInstance().getModule("tunevault_engine").callAttr("clear_cache")
    }

    suspend fun remuxAudioFile(context: Context, inputFile: File, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!inputFile.exists() || inputFile.length() == 0L) return@withContext false
                val nativeDir = File(context.applicationContext.applicationInfo.nativeLibraryDir)
                val ffmpeg = File(nativeDir, "libffmpeg.so")
                if (!ffmpeg.exists()) return@withContext false
                ffmpeg.setExecutable(true, false)

                if (outputFile.exists()) outputFile.delete()

                val process = ProcessBuilder(
                    ffmpeg.absolutePath,
                    "-y",
                    "-i", inputFile.absolutePath,
                    "-c", "copy",
                    "-movflags", "+faststart",
                    outputFile.absolutePath
                ).redirectErrorStream(true).start()

                val completed = process.waitFor(45, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    return@withContext false
                }
                val success = process.exitValue() == 0 && outputFile.exists() && outputFile.length() > 0L
                if (success) {
                    Log.i(TAG, "Remux successful: ${outputFile.name} (${outputFile.length()} bytes)")
                }
                success
            } catch (e: Exception) {
                Log.w(TAG, "Remux error: ${e.message}", e)
                false
            }
        }

    private fun ensureInitialized(context: Context) {
        if (!initialized) throw IllegalStateException("Embedded yt-dlp engine is not initialized")
    }

    private fun getVersionsInternal(): EngineVersions {
        val app = appContext ?: throw IllegalStateException("Engine context is unavailable")
        val nativeDir = File(app.applicationInfo.nativeLibraryDir)
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        val ffprobe = File(nativeDir, "libffprobe.so")
        val quickJs = quickJsExecutable ?: File(nativeDir, "libqjs.so")
        val info = JSONObject(
            Python.getInstance().getModule("tunevault_engine")
                .callAttr("versions").toJava(String::class.java)
        )
        return EngineVersions(
            python = info.optString("python", "unknown"),
            ytDlp = info.optString("yt_dlp", "unknown"),
            ejs = info.optString("ejs", "not installed"),
            ffmpeg = safeVersion(ffmpeg),
            ffprobe = safeVersion(ffprobe),
            quickJs = safeQuickJsVersion(quickJs),
            architecture = info.optString("architecture", "unknown"),
            curlCffi = info.optBoolean("curl_cffi", false),
            extractorCount = info.optInt("extractors", 0)
        )
    }

    private fun safeVersion(file: File): String = try {
        readVersion(file)
    } catch (error: Exception) {
        Log.w(TAG, "Version check unavailable for ${file.name}; continuing.", error)
        "unavailable: ${error.message ?: error.javaClass.simpleName}"
    }

    private fun safeQuickJsVersion(file: File): String = try {
        if (!file.exists()) throw IllegalStateException("QuickJS executable is missing")
        val output = runBinary(file, "-v")
        output.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "version unavailable"
    } catch (error: Exception) {
        Log.w(TAG, "QuickJS version check unavailable; continuing.", error)
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
