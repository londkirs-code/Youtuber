package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SearchResultItem
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AudioTrimmerDialog(
    item: SearchResultItem,
    initialStartMs: Long,
    initialEndMs: Long,
    onDismiss: () -> Unit,
    onConfirmTrim: (startMs: Long, endMs: Long) -> Unit,
    onDownloadFull: () -> Unit
) {
    val maxDurationMs = if (item.durationMs > 0) item.durationMs else 180000L
    var rangeValues by remember {
        mutableStateOf(
            (initialStartMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)..(initialEndMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)
        )
    }

    val currentStartMs = (rangeValues.start * maxDurationMs).toLong()
    val currentEndMs = (rangeValues.endInclusive * maxDurationMs).toLong()
    val trimmedDurationMs = (currentEndMs - currentStartMs).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = ScarletRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "חיתוך שמע מדויק (MediaMuxer)",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.title,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("התחלה", color = TextSecondary, fontSize = 11.sp)
                        Text(formatDuration(currentStartMs), color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("משך מקטע", color = TextSecondary, fontSize = 11.sp)
                        Text(formatDuration(trimmedDurationMs), color = ScarletRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("סיום", color = TextSecondary, fontSize = 11.sp)
                        Text(formatDuration(currentEndMs), color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                RangeSlider(
                    value = rangeValues,
                    onValueChange = { rangeValues = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ScarletRed,
                        activeTrackColor = ScarletRed,
                        inactiveTrackColor = SurfaceCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trim_range_slider")
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "החיתוך מבוצע ללא דחיסה וללא פגיעה באיכות המקורית (M4A Lossless)",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmTrim(currentStartMs, currentEndMs) },
                colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("confirm_trim_btn")
            ) {
                Text("הורד מקטע חתוך", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("ביטול", color = TextSecondary)
                }
                TextButton(onClick = onDownloadFull) {
                    Text("הורד מלא", color = TextPrimary)
                }
            }
        }
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
