package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Contrast
import com.example.R
import com.example.BuildConfig
import com.example.data.model.AudioQuality
import com.example.data.model.UserSettings
import com.example.ui.theme.BorderDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onAllowCellularChanged: (Boolean) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit = {},
    onAmoledChanged: (Boolean) -> Unit,
    onQualityChanged: (AudioQuality) -> Unit,
    onEmbedCoverArtChanged: (Boolean) -> Unit,
    onFolderChanged: (String) -> Unit = {},
    onFolderUriChanged: (String) -> Unit = {},
    onReminderChanged: (enabled: Boolean, hour: Int, minute: Int, days: List<Int>) -> Unit = { _, _, _, _ -> },
    onAutoDownloadChanged: (enabled: Boolean, keyword: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFolderDialogOpen by remember { mutableStateOf(false) }
    var folderInput by remember { mutableStateOf(settings.downloadFolderPath) }

    val openFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                android.util.Log.w("SettingsScreen", "Failed to persist URI permission", e)
            }
            onFolderUriChanged(uri.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = "הגדרות והתאמה אישית",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION: Network & Downloads
        SectionHeader("רשת והורדות")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Cellular download toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = null,
                        tint = ScarletRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "הורדה דרך נתונים סלולריים",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "מאפשר הורדת קבצים גם כשאינך מחובר ל-Wi-Fi",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.allowCellularDownloads,
                        onCheckedChange = onAllowCellularChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScarletRed,
                            checkedTrackColor = ScarletRed.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("cellular_download_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderDark)
                Spacer(modifier = Modifier.height(12.dp))

                // Real Folder Picker & Custom Folder
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = ScarletRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "מיקום שמירת ההורדות",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val displayPath = formatFolderDisplayName(settings.downloadFolderUri, settings.downloadFolderPath)
                            Text(
                                text = displayPath,
                                color = NeonCyan,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { openFolderTreeLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("בחר תיקייה", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderDark)
                Spacer(modifier = Modifier.height(12.dp))

                // Auto Embed Cover Art
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "הטמעת עטיפת אלבום ותגיות",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "הוספת תמונת שער, יוצר ושם קובץ למטא-דאטה",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.autoEmbedCoverArt,
                        onCheckedChange = onEmbedCoverArtChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScarletRed,
                            checkedTrackColor = ScarletRed.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: Appearance
        SectionHeader("מראה וערכת נושא")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Day / Night Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (settings.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = ScarletRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.isDarkMode) "מצב לילה (כהה)" else "מצב יום (בהיר)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (settings.isDarkMode) "ערכת נושא כהה ונוחה לעיניים" else "ערכת נושא בהירה ונקייה לשימוש באור יום",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.isDarkMode,
                        onCheckedChange = onDarkModeChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScarletRed,
                            checkedTrackColor = ScarletRed.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }

                if (settings.isDarkMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    // AMOLED switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contrast,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "שחור מוחלט (AMOLED Pure Black)",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "חיסכון מקסימלי בסוללה במסכי OLED",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.isAmoledTheme,
                            onCheckedChange = onAmoledChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ScarletRed,
                                checkedTrackColor = ScarletRed.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: Audio Quality
        SectionHeader("איכות שמע מועדפת (פורמט M4A מקורי)")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                AudioQuality.values().forEach { quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = settings.audioQuality == quality,
                            onClick = { onQualityChanged(quality) },
                            colors = RadioButtonDefaults.colors(selectedColor = ScarletRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(quality.labelHebrew, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(quality.bitrateText, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // SECTION: Smart Reminders & Auto Download
        SectionHeader("תזכורות חכמות והורדה אוטומטית")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Reminder alarm toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = ScarletRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "תזכורת קבועה לצפייה בסרטון (שעון מעורר)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "התראה בשעה ובימים הנבחרים לצפייה והאזנה",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.videoReminderEnabled,
                        onCheckedChange = {
                            onReminderChanged(it, settings.reminderHour, settings.reminderMinute, settings.reminderDays)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScarletRed,
                            checkedTrackColor = ScarletRed.copy(alpha = 0.4f)
                        )
                    )
                }

                if (settings.videoReminderEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    // Time selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "שעת התזכורת:",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    val newHour = if (settings.reminderHour - 1 < 0) 23 else settings.reminderHour - 1
                                    onReminderChanged(true, newHour, settings.reminderMinute, settings.reminderDays)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                modifier = Modifier.size(32.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("-", color = TextPrimary, fontSize = 16.sp)
                            }
                            Text(
                                text = String.format("%02d:%02d", settings.reminderHour, settings.reminderMinute),
                                color = NeonCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Button(
                                onClick = {
                                    val newHour = (settings.reminderHour + 1) % 24
                                    onReminderChanged(true, newHour, settings.reminderMinute, settings.reminderDays)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                modifier = Modifier.size(32.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("+", color = TextPrimary, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ימים בשבוע:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val dayNames = listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        dayNames.forEachIndexed { idx, name ->
                            val dayNum = idx + 1
                            val isSelected = settings.reminderDays.contains(dayNum)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val newDays = if (isSelected) {
                                        settings.reminderDays.filter { it != dayNum }
                                    } else {
                                        settings.reminderDays + dayNum
                                    }
                                    onReminderChanged(true, settings.reminderHour, settings.reminderMinute, newDays)
                                },
                                label = { Text(name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ScarletRed,
                                    selectedLabelColor = androidx.compose.ui.graphics.Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderDark)
                Spacer(modifier = Modifier.height(14.dp))

                // Smart Auto Download toggle & keyword input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "מנגנון הורדה אוטומטית חכם",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "מוריד אוטומטית ברקע סרטונים חדשים בערוצים השמורים לפי מילת מפתח",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.autoDownloadEnabled,
                        onCheckedChange = {
                            onAutoDownloadChanged(it, settings.autoDownloadKeyword)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScarletRed,
                            checkedTrackColor = ScarletRed.copy(alpha = 0.4f)
                        )
                    )
                }

                if (settings.autoDownloadEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "מילת מפתח בתחילת הכותרת להורדה:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = settings.autoDownloadKeyword,
                        onValueChange = { onAutoDownloadChanged(true, it) },
                        placeholder = { Text("למשל: שיעור, פרק, פודקאסט", color = TextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Developer diagnostics and engine test bench
        EnginePlayground()

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: About & Engine
        SectionHeader("אודות המערכת")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_youtuber_logo),
                        contentDescription = "Youtuber Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Youtuber v${BuildConfig.VERSION_NAME}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("מנוע yt-dlp עדכני וחזק", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "אפליקציה מהירה, חלקה וקלת משקל להורדת קבצים, פודקאסטים וסרטונים מיוטיוב והשמעה ברקע דרך ההתראות במצב יום ולילה.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))

        // Folder selection dialog
        if (isFolderDialogOpen) {
            AlertDialog(
                onDismissRequest = { isFolderDialogOpen = false },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(18.dp),
                title = { Text("מיקום שמירת ההורדות", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "הזן את שם תיקיית ההורדות במכשיר:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = folderInput,
                            onValueChange = { folderInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (folderInput.isNotBlank()) {
                                onFolderChanged(folderInput.trim())
                            }
                            isFolderDialogOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("שמור")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isFolderDialogOpen = false }) {
                        Text("ביטול", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = NeonCyan,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

private fun formatFolderDisplayName(folderUri: String, fallbackPath: String): String {
    if (folderUri.isBlank()) return fallbackPath
    return try {
        val uri = android.net.Uri.parse(folderUri)
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val decoded = java.net.URLDecoder.decode(docId, "UTF-8")
        val cleanName = decoded.substringAfterLast(":").substringAfterLast("/")
        if (cleanName.isNotBlank()) "תיקייה נבחרה במכשיר: $cleanName" else "תיקייה נבחרה במכשיר: $decoded"
    } catch (_: Exception) {
        try {
            val part = folderUri.substringAfterLast("%2F").substringAfterLast("%3A").substringAfterLast("/")
            val decoded = java.net.URLDecoder.decode(part, "UTF-8")
            "תיקייה נבחרה במכשיר: $decoded"
        } catch (_: Exception) {
            fallbackPath
        }
    }
}

