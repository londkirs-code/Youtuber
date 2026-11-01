package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val downloadManager = DownloadManager(application)

    // Navigation Tab (0: Search, 1: Downloads, 2: Library, 3: Settings)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun selectTab(index: Int) {
        _currentTab.value = index
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

    // Trimmer Dialog state
    private val _trimTarget = MutableStateFlow<SearchResultItem?>(null)
    val trimTarget: StateFlow<SearchResultItem?> = _trimTarget.asStateFlow()

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(0L)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    // User Settings
    private val _settings = MutableStateFlow(UserSettings())
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

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        suggestionDebounceJob?.cancel()

        if (newQuery.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }

        suggestionDebounceJob = viewModelScope.launch {
            delay(250)
            val suggestions = YouTubeEngine.getSearchSuggestions(newQuery)
            _searchSuggestions.value = suggestions
        }
    }

    fun executeSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        _searchQuery.value = q
        _searchSuggestions.value = emptyList()
        _isSearching.value = true

        // Persist to Room search history
        viewModelScope.launch {
            database.searchHistoryDao().insertSearch(SearchHistoryEntity(query = q))
        }

        viewModelScope.launch {
            val results = YouTubeEngine.searchVideos(q)
            _searchResults.value = results
            _isSearching.value = false
        }
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

    fun toggleSaveChannel(channelTitle: String) {
        if (channelTitle.isBlank()) return
        viewModelScope.launch {
            val isAlreadySaved = savedChannels.value.any { it.channelTitle == channelTitle }
            if (isAlreadySaved) {
                database.savedMediaDao().deleteSavedChannel(channelTitle)
            } else {
                database.savedMediaDao().saveChannel(SavedChannelEntity(channelTitle = channelTitle))
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        _searchResults.value = emptyList()
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

    fun startDownload(item: SearchResultItem, applyTrim: Boolean = false) {
        val trimRange = if (applyTrim) {
            TrimRange(startMs = _trimStartMs.value, endMs = _trimEndMs.value, totalMs = item.durationMs)
        } else null

        downloadManager.startDownload(
            videoId = item.id,
            title = item.title,
            channel = item.channelTitle,
            thumbnailUrl = item.thumbnailUrl,
            isLive = item.isLive,
            trimRange = trimRange,
            settings = _settings.value
        )

        closeTrimmerDialog()
        _currentTab.value = 1
    }

    // Playback Controls
    fun playTrack(track: TrackEntity) {
        playerService?.playTrackList(listOf(track), 0)
    }

    fun playTrackInList(trackList: List<TrackEntity>, index: Int) {
        playerService?.playTrackList(trackList, index)
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

    // Settings actions
    fun setAllowCellularDownloads(allow: Boolean) {
        _settings.value = _settings.value.copy(allowCellularDownloads = allow)
    }

    fun setAmoledTheme(isAmoled: Boolean) {
        _settings.value = _settings.value.copy(isAmoledTheme = isAmoled)
    }

    fun setAudioQuality(quality: AudioQuality) {
        _settings.value = _settings.value.copy(audioQuality = quality)
    }

    fun setAutoEmbedCoverArt(embed: Boolean) {
        _settings.value = _settings.value.copy(autoEmbedCoverArt = embed)
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
