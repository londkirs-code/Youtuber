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
import com.example.engine.EngineCancelledException
import com.example.engine.EngineExecutionException
import com.example.engine.EngineRuntime
import com.example.engine.YtDlpBridgeCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { coroutineContext, error ->
            val failedTaskId = coroutineContext[CoroutineName]?.name
            Log.e(TAG, "Uncaught download coroutine failure for task $failedTaskId", error)
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.id == failedTaskId) {
                        Notifier.downloadFailed(context, task.id, task.title, "שגיאה בלתי צפויה: ${error.message ?: error.javaClass.simpleName}")
                        task.copy(
                            status = DownloadStatus.FAILED,
                            hebrewStatusText = "שגיאה בלתי צפויה במהלך ההורדה",
                            rawLogs = (task.rawLogs + "[COROUTINE FATAL] " + error.stackTraceToString()).takeLast(1000)
                        )
                    } else task
                }
            }
        }
    )
    private val database = AppDatabase.getDatabase(context)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val liveStopRequested = ConcurrentHashMap.newKeySet<String>()

    private fun getDownloadFolder(settings: UserSettings): File {
        // 1. If user picked a folder via SAF DocumentTree, resolve directly to storage path if writable
        if (settings.downloadFolderUri.isNotBlank()) {
            try {
                val uri = android.net.Uri.parse(settings.downloadFolderUri)
                val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                val decoded = java.net.URLDecoder.decode(docId, "UTF-8")
                if (decoded.startsWith("primary:")) {
                    val relativePath = decoded.removePrefix("primary:").trim('/', ' ')
                    val candidate = if (relativePath.isEmpty()) {
                        android.os.Environment.getExternalStorageDirectory()
                    } else {
                        File(android.os.Environment.getExternalStorageDirectory(), relativePath)
                    }
                    if (candidate.exists() || candidate.mkdirs()) {
                        val testFile = File(candidate, ".tv_test_${System.currentTimeMillis()}")
                        if (testFile.createNewFile()) {
                            testFile.delete()
                            return candidate
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot write directly to SAF resolved directory: ${e.message}")
            }
        }

        // 2. Custom name provided as a path
        val customName = settings.downloadFolderPath.trim()
        if (customName.isNotBlank() && !customName.contains("Music/TuneVault") && customName != "Music") {
            try {
                val candidate = File(android.os.Environment.getExternalStorageDirectory(), customName)
                if (candidate.exists() || candidate.mkdirs()) {
                    val testFile = File(candidate, ".tv_test_${System.currentTimeMillis()}")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        return candidate
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Public Music/TuneVault on external storage
        try {
            val publicMusic = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                "TuneVault"
            )
            if (publicMusic.exists() || publicMusic.mkdirs()) {
                val testFile = File(publicMusic, ".tv_test_${System.currentTimeMillis()}")
                if (testFile.createNewFile()) {
                    testFile.delete()
                    return publicMusic
                }
            }
        } catch (_: Exception) {}

        // 4. Fallback to app-specific external files dir
        val fallback = File(context.getExternalFilesDir(null), "Music")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
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
        val targetFolder = getDownloadFolder(settings)
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9א-ת _-]"), "").trim().ifEmpty { "Audio_$videoId" }
        val rawBase = File(targetFolder, "${sanitizedTitle}_raw_${System.currentTimeMillis()}")
        val initialFilePath = rawBase.absolutePath + ".m4a"

        val folderLabel = if (settings.downloadFolderUri.isNotBlank()) {
            settings.downloadFolderPath.ifBlank { targetFolder.name }
        } else {
            targetFolder.name
        }
        val destinationNote = "נשמר ב: $folderLabel (${targetFolder.absolutePath})"

        val newTask = DownloadTask(
            id = taskId,
            videoId = videoId,
            title = title,
            channel = channel,
            thumbnailUrl = thumbnailUrl,
            isLiveStream = isLive,
            trimRange = trimRange,
            status = DownloadStatus.QUEUED,
            hebrewStatusText = "נוסף לתור ההורדות...",
            filePath = initialFilePath,
            logs = listOf(destinationNote)
        )

        _tasks.update { listOf(newTask) + it }

        // Keep the process alive + show "download started" notification
        DownloadService.start(context)
        Notifier.downloadStarted(context, taskId, title, thumbnailUrl)

        scope.launch {
            try {
                database.trackDao().insertTrack(
                    TrackEntity(
                        id = taskId,
                        title = title,
                        artist = channel,
                        durationMs = 0L,
                        filePath = initialFilePath,
                        thumbnailUrl = thumbnailUrl,
                        fileSizeBytes = 0L,
                        isLiveCapture = isLive,
                        isTrimmed = false,
                        trimStartMs = 0L,
                        trimEndMs = 0L,
                        isPartial = true,
                        videoId = videoId
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting initial track in Room", e)
            }
        }

        val job = scope.launch(CoroutineName(taskId)) {
            executeDownload(taskId, videoId, title, channel, thumbnailUrl, isLive, trimRange, settings, targetFolder, rawBase)
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
        settings: UserSettings,
        targetFolder: File,
        rawBase: File
    ) {
        var lastPercent = 0

        val rawLogBuffer = mutableListOf<String>()
        var lastLogFlushTime = 0L

        fun flushLogs() {
            if (rawLogBuffer.isEmpty()) return
            val toAdd = rawLogBuffer.toList()
            rawLogBuffer.clear()
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) {
                        val current = it.rawLogs
                        val next = (current + toAdd).takeLast(250)
                        it.copy(rawLogs = next)
                    } else it
                }
            }
        }

        fun log(msg: String) {
            flushLogs()
            _tasks.update { list ->
                list.map { if (it.id == taskId) it.copy(logs = (it.logs + msg).takeLast(100)) else it }
            }
        }

        fun rawLog(line: String) {
            if (line.isBlank()) return
            rawLogBuffer.add(line)
            val now = System.currentTimeMillis()
            if (now - lastLogFlushTime >= 800L || rawLogBuffer.size >= 25) {
                lastLogFlushTime = now
                flushLogs()
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
                    Notifier.downloadProgress(context, taskId, title, -1, hebrewStatus ?: "מקליט שידור חי...", thumbnailUrl)
                DownloadStatus.DOWNLOADING ->
                    Notifier.downloadProgress(context, taskId, title, lastPercent, hebrewStatus ?: "מוריד...", thumbnailUrl)
                else ->
                    Notifier.downloadProgress(context, taskId, title, if (status == DownloadStatus.FINISHING) 100 else -1, hebrewStatus ?: "", thumbnailUrl)
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
                val engineError = context.getSharedPreferences("yt-dlp-engine", Context.MODE_PRIVATE)
                    .getString("engine_error", "No engine_error was recorded.")
                rawLog("[ENGINE INITIALIZATION ERROR - FULL STACK TRACE]\n" + engineError)
                log("[שגיאה] yt-dlp לא הופעל. פרטי האתחול המלאים נשמרו בלוג.")
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: מנוע yt-dlp לא נטען")
                return
            }

            updateState(DownloadStatus.CONNECTING, hebrewStatus = "מתחבר ליוטיוב ומחלץ שמע...")

            var rawFile: File? = null
            // For live, start strictly with "default". For normal downloads, use all standard clients.
            val playerClients = if (isLive) listOf("default") else listOf("default", "tv_samsung", "visionos", "tv", "web_embedded")
            val attemptErrors = mutableListOf<String>()

            suspend fun tryPlayerClients(stage: String) {
                for (playerClient in playerClients) {
                    if (rawFile != null && rawFile!!.exists() && rawFile!!.length() > 0L) return

                    try {
                        log("[yt-dlp] מנסה לקוח YouTube: $playerClient ($stage)")
                        rawFile = runYtDlp(
                            taskId, videoId, isLive, targetFolder, rawBase,
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
                    } catch (e: EngineCancelledException) {
                        throw e
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reason = "$playerClient: ${shortError(e)}"
                        attemptErrors += reason
                        log("[yt-dlp] הלקוח $playerClient נכשל: ${shortError(e)}")
                        rawLog("[fallback] $reason")
                        rawFile = null
                    }
                }

                // In live stream: only try fallback player clients if extraction itself failed (e.g. No video formats found)
                if (isLive && (rawFile == null || !rawFile!!.exists() || rawFile!!.length() == 0L)) {
                    val extractionFailed = attemptErrors.any { error ->
                        error.contains("No video formats found", ignoreCase = true) ||
                            error.contains("ExtractorError", ignoreCase = true) ||
                            error.contains("Unable to extract", ignoreCase = true)
                    }
                    if (extractionFailed) {
                        val fallbackClients = listOf("tv_samsung", "visionos", "tv", "web_embedded")
                        for (playerClient in fallbackClients) {
                            if (rawFile != null && rawFile!!.exists() && rawFile!!.length() > 0L) return

                            try {
                                log("[yt-dlp] מנסה לקוח גיבוי לחילוץ: $playerClient ($stage)")
                                rawFile = runYtDlp(
                                    taskId, videoId, isLive, targetFolder, rawBase,
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
                            } catch (e: EngineCancelledException) {
                                throw e
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val reason = "$playerClient: ${shortError(e)}"
                                attemptErrors += reason
                                log("[yt-dlp] הלקוח $playerClient נכשל: ${shortError(e)}")
                                rawLog("[fallback] $reason")
                                rawFile = null
                            }
                        }
                    }
                }
            }

            // Avoid the default iOS/web_creator combination that produced HTTP 400
            // and "Please sign in" in the reported failure. These clients do not
            // require PO Tokens for GVS according to yt-dlp's current client matrix.
            tryPlayerClients("initial")

            if (rawFile?.let { it.exists() && it.length() > 0L } != true) {
                if (!isLive) {
                    log("[yt-dlp] כל לקוחות YouTube החלופיים נכשלו. בודק ומעדכן את yt-dlp...")
                    rawLog("[INFO] All fallback player clients failed; updating yt-dlp nightly once.")
                    updateState(DownloadStatus.CONNECTING, hebrewStatus = "מעדכן את מנוע ההורדה ומנסה שיטות נוספות...")
                    val updateSucceeded = app.updateYtdlp { message -> rawLog("[update] $message") }
                    rawLog("[update] yt-dlp update " + if (updateSucceeded) "succeeded" else "failed or unavailable")

                    if (updateSucceeded) {
                        tryPlayerClients("after update")
                    }
                }
            }

            if (rawFile?.let { it.exists() && it.length() > 0L } != true) {
                val details = attemptErrors.takeLast(6).joinToString(" | ")
                throw EngineExecutionException(
                    "All YouTube player clients failed. " + details
                )
            }

            val downloadedRawFile = rawFile?.takeIf { it.exists() && it.length() > 0L }
                ?: throw EngineExecutionException("No non-empty audio file was produced by any YouTube player client.")

            val ext = downloadedRawFile.extension.ifEmpty { "m4a" }
            var finalFile: File = downloadedRawFile
            val isTrimmed = trimRange != null && trimRange.endMs > trimRange.startMs && ext in TRIMMABLE_EXT

            if (trimRange != null && trimRange.endMs > trimRange.startMs && !isTrimmed) {
                log("[חיתוך] הפורמט .$ext לא נתמך לחיתוך, נשמר הקובץ המלא.")
            }

            if (isTrimmed && trimRange != null) {
                updateState(DownloadStatus.TRIMMING, hebrewStatus = "חותך מקטע שמע נבחר...")
                log("[חיתוך] מפעיל MediaMuxer לחיתוך מקטע (${formatDuration(trimRange.startMs)} עד ${formatDuration(trimRange.endMs)})...")
                val trimmedFile = File(targetFolder, "${rawBase.nameWithoutExtension}_trimmed.$ext")

                val success = AudioTrimmer.trimM4A(
                    sourceFile = downloadedRawFile,
                    destinationFile = trimmedFile,
                    startMs = trimRange.startMs,
                    endMs = trimRange.endMs
                )

                if (success && trimmedFile.exists() && trimmedFile.length() > 0L) {
                    downloadedRawFile.delete()
                    finalFile = trimmedFile
                    log("[חיתוך] המקטע נחתך ונשמר בהצלחה ללא איבוד איכות!")
                } else {
                    log("[חיתוך] החיתוך נכשל, נשמר הקובץ המלא.")
                }
            }

            if (isLive) {
                updateState(DownloadStatus.FINISHING, hebrewStatus = "מעבד ומשלים את קובץ ההקלטה...")
                val fixedLiveFile = File(targetFolder, "${rawBase.nameWithoutExtension}_live.$ext")
                val remuxSuccess = EngineRuntime.remuxAudioFile(context, downloadedRawFile, fixedLiveFile)
                if (remuxSuccess && fixedLiveFile.exists() && fixedLiveFile.length() > 0L) {
                    downloadedRawFile.delete()
                    finalFile = fixedLiveFile
                    log("[שידור חי] קובץ ההקלטה עובד בהצלחה עם תגיות זמן מדויקות!")
                } else if (finalFile == downloadedRawFile) {
                    val cleanFile = File(targetFolder, "${rawBase.nameWithoutExtension}.$ext")
                    if (downloadedRawFile.renameTo(cleanFile)) finalFile = cleanFile
                }
            } else if (finalFile == downloadedRawFile) {
                val cleanFile = File(targetFolder, "${rawBase.nameWithoutExtension}.$ext")
                if (downloadedRawFile.renameTo(cleanFile)) finalFile = cleanFile
            }

            updateState(DownloadStatus.FINISHING, progress = 100, hebrewStatus = "שומר בספרייה...")

            val fullDurationMs = fileDurationMs(finalFile)
            val durationToSave = if (isLive) {
                val recordedSec = _tasks.value.find { it.id == taskId }?.recordedDurationSeconds ?: 0L
                val recordedMs = recordedSec * 1000L
                if (fullDurationMs > 5000L && fullDurationMs >= (recordedMs * 0.85).toLong()) {
                    fullDurationMs
                } else if (recordedMs > 0L) {
                    recordedMs
                } else {
                    fullDurationMs.coerceAtLeast(1000L)
                }
            } else if (isTrimmed && trimRange != null) {
                (trimRange.endMs - trimRange.startMs)
            } else if (fullDurationMs > 0L) {
                fullDurationMs
            } else {
                0L
            }

            val track = TrackEntity(
                id = taskId,
                title = title,
                artist = channel,
                durationMs = durationToSave,
                filePath = finalFile.absolutePath,
                thumbnailUrl = thumbnailUrl,
                fileSizeBytes = finalFile.length(),
                isLiveCapture = isLive,
                isTrimmed = isTrimmed,
                trimStartMs = trimRange?.startMs ?: 0L,
                trimEndMs = trimRange?.endMs ?: 0L,
                isPartial = false,
                videoId = videoId
            )

            database.trackDao().insertTrack(track)

            if (settings.downloadFolderUri.isNotBlank()) {
                try {
                    copyToSafTree(context, settings.downloadFolderUri, finalFile, title, ext)
                    log("[שמירה] הקובץ נשמר בהצלחה בתיקייה שנבחרה במכשיר!")
                    rawLog("[saf] Exported to external folder: ${settings.downloadFolderUri}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed saving to SAF tree folder: ${e.message}", e)
                    log("[שמירה] שים לב: הקובץ שמור בספרייה (העתקה לתיקייה חיצונית נכשלה: ${e.message})")
                }
            }

            log("[מיקום סופי] נשמר בהצלחה בנתיב: ${finalFile.absolutePath}")
            log("[הצלחה] הקובץ מוכן ונשמר בספרייה!")
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
            Notifier.downloadFinished(context, taskId, title, finalFile.absolutePath)

        } catch (e: EngineCancelledException) {
            log("[ביטול] ההורדה בוטלה - קובץ חלקי נשמר בספרייה.")
            rawLog("[process] Canceled by user.")
            scope.launch {
                try { database.trackDao().updateTrackPartial(taskId, true) } catch (_: Exception) {}
            }
        } catch (e: CancellationException) {
            scope.launch {
                try { database.trackDao().updateTrackPartial(taskId, true) } catch (_: Exception) {}
            }
            throw e
        } catch (e: EngineExecutionException) {
            Log.e(TAG, "yt-dlp error", e)
            val reason = friendlyError(e)
            log("[שגיאה] ${shortError(e)}")
            rawLog("[ERROR] ${e.message ?: "yt-dlp execution error"}")
            updateState(DownloadStatus.FAILED, hebrewStatus = reason)
            scope.launch {
                try { database.trackDao().updateTrackPartial(taskId, true) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            log("[שגיאה קריטית] ${e.localizedMessage ?: "שגיאה בלתי צפויה"}")
            rawLog("[FATAL] " + e.stackTraceToString())
            updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה במהלך ההורדה")
            scope.launch {
                try { database.trackDao().updateTrackPartial(taskId, true) } catch (_: Exception) {}
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Fatal download error", e)
            scope.launch {
                try { database.trackDao().updateTrackPartial(taskId, true) } catch (_: Exception) {}
            }
            log("[שגיאה קריטית] ${e.localizedMessage ?: e.javaClass.simpleName}")
            rawLog("[FATAL THROWABLE] " + e.stackTraceToString())
            updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה קריטית במהלך ההורדה")
        } finally {
            flushLogs()
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
        targetFolder: File,
        rawBase: File,
        playerClient: String,
        log: (String) -> Unit,
        rawLog: (String) -> Unit,
        updateState: (DownloadStatus, Int, String?, String?, String?, String?, Long?) -> Unit
    ): File? {
        targetFolder.listFiles { file -> file.name.startsWith(rawBase.name + ".") }?.forEach { it.delete() }

        fun cleanPartialFiles() {
            targetFolder.listFiles { file -> file.name.startsWith(rawBase.name + ".") }?.forEach { it.delete() }
        }

        fun findOutputFile(): File? {
            return targetFolder.listFiles { file ->
                file.isFile && file.name.startsWith(rawBase.name + ".") &&
                    !file.name.endsWith(".part") && !file.name.endsWith(".ytdl") && file.length() > 0L
            }?.maxByOrNull { it.length() }
        }

        suspend fun executeFormat(formatSpec: String): Pair<Int, File?> {
            cleanPartialFiles()

            val args = mutableListOf(
                "-f", formatSpec,
                "-o", rawBase.absolutePath + ".%(ext)s",
                "--no-playlist", "--no-mtime", "--newline",
                "--retries", "5", "--socket-timeout", "20",
                "--extractor-args", "youtube:player_client=$playerClient",
                "https://www.youtube.com/watch?v=$videoId"
            )

            rawLog("[cmd] yt-dlp " + args.joinToString(" ") { token ->
                if (token.any { it.isWhitespace() }) "\"$token\"" else token
            })

            updateState(DownloadStatus.DOWNLOADING, 0, null, null, null, "מתחיל להוריד...", null)

            val callback = object : YtDlpBridgeCallback {
                private var lastLoggedPercent = -1
                private var lastReportedPercent = -1
                private var lastProgressUpdateTime = 0L

                override fun onLog(level: String, message: String) {
                    rawLog("[$level] $message")
                }

                override fun onProgress(percent: Double, downloadedBytes: Long, totalBytes: Long, speed: String, etaSeconds: Long) {
                    val safePercent = percent.toInt().coerceIn(0, 99)
                    val now = System.currentTimeMillis()
                    if (safePercent != lastReportedPercent || now - lastProgressUpdateTime >= 400L) {
                        lastReportedPercent = safePercent
                        lastProgressUpdateTime = now
                        val downloadedText = bytesToText(downloadedBytes)
                        val totalText = if (totalBytes > 0L) bytesToText(totalBytes) else null
                        updateState(
                            DownloadStatus.DOWNLOADING, safePercent, speed.takeIf { it.isNotBlank() },
                            downloadedText, totalText,
                            "מוריד נתוני שמע: $safePercent%" + (totalText?.let { " מתוך $it" } ?: ""), null
                        )
                    }

                    // Log Hebrew status with percentage
                    if (safePercent >= lastLoggedPercent + 15 || safePercent in listOf(25, 50, 75, 95)) {
                        if (safePercent != lastLoggedPercent) {
                            lastLoggedPercent = safePercent
                            val downloadedText = bytesToText(downloadedBytes)
                            val totalText = if (totalBytes > 0L) bytesToText(totalBytes) else null
                            log("[הורדה] התקדמות: $safePercent% ($downloadedText מתוך ${totalText ?: "--"}) במהירות ${speed.ifEmpty { "..." }}")
                        }
                    }
                }

                override fun isCancelled(): Boolean = false
            }

            var exitCode = -1
            try {
                exitCode = EngineRuntime.runYtDlp(context, taskId, args, callback)
                rawLog("[process] yt-dlp exit code: $exitCode for format=$formatSpec")
            } catch (error: EngineCancelledException) {
                rawLog("[process] Execution canceled by user.")
                throw error
            } catch (error: EngineExecutionException) {
                rawLog("[error] yt-dlp execution error for format $formatSpec: ${error.message}")
                exitCode = -1
            }

            val produced = findOutputFile()
            return Pair(exitCode, produced)
        }

        if (!isLive) {
            val (exitCode, produced) = executeFormat("bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
            if (exitCode != 0) {
                throw EngineExecutionException("yt-dlp exited with code $exitCode for player_client=$playerClient")
            }
            return produced
        }

        // Live stream execution: direct HLS downloader without FFmpeg network dependency
        cleanPartialFiles()
        log("[שידור חי] מתחיל הקלטה ישירה של זרם HLS...")
        updateState(DownloadStatus.RECORDING_LIVE, 0, "LIVE", "0 MB", "LIVE", "מקליט שידור חי...", 0L)

        var lastReportedAudioSec = -1L
        val liveCallback = object : YtDlpBridgeCallback {
            override fun onLog(level: String, message: String) {
                rawLog("[$level] $message")
                if (message.startsWith("[live-hls]")) {
                    log(message)
                }
            }

            override fun onProgress(percent: Double, downloadedBytes: Long, totalBytes: Long, speed: String, etaSeconds: Long) {
                val audioSec = etaSeconds.coerceAtLeast(0L)
                if (audioSec != lastReportedAudioSec) {
                    lastReportedAudioSec = audioSec
                    val downloadedText = bytesToText(downloadedBytes)
                    updateState(
                        DownloadStatus.RECORDING_LIVE,
                        -1,
                        "LIVE",
                        downloadedText,
                        "LIVE",
                        "מקליט שידור חי: ${formatDuration(audioSec * 1000L)} ($downloadedText)",
                        audioSec
                    )
                }
            }

            override fun isCancelled(): Boolean = liveStopRequested.contains(taskId)
        }

        try {
            val resultJson = EngineRuntime.downloadLiveHls(
                context = context,
                taskId = taskId,
                url = "https://www.youtube.com/watch?v=$videoId",
                playerClient = playerClient,
                outBase = rawBase.absolutePath,
                callback = liveCallback
            )
            rawLog("[live-hls] Completed with result: $resultJson")
            log("[שידור חי] הקלטת הלייב הושלמה בהצלחה!")
        } catch (error: EngineCancelledException) {
            rawLog("[live-hls] Live recording stopped by user.")
            if (!liveStopRequested.contains(taskId)) throw error
            log("[שידור חי] ההקלטה הופסקה ונשמרה.")
        } catch (error: EngineExecutionException) {
            rawLog("[live-hls] Live recording error: ${error.message}")
            if (liveStopRequested.contains(taskId)) {
                log("[שידור חי] ההקלטה הופסקה לפי בקשת המשתמש.")
            } else {
                throw error
            }
        }

        val producedLiveFile = findOutputFile()
        if (producedLiveFile != null && producedLiveFile.length() > 0L) {
            log("[שידור חי] קובץ ההקלטה נוצר בהצלחה: ${producedLiveFile.name} (${producedLiveFile.length()} בתים)")
        }
        return producedLiveFile
    }

    fun stopLiveRecording(taskId: String) {
        liveStopRequested.add(taskId)
        EngineRuntime.cancel(taskId)
    }

    fun cancelDownload(taskId: String) {
        val task = _tasks.value.find { it.id == taskId }
        if (task?.isLiveStream == true && task.status == DownloadStatus.RECORDING_LIVE) {
            stopLiveRecording(taskId)
            return
        }
        liveStopRequested.remove(taskId)
        EngineRuntime.cancel(taskId)
        activeJobs[taskId]?.cancel()
        _tasks.update { it.filterNot { task -> task.id == taskId } }
        Notifier.cancel(context, taskId)
        scope.launch {
            try {
                val track = database.trackDao().getTrackById(taskId)
                if (track != null) {
                    val file = File(track.filePath)
                    val partFile = File(track.filePath + ".part")
                    if ((file.exists() && file.length() > 5000L) || (partFile.exists() && partFile.length() > 5000L)) {
                        database.trackDao().updateTrackPartial(taskId, true)
                    } else {
                        database.trackDao().deleteTrackById(taskId)
                    }
                }
            } catch (_: Exception) {}
        }
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

    private fun copyToSafTree(ctx: Context, treeUriStr: String, sourceFile: File, title: String, ext: String) {
        val treeUri = android.net.Uri.parse(treeUriStr)
        val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Audio" }
        val mimeType = if (ext.equals("mp3", ignoreCase = true)) "audio/mpeg" else "audio/m4a"
        val newDocUri = android.provider.DocumentsContract.createDocument(
            ctx.contentResolver,
            parentDocUri,
            mimeType,
            "$cleanTitle.$ext"
        ) ?: throw IllegalStateException("Could not create document in tree")

        ctx.contentResolver.openOutputStream(newDocUri)?.use { out ->
            sourceFile.inputStream().use { inp ->
                inp.copyTo(out)
            }
        }
    }

    companion object {
        private val TRIMMABLE_EXT = setOf("m4a", "mp4", "aac")

        // "[download]  45.3% of ~  3.42MiB at  1.20MiB/s ETA 00:02"
        private val PROGRESS_LINE = Regex("of\\s+~?\\s*([\\d.]+\\s*[KMG]?i?B)(?:\\s+at\\s+([\\d.]+\\s*[KMG]?i?B/s))?")
    }
}
