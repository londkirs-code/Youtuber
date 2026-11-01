package com.example.ui.screens

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

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onAllowCellularChanged: (Boolean) -> Unit,
    onAmoledChanged: (Boolean) -> Unit,
    onQualityChanged: (AudioQuality) -> Unit,
    onEmbedCoverArtChanged: (Boolean) -> Unit,
    onFolderChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFolderDialogOpen by remember { mutableStateOf(false) }
    var folderInput by remember { mutableStateOf(settings.downloadFolderPath) }

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

                // Custom Download Folder (Requirement 7)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            folderInput = settings.downloadFolderPath
                            isFolderDialogOpen = true
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
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
                        Text(
                            text = settings.downloadFolderPath,
                            color = NeonCyan,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            folderInput = settings.downloadFolderPath
                            isFolderDialogOpen = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("שנה", color = TextPrimary, fontSize = 11.sp)
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
        SectionHeader("מראה ותצוגה")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = ScarletRed,
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

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: Audio Quality
        SectionHeader("איכות שמע מועדפת (פורמט M4A מקורי)")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
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

        Spacer(modifier = Modifier.height(16.dp))

        // Developer diagnostics and engine test bench
        EnginePlayground()

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: About & Engine
        SectionHeader("אודות המערכת")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("YouTube v${BuildConfig.VERSION_NAME} • מנוע yt-dlp", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "אפליקציה מהירה, חלקה וקלת משקל להורדת קבצים, פודקאסטים וסרטונים מיוטיוב והשמעה ברקע דרך ההתראות.",
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
