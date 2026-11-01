package com.example.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.App
import com.example.engine.EngineRuntime
import com.example.engine.EngineVersions
import com.example.engine.YtDlpBridgeCallback
import com.example.ui.theme.BorderDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EnginePlayground() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }
    val logs = remember { mutableStateListOf<String>() }
    var versions by remember { mutableStateOf<EngineVersions?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var activeTaskId by remember { mutableStateOf<String?>(null) }
    var youtubeUrl by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("--version") }
    var resultText by remember { mutableStateOf("") }

    val callback = remember {
        object : YtDlpBridgeCallback {
            override fun onLog(level: String, message: String) {
                handler.post {
                    logs.add("[" + level + "] " + message)
                    if (logs.size > 350) repeat(logs.size - 350) { logs.removeAt(0) }
                }
            }

            override fun onProgress(
                percent: Double,
                downloadedBytes: Long,
                totalBytes: Long,
                speed: String,
                etaSeconds: Long
            ) {
                handler.post { progress = percent.toInt().coerceIn(0, 100) }
            }

            override fun isCancelled(): Boolean = false
        }
    }

    suspend fun ensureEngineReady() {
        val app = context.applicationContext as? App
        if (app != null) {
            if (!app.awaitYtdlp()) {
                throw IllegalStateException("מנוע ההורדה לא הצליח להיטען בהפעלת האפליקציה")
            }
        } else if (!EngineRuntime.isReady()) {
            EngineRuntime.initialize(context)
        }
    }

    fun runTask(taskId: String? = null, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        progress = 0
        activeTaskId = taskId
        logs.clear()
        resultText = ""
        scope.launch {
            try {
                ensureEngineReady()
                action()
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                logs.add("[ERROR] " + message)
                resultText = "הפעולה נכשלה: " + message
            } finally {
                activeTaskId = null
                busy = false
            }
        }
    }

    fun refreshDiagnostics() {
        runTask {
            val current = EngineRuntime.getVersions(context)
            versions = current
            logs.add("[INFO] Python " + current.python)
            logs.add("[INFO] yt-dlp " + current.ytDlp)
            logs.add("[INFO] yt-dlp-ejs " + current.ejs)
            logs.add("[INFO] FFmpeg " + current.ffmpeg)
            logs.add("[INFO] FFprobe " + current.ffprobe)
            logs.add("[INFO] QuickJS " + current.quickJs)
            logs.add("[INFO] Architecture " + current.architecture)
            logs.add("[INFO] Extractors " + current.extractorCount)
            val exitCode = EngineRuntime.runCommand(context, "--version", callback)
            logs.add("[INFO] yt-dlp diagnostic exit code: " + exitCode)
            resultText = if (exitCode == 0) "בדיקת הרכיבים הסתיימה" else "בדיקת yt-dlp הסתיימה עם קוד " + exitCode
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Playground • מעבדת מנוע ההורדה",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "אבחון, בדיקות YouTube, פקודות yt-dlp, עדכוני מנוע ויומן מפורט.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { refreshDiagnostics() },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("בדיקת תקינות לכל הרכיבים")
            }

            versions?.let { v ->
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    VersionRow("Python", v.python)
                    VersionRow("yt-dlp", v.ytDlp)
                    VersionRow("yt-dlp-ejs", v.ejs)
                    VersionRow("FFmpeg", v.ffmpeg)
                    VersionRow("FFprobe", v.ffprobe)
                    VersionRow("QuickJS", v.quickJs)
                    VersionRow("ארכיטקטורה", v.architecture)
                    VersionRow("Extractors", v.extractorCount.toString())
                    VersionRow("curl_cffi", if (v.curlCffi) "זמין" else "לא מותקן")
                    VersionRow("Deno", "לא משולב ב־Android; QuickJS הוא המנוע הפעיל")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderDark)
            Spacer(modifier = Modifier.height(12.dp))

            Text("בדיקת קישור YouTube", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = youtubeUrl,
                onValueChange = { youtubeUrl = it },
                label = { Text("כתובת סרטון YouTube") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        runTask {
                            if (youtubeUrl.isBlank()) throw IllegalArgumentException("יש להזין כתובת YouTube")
                            resultText = EngineRuntime.inspectUrl(context, youtubeUrl.trim(), "default", callback)
                            logs.add("[SUCCESS] חילוץ המידע הסתיים")
                        }
                    },
                    enabled = !busy && youtubeUrl.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("בדיקת חילוץ") }
                OutlinedButton(
                    onClick = {
                        runTask {
                            if (youtubeUrl.isBlank()) throw IllegalArgumentException("יש להזין כתובת YouTube")
                            val code = EngineRuntime.runCommand(context, "--list-formats " + youtubeUrl.trim(), callback)
                            resultText = "רשימת הפורמטים הסתיימה. קוד יציאה: " + code
                        }
                    },
                    enabled = !busy && youtubeUrl.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("רשימת פורמטים") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val taskId = "playground-" + System.currentTimeMillis()
                    runTask(taskId) {
                        if (youtubeUrl.isBlank()) throw IllegalArgumentException("יש להזין כתובת YouTube")
                        val outputDir = File(context.filesDir, "playground-downloads").apply { mkdirs() }
                        val outputTemplate = File(outputDir, "%(title).60s.%(ext)s").absolutePath
                        val code = EngineRuntime.runYtDlp(
                            context,
                            taskId,
                            listOf(
                                "--no-playlist",
                                "--download-sections", "*0-20",
                                "-f", "bestaudio[ext=m4a]/bestaudio",
                                "-o", outputTemplate,
                                youtubeUrl.trim()
                            ),
                            callback
                        )
                        resultText = "בדיקת הורדה הסתיימה. קוד יציאה: " + code + ". הקבצים נשמרים בתיקייה הפרטית של האפליקציה."
                    }
                },
                enabled = !busy && youtubeUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                modifier = Modifier.fillMaxWidth()
            ) { Text("בדיקת הורדה אמיתית (20 שניות)") }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderDark)
            Spacer(modifier = Modifier.height(12.dp))

            Text("מסוף פקודות yt-dlp", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "הפקודות רצות דרך מנתח yt-dlp בתוך Python, לא דרך shell של Android. פקודות shell, טעינת plugins וקובצי config חיצוניים חסומים.",
                color = TextSecondary,
                fontSize = 11.sp
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("פקודת yt-dlp") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        runTask {
                            if (command.isBlank()) throw IllegalArgumentException("יש להזין פקודה")
                            val code = EngineRuntime.runCommand(context, command, callback)
                            resultText = "הפקודה הסתיימה. קוד יציאה: " + code
                        }
                    },
                    enabled = !busy && command.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ScarletRed),
                    modifier = Modifier.weight(1f)
                ) { Text("הרץ פקודה") }
                OutlinedButton(
                    onClick = { command = "--version" },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("איפוס פקודה") }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderDark)
            Spacer(modifier = Modifier.height(12.dp))
            Text("ניהול רכיבים", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        runTask {
                            resultText = EngineRuntime.updateYtDlp(context, callback)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("עדכון yt-dlp ו־EJS") }
                OutlinedButton(
                    onClick = {
                        runTask {
                            resultText = EngineRuntime.updateFfmpeg(context, callback)
                            versions = EngineRuntime.getVersions(context)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("שחזור FFmpeg") }
            }
            Text(
                "עדכון yt-dlp ו־EJS מתקין חבילות חדשות ומחייב הפעלה מחדש. FFmpeg/FFprobe מתעדכנים עם גרסת APK חדשה; הכפתור משחזר את הזוג המובנה במקרה של תקלה.",
                color = TextSecondary,
                fontSize = 11.sp
            )

            if (busy) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Text(if (progress > 0) "בתהליך… " + progress + "%" else "הבדיקה מתבצעת…", color = TextSecondary, fontSize = 12.sp)
                    activeTaskId?.let { taskId ->
                        OutlinedButton(onClick = { EngineRuntime.cancel(taskId) }) { Text("ביטול") }
                    }
                }
            }

            if (resultText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(resultText, color = NeonCyan, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("יומן פעילות", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { logs.clear() }, enabled = logs.isNotEmpty() && !busy) {
                    Text("נקה יומן")
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101319)),
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = if (logs.isEmpty()) "עדיין אין פלט. הפעל בדיקה כדי להתחיל." else logs.joinToString("\n"),
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
