package com.example.ui.components

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isLogsExpanded by remember { mutableStateOf(false) }
    // DEFAULT IS 1 (Hebrew notes / הערות בעברית) per user request #10!
    var selectedLogTab by remember { mutableIntStateOf(1) }
    var copiedFeedback by remember { mutableStateOf(false) }

    fun copyUrl() {
        clipboardManager.setText(AnnotatedString("https://www.youtube.com/watch?v=${task.videoId}"))
        Toast.makeText(context, "הקישור הועתק ללוח", Toast.LENGTH_SHORT).show()
    }

    fun shareUrl() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, task.title)
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${task.videoId}")
        }
        context.startActivity(Intent.createChooser(shareIntent, "שתף קישור"))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_task_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Hebrew Status (with percentage included!)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.channel,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (task.status == DownloadStatus.DOWNLOADING && task.progressPercent > 0)
                            "${task.hebrewStatusText} (${task.progressPercent}%)"
                        else
                            task.hebrewStatusText,
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

                // Copy & Share buttons (Requirement 9 & 10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { copyUrl() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "העתק קישור",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = { shareUrl() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "שתף קישור",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action or Status Icon
                    when (task.status) {
                        DownloadStatus.COMPLETED -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "הושלם",
                                tint = SuccessGreen,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(22.dp)
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "שגיאה",
                                tint = LiveBadgeColor,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(22.dp)
                            )
                        }
                        DownloadStatus.RECORDING_LIVE -> {
                            Button(
                                onClick = onStopLiveRecording,
                                colors = ButtonDefaults.buttonColors(containerColor = LiveBadgeColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("עצור", fontSize = 11.sp)
                            }
                        }
                        else -> {
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
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
            }

            // Progress Bar
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING || task.status == DownloadStatus.TRIMMING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ScarletRed,
                    trackColor = BorderDark
                )
                Spacer(modifier = Modifier.height(4.dp))
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
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isLogsExpanded = !isLogsExpanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (isLogsExpanded) NeonCyan else TextTertiary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedLogTab == 1)
                        "הערות בעברית (${task.logs.size} רשומות • ${task.progressPercent}%)"
                    else
                        "פלט מקורי של yt-dlp (${task.rawLogs.size} שורות)",
                    color = if (isLogsExpanded) NeonCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isLogsExpanded) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isLogsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Terminal Logs Box
            AnimatedVisibility(visible = isLogsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Header controls: Tabs + Copy button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        // Segmented tabs (Tab 1 Hebrew is first and default!)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Tab 1: Hebrew translated logs (DEFAULT)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedLogTab == 1) NeonCyan.copy(alpha = 0.2f) else SurfaceDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedLogTab = 1 }
                            ) {
                                Text(
                                    text = "הערות בעברית (${task.logs.size})",
                                    color = if (selectedLogTab == 1) NeonCyan else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

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
                                modifier = Modifier.size(13.dp)
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
                            .heightIn(min = 80.dp, max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF090D12))
                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (selectedLogTab == 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Prominent Storage Location Banner (User Requirement)
                                val storageLocation = task.filePath ?: "ספריית המכשיר (Music/TuneVault)"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceDark, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "נשמר ב: $storageLocation",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (task.logs.isEmpty()) {
                                    Text(
                                        text = "ההורדה מתבצעת... (${task.progressPercent}%)",
                                        color = TextTertiary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
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
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        } else {
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
                                            rawLine.startsWith("[cmd]") -> Color(0xFFFFD54F)
                                            rawLine.startsWith("[youtube]") || rawLine.startsWith("[info]") -> Color(0xFF00E5FF)
                                            rawLine.startsWith("[download]") -> Color(0xFF00E676)
                                            rawLine.startsWith("[Fixup") || rawLine.startsWith("[ExtractAudio]") -> Color(0xFFEA80FC)
                                            rawLine.contains("ERROR", ignoreCase = true) ||
                                                rawLine.startsWith("[stderr]") ||
                                                rawLine.startsWith("[error]") ||
                                                rawLine.startsWith("[FATAL]") -> Color(0xFFFF5252)
                                            rawLine.contains("WARNING", ignoreCase = true) -> Color(0xFFFFA726)
                                            rawLine.startsWith("[process]") || rawLine.startsWith("[success]") -> Color(0xFF1DE9B6)
                                            else -> Color(0xFFCFD8DC)
                                        }
                                        Text(
                                            text = rawLine,
                                            color = lineColor,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
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
