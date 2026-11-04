package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.data.model.DownloadStatus
import com.example.notify.Notifier
import com.example.ui.components.AudioTrimmerDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.ChannelScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SavedContentScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BorderDark
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TuneVaultTheme
import com.example.viewmodel.MainViewModel

import androidx.compose.material.icons.filled.Bookmark

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Android 13+: notifications need a runtime permission
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIncomingShareIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()

            TuneVaultTheme(
                isDarkMode = settings.isDarkMode,
                isAmoled = settings.isAmoledTheme
            ) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        // Tap on notification to play track directly
        val playTrackId = intent?.getStringExtra(Notifier.EXTRA_PLAY_TRACK_ID)
        if (!playTrackId.isNullOrBlank()) {
            intent.removeExtra(Notifier.EXTRA_PLAY_TRACK_ID)
            viewModel.playTrackById(playTrackId)
            viewModel.selectTab(3)
            return
        }

        // Tap on a "new video" notification -> open the video card
        val openVideoId = intent?.getStringExtra(Notifier.EXTRA_OPEN_VIDEO_ID)
        if (!openVideoId.isNullOrBlank()) {
            intent.removeExtra(Notifier.EXTRA_OPEN_VIDEO_ID)
            viewModel.onSearchQueryChanged("https://www.youtube.com/watch?v=$openVideoId")
            viewModel.selectTab(0)
            return
        }

        // Direct YouTube Link Opener (Open with Youtuber)
        if (intent?.action == Intent.ACTION_VIEW) {
            val viewUrl = intent.dataString
            if (!viewUrl.isNullOrBlank()) {
                viewModel.onSearchQueryChanged(viewUrl)
                viewModel.executeSearch(viewUrl)
                viewModel.selectTab(0)
                return
            }
        }

        // Share text / link
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedText.isNotBlank()) {
                viewModel.onSearchQueryChanged(sharedText)
                viewModel.executeSearch(sharedText)
                viewModel.selectTab(0)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val savedVideos by viewModel.savedVideos.collectAsState()
    val savedChannels by viewModel.savedChannels.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsState()

    val trimTarget by viewModel.trimTarget.collectAsState()
    val trimStartMs by viewModel.trimStartMs.collectAsState()
    val trimEndMs by viewModel.trimEndMs.collectAsState()

    val activeDownloadsCount = downloadTasks.count {
        it.status != DownloadStatus.COMPLETED && it.status != DownloadStatus.FAILED
    }

    var isSavedContentOpen by remember { mutableStateOf(false) }
    var activeChannelTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Docked MiniPlayer above bottom navigation
                AnimatedVisibility(
                    visible = currentTrack != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    currentTrack?.let { track ->
                        MiniPlayer(
                            track = track,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.playNext() },
                            onClose = { viewModel.stopPlayback() },
                            onClick = { viewModel.setFullPlayerOpen(true) }
                        )
                    }
                }

                // Bottom Navigation Bar with 5 tabs and reselection reload
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { viewModel.onTabClicked(0) },
                        icon = { Icon(Icons.Default.Search, contentDescription = "חיפוש") },
                        label = { Text("חיפוש", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_search_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.onTabClicked(1) },
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = "שמורים") },
                        label = { Text("שמורים", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_saved_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { viewModel.onTabClicked(2) },
                        icon = {
                            if (activeDownloadsCount > 0) {
                                BadgedBox(badge = { Badge { Text("$activeDownloadsCount") } }) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = "הורדות")
                                }
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = "הורדות")
                            }
                        },
                        label = { Text("הורדות", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_downloads_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { viewModel.onTabClicked(3) },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "קבצים") },
                        label = { Text("קבצים", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_library_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { viewModel.onTabClicked(4) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "הגדרות") },
                        label = { Text("הגדרות", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> SearchScreen(
                    searchQuery = searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onSearch = { viewModel.executeSearch(it) },
                    onClear = { viewModel.clearSearch() },
                    suggestions = searchSuggestions,
                    history = searchHistory,
                    onDeleteHistoryItem = { viewModel.deleteSearchHistoryItem(it) },
                    onClearAllHistory = { viewModel.clearAllSearchHistory() },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    savedVideos = savedVideos,
                    savedChannels = savedChannels,
                    tracks = tracks,
                    downloadTasks = downloadTasks,
                    onToggleSaveVideo = { viewModel.toggleSaveVideo(it) },
                    onToggleSaveChannel = { viewModel.toggleSaveChannel(it) },
                    onDownload = { item, quality -> viewModel.startDownload(item, applyTrim = false, quality = quality) },
                    onPlayTrack = { track -> viewModel.playTrack(track) },
                    onTrim = { viewModel.openTrimmerDialog(it) },
                    onDismissSuggestions = { viewModel.dismissSuggestions() },
                    onLoadMore = { viewModel.loadMoreSearchResults() },
                    onOpenSavedContent = { viewModel.selectTab(1) },
                    onOpenChannel = { title, id -> activeChannelTarget = Pair(title, id) }
                )
                1 -> SavedContentScreen(
                    savedChannels = savedChannels,
                    savedVideos = savedVideos,
                    onBack = { viewModel.selectTab(0) },
                    onToggleChannelNotification = { title, enabled -> viewModel.toggleChannelNotification(title, enabled) },
                    onDeleteChannel = { title -> viewModel.deleteSavedChannel(title) },
                    onDeleteVideo = { id -> viewModel.deleteSavedVideo(id) },
                    onLoadChannelVideos = { ch -> viewModel.fetchChannelVideos(ch) },
                    onDownloadVideo = { item, q -> viewModel.startDownload(item, applyTrim = false, quality = q) },
                    onPlayVideo = { item ->
                        val existing = tracks.firstOrNull { it.id == item.id }
                        if (existing != null) {
                            viewModel.playTrack(existing)
                        } else {
                            viewModel.startDownload(item, applyTrim = false, quality = null)
                        }
                    },
                    onOpenChannel = { title, id -> activeChannelTarget = Pair(title, id) }
                )
                2 -> DownloadsScreen(
                    tasks = downloadTasks,
                    onCancelTask = { viewModel.downloadManager.cancelDownload(it) },
                    onStopLiveRecording = { viewModel.downloadManager.stopLiveRecording(it) },
                    onDownloadUrl = { viewModel.startDirectUrlDownload(it) }
                )
                3 -> LibraryScreen(
                    tracks = tracks,
                    playlists = playlists,
                    downloadTasks = downloadTasks,
                    onPlayTrack = { list, idx -> viewModel.playTrackInList(list, idx) },
                    onDeleteTrack = { viewModel.deleteTrack(it) },
                    onCreatePlaylist = { viewModel.createPlaylist(it) },
                    onAddTrackToPlaylist = { plId, trId -> viewModel.addTrackToPlaylist(plId, trId) },
                    onOpenChannel = { title, id -> activeChannelTarget = Pair(title, id) }
                )
                4 -> SettingsScreen(
                    settings = settings,
                    onAllowCellularChanged = { viewModel.setAllowCellularDownloads(it) },
                    onDarkModeChanged = { viewModel.setDarkMode(it) },
                    onAmoledChanged = { viewModel.setAmoledTheme(it) },
                    onQualityChanged = { viewModel.setAudioQuality(it) },
                    onEmbedCoverArtChanged = { viewModel.setAutoEmbedCoverArt(it) },
                    onFolderChanged = { viewModel.setDownloadFolder(it) },
                    onFolderUriChanged = { viewModel.setFolderUri(it) },
                    onReminderChanged = { en, h, m, d -> viewModel.setVideoReminder(en, h, m, d) },
                    onAutoDownloadChanged = { en, kw -> viewModel.setAutoDownloadSettings(en, kw) }
                )
            }
        }
    }

    // Audio Trimmer Dialog
    trimTarget?.let { item ->
        AudioTrimmerDialog(
            item = item,
            initialStartMs = trimStartMs,
            initialEndMs = trimEndMs,
            onDismiss = { viewModel.closeTrimmerDialog() },
            onConfirmTrim = { start, end ->
                viewModel.updateTrimRange(start, end)
                viewModel.startDownload(item, applyTrim = true)
            },
            onDownloadFull = {
                viewModel.startDownload(item, applyTrim = false)
            }
        )
    }

    // Full Player Modal Sheet
    if (isFullPlayerOpen && currentTrack != null) {
        FullPlayerSheet(
            track = currentTrack!!,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onRewind10s = { viewModel.rewind10s() },
            onForward10s = { viewModel.forward10s() },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onSetSpeed = { viewModel.setPlaybackSpeed(it) },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onClosePlayback = { viewModel.stopPlayback() },
            onDismiss = { viewModel.setFullPlayerOpen(false) }
        )
    }

    if (isSavedContentOpen) {
        SavedContentScreen(
            savedChannels = savedChannels,
            savedVideos = savedVideos,
            onBack = { isSavedContentOpen = false },
            onToggleChannelNotification = { title, enabled -> viewModel.toggleChannelNotification(title, enabled) },
            onDeleteChannel = { title -> viewModel.deleteSavedChannel(title) },
            onDeleteVideo = { id -> viewModel.deleteSavedVideo(id) },
            onLoadChannelVideos = { ch -> viewModel.fetchChannelVideos(ch) },
            onDownloadVideo = { item, q -> viewModel.startDownload(item, applyTrim = false, quality = q) },
            onPlayVideo = { item ->
                val existing = tracks.firstOrNull { it.id == item.id }
                if (existing != null) {
                    viewModel.playTrack(existing)
                } else {
                    viewModel.startDownload(item, applyTrim = false, quality = null)
                }
            },
            onOpenChannel = { title, id -> activeChannelTarget = Pair(title, id) }
        )
    }

    // Dedicated Channel Screen (Requirement: עמוד ערוץ ייעודי מבוסס מידע של הערוץ מהחדש לישן)
    activeChannelTarget?.let { (chTitle, chId) ->
        ChannelScreen(
            channelTitle = chTitle,
            channelId = chId,
            savedChannels = savedChannels,
            tracks = tracks,
            downloadTasks = downloadTasks,
            onBack = { activeChannelTarget = null },
            onToggleSaveChannel = { viewModel.toggleSaveChannel(it) },
            onToggleNotification = { title, enabled -> viewModel.toggleChannelNotification(title, enabled) },
            onDownloadVideo = { item, quality -> viewModel.startDownload(item, applyTrim = false, quality = quality) },
            onPlayTrack = { track -> viewModel.playTrack(track) },
            onTrim = { viewModel.openTrimmerDialog(it) }
        )
    }
}
