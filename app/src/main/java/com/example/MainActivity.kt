package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.data.model.DownloadStatus
import com.example.ui.components.AudioTrimmerDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.LibraryScreen
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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingShareIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()

            TuneVaultTheme(isAmoled = settings.isAmoledTheme) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
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
                            onClick = { viewModel.setFullPlayerOpen(true) }
                        )
                    }
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { viewModel.selectTab(0) },
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
                        onClick = { viewModel.selectTab(1) },
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
                        selected = currentTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "ספרייה") },
                        label = { Text("ספרייה", fontSize = 11.sp) },
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
                        selected = currentTab == 3,
                        onClick = { viewModel.selectTab(3) },
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
                    onToggleSaveVideo = { viewModel.toggleSaveVideo(it) },
                    onToggleSaveChannel = { viewModel.toggleSaveChannel(it) },
                    onDownload = { viewModel.startDownload(it, applyTrim = false) },
                    onTrim = { viewModel.openTrimmerDialog(it) }
                )
                1 -> DownloadsScreen(
                    tasks = downloadTasks,
                    onCancelTask = { viewModel.downloadManager.cancelDownload(it) },
                    onStopLiveRecording = { viewModel.downloadManager.stopLiveRecording(it) }
                )
                2 -> LibraryScreen(
                    tracks = tracks,
                    playlists = playlists,
                    onPlayTrack = { list, idx -> viewModel.playTrackInList(list, idx) },
                    onDeleteTrack = { viewModel.deleteTrack(it) },
                    onCreatePlaylist = { viewModel.createPlaylist(it) },
                    onAddTrackToPlaylist = { plId, trId -> viewModel.addTrackToPlaylist(plId, trId) }
                )
                3 -> SettingsScreen(
                    settings = settings,
                    onAllowCellularChanged = { viewModel.setAllowCellularDownloads(it) },
                    onAmoledChanged = { viewModel.setAmoledTheme(it) },
                    onQualityChanged = { viewModel.setAudioQuality(it) },
                    onEmbedCoverArtChanged = { viewModel.setAutoEmbedCoverArt(it) }
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
            onDismiss = { viewModel.setFullPlayerOpen(false) }
        )
    }
}
