package com.example.data.model

data class SearchResultItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val durationText: String,
    val durationMs: Long,
    val thumbnailUrl: String,
    val viewsText: String,
    val uploadedText: String,
    val isLive: Boolean = false,
    val channelId: String = ""
)

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    TRIMMING,
    FINISHING,
    COMPLETED,
    FAILED,
    RECORDING_LIVE
}

data class DownloadTask(
    val id: String,
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val isLiveStream: Boolean = false,
    val progressPercent: Int = 0,
    val downloadSpeedText: String = "0 KB/s",
    val downloadedSizeText: String = "0 MB",
    val totalSizeText: String = "-- MB",
    val hebrewStatusText: String = "ממתין בתור להורדה...",
    val logs: List<String> = emptyList(),
    val rawLogs: List<String> = emptyList(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val trimRange: TrimRange? = null,
    val recordedDurationSeconds: Long = 0L
)

data class TrimRange(
    val startMs: Long,
    val endMs: Long,
    val totalMs: Long
)

enum class AudioQuality(val labelHebrew: String, val bitrateText: String) {
    BEST("איכות מקורית (M4A)", "160 kbps"),
    HIGH("איכות גבוהה (M4A)", "128 kbps"),
    NORMAL("איכות חסכונית (M4A)", "96 kbps")
}

data class UserSettings(
    val allowCellularDownloads: Boolean = true,
    val isDarkMode: Boolean = true,
    val isAmoledTheme: Boolean = false,
    val audioQuality: AudioQuality = AudioQuality.BEST,
    val autoEmbedCoverArt: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val bufferSizeMs: Int = 2000,
    val autoSaveToMusicDir: Boolean = true,
    val downloadFolderPath: String = "תיקיית המוזיקה (Music/TuneVault)",
    val downloadFolderUri: String = "",
    val videoReminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val reminderDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val autoDownloadEnabled: Boolean = false,
    val autoDownloadKeyword: String = ""
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
