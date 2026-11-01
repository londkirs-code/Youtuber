package com.example.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.TrackEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.TrimRange
import com.example.data.model.UserSettings
import com.example.network.YouTubeEngine
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
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class DownloadManager(private val context: Context) {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val database = AppDatabase.getDatabase(context)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val liveRecordingActive = mutableMapOf<String, Boolean>()

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
        fun log(msg: String) {
            _tasks.update { list ->
                list.map { if (it.id == taskId) it.copy(logs = it.logs + msg) else it }
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

            updateState(DownloadStatus.CONNECTING, hebrewStatus = "מתחבר לשרת YouTube...")
            log("[yt-dlp] מתחבר למנוע החילוץ ומנתח ערוצי שמע...")

            val streamInfo = YouTubeEngine.resolveVideoDetails(videoId)
            if (streamInfo == null || streamInfo.streamUrl.isBlank()) {
                log("[שגיאה] לא נמצא קישור הזרמת שמע תקין עבור הסרטון")
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: חילוץ קישור השמע נכשל")
                return
            }

            log("[yt-dlp] ערוץ שמע אותר בהצלחה: ${streamInfo.audioBitrateKbps} kbps, M4A/AAC מקורי ללא דחיסה")

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9א-ת _-]"), "").trim().ifEmpty { "Audio_$videoId" }
            val rawFile = File(musicDir, "${sanitizedTitle}_raw_${System.currentTimeMillis()}.m4a")

            if (isLive || streamInfo.isLive) {
                // Live broadcast recording flow
                handleLiveStreamRecording(taskId, streamInfo.streamUrl, rawFile, ::log, ::updateState)
            } else {
                // Standard VOD audio download
                handleVodDownload(taskId, streamInfo.streamUrl, rawFile, streamInfo.approxSizeBytes, ::log, ::updateState)
            }

            if (!rawFile.exists() || rawFile.length() == 0L) {
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: קובץ השמע לא נוצר")
                return
            }

            var finalFile = rawFile
            val isTrimmed = trimRange != null && trimRange.endMs > trimRange.startMs

            if (isTrimmed) {
                updateState(DownloadStatus.TRIMMING, hebrewStatus = "חותך מקטע שמע נבחר...")
                log("[חיתוך] מפעיל MediaMuxer לחיתוך מקטע (${formatDuration(trimRange.startMs)} עד ${formatDuration(trimRange.endMs)})...")
                val trimmedFile = File(musicDir, "${sanitizedTitle}_${System.currentTimeMillis()}.m4a")

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
            } else {
                val cleanFile = File(musicDir, "${sanitizedTitle}_${System.currentTimeMillis()}.m4a")
                rawFile.renameTo(cleanFile)
                finalFile = cleanFile
            }

            updateState(DownloadStatus.FINISHING, progress = 100, hebrewStatus = "מטמיע פרטי שיר ותמונת עטיפה...")
            log("[תגיות] מטמיע כותרת: '$title', אמן: '$channel', תמונת שער...")

            val track = TrackEntity(
                id = videoId + "_" + System.currentTimeMillis(),
                title = title,
                artist = channel,
                durationMs = if (isTrimmed) (trimRange.endMs - trimRange.startMs) else streamInfo.durationMs,
                filePath = finalFile.absolutePath,
                thumbnailUrl = thumbnailUrl,
                fileSizeBytes = finalFile.length(),
                isLiveCapture = isLive || streamInfo.isLive,
                isTrimmed = isTrimmed,
                trimStartMs = trimRange?.startMs ?: 0L,
                trimEndMs = trimRange?.endMs ?: 0L
            )

            database.trackDao().insertTrack(track)

            log("[הצלחה] הקובץ מוכן ונשמר בספריית המוזיקה!")
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

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            log("[שגיאה קריטית] ${e.localizedMessage ?: "שגיאה בלתי צפויה"}")
            updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה במהלך ההורדה")
        } finally {
            activeJobs.remove(taskId)
            liveRecordingActive.remove(taskId)
        }
    }

    private suspend fun handleVodDownload(
        taskId: String,
        streamUrl: String,
        destinationFile: File,
        approxSizeBytes: Long,
        log: (String) -> Unit,
        updateState: (DownloadStatus, Int, String?, String?, String?, String?, Long?) -> Unit
    ) {
        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        YouTubeEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("קריאת שרת החזרתה שגיאה: ${response.code}")
            }

            val body = response.body ?: throw Exception("גוף התשובה ריק")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else approxSizeBytes
            val totalSizeMb = if (totalBytes > 0) String.format("%.1f MB", totalBytes / (1024f * 1024f)) else "-- MB"

            val input: InputStream = body.byteStream()
            val output = FileOutputStream(destinationFile)
            val buffer = ByteArray(32 * 1024)

            var downloadedBytes = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L

            var bytesRead = 0
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLast += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastTime
                if (delta >= 400) {
                    val speedBytesSec = (bytesSinceLast * 1000f) / delta
                    val speedText = if (speedBytesSec >= 1024 * 1024) {
                        String.format("%.1f MB/s", speedBytesSec / (1024 * 1024))
                    } else {
                        String.format("%.0f KB/s", speedBytesSec / 1024)
                    }

                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                    } else 50

                    val downloadedMb = String.format("%.1f MB", downloadedBytes / (1024f * 1024f))
                    val statusText = "מוריד נתוני שמע: $progress% ($downloadedMb מתוך $totalSizeMb)"

                    updateState(DownloadStatus.DOWNLOADING, progress, speedText, downloadedMb, totalSizeMb, statusText, null)
                    lastTime = now
                    bytesSinceLast = 0L
                }
            }

            output.flush()
            output.close()
            input.close()
        }
    }

    private suspend fun handleLiveStreamRecording(
        taskId: String,
        streamUrl: String,
        destinationFile: File,
        log: (String) -> Unit,
        updateState: (DownloadStatus, Int, String?, String?, String?, String?, Long?) -> Unit
    ) {
        log("[שידור חי] מתחבר להזרמת לייב (HLS/Chunked)...")
        updateState(DownloadStatus.RECORDING_LIVE, 0, "0 KB/s", "0 MB", "LIVE", "מקליט שידור חי...", 0L)
        liveRecordingActive[taskId] = true

        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        YouTubeEngine.client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("גוף שידור חי ריק")
            val input = body.byteStream()
            val output = FileOutputStream(destinationFile)
            val buffer = ByteArray(16 * 1024)

            var recordedBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdate = System.currentTimeMillis()

            var bytesRead = 0
            while (liveRecordingActive[taskId] == true && input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                recordedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 1000) {
                    val durationSec = (now - startTime) / 1000
                    val sizeMb = String.format("%.2f MB", recordedBytes / (1024f * 1024f))
                    val statusText = "מקליט שידור חי: ${formatDuration(durationSec * 1000L)} ($sizeMb)"
                    updateState(DownloadStatus.RECORDING_LIVE, -1, "LIVE", sizeMb, "LIVE", statusText, durationSec)
                    lastUpdate = now
                }
            }

            output.flush()
            output.close()
            input.close()
            log("[שידור חי] ההקלטה הופסקה ונשמרה.")
        }
    }

    fun stopLiveRecording(taskId: String) {
        liveRecordingActive[taskId] = false
    }

    fun cancelDownload(taskId: String) {
        liveRecordingActive[taskId] = false
        activeJobs[taskId]?.cancel()
        _tasks.update { it.filterNot { task -> task.id == taskId } }
    }

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
}
