package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.ui.theme.BorderDark
import com.example.ui.theme.LiveBadgeColor
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun DownloadItemCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onStopLiveRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLogsExpanded by remember { mutableStateOf(false) }
    var selectedLogTab by remember { mutableIntStateOf(0) }
    var copiedFeedback by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_task_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Thumbnail
                AsyncImage(
                    model = task.thumbnailUrl,
                    contentDescription = task.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 80.dp, height = 54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Channel
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.channel,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.hebrewStatusText,
                        color = when (task.status) {
                            DownloadStatus.COMPLETED -> SuccessGreen
                            DownloadStatus.FAILED -> LiveBadgeColor
                            DownloadStatus.RECORDING_LIVE -> LiveBadgeColor
                            else -> NeonCyan
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Action or Status Icon
                when (task.status) {
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "הושלם",
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "שגיאה",
                            tint = LiveBadgeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DownloadStatus.RECORDING_LIVE -> {
                        Button(
                            onClick = onStopLiveRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = LiveBadgeColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("עצור ושמור", fontSize = 11.sp)
                        }
                    }
                    else -> {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "בטל",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Progress Bar & Diagnostics
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING || task.status == DownloadStatus.TRIMMING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ScarletRed,
                    trackColor = BorderDark
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${task.progressPercent}% • ${task.downloadSpeedText}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${task.downloadedSizeText} / ${task.totalSizeText}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Expandable Logs Toggle
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isLogsExpanded = !isLogsExpanded }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (isLogsExpanded) NeonCyan else TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (task.rawLogs.isNotEmpty())
                        "פלט מקורי של yt-dlp (${task.rawLogs.size} שורות)"
                    else
                        "פרטי תהליך ופלט yt-dlp",
                    color = if (isLogsExpanded) NeonCyan else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isLogsExpanded) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isLogsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Terminal Logs Box
            AnimatedVisibility(visible = isLogsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    // Header controls: Tabs + Copy button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        // Segmented tabs
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Tab 0: Raw yt-dlp
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedLogTab == 0) ScarletRed.copy(alpha = 0.2f) else SurfaceDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedLogTab = 0 }
                            ) {
                                Text(
                                    text = "פלט מקורי (${task.rawLogs.size})",
                                    color = if (selectedLogTab == 0) ScarletRed else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Tab 1: Hebrew translated logs
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedLogTab == 1) NeonCyan.copy(alpha = 0.2f) else SurfaceDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedLogTab = 1 }
                            ) {
                                Text(
                                    text = "יומן בעברית (${task.logs.size})",
                                    color = if (selectedLogTab == 1) NeonCyan else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Copy button
                        val linesToCopy = if (selectedLogTab == 0) task.rawLogs else task.logs
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = linesToCopy.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(linesToCopy.joinToString("\n")))
                                    copiedFeedback = true
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "העתק פלט",
                                tint = if (copiedFeedback) SuccessGreen else TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (copiedFeedback) "הועתק!" else "העתק",
                                color = if (copiedFeedback) SuccessGreen else TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Console output view
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF090D12))
                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (selectedLogTab == 0) {
                            // Raw yt-dlp Output
                            if (task.rawLogs.isEmpty()) {
                                Text(
                                    text = "ממתין לפלט תהליך ממנוע yt-dlp...",
                                    color = TextTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    task.rawLogs.forEach { rawLine ->
                                        val lineColor = when {
                                            rawLine.startsWith("[cmd]") -> Color(0xFFFFD54F) // Gold/Yellow
                                            rawLine.startsWith("[youtube]") || rawLine.startsWith("[info]") -> Color(0xFF00E5FF) // Cyan
                                            rawLine.startsWith("[download]") -> Color(0xFF00E676) // Green
                                            rawLine.startsWith("[Fixup") || rawLine.startsWith("[ExtractAudio]") -> Color(0xFFEA80FC) // Magenta
                                            rawLine.contains("ERROR", ignoreCase = true) ||
                                                rawLine.startsWith("[stderr]") ||
                                                rawLine.startsWith("[error]") ||
                                                rawLine.startsWith("[FATAL]") -> Color(0xFFFF5252) // Red
                                            rawLine.contains("WARNING", ignoreCase = true) -> Color(0xFFFFA726) // Orange
                                            rawLine.startsWith("[process]") || rawLine.startsWith("[success]") -> Color(0xFF1DE9B6) // Teal
                                            else -> Color(0xFFCFD8DC) // Soft white/silver
                                        }
                                        Text(
                                            text = rawLine,
                                            color = lineColor,
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // Hebrew logs
                            if (task.logs.isEmpty()) {
                                Text(
                                    text = "אין נתוני יומן בעברית עדיין...",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    task.logs.forEach { logLine ->
                                        Text(
                                            text = logLine,
                                            color = when {
                                                logLine.contains("שגיאה") -> LiveBadgeColor
                                                logLine.contains("הצלחה") -> SuccessGreen
                                                logLine.contains("חיתוך") -> NeonCyan
                                                else -> TextSecondary
                                            },
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
