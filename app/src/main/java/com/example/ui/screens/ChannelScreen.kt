package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedChannelEntity
import com.example.data.local.TrackEntity
import com.example.data.model.AudioQuality
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.SearchResultItem
import com.example.network.YouTubeEngine
import com.example.ui.components.VideoResultCard
import com.example.ui.theme.BorderDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChannelScreen(
    channelTitle: String,
    channelId: String = "",
    savedChannels: List<SavedChannelEntity>,
    tracks: List<TrackEntity>,
    downloadTasks: List<DownloadTask>,
    onBack: () -> Unit,
    onToggleSaveChannel: (String) -> Unit,
    onToggleNotification: (String, Boolean) -> Unit,
    onDownloadVideo: (SearchResultItem, AudioQuality?) -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onTrim: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var videos by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedChannelId by remember { mutableStateOf(channelId) }

    val isSaved = savedChannels.any { it.channelTitle.equals(channelTitle, ignoreCase = true) }
    val savedChannelObj = savedChannels.firstOrNull { it.channelTitle.equals(channelTitle, ignoreCase = true) }
    val isNotificationsEnabled = savedChannelObj?.notifyNewVideos ?: true

    fun loadChannelContent() {
        isLoading = true
        scope.launch {
            try {
                val list = YouTubeEngine.getChannelVideosNewestFirst(resolvedChannelId, channelTitle)
                videos = list
                if (resolvedChannelId.isBlank() && list.isNotEmpty() && list.first().channelId.isNotBlank()) {
                    resolvedChannelId = list.first().channelId
                }
            } catch (e: Exception) {
                android.util.Log.e("ChannelScreen", "Failed to load channel videos", e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(channelTitle, channelId) {
        loadChannelContent()
    }

    fun copyUrl(videoId: String) {
        clipboardManager.setText(AnnotatedString("https://www.youtube.com/watch?v=$videoId"))
        Toast.makeText(context, "הקישור הועתק ללוח", Toast.LENGTH_SHORT).show()
    }

    fun shareUrl(videoId: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$videoId")
        }
        context.startActivity(Intent.createChooser(intent, "שתף סרטון"))
    }

    // Precomputed O(1) in-memory lookup sets & maps to eliminate disk I/O and quadratic scans during list composition
    val downloadedTracksMap = remember(tracks) {
        val map = HashMap<String, TrackEntity>()
        for (t in tracks) {
            if (!t.isPartial) {
                if (t.videoId.isNotBlank()) map[t.videoId] = t
                map[t.id] = t
            }
        }
        map
    }
    val partialTrackIds = remember(tracks) {
        tracks.filter { it.isPartial }.map { it.videoId.ifEmpty { it.id } }.toHashSet()
    }
    val downloadTasksMap = remember(downloadTasks) {
        downloadTasks.associateBy { it.videoId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("channel_screen")
    ) {
        // Header Bar: Back Button & Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceCard, CircleShape)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "חזור",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "עמוד ערוץ",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { loadChannelContent() },
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceCard, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "רענן",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Channel Info Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Channel Avatar (Gradient badge with initial)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ScarletRed)
                    ) {
                        Text(
                            text = channelTitle.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channelTitle,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.OndemandVideo,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ערוץ רשמי ביוטיוב",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Save Channel & Notification Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onToggleSaveChannel(channelTitle) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) WarningAmber.copy(alpha = 0.2f) else SurfaceDark
                        ),
                        border = BorderStroke(1.dp, if (isSaved) WarningAmber else BorderDark),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isSaved) WarningAmber else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaved) "ערוץ שמור" else "שמור ערוץ",
                            color = if (isSaved) WarningAmber else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isSaved) {
                        Button(
                            onClick = {
                                onToggleNotification(channelTitle, !isNotificationsEnabled)
                                Toast.makeText(
                                    context,
                                    if (!isNotificationsEnabled) "התראות הופעלו עבור $channelTitle" else "התראות הושתקו עבור $channelTitle",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNotificationsEnabled) ScarletRed.copy(alpha = 0.15f) else SurfaceDark
                            ),
                            border = BorderStroke(1.dp, if (isNotificationsEnabled) ScarletRed else BorderDark),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isNotificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = "התראות",
                                tint = if (isNotificationsEnabled) ScarletRed else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isNotificationsEnabled) "התראות פעילות" else "התראות כבויות",
                                color = if (isNotificationsEnabled) ScarletRed else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtitle: Newest to Oldest Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "סרטוני הערוץ (מהחדש לישן)",
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (videos.isNotEmpty()) {
                Text(
                    text = "${videos.size} סרטונים",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Videos List
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ScarletRed, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("טוען סרטונים מהערוץ...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else if (videos.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text("לא נמצאו סרטונים בערוץ זה", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = videos,
                    key = { it.id }
                ) { item ->
                    val downloadedTrack = downloadedTracksMap[item.id]
                    val downloadTask = downloadTasksMap[item.id]
                    val isDownloaded = downloadedTrack != null || downloadTask?.status == DownloadStatus.COMPLETED
                    val isDownloading = downloadTask != null && (downloadTask.status == DownloadStatus.DOWNLOADING || downloadTask.status == DownloadStatus.CONNECTING || downloadTask.status == DownloadStatus.TRIMMING || downloadTask.status == DownloadStatus.QUEUED)
                    val isPartial = !isDownloaded && !isDownloading && (item.id in partialTrackIds || downloadTask?.status == DownloadStatus.FAILED)
                    val downloadProgress = downloadTask?.progressPercent ?: 0

                    VideoResultCard(
                        item = item,
                        isVideoSaved = false,
                        isChannelSaved = isSaved,
                        isDownloaded = isDownloaded,
                        isPartial = isPartial,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onClick = {},
                        onCopyUrl = { copyUrl(item.id) },
                        onShareUrl = { shareUrl(item.id, item.title) },
                        onDownload = { onDownloadVideo(item, null) },
                        onPlay = {
                            val trackToPlay = downloadedTrack ?: downloadedTracksMap[item.id]
                            if (trackToPlay != null) onPlayTrack(trackToPlay)
                        },
                        onTrim = { onTrim(item) },
                        onToggleSaveVideo = {},
                        onToggleSaveChannel = { onToggleSaveChannel(channelTitle) },
                        onOpenChannel = { _, _ -> /* already on channel */ }
                    )
                }
            }
        }
    }
}
