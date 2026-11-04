package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.PlaylistEntity
import com.example.data.local.TrackEntity
import com.example.data.model.DownloadTask
import androidx.compose.foundation.border
import com.example.ui.theme.BorderDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.io.File

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    downloadTasks: List<DownloadTask> = emptyList(),
    onPlayTrack: (List<TrackEntity>, Int) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (Long, String) -> Unit,
    onOpenChannel: (channelTitle: String, channelId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var isCreatePlaylistOpen by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    fun copyUrl(track: TrackEntity) {
        // Extract video ID from track.id (format is videoId_timestamp or videoId)
        val videoId = track.id.substringBefore("_")
        clipboardManager.setText(AnnotatedString("https://www.youtube.com/watch?v=$videoId"))
        Toast.makeText(context, "הקישור הועתק ללוח", Toast.LENGTH_SHORT).show()
    }

    fun shareUrl(track: TrackEntity) {
        val videoId = track.id.substringBefore("_")
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$videoId")
        }
        context.startActivity(Intent.createChooser(shareIntent, "שתף קישור ליוטיוב"))
    }

    fun shareAudioFile(track: TrackEntity) {
        try {
            val file = File(track.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "קובץ השמע אינו זמין לשיתוף", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, track.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "שתף קובץ שמע"))
        } catch (e: Exception) {
            Toast.makeText(context, "שגיאה בשיתוף הקובץ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = "ספריית הקבצים שלי",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (selectedTab == 1) {
                IconButton(onClick = { isCreatePlaylistOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "צור רשימת השמעה",
                        tint = ScarletRed,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Sub Tabs
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("קבצים שהורדו (${tracks.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ScarletRed,
                    selectedLabelColor = Color.White,
                    containerColor = SurfaceCard,
                    labelColor = TextSecondary
                ),
                border = null,
                shape = CircleShape
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("פלייליסטים (${playlists.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ScarletRed,
                    selectedLabelColor = Color.White,
                    containerColor = SurfaceCard,
                    labelColor = TextSecondary
                ),
                border = null,
                shape = CircleShape
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTab == 0) {
            // Tracks List
            if (tracks.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = ScarletRed.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "הספרייה שלך ריקה",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "הורד סרטונים וקבצי שמע מיוטיוב והם יופיעו כאן להאזנה מלאה ברקע ללא אינטרנט",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("library_tracks_list")
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { _, item -> item.id }
                    ) { index, track ->
                        TrackRowItem(
                            track = track,
                            playlists = playlists,
                            onClick = { onPlayTrack(tracks, index) },
                            onCopyUrl = { copyUrl(track) },
                            onShareUrl = { shareUrl(track) },
                            onShareFile = { shareAudioFile(track) },
                            onDelete = { onDeleteTrack(track) },
                            onAddToPlaylist = { playlistId -> onAddTrackToPlaylist(playlistId, track.id) },
                            onOpenChannel = onOpenChannel
                        )
                    }
                }
            }
        } else {
            // Playlists List
            if (playlists.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "אין רשימות השמעה",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { isCreatePlaylistOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("צור פלייליסט חדש")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(
                        items = playlists,
                        key = { _, item -> item.playlistId }
                    ) { _, playlist ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ScarletRed.copy(alpha = 0.2f))
                                    ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = ScarletRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = playlist.name,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "רשימת השמעה מותאמת",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create Playlist Dialog
        if (isCreatePlaylistOpen) {
            AlertDialog(
                onDismissRequest = { isCreatePlaylistOpen = false },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(20.dp),
                title = { Text("רשימת השמעה חדשה", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("שם הפלייליסט (לדוגמה: פודקאסטים, מוזיקה)", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onCreatePlaylist(newPlaylistName)
                            newPlaylistName = ""
                            isCreatePlaylistOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("צור פלייליסט")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isCreatePlaylistOpen = false }) {
                        Text("ביטול", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun TrackRowItem(
    track: TrackEntity,
    playlists: List<PlaylistEntity>,
    onClick: () -> Unit,
    onCopyUrl: () -> Unit,
    onShareUrl: () -> Unit,
    onShareFile: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onOpenChannel: (channelTitle: String, channelId: String) -> Unit = { _, _ -> }
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
            .testTag("track_row_item")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.isPartial) {
                        Text(
                            text = "חלקי",
                            color = com.example.ui.theme.WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(com.example.ui.theme.WarningAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = track.artist,
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenChannel(track.artist, "") }
                            .padding(vertical = 1.dp)
                            .weight(1f, fill = false)
                    )
                    if (track.durationMs > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${formatDuration(track.durationMs)}",
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                    if (track.isTrimmed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• חתוך",
                            color = NeonCyan,
                            fontSize = 10.sp
                        )
                    }
                }

                // Storage location display (User Requirement: במידע על פריטים שהורדו יהיה מידע איפה הם שמורים)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "נשמר ב: ${track.filePath.ifBlank { "ספריית המכשיר" }}",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(onClick = { isMenuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "אפשרויות",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("נגן קובץ זה") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ScarletRed) },
                        onClick = {
                            isMenuOpen = false
                            onClick()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("העתק קישור") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            isMenuOpen = false
                            onCopyUrl()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("שתף קישור") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            isMenuOpen = false
                            onShareUrl()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("שלח קובץ שמע") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            isMenuOpen = false
                            onShareFile()
                        }
                    )

                    if (playlists.isNotEmpty()) {
                        HorizontalDivider()
                        playlists.forEach { pl ->
                            DropdownMenuItem(
                                text = { Text("הוסף ל-'${pl.name}'") },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    isMenuOpen = false
                                    onAddToPlaylist(pl.playlistId)
                                }
                            )
                        }
                    }

                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("מחק מהמכשיר", color = ScarletRed) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ScarletRed) },
                        onClick = {
                            isMenuOpen = false
                            onDelete()
                        }
                    )
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
