package com.example.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.App
import com.example.data.local.AppDatabase
import com.example.data.local.TrackEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.TrimRange
import com.example.data.model.UserSettings
import com.example.notify.Notifier
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(private val context: Context) {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val database = AppDatabase.getDatabase(context)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val liveStopRequested = ConcurrentHashMap.newKeySet<String>()

    private val musicDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), "Music")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun startDownload(
        videoId: String,
        title: String,
        channel: String,
        thumbnailUrl: String,
        isLive: Boolean = false,
        trimRange: TrimRange? = null,
        settings: UserSettings
    ): String {
        val taskId = UUID.randomUUID().toString()
        val newTask = DownloadTask(
            id = taskId,
            videoId = videoId,
            title = title,
            channel = channel,
            thumbnailUrl = thumbnailUrl,
            isLiveStream = isLive,
            trimRange = trimRange,
            status = DownloadStatus.QUEUED,
            hebrewStatusText = "נוסף לתור ההורדות..."
        )

        _tasks.update { listOf(newTask) + it }

        // Keep the process alive + show "download started" notification
        DownloadService.start(context)
        Notifier.downloadStarted(context, taskId, title)

        val job = scope.launch {
            executeDownload(taskId, videoId, title, channel, thumbnailUrl, isLive, trimRange, settings)
        }
        activeJobs[taskId] = job
        return taskId
    }

    private suspend fun executeDownload(
        taskId: String,
        videoId: String,
        title: String,
        channel: String,
        thumbnailUrl: String,
        isLive: Boolean,
        trimRange: TrimRange?,
        settings: UserSettings
    ) {
        var lastPercent = 0

        fun log(msg: String) {
            _tasks.update { list ->
                list.map { if (it.id == taskId) it.copy(logs = it.logs + msg) else it }
            }
        }

        fun rawLog(line: String) {
            if (line.isBlank()) return
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) {
                        val current = it.rawLogs
                        val next = if (current.size >= 1000) current.drop(current.size - 999) + line else current + line
                        it.copy(rawLogs = next)
                    } else it
                }
            }
        }

        fun updateState(
            status: DownloadStatus,
            progress: Int = -1,
            speed: String? = null,
            downloadedSize: String? = null,
            totalSize: String? = null,
            hebrewStatus: String? = null,
            recordedSec: Long? = null
        ) {
            if (progress >= 0) lastPercent = progress
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) {
                        it.copy(
                            status = status,
                            progressPercent = if (progress >= 0) progress else it.progressPercent,
                            downloadSpeedText = speed ?: it.downloadSpeedText,
                            downloadedSizeText = downloadedSize ?: it.downloadedSizeText,
                            totalSizeText = totalSize ?: it.totalSizeText,
                            hebrewStatusText = hebrewStatus ?: it.hebrewStatusText,
                            recordedDurationSeconds = recordedSec ?: it.recordedDurationSeconds
                        )
                    } else it
                }
            }
            // ---- notifications follow the task state ----
            when (status) {
                DownloadStatus.FAILED ->
                    Notifier.downloadFailed(context, taskId, title, hebrewStatus ?: "שגיאה")
                DownloadStatus.COMPLETED -> Unit // handled at the end of the download
                DownloadStatus.RECORDING_LIVE ->
                    Notifier.downloadProgress(context, taskId, title, -1, hebrewStatus ?: "מקליט שידור חי...")
                DownloadStatus.DOWNLOADING ->
                    Notifier.downloadProgress(context, taskId, title, lastPercent, hebrewStatus ?: "מוריד...")
                else ->
                    Notifier.downloadProgress(context, taskId, title, if (status == DownloadStatus.FINISHING) 100 else -1, hebrewStatus ?: "")
            }
        }

        try {
            log("[yt-dlp] מתחיל תהליך הורדה עבור: $title")

            // Network check
            if (!settings.allowCellularDownloads && !isWifiConnected()) {
                log("[שגיאה] ההגדרה חוסמת הורדה ברשת סלולרית. אנא התחבר ל-Wi-Fi או אפשר נתונים בהגדרות.")
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: נדרש חיבור Wi-Fi לפי ההגדרות")
                return
            }

            val networkType = if (isWifiConnected()) "Wi-Fi" else "נתונים סלולריים"
            log("[רשת] חיבור פעיל: $networkType")

            updateState(DownloadStatus.CONNECTING, hebrewStatus = "מכין את מנוע yt-dlp...")

            val app = context.applicationContext as App
            if (!app.awaitYtdlp()) {
                log("[שגיאה] yt-dlp לא הופעל. בדוק שה-APK נבנה עם ספריות המנוע (abiFilters / extractNativeLibs).")
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: מנוע yt-dlp לא נטען")
                return
            }

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9א-ת _-]"), "").trim().ifEmpty { "Audio_$videoId" }
            val rawBase = File(musicDir, "${sanitizedTitle}_raw_${System.currentTimeMillis()}")

            updateState(DownloadStatus.CONNECTING, hebrewStatus = "מתחבר ליוטיוב ומחלץ שמע...")

            var rawFile: File? = null
            val playerClients = listOf("android_vr", "web_embedded", "tv")
            val attemptErrors = mutableListOf<String>()

            suspend fun tryPlayerClients(stage: String) {
                for (playerClient in playerClients) {
                    if (rawFile != null && rawFile!!.exists() && rawFile!!.length() > 0L) return

                    try {
                        log("[yt-dlp] מנסה לקוח YouTube: $playerClient ($stage)")
                        rawFile = runYtDlp(
                            taskId, videoId, isLive, rawBase,
                            playerClient, ::log, ::rawLog, ::updateState
                        )
                        if (rawFile != null && rawFile!!.exists() && rawFile!!.length() > 0L) {
                            log("[yt-dlp] הלקוח $playerClient הצליח להפיק קובץ שמע.")
                            rawLog("[fallback] success with player_client=$playerClient")
                            return
                        }

                        val reason = "$playerClient: no non-empty output file"
                        attemptErrors += reason
                        rawLog("[fallback] $reason")
                        rawFile = null
                    } catch (e: YoutubeDLException) {
                        val reason = "$playerClient: ${shortError(e)}"
                        attemptErrors += reason
                        log("[yt-dlp] הלקוח $playerClient נכשל: ${shortError(e)}")
                        rawLog("[fallback] $reason")
                        rawFile = null
                    }
                }
            }

            // Avoid the default iOS/web_creator combination that produced HTTP 400
            // and "Please sign in" in the reported failure. These clients do not
            // require PO Tokens for GVS according to yt-dlp's current client matrix.
            tryPlayerClients("initial")

            if (rawFile == null || !rawFile!!.exists() || rawFile!!.length() == 0L) {
                log("[yt-dlp] כל לקוחות YouTube החלופיים נכשלו. בודק ומעדכן את yt-dlp...")
                rawLog("[INFO] All fallback player clients failed; updating yt-dlp nightly once.")
                updateState(DownloadStatus.CONNECTING, hebrewStatus = "מעדכן את מנוע ההורדה ומנסה שיטות נוספות...")
                val updateSucceeded = app.updateYtdlp()
                rawLog("[update] yt-dlp update " + if (updateSucceeded) "succeeded" else "failed or unavailable")

                if (updateSucceeded) {
                    tryPlayerClients("after update")
                }
            }

            if (rawFile == null || !rawFile!!.exists() || rawFile!!.length() == 0L) {
                val details = attemptErrors.takeLast(6).joinToString(" | ")
                throw YoutubeDLException(
                    "All YouTube player clients failed. " + details
                )
            }

            val ext = rawFile.extension.ifEmpty { "m4a" }
            var finalFile: File = rawFile
            val isTrimmed = trimRange != null && trimRange.endMs > trimRange.startMs && ext in TRIMMABLE_EXT

            if (trimRange != null && trimRange.endMs > trimRange.startMs && !isTrimmed) {
                log("[חיתוך] הפורמט .$ext לא נתמך לחיתוך, נשמר הקובץ המלא.")
            }

            if (isTrimmed && trimRange != null) {
                updateState(DownloadStatus.TRIMMING, hebrewStatus = "חותך מקטע שמע נבחר...")
                log("[חיתוך] מפעיל MediaMuxer לחיתוך מקטע (${formatDuration(trimRange.startMs)} עד ${formatDuration(trimRange.endMs)})...")
                val trimmedFile = File(musicDir, "${sanitizedTitle}_${System.currentTimeMillis()}.$ext")

                val success = AudioTrimmer.trimM4A(
                    sourceFile = rawFile,
                    destinationFile = trimmedFile,
                    startMs = trimRange.startMs,
                    endMs = trimRange.endMs
                )

                if (success && trimmedFile.exists() && trimmedFile.length() > 0L) {
                    rawFile.delete()
                    finalFile = trimmedFile
                    log("[חיתוך] המקטע נחתך ונשמר בהצלחה ללא איבוד איכות!")
                } else {
                    log("[חיתוך] החיתוך נכשל, נשמר הקובץ המלא.")
                }
            }

            if (finalFile == rawFile) {
                val cleanFile = File(musicDir, "${sanitizedTitle}_${System.currentTimeMillis()}.$ext")
                if (rawFile.renameTo(cleanFile)) finalFile = cleanFile
            }

            updateState(DownloadStatus.FINISHING, progress = 100, hebrewStatus = "שומר בספרייה...")

            val fullDurationMs = fileDurationMs(finalFile)
            val track = TrackEntity(
                id = videoId + "_" + System.currentTimeMillis(),
                title = title,
                artist = channel,
                durationMs = if (isTrimmed && trimRange != null) (trimRange.endMs - trimRange.startMs) else fullDurationMs,
                filePath = finalFile.absolutePath,
                thumbnailUrl = thumbnailUrl,
                fileSizeBytes = finalFile.length(),
                isLiveCapture = isLive,
                isTrimmed = isTrimmed,
                trimStartMs = trimRange?.startMs ?: 0L,
                trimEndMs = trimRange?.endMs ?: 0L
            )

            database.trackDao().insertTrack(track)

            log("[הצלחה] הקובץ מוכן ונשמר בספריית המוזיקה!")
            rawLog("[success] File successfully saved: ${finalFile.name} (${finalFile.length()} bytes)")
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            progressPercent = 100,
                            hebrewStatusText = "ההורדה הושלמה בהצלחה!",
                            filePath = finalFile.absolutePath
                        )
                    } else it
                }
            }
            Notifier.downloadFinished(context, taskId, title)

        } catch (e: YoutubeDL.CanceledException) {
            log("[ביטול] ההורדה בוטלה.")
            rawLog("[process] Canceled by user.")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "yt-dlp error", e)
            val reason = friendlyError(e)
            log("[שגיאה] ${shortError(e)}")
            rawLog("[ERROR] ${e.message ?: "yt-dlp execution error"}")
            updateState(DownloadStatus.FAILED, hebrewStatus = reason)
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            log("[שגיאה קריטית] ${e.localizedMessage ?: "שגיאה בלתי צפויה"}")
            rawLog("[FATAL] ${e.localizedMessage ?: e.javaClass.simpleName}")
            updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה במהלך ההורדה")
        } finally {
            activeJobs.remove(taskId)
            liveStopRequested.remove(taskId)
            if (activeJobs.isEmpty()) DownloadService.stop(context)
        }
    }

    /**
     * Runs yt-dlp itself (extraction + download). Returns the downloaded file, or null.
     * yt-dlp is kept up to date, so it keeps working when YouTube changes its internals.
     */
    private suspend fun runYtDlp(
        taskId: String,
        videoId: String,
        isLive: Boolean,
        rawBase: File,
        playerClient: String,
        log: (String) -> Unit,
        rawLog: (String) -> Unit,
        updateState: (DownloadStatus, Int, String?, String?, String?, String?, Long?) -> Unit
    ): File? {
        // Remove remnants from previous player-client attempts so a partial file
        // cannot be mistaken for a successful result of the next strategy.
        musicDir.listFiles { file ->
            file.name.startsWith(rawBase.name + ".")
        }?.forEach { it.delete() }

        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            // Prefer native m4a (AAC) so no re-encoding is needed and the trimmer keeps working
            addOption("-f", "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
            addOption("-o", rawBase.absolutePath + ".%(ext)s")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--retries", "5")
            addOption("--socket-timeout", "20")
            addOption("--extractor-args", "youtube:player_client=$playerClient")
            if (isLive) {
                addOption("--no-part")
                addOption("--hls-use-mpegts")
            }
        }

        val cmdLine = buildString {
            append("yt-dlp -f \"bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best\" ")
            append("-o \"${rawBase.name}.%(ext)s\" ")
            append("--no-playlist --no-mtime --newline --retries 5 --socket-timeout 20 ")
            append("--extractor-args \"youtube:player_client=$playerClient\" ")
            if (isLive) append("--no-part --hls-use-mpegts ")
            append("https://www.youtube.com/watch?v=$videoId")
        }
        rawLog("[cmd] $cmdLine")

        var ticker: Job? = null
        if (isLive) {
            log("[שידור חי] מתחיל הקלטה (yt-dlp)...")
            updateState(DownloadStatus.RECORDING_LIVE, 0, "LIVE", "0 MB", "LIVE", "מקליט שידור חי...", 0L)
            ticker = scope.launch {
                var sec = 0L
                while (isActive) {
                    delay(1000)
                    sec++
                    updateState(
                        DownloadStatus.RECORDING_LIVE, -1, "LIVE", null, "LIVE",
                        "מקליט שידור חי: ${formatDuration(sec * 1000L)}", sec
                    )
                }
            }
        } else {
            updateState(DownloadStatus.DOWNLOADING, 0, null, null, null, "מתחיל להוריד...", null)
        }

        try {
            val response = YoutubeDL.getInstance().execute(request, taskId) { progress, _, line ->
                if (!line.isNullOrBlank()) {
                    rawLog(line)
                }
                if (!isLive && progress >= 0f) {
                    val percent = progress.toInt().coerceIn(0, 99)
                    val m = PROGRESS_LINE.find(line)
                    val total = m?.groupValues?.get(1)?.trim()
                    val speed = m?.groupValues?.get(2)?.trim()
                    val downloaded = total?.let { sizeToBytes(it) }?.let { bytesToText((it * percent / 100.0).toLong()) }
                    updateState(
                        DownloadStatus.DOWNLOADING, percent, speed, downloaded, total,
                        "מוריד נתוני שמע: $percent%" + (if (total != null) " מתוך $total" else ""),
                        null
                    )
                }
            }
            if (!response.err.isNullOrBlank()) {
                rawLog("[stderr] " + response.err)
            }
            rawLog("[process] yt-dlp exit code: ${response.exitCode} (elapsed: ${response.elapsedTime}ms)")
        } catch (e: YoutubeDL.CanceledException) {
            rawLog("[process] Execution canceled by user.")
            // For live streams "stop" is a normal ending: keep what was recorded
            if (!(isLive && liveStopRequested.contains(taskId))) throw e
            log("[שידור חי] ההקלטה הופסקה ונשמרה.")
        } catch (e: YoutubeDLException) {
            rawLog("[error] " + (e.message ?: "YoutubeDLException"))
            throw e
        } finally {
            ticker?.cancel()
        }

        return musicDir.listFiles { f ->
            f.isFile &&
                f.name.startsWith(rawBase.name + ".") &&
                !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") &&
                f.length() > 0L
        }?.maxByOrNull { it.length() }
    }

    fun stopLiveRecording(taskId: String) {
        liveStopRequested.add(taskId)
        YoutubeDL.getInstance().destroyProcessById(taskId)
    }

    fun cancelDownload(taskId: String) {
        liveStopRequested.remove(taskId)
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (e: Exception) {
            Log.w(TAG, "destroyProcess failed", e)
        }
        activeJobs[taskId]?.cancel()
        _tasks.update { it.filterNot { task -> task.id == taskId } }
        Notifier.cancel(context, taskId)
    }

    // ---------------- helpers ----------------

    private fun shortError(e: Exception): String =
        (e.message ?: "שגיאה לא ידועה").lines().lastOrNull { it.isNotBlank() }?.take(200) ?: "שגיאה לא ידועה"

    private fun friendlyError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Sign in to confirm", ignoreCase = true) ||
                msg.contains("not a bot", ignoreCase = true) ||
                msg.contains("Precondition check failed", ignoreCase = true) ||
                msg.contains("FAILED_PRECONDITION", ignoreCase = true) ||
                msg.contains("Please sign in", ignoreCase = true) ->
                "YouTube דורש אימות נוסף או דחה את לקוחות הניגון. האפליקציה ניסתה לקוחות חלופיים ועדכון מנוע."
            msg.contains("Private video", ignoreCase = true) -> "הסרטון פרטי"
            msg.contains("unavailable", ignoreCase = true) -> "הסרטון לא זמין"
            msg.contains("age", ignoreCase = true) && msg.contains("restricted", ignoreCase = true) -> "הסרטון מוגבל גיל"
            msg.contains("Unable to download", ignoreCase = true) ||
                msg.contains("urlopen", ignoreCase = true) -> "בעיית רשת"
            else -> "שגיאה: חילוץ השמע נכשל"
        }
    }

    private fun fileDurationMs(file: File): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (e: Exception) {
        0L
    }

    private fun sizeToBytes(text: String): Long? {
        val m = Regex("([\\d.]+)\\s*([KMG]?i?B)", RegexOption.IGNORE_CASE).find(text.replace("~", "")) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].uppercase().first()) {
            'K' -> 1024.0
            'M' -> 1024.0 * 1024
            'G' -> 1024.0 * 1024 * 1024
            else -> 1.0
        }
        return (n * mult).toLong()
    }

    private fun bytesToText(bytes: Long): String = String.format("%.1f MB", bytes / (1024f * 1024f))

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val hours = min / 60
        val remainingMin = min % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, remainingMin, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }

    companion object {
        private val TRIMMABLE_EXT = setOf("m4a", "mp4", "aac")

        // "[download]  45.3% of ~  3.42MiB at  1.20MiB/s ETA 00:02"
        private val PROGRESS_LINE = Regex("of\\s+~?\\s*([\\d.]+\\s*[KMG]?i?B)(?:\\s+at\\s+([\\d.]+\\s*[KMG]?i?B/s))?")
    }
}
