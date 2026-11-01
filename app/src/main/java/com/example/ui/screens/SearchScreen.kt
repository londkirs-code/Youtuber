package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedChannelEntity
import com.example.data.local.SavedVideoEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.model.SearchResultItem
import com.example.ui.components.VideoResultCard
import com.example.ui.components.YouTubeSearchBar
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WarningAmber

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
    onDownload: (SearchResultItem) -> Unit,
    onTrim: (SearchResultItem) -> Unit,
    onDismissSuggestions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
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

            Spacer(modifier = Modifier.height(10.dp))

            if (isSearching) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ScarletRed, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "מחפש סרטונים ושירים ביוטיוב...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (searchResults.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            onToggleSaveVideo = { onToggleSaveVideo(item) },
                            onToggleSaveChannel = { onToggleSaveChannel(item.channelTitle) },
                            onDownload = { onDownload(item) },
                            onTrim = { onTrim(item) }
                        )
                    }
                }
            } else {
                // Empty search state - display Saved Channels & Saved Videos quick sections
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ערוצים שמורים (${savedChannels.size})",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
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
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = ScarletRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "סרטונים ששמרת (${savedVideos.size})",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
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
                                onToggleSaveVideo = { onToggleSaveVideo(resultItem) },
                                onToggleSaveChannel = { onToggleSaveChannel(sv.channelTitle) },
                                onDownload = { onDownload(resultItem) },
                                onTrim = { onTrim(resultItem) }
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
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = ScarletRed.copy(alpha = 0.6f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "חיפוש יוטיוב מובנה",
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "הקלד שם שיר, אמן או הדבק קישור ישיר לסרטון או שידור חי מיוטיוב. תוכל לשמור ערוצים וסרטונים מועדפים בלחיצה על כוכב או סימנייה!",
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

        // Quick Paste FAB
        FloatingActionButton(
            onClick = {
                val clipText = clipboardManager.getText()?.text ?: ""
                if (clipText.isNotBlank()) {
                    onQueryChanged(clipText)
                    onSearch(clipText)
                }
            },
            containerColor = ScarletRed,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
                .testTag("quick_paste_fab")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "הדבק מהלוח",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("הדבק קישור", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
