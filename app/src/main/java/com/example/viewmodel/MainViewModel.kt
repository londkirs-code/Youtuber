package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.data.local.AppDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SavedChannelEntity
import com.example.data.local.SavedVideoEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TrackEntity
import com.example.data.model.AudioQuality
import com.example.data.model.DownloadTask
import com.example.data.model.RepeatMode
import com.example.data.model.SearchResultItem
import com.example.data.model.TrimRange
import com.example.data.model.UserSettings
import com.example.download.DownloadManager
import com.example.network.YouTubeEngine
import com.example.service.AudioPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    // One shared instance (also used by notification buttons)
    val downloadManager: DownloadManager = (application as App).downloadManager

    // Navigation Tab (0: Search, 1: Downloads, 2: Library, 3: Settings)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    fun onTabClicked(index: Int) {
        if (_currentTab.value == index) {
            reloadTab(index)
        } else {
            _currentTab.value = index
        }
    }

    fun reloadTab(index: Int) {
        when (index) {
            0 -> {
                val q = _searchQuery.value.trim()
                if (q.isNotBlank()) executeSearch(q)
            }
            1 -> {
                // Downloads list auto-reflects StateFlow
            }
            2 -> {
                // Library tracks auto-reflect StateFlow
            }
            3 -> {
                // Settings
            }
        }
    }

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = database.searchHistoryDao().getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedVideos: StateFlow<List<SavedVideoEntity>> = database.savedMediaDao().getAllSavedVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedChannels: StateFlow<List<SavedChannelEntity>> = database.savedMediaDao().getAllSavedChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var suggestionDebounceJob: Job? = null
    private var searchJob: Job? = null

    // Trimmer Dialog state
    private val _trimTarget = MutableStateFlow<SearchResultItem?>(null)
    val trimTarget: StateFlow<SearchResultItem?> = _trimTarget.asStateFlow()

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(0L)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    // User Settings persistence
    private val prefs = application.getSharedPreferences("youtuber_user_settings", Context.MODE_PRIVATE)

    private fun loadSettings(): UserSettings {
        val dark = prefs.getBoolean("is_dark", true)
        val amoled = prefs.getBoolean("is_amoled", false)
        val cellular = prefs.getBoolean("allow_cellular", true)
        val coverArt = prefs.getBoolean("auto_embed_cover", true)
        val folder = prefs.getString("download_folder", "תיקיית המוזיקה (Music/TuneVault)") ?: "תיקיית המוזיקה (Music/TuneVault)"
        val folderUri = prefs.getString("download_folder_uri", "") ?: ""
        val reminder = prefs.getBoolean("reminder_enabled", false)
        val reminderH = prefs.getInt("reminder_hour", 20)
        val reminderM = prefs.getInt("reminder_min", 0)
        val autoDl = prefs.getBoolean("auto_dl_enabled", false)
        val autoKw = prefs.getString("auto_dl_keyword", "") ?: ""
        return UserSettings(
            allowCellularDownloads = cellular,
            isDarkMode = dark,
            isAmoledTheme = amoled,
            autoEmbedCoverArt = coverArt,
            downloadFolderPath = folder,
            downloadFolderUri = folderUri,
            videoReminderEnabled = reminder,
            reminderHour = reminderH,
            reminderMinute = reminderM,
            autoDownloadEnabled = autoDl,
            autoDownloadKeyword = autoKw
        )
    }

    private fun updateAndPersistSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putBoolean("is_dark", updated.isDarkMode)
            .putBoolean("is_amoled", updated.isAmoledTheme)
            .putBoolean("allow_cellular", updated.allowCellularDownloads)
            .putBoolean("auto_embed_cover", updated.autoEmbedCoverArt)
            .putString("download_folder", updated.downloadFolderPath)
            .putString("download_folder_uri", updated.downloadFolderUri)
            .putBoolean("reminder_enabled", updated.videoReminderEnabled)
            .putInt("reminder_hour", updated.reminderHour)
            .putInt("reminder_min", updated.reminderMinute)
            .putBoolean("auto_dl_enabled", updated.autoDownloadEnabled)
            .putString("auto_dl_keyword", updated.autoDownloadKeyword)
            .apply()
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    // Room Database Flows
    val tracks: StateFlow<List<TrackEntity>> = database.trackDao().getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = database.playlistDao().getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Downloads from DownloadManager
    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    // Player Service connection
    private var playerService: AudioPlayerService? = null
    private val _isPlayerBound = MutableStateFlow(false)

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioPlayerService.LocalBinder ?: return
            playerService = localBinder.getService()
            _isPlayerBound.value = true

            playerService?.let { s ->
                viewModelScope.launch { s.currentTrack.collect { _currentTrack.value = it } }
                viewModelScope.launch { s.isPlaying.collect { _isPlaying.value = it } }
                viewModelScope.launch { s.currentPositionMs.collect { _currentPositionMs.value = it } }
                viewModelScope.launch { s.durationMs.collect { _durationMs.value = it } }
                viewModelScope.launch { s.playbackSpeed.collect { _playbackSpeed.value = it } }
                viewModelScope.launch { s.repeatMode.collect { _repeatMode.value = it } }
                viewModelScope.launch { s.isShuffle.collect { _isShuffle.value = it } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            _isPlayerBound.value = false
        }
    }

    init {
        val intent = Intent(application, AudioPlayerService::class.java)
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** A real YouTube link (not just a word that happens to be 11 characters long). */
    private fun isYoutubeLink(text: String): Boolean =
        (text.contains("youtu.be/") || text.contains("youtube.com/")) &&
            YouTubeEngine.extractVideoId(text) != null

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        suggestionDebounceJob?.cancel()

        if (newQuery.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }

        // Pasted link: resolve immediately (no debounce, no suggestions, no search button)
        if (isYoutubeLink(newQuery)) {
            _searchSuggestions.value = emptyList()
            val url = Regex("https?://\\S+").find(newQuery)?.value ?: newQuery.trim()
            executeSearch(url)
            return
        }

        suggestionDebounceJob = viewModelScope.launch {
            delay(250)
            val suggestions = YouTubeEngine.getSearchSuggestions(newQuery)
            _searchSuggestions.value = suggestions
        }
    }

    fun executeSearch(query: String) {
        suggestionDebounceJob?.cancel()
        _searchSuggestions.value = emptyList()
        val q = query.trim()
        if (q.isBlank()) return
        _searchQuery.value = q
        _isSearching.value = true

        // Persist to Room search history (links are not worth keeping)
        if (!isYoutubeLink(q)) {
            viewModelScope.launch {
                database.searchHistoryDao().insertSearch(SearchHistoryEntity(query = q))
            }
        }

        // A newer search/paste cancels the older one so stale results never overwrite fresh ones
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = YouTubeEngine.searchVideos(q)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun loadMoreSearchResults() {
        val q = _searchQuery.value.trim()
        if (q.isBlank() || _isSearching.value || isYoutubeLink(q)) return
        viewModelScope.launch {
            val currentSize = _searchResults.value.size
            val more = YouTubeEngine.searchMoreVideos(q, currentSize)
            if (more.isNotEmpty()) {
                val existingIds = _searchResults.value.map { it.id }.toSet()
                val filtered = more.filter { it.id !in existingIds }
                _searchResults.value = _searchResults.value + filtered
            }
        }
    }

    fun dismissSuggestions() {
        suggestionDebounceJob?.cancel()
        _searchSuggestions.value = emptyList()
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            database.searchHistoryDao().deleteSearch(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            database.searchHistoryDao().clearAllHistory()
        }
    }

    fun toggleSaveVideo(item: SearchResultItem) {
        viewModelScope.launch {
            val isAlreadySaved = savedVideos.value.any { it.id == item.id }
            if (isAlreadySaved) {
                database.savedMediaDao().deleteSavedVideo(item.id)
            } else {
                database.savedMediaDao().saveVideo(
                    SavedVideoEntity(
                        id = item.id,
                        title = item.title,
                        channelTitle = item.channelTitle,
                        durationText = item.durationText,
                        durationMs = item.durationMs,
                        thumbnailUrl = item.thumbnailUrl,
                        viewsText = item.viewsText,
                        isLive = item.isLive
                    )
                )
            }
        }
    }

    /**
     * Saving a channel also turns on "new video" notifications for it
     * (checked every ~15 minutes by ChannelCheckWorker).
     */
    fun toggleSaveChannel(channelTitle: String) {
        if (channelTitle.isBlank()) return
        viewModelScope.launch {
            val dao = database.savedMediaDao()
            val isAlreadySaved = savedChannels.value.any { it.channelTitle == channelTitle }
            if (isAlreadySaved) {
                dao.deleteSavedChannel(channelTitle)
                return@launch
            }

            // Find what we know about this channel from the visible results / saved videos
            val fromResults = _searchResults.value.firstOrNull { it.channelTitle == channelTitle }
            val seedVideoId = fromResults?.id
                ?: savedVideos.value.firstOrNull { it.channelTitle == channelTitle }?.id
                ?: ""
            var channelId = fromResults?.channelId.orEmpty()

            dao.saveChannel(
                SavedChannelEntity(
                    channelTitle = channelTitle,
                    channelId = channelId,
                    seedVideoId = seedVideoId
                )
            )

            // Resolve the channel id + remember the newest video now, so only FUTURE uploads notify
            withContext(Dispatchers.IO) {
                if (channelId.isBlank() && seedVideoId.isNotBlank()) {
                    channelId = YouTubeEngine.resolveChannelIdFromVideo(seedVideoId).orEmpty()
                    if (channelId.isNotBlank()) dao.updateChannelId(channelTitle, channelId)
                }
                if (channelId.isNotBlank()) {
                    YouTubeEngine.fetchChannelFeed(channelId).firstOrNull()?.let {
                        dao.updateLastSeen(channelTitle, it.videoId)
                    }
                }
            }
        }
    }

    fun toggleChannelNotification(channelTitle: String, enabled: Boolean) {
        viewModelScope.launch {
            database.savedMediaDao().setChannelNotify(channelTitle, enabled)
        }
    }

    fun deleteSavedChannel(channelTitle: String) {
        viewModelScope.launch {
            database.savedMediaDao().deleteSavedChannel(channelTitle)
        }
    }

    fun deleteSavedVideo(videoId: String) {
        viewModelScope.launch {
            database.savedMediaDao().deleteSavedVideo(videoId)
        }
    }

    suspend fun fetchChannelVideos(channel: SavedChannelEntity): List<SearchResultItem> {
        return withContext(Dispatchers.IO) {
            var channelId = channel.channelId
            if (channelId.isBlank() && channel.seedVideoId.isNotBlank()) {
                channelId = YouTubeEngine.resolveChannelIdFromVideo(channel.seedVideoId).orEmpty()
                if (channelId.isNotBlank()) {
                    database.savedMediaDao().updateChannelId(channel.channelTitle, channelId)
                }
            }
            YouTubeEngine.getChannelVideosNewestFirst(channelId, channel.channelTitle)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        // Retain existing search results so pressing X does not wipe them out per user request
    }

    fun openTrimmerDialog(item: SearchResultItem) {
        _trimTarget.value = item
        _trimStartMs.value = 0L
        _trimEndMs.value = if (item.durationMs > 0) item.durationMs else 180000L
    }

    fun closeTrimmerDialog() {
        _trimTarget.value = null
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _trimStartMs.value = startMs
        _trimEndMs.value = endMs
    }

    fun startDownload(item: SearchResultItem, applyTrim: Boolean = false, quality: AudioQuality? = null) {
        val trimRange = if (applyTrim) {
            TrimRange(startMs = _trimStartMs.value, endMs = _trimEndMs.value, totalMs = item.durationMs)
        } else null

        val effectiveSettings = if (quality != null) {
            _settings.value.copy(audioQuality = quality)
        } else {
            _settings.value
        }

        downloadManager.startDownload(
            videoId = item.id,
            title = item.title,
            channel = item.channelTitle,
            thumbnailUrl = item.thumbnailUrl,
            isLive = item.isLive,
            trimRange = trimRange,
            settings = effectiveSettings
        )

        closeTrimmerDialog()
        _currentTab.value = 2
    }

    // Playback Controls (Clicking opens Full Player directly per Requirement 2)
    fun playTrack(track: TrackEntity) {
        playerService?.playTrackList(listOf(track), 0)
        _isFullPlayerOpen.value = true
    }

    fun playTrackInList(trackList: List<TrackEntity>, index: Int) {
        playerService?.playTrackList(trackList, index)
        _isFullPlayerOpen.value = true
    }

    fun playTrackById(trackId: String) {
        viewModelScope.launch {
            val track = database.trackDao().getTrackById(trackId)
                ?: tracks.value.firstOrNull { it.id == trackId }
            if (track != null) {
                playTrack(track)
            }
        }
    }

    fun togglePlayPause() {
        playerService?.togglePlayPause()
    }

    fun seekTo(posMs: Long) {
        playerService?.seekTo(posMs)
    }

    fun rewind10s() {
        playerService?.rewind10Seconds()
    }

    fun forward10s() {
        playerService?.forward10Seconds()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerService?.setPlaybackSpeed(speed)
    }

    fun toggleRepeat() {
        playerService?.toggleRepeatMode()
    }

    fun toggleShuffle() {
        playerService?.toggleShuffle()
    }

    fun playNext() {
        playerService?.playNext()
    }

    fun playPrevious() {
        playerService?.playPrevious()
    }

    fun setFullPlayerOpen(open: Boolean) {
        _isFullPlayerOpen.value = open
    }

    fun stopPlayback() {
        playerService?.stopPlayback()
        _isFullPlayerOpen.value = false
        _currentTrack.value = null
        _isPlaying.value = false
    }

    // Settings actions
    fun setAllowCellularDownloads(allow: Boolean) {
        updateAndPersistSettings { it.copy(allowCellularDownloads = allow) }
    }

    fun setDarkMode(isDark: Boolean) {
        updateAndPersistSettings { it.copy(isDarkMode = isDark) }
    }

    fun setAmoledTheme(isAmoled: Boolean) {
        updateAndPersistSettings { it.copy(isAmoledTheme = isAmoled) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        updateAndPersistSettings { it.copy(audioQuality = quality) }
    }

    fun setAutoEmbedCoverArt(embed: Boolean) {
        updateAndPersistSettings { it.copy(autoEmbedCoverArt = embed) }
    }

    fun setDownloadFolder(path: String) {
        updateAndPersistSettings { it.copy(downloadFolderPath = path) }
    }

    fun setFolderUri(uri: String) {
        updateAndPersistSettings { it.copy(downloadFolderUri = uri) }
    }

    fun setVideoReminder(enabled: Boolean, hour: Int, minute: Int, days: List<Int>) {
        updateAndPersistSettings {
            it.copy(
                videoReminderEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute,
                reminderDays = days
            )
        }
    }

    fun setAutoDownloadSettings(enabled: Boolean, keyword: String) {
        updateAndPersistSettings {
            it.copy(
                autoDownloadEnabled = enabled,
                autoDownloadKeyword = keyword
            )
        }
    }

    fun startDirectUrlDownload(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val id = YouTubeEngine.extractVideoId(trimmed) ?: System.currentTimeMillis().toString()
        val item = SearchResultItem(
            id = id,
            title = "הורדה ישירה $id",
            channelTitle = "YouTube",
            durationText = "",
            durationMs = 0L,
            thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
            viewsText = "",
            uploadedText = "",
            isLive = false
        )
        startDownload(item, applyTrim = false, quality = null)
        _currentTab.value = 2 // Tab index for Downloads
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            database.trackDao().deleteTrack(track)
            try {
                val file = java.io.File(track.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // Ignore file delete errors
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            database.playlistDao().insertPlaylist(PlaylistEntity(name = name.trim()))
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            database.playlistDao().addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
        }
    }

    override fun onCleared() {
        if (_isPlayerBound.value) {
            getApplication<Application>().unbindService(serviceConnection)
            _isPlayerBound.value = false
        }
        super.onCleared()
    }
}
