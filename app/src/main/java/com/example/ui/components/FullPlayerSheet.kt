package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.TrackEntity
import com.example.data.model.RepeatMode
import com.example.ui.theme.BorderDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    track: TrackEntity,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    repeatMode: RepeatMode,
    isShuffle: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind10s: () -> Unit,
    onForward10s: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClosePlayback: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggedPositionMs by remember { mutableLongStateOf(0L) }

    val displayPosition = if (isDraggingSlider) draggedPositionMs else currentPositionMs
    val progressFraction = if (durationMs > 0) (displayPosition.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = ObsidianDark,
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_player_sheet")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactScreen = maxHeight < 650.dp
            val artworkDimension = if (isCompactScreen) 150.dp else (maxHeight * 0.28f).coerceIn(190.dp, 280.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                // 1. Top Header Bar: Minimize, Title + Live Indicator, Close Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(SurfaceCard, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "מזער נגן",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "מתנגן כעת",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Youtuber Audio",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onClosePlayback,
                        modifier = Modifier
                            .size(40.dp)
                            .background(ScarletRed.copy(alpha = 0.15f), CircleShape)
                            .testTag("full_player_stop_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "סגור נגן",
                            tint = ScarletRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 6.dp else 12.dp))

                // 2. Proportional High-Quality Artwork
                Box(
                    modifier = Modifier
                        .size(artworkDimension)
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceCard)
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(22.dp))
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 6.dp else 12.dp))

                // 3. Track Title & Artist Information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = if (isCompactScreen) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 22.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontSize = if (isCompactScreen) 12.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 4.dp else 8.dp))

                // 4. Interactive Progress Slider & Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressFraction,
                        onValueChange = {
                            isDraggingSlider = true
                            draggedPositionMs = (it * durationMs).toLong()
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            onSeekTo(draggedPositionMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ScarletRed,
                            activeTrackColor = ScarletRed,
                            inactiveTrackColor = SurfaceCard
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .testTag("player_progress_slider")
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(formatDuration(displayPosition), color = TextSecondary, fontSize = 12.sp)
                        Text(formatDuration(durationMs), color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 4.dp else 8.dp))

                // 5. Primary Playback Controls Deck
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "ערבב שירים",
                            tint = if (isShuffle) ScarletRed else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onRewind10s, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "10 שניות אחורה",
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "הקודם",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Large 64dp Play/Pause Centerpiece
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(ScarletRed)
                    ) {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("full_player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "השהה" else "נגן",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "הבא",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(onClick = onForward10s, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "10 שניות קדימה",
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onToggleRepeat, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = when (repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                RepeatMode.ALL -> Icons.Default.Repeat
                                RepeatMode.OFF -> Icons.Default.Repeat
                            },
                            contentDescription = "חזרה על שיר",
                            tint = if (repeatMode != RepeatMode.OFF) ScarletRed else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 6.dp else 12.dp))

                // 6. Speed Control Deck (Sleek and integrated)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "מהירות נגינה",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Stepper Controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val newSpeed = (playbackSpeed - 0.05f).coerceAtLeast(0.25f)
                                        onSetSpeed((newSpeed * 100).toInt() / 100f)
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(SurfaceCard, CircleShape)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "האט", tint = TextPrimary, modifier = Modifier.size(14.dp))
                                }

                                Text(
                                    text = String.format("%.2fx", playbackSpeed),
                                    color = ScarletRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(
                                    onClick = {
                                        val newSpeed = (playbackSpeed + 0.05f).coerceAtMost(3.00f)
                                        onSetSpeed((newSpeed * 100).toInt() / 100f)
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(SurfaceCard, CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "האץ", tint = TextPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Speed Slider
                        Slider(
                            value = playbackSpeed,
                            onValueChange = {
                                val rounded = ((it * 20).toInt() / 20f).coerceIn(0.25f, 3.00f)
                                onSetSpeed(rounded)
                            },
                            valueRange = 0.25f..3.00f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = SurfaceCard
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        )

                        // Quick Speed Preset Chips
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { preset ->
                                val isSelected = kotlin.math.abs(playbackSpeed - preset) < 0.02f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetSpeed(preset) },
                                    label = { Text("${preset}x".replace(".0x", "x"), fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ScarletRed,
                                        selectedLabelColor = Color.White,
                                        containerColor = SurfaceCard,
                                        labelColor = TextSecondary
                                    ),
                                    border = null,
                                    shape = CircleShape,
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val hours = min / 60
    val remainingMin = min % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, remainingMin, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
