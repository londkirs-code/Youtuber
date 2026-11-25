package com.example.data.prefs

import android.content.Context
import com.example.data.model.AudioQuality
import com.example.data.model.UserSettings

/**
 * Single place that reads/writes the user's settings.
 * Used by the UI and by background entry points (notification "download" button,
 * channel auto-download), which previously used default settings and ignored the
 * user's folder, quality and Wi-Fi-only choice.
 */
object SettingsStore {
    private const val PREFS = "youtuber_user_settings"
    private const val DEFAULT_FOLDER = "תיקיית המוזיקה (Music/TuneVault)"

    fun load(context: Context): UserSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val quality = prefs.getString("audio_quality", null)
            ?.let { name -> AudioQuality.entries.firstOrNull { it.name == name } }
            ?: AudioQuality.BEST
        val days = prefs.getString("reminder_days", null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(1, 2, 3, 4, 5, 6, 7)
        return UserSettings(
            allowCellularDownloads = prefs.getBoolean("allow_cellular", true),
            isDarkMode = prefs.getBoolean("is_dark", true),
            isAmoledTheme = prefs.getBoolean("is_amoled", false),
            audioQuality = quality,
            autoEmbedCoverArt = prefs.getBoolean("auto_embed_cover", true),
            downloadFolderPath = prefs.getString("download_folder", DEFAULT_FOLDER) ?: DEFAULT_FOLDER,
            downloadFolderUri = prefs.getString("download_folder_uri", "") ?: "",
            videoReminderEnabled = prefs.getBoolean("reminder_enabled", false),
            reminderHour = prefs.getInt("reminder_hour", 20),
            reminderMinute = prefs.getInt("reminder_min", 0),
            reminderDays = days,
            autoDownloadEnabled = prefs.getBoolean("auto_dl_enabled", false),
            autoDownloadKeyword = prefs.getString("auto_dl_keyword", "") ?: ""
        )
    }

    fun save(context: Context, s: UserSettings) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("is_dark", s.isDarkMode)
            .putBoolean("is_amoled", s.isAmoledTheme)
            .putBoolean("allow_cellular", s.allowCellularDownloads)
            .putBoolean("auto_embed_cover", s.autoEmbedCoverArt)
            .putString("download_folder", s.downloadFolderPath)
            .putString("download_folder_uri", s.downloadFolderUri)
            .putBoolean("reminder_enabled", s.videoReminderEnabled)
            .putInt("reminder_hour", s.reminderHour)
            .putInt("reminder_min", s.reminderMinute)
            .putString("reminder_days", s.reminderDays.joinToString(","))
            .putBoolean("auto_dl_enabled", s.autoDownloadEnabled)
            .putString("auto_dl_keyword", s.autoDownloadKeyword)
            .putString("audio_quality", s.audioQuality.name)
            .apply()
    }
}
