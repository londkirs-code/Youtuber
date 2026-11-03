package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SearchResultItem
import com.example.ui.theme.BorderDark
import com.example.ui.theme.LiveBadgeColor
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun VideoResultCard(
    item: SearchResultItem,
    isVideoSaved: Boolean = false,
    isChannelSaved: Boolean = false,
    isDownloaded: Boolean = false,
    isPartial: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    onClick: () -> Unit = {},
    onCopyUrl: () -> Unit = {},
    onShareUrl: () -> Unit = {},
    onDownload: () -> Unit,
    onPlay: () -> Unit = {},
    onTrim: () -> Unit = {},
    onToggleSaveVideo: () -> Unit = {},
    onToggleSaveChannel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderDark),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("video_result_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Upper Tier: Thumbnail + Video Details with full horizontal space
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 16:9 Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )

                    if (item.isLive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(LiveBadgeColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (item.durationText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.durationText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata: Title (up to 4 lines, ample width), Channel, Views & Upload Time
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.channelTitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Upload date / time ago + views with high contrast
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (item.uploadedText.isNotBlank()) {
                            Text(
                                text = item.uploadedText,
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                            if (item.viewsText.isNotBlank()) {
                                Text(
                                    text = " • ",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        if (item.viewsText.isNotBlank()) {
                            Text(
                                text = item.viewsText,
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // Download status badges
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "הורד לספרייה",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isDownloading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "מוריד... $downloadProgress%",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isPartial) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(WarningAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "הורדה חלקית",
                                color = WarningAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtle divider for clean visual hierarchy
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderDark.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Lower Tier: Expanded Action Bar with Action Icons and Play/Download Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Secondary action buttons (Save, Copy, Share, Trim)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Bookmark / Save video
                    IconButton(
                        onClick = onToggleSaveVideo,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideoSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isVideoSaved) "הסר משמורים" else "שמור סרטון",
                            tint = if (isVideoSaved) WarningAmber else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Copy URL
                    IconButton(
                        onClick = onCopyUrl,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "העתק קישור",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Share URL
                    IconButton(
                        onClick = onShareUrl,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "שתף קישור",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Audio Trim
                    IconButton(
                        onClick = onTrim,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "חיתוך שמע",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // If already downloaded: SHOW PLAY BUTTON INSTEAD OF DOWNLOAD BUTTON
                if (isDownloaded) {
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("play_video_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "נגן סרטון",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "נגן",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isDownloading) {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = SurfaceCard),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("downloading_video_btn")
                    ) {
                        CircularProgressIndicator(
                            color = ScarletRed,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$downloadProgress%",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Prominent Large Download Button
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("download_video_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "הורד שמע",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPartial) "הורד שוב" else "הורדה",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
