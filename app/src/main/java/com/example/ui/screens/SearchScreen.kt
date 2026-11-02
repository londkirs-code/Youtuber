package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedChannelEntity
import com.example.data.local.SavedVideoEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.AudioQuality
import com.example.data.model.SearchResultItem
import com.example.ui.components.VideoDetailDialog
import com.example.ui.components.VideoResultCard
import com.example.ui.components.YouTubeSearchBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import com.example.ui.theme.BorderDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    suggestions: List<String>,
    history: List<SearchHistoryEntity>,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    searchResults: List<SearchResultItem>,
    isSearching: Boolean,
    savedVideos: List<SavedVideoEntity>,
    savedChannels: List<SavedChannelEntity>,
    onToggleSaveVideo: (SearchResultItem) -> Unit,
    onToggleSaveChannel: (String) -> Unit,
    onDownload: (SearchResultItem, AudioQuality?) -> Unit,
    onTrim: (SearchResultItem) -> Unit,
    onDismissSuggestions: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenSavedContent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var selectedDetailItem by remember { mutableStateOf<SearchResultItem?>(null) }

    fun copyUrl(videoId: String) {
        clipboardManager.setText(AnnotatedString("https://www.youtube.com/watch?v=$videoId"))
        Toast.makeText(context, "הקישור הועתק ללוח", Toast.LENGTH_SHORT).show()
    }

    fun shareUrl(videoId: String, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$videoId")
        }
        context.startActivity(Intent.createChooser(shareIntent, "שתף קישור ליוטיוב"))
    }

    // Auto load next results when user reaches near end of list (Requirement 4)
    LaunchedEffect(listState, searchResults.size) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }.distinctUntilChanged().collect { shouldLoadMore ->
            if (shouldLoadMore && !isSearching) {
                onLoadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Top Branding & Saved Content button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(com.example.R.drawable.ic_youtuber_logo),
                        contentDescription = "Youtuber Logo",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Youtuber",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Dedicated button to open Saved Content management
                Button(
                    onClick = onOpenSavedContent,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("open_saved_content_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "שמורים (${savedChannels.size + savedVideos.size})",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            YouTubeSearchBar(
                query = searchQuery,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onClear = onClear,
                suggestions = suggestions,
                history = history,
                onDeleteHistoryItem = onDeleteHistoryItem,
                onClearAllHistory = onClearAllHistory,
                onDismissSuggestions = onDismissSuggestions
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isSearching && searchResults.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ScarletRed, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "מחפש סרטונים וקבצי שמע...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (searchResults.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("search_results_list")
                ) {
                    items(
                        items = searchResults,
                        key = { it.id }
                    ) { item ->
                        val isVideoSaved = savedVideos.any { it.id == item.id }
                        val isChannelSaved = savedChannels.any { it.channelTitle == item.channelTitle }

                        VideoResultCard(
                            item = item,
                            isVideoSaved = isVideoSaved,
                            isChannelSaved = isChannelSaved,
                            onClick = { selectedDetailItem = item },
                            onCopyUrl = { copyUrl(item.id) },
                            onShareUrl = { shareUrl(item.id, item.title) },
                            onDownload = { onDownload(item, null) },
                            onTrim = { onTrim(item) },
                            onToggleSaveVideo = { onToggleSaveVideo(item) },
                            onToggleSaveChannel = { onToggleSaveChannel(item.channelTitle) }
                        )
                    }

                    if (isSearching) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(color = ScarletRed, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            } else {
                // Empty search state - display Saved Channels & Saved Videos quick sections
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // SAVED CHANNELS SECTION
                    if (savedChannels.isNotEmpty()) {
                        item {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "ערוצים שמורים (${savedChannels.size})",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(onClick = onOpenSavedContent) {
                                        Text("נהל הכל", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(savedChannels, key = { it.channelTitle }) { ch ->
                                        SuggestionChip(
                                            onClick = {
                                                onDismissSuggestions()
                                                onSearch(ch.channelTitle)
                                            },
                                            label = { Text(ch.channelTitle, fontSize = 12.sp, color = TextPrimary) },
                                            icon = {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = SurfaceCard
                                            ),
                                            border = null,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SAVED VIDEOS SECTION
                    if (savedVideos.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = ScarletRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "קבצים ששמרת (${savedVideos.size})",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        items(savedVideos, key = { it.id }) { sv ->
                            val resultItem = SearchResultItem(
                                id = sv.id,
                                title = sv.title,
                                channelTitle = sv.channelTitle,
                                durationText = sv.durationText,
                                durationMs = sv.durationMs,
                                thumbnailUrl = sv.thumbnailUrl,
                                viewsText = sv.viewsText,
                                uploadedText = "שמור",
                                isLive = sv.isLive
                            )
                            val isChannelSaved = savedChannels.any { it.channelTitle == sv.channelTitle }

                            VideoResultCard(
                                item = resultItem,
                                isVideoSaved = true,
                                isChannelSaved = isChannelSaved,
                                onClick = { selectedDetailItem = resultItem },
                                onCopyUrl = { copyUrl(resultItem.id) },
                                onShareUrl = { shareUrl(resultItem.id, resultItem.title) },
                                onDownload = { onDownload(resultItem, null) },
                                onTrim = { onTrim(resultItem) },
                                onToggleSaveVideo = { onToggleSaveVideo(resultItem) },
                                onToggleSaveChannel = { onToggleSaveChannel(sv.channelTitle) }
                            )
                        }
                    }

                    // Welcome / Discovery prompt when no saved items
                    if (savedChannels.isEmpty() && savedVideos.isEmpty()) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    Image(
                                        painter = painterResource(com.example.R.drawable.ic_youtuber_logo),
                                        contentDescription = "Youtuber Logo",
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "חיפוש Youtuber מובנה",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "הקלד שם סרטון, פודקאסט, אמן או הדבק קישור ישיר לסרטון או שידור חי מיוטיוב להורדה והאזנה מהירה.",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Video Detail Dialog (Requirement 4)
        selectedDetailItem?.let { item ->
            val isVideoSaved = savedVideos.any { it.id == item.id }
            val isChannelSaved = savedChannels.any { it.channelTitle == item.channelTitle }

            VideoDetailDialog(
                item = item,
                isVideoSaved = isVideoSaved,
                isChannelSaved = isChannelSaved,
                onDismiss = { selectedDetailItem = null },
                onDownload = { quality ->
                    onDownload(item, quality)
                    selectedDetailItem = null
                },
                onTrim = {
                    onTrim(item)
                    selectedDetailItem = null
                },
                onCopyUrl = { copyUrl(item.id) },
                onShareUrl = { shareUrl(item.id, item.title) },
                onToggleSaveVideo = { onToggleSaveVideo(item) },
                onToggleSaveChannel = { onToggleSaveChannel(item.channelTitle) }
            )
        }
    }
}
