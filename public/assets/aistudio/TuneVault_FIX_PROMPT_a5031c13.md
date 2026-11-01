# משימה: תיקון והרחבת אפליקציית אנדרואיד (ריפו londkirs-code/Youtuber)

אתה מקבל רשימה של 16 קבצים מלאים. כל קובץ כבר כתוב ומוכן. **התפקיד שלך הוא רק להעתיק אותם למקום הנכון**, לא לכתוב מחדש ולא "לשפר".

## חוקים (חובה)
1. עבור כל קובץ ברשימה: פתח את הנתיב המדויק. אם כתוב **החלף**, מחק את כל התוכן הקיים והדבק את הקוד המלא שמתחת לכותרת. אם כתוב **חדש**, צור קובץ חדש בנתיב הזה (וצור גם תיקיות חסרות).
2. אל תקצר, אל תדלג על שורות ואל תכתוב "..." או "שאר הקוד כמו קודם". הדבק הכול.
3. אל תשנה שמות חבילות, imports, או שמות פונקציות. הנתיבים והחבילות (`com.example...`) צריכים להישאר זהים.
4. אל תיגע בקבצים שלא ברשימה (SearchScreen, DownloadsScreen, LibraryScreen, AudioPlayerService, AudioTrimmer וכו').
5. אל תוסיף ספריות נוספות ואל תשנה גרסאות של ספריות אחרות.
6. אחרי שסיימת, ודא שכל 16 הקבצים קיימים, ואז הרץ build (`gradle :app:assembleDebug`) ותקן **רק** שגיאות קומפילציה, באופן מינימלי.

## מה הקוד עושה (לידיעתך בלבד)
- ההורדה עוברת ל-yt-dlp אמיתי (ספריית youtubedl-android), כי החילוץ הידני הישן מול יוטיוב מת.
- הדבקת קישור יוטיוב פותחת מיד כרטיס, בלי חיפוש.
- התראות: התחלת הורדה, התקדמות, סיום וכישלון.
- שמירת ערוץ (הכוכב) מפעילה התראה כשעולה בו סרטון חדש, בדיקה כל ~15 דקות.

## אם ה-build נכשל
- אם Gradle לא מוצא `io.github.junkfood02.youtubedl-android` בגרסה `0.17.2`: שנה את המספר `youtubedl = "0.17.2"` בקובץ `gradle/libs.versions.toml` לגרסה העדכנית שקיימת ב-Maven Central. שום דבר אחר לא משתנה.
- אם יש שגיאה בקריאה `YoutubeDL.getInstance().execute(request, taskId) { progress, _, line -> ... }` בקובץ `DownloadManager.kt`: התאם רק את חתימת ה-callback לגרסה שהותקנה.
- אם יש שגיאת Room על סכימה: ודא ש-`version = 3` ב-`AppDatabase.kt` ושה-`MIGRATION_2_3` קיים.

## רשימת הקבצים (בסדר הזה)

1. `gradle/libs.versions.toml` — **החלף**
2. `app/build.gradle.kts` — **החלף**
3. `app/src/main/AndroidManifest.xml` — **החלף**
4. `app/src/main/java/com/example/App.kt` — **חדש**
5. `app/src/main/java/com/example/notify/Notifier.kt` — **חדש**
6. `app/src/main/java/com/example/download/DownloadService.kt` — **חדש**
7. `app/src/main/java/com/example/download/DownloadActionReceiver.kt` — **חדש**
8. `app/src/main/java/com/example/work/ChannelCheckWorker.kt` — **חדש**
9. `app/src/main/java/com/example/data/model/Models.kt` — **החלף**
10. `app/src/main/java/com/example/data/local/SavedEntities.kt` — **החלף**
11. `app/src/main/java/com/example/data/local/SavedDaos.kt` — **החלף**
12. `app/src/main/java/com/example/data/local/AppDatabase.kt` — **החלף**
13. `app/src/main/java/com/example/network/YouTubeEngine.kt` — **החלף**
14. `app/src/main/java/com/example/download/DownloadManager.kt` — **החלף**
15. `app/src/main/java/com/example/viewmodel/MainViewModel.kt` — **החלף**
16. `app/src/main/java/com/example/MainActivity.kt` — **החלף**

---


## קובץ 1/16: `gradle/libs.versions.toml` — **החלף**

````toml
[versions]
agp = "9.1.1"
coreKtx = "1.18.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.8.7"
lifecycleViewmodelCompose = "2.8.7"
lifecycleRuntimeCompose = "2.8.7"
activityCompose = "1.10.1"
kotlin = "2.2.10"
composeBom = "2024.09.00"
googleDevtoolsKsp = "2.3.5"
navigationCompose = "2.8.9"
roomRuntime = "2.7.0"
roomKtx = "2.7.0"
roomCompiler = "2.7.0"
kotlinxCoroutinesTest = "1.10.2"
core = "1.6.1"
runner = "1.6.2"
coilCompose = "2.7.0"
retrofit = "2.12.0"
converterMoshi = "2.12.0"
kotlinxCoroutinesAndroid = "1.10.2"
kotlinxCoroutinesCore = "1.10.2"
accompanistPermissions = "0.37.3"
playServicesLocation = "21.3.0"
cameraCamera2 = "1.5.0"
cameraLifecycle = "1.5.0"
cameraView = "1.5.0"
cameraCore = "1.5.0"
loggingInterceptor = "4.10.0"
okhttp = "4.10.0"
moshiKotlin = "1.15.2"
moshiKotlinCodegen = "1.15.2"
datastorePreferences = "1.1.7"
robolectric = "4.16.1"
firebaseBom = "34.17.0"
secretsGradlePlugin = "2.0.1"
googleServices = "4.5.0"
credentials = "1.5.0"
googleid = "1.1.1"
youtubedl = "0.17.2"
work = "2.10.0"


[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "roomRuntime" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "roomKtx" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "roomCompiler" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-core = { group = "androidx.test", name = "core", version.ref = "core" }
androidx-runner = { group = "androidx.test", name = "runner", version.ref = "runner" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coilCompose" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
converter-moshi = { group = "com.squareup.retrofit2", name = "converter-moshi", version.ref = "converterMoshi" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutinesAndroid" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutinesCore" }
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanistPermissions" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "cameraCamera2" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "cameraLifecycle" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "cameraView" }
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "cameraCore" }
logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "loggingInterceptor" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
youtubedl-library = { group = "io.github.junkfood02.youtubedl-android", name = "library", version.ref = "youtubedl" }
youtubedl-ffmpeg = { group = "io.github.junkfood02.youtubedl-android", name = "ffmpeg", version.ref = "youtubedl" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
moshi-kotlin = { group = "com.squareup.moshi", name = "moshi-kotlin", version.ref = "moshiKotlin" }
moshi-kotlin-codegen = { group = "com.squareup.moshi", name = "moshi-kotlin-codegen", version.ref = "moshiKotlinCodegen" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-ai = { group = "com.google.firebase", name = "firebase-ai" }
firebase-appcheck-recaptcha = { group = "com.google.firebase", name = "firebase-appcheck-recaptcha" }
firebase-appcheck-debug = { group = "com.google.firebase", name = "firebase-appcheck-debug" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
androidx-credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }


[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
google-devtools-ksp = { id = "com.google.devtools.ksp", version.ref = "googleDevtoolsKsp" }
secrets = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secretsGradlePlugin" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
````


## קובץ 2/16: `app/build.gradle.kts` — **החלף**

````kotlin
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.tunevault.ytmpro"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // yt-dlp + python + ffmpeg are native libraries. arm64 only keeps the APK small enough for GitHub
    // (add "armeabi-v7a" for very old 32-bit phones; the APK grows by ~25 MB per extra ABI).
    ndk { abiFilters += listOf("arm64-v8a") }
  }

  // Required: python/ffmpeg libraries must be extracted to disk
  packaging { jniLibs { useLegacyPackaging = true } }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // NEW: yt-dlp engine (extraction + download), ffmpeg helper, and background checks for new videos
  implementation(libs.youtubedl.library)
  implementation(libs.youtubedl.ffmpeg)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
````


## קובץ 3/16: `app/src/main/AndroidManifest.xml` — **החלף**

````xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <!-- NEW: background downloads -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- NEW: android:name=".App" and extractNativeLibs="true" (required by yt-dlp/python/ffmpeg) -->
    <application
        android:name=".App"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:extractNativeLibs="true"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApplication"
        tools:targetApi="36">

        <service
            android:name=".service.AudioPlayerService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <!-- NEW: keeps downloads alive in the background -->
        <service
            android:name=".download.DownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <!-- NEW: buttons inside notifications (cancel / download) -->
        <receiver
            android:name=".download.DownloadActionReceiver"
            android:exported="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTop"
            android:theme="@style/Theme.MyApplication"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Direct YouTube Share Sheet Handler -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
    </application>

</manifest>
````


## קובץ 4/16: `app/src/main/java/com/example/App.kt` — **חדש**

````kotlin
package com.example

import android.app.Application
import android.util.Log
import com.example.download.DownloadManager
import com.example.notify.Notifier
import com.example.work.ChannelCheckWorker
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ytdlpReady = CompletableDeferred<Boolean>()
    private val updateMutex = Mutex()

    // One shared DownloadManager for the whole process (UI, notifications, receivers)
    val downloadManager: DownloadManager by lazy { DownloadManager(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ChannelCheckWorker.schedule(this)

        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp init failed", e)
                ytdlpReady.complete(false)
                return@launch
            }
            // Update yt-dlp at most once every 12 hours (YouTube changes constantly)
            val prefs = getSharedPreferences("ytdlp", MODE_PRIVATE)
            val last = prefs.getLong("last_update", 0L)
            if (System.currentTimeMillis() - last > 12L * 60 * 60 * 1000) {
                updateYtdlp()
            }
            ytdlpReady.complete(true)
        }
    }

    /** Download code calls this before the first yt-dlp command. */
    suspend fun awaitYtdlp(): Boolean = ytdlpReady.await()

    /** Updates the yt-dlp binary. Safe to call from several places. */
    suspend fun updateYtdlp() {
        updateMutex.withLock {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.NIGHTLY)
                Log.i(TAG, "yt-dlp update status: $status")
                getSharedPreferences("ytdlp", MODE_PRIVATE)
                    .edit().putLong("last_update", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "App"
    }
}
````


## קובץ 5/16: `app/src/main/java/com/example/notify/Notifier.kt` — **חדש**

````kotlin
package com.example.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.download.DownloadActionReceiver

object Notifier {
    const val CH_PROGRESS = "download_progress"
    const val CH_DONE = "download_done"
    const val CH_NEW = "new_videos"
    const val CH_SERVICE = "download_service"
    const val FG_ID = 7001

    const val EXTRA_OPEN_VIDEO_ID = "open_video_id"

    private val lastProgressAt = HashMap<String, Long>()

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "התקדמות הורדה", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "הורדה הסתיימה", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NEW, "סרטונים חדשים בערוצים שמורים", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "שירות הורדות", NotificationManager.IMPORTANCE_MIN)
        )
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notifyId(key: String) = key.hashCode()

    private fun openAppIntent(ctx: Context, videoId: String? = null, requestCode: Int = 0): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (videoId != null) putExtra(EXTRA_OPEN_VIDEO_ID, videoId)
        }
        return PendingIntent.getActivity(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Notification shown by the foreground service while downloads are running. */
    fun foregroundNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("TuneVault מוריד ברקע")
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun downloadStarted(ctx: Context, taskId: String, title: String) {
        if (!canNotify(ctx)) return
        val n = progressBuilder(ctx, taskId, title)
            .setContentText("ההורדה התחילה...")
            .setProgress(0, 0, true)
            .build()
        nm(ctx).notify(notifyId(taskId), n)
    }

    /** percent < 0 => indeterminate bar (live recording, connecting...) */
    fun downloadProgress(ctx: Context, taskId: String, title: String, percent: Int, text: String) {
        if (!canNotify(ctx)) return
        val now = System.currentTimeMillis()
        synchronized(lastProgressAt) {
            val last = lastProgressAt[taskId] ?: 0L
            if (now - last < 800 && percent < 100) return   // avoid notification rate limiting
            lastProgressAt[taskId] = now
        }
        val b = progressBuilder(ctx, taskId, title).setContentText(text)
        if (percent >= 0) b.setProgress(100, percent.coerceIn(0, 100), false)
        else b.setProgress(0, 0, true)
        nm(ctx).notify(notifyId(taskId), b.build())
    }

    private fun progressBuilder(ctx: Context, taskId: String, title: String): NotificationCompat.Builder {
        val cancel = PendingIntent.getBroadcast(
            ctx, notifyId(taskId),
            Intent(ctx, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_CANCEL
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, "ביטול", cancel)
    }

    fun downloadFinished(ctx: Context, taskId: String, title: String) {
        synchronized(lastProgressAt) { lastProgressAt.remove(taskId) }
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("ההורדה הסתיימה")
            .setContentText(title)
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(notifyId(taskId), n)   // same id => replaces the progress notification
    }

    fun downloadFailed(ctx: Context, taskId: String, title: String, reason: String) {
        synchronized(lastProgressAt) { lastProgressAt.remove(taskId) }
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("ההורדה נכשלה")
            .setContentText("$title — $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$reason"))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(notifyId(taskId), n)
    }

    fun cancel(ctx: Context, key: String) {
        synchronized(lastProgressAt) { lastProgressAt.remove(key) }
        nm(ctx).cancel(notifyId(key))
    }

    fun newVideo(ctx: Context, videoId: String, title: String, channel: String, thumbnailUrl: String) {
        if (!canNotify(ctx)) return
        val download = PendingIntent.getBroadcast(
            ctx, notifyId("dl_$videoId"),
            Intent(ctx, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_DOWNLOAD
                putExtra(DownloadActionReceiver.EXTRA_VIDEO_ID, videoId)
                putExtra(DownloadActionReceiver.EXTRA_TITLE, title)
                putExtra(DownloadActionReceiver.EXTRA_CHANNEL, channel)
                putExtra(DownloadActionReceiver.EXTRA_THUMB, thumbnailUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_NEW)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("סרטון חדש ב-$channel")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(openAppIntent(ctx, videoId, notifyId("open_$videoId")))
            .addAction(0, "הורד שמע", download)
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(notifyId("new_$videoId"), n)
    }

    fun cancelNewVideo(ctx: Context, videoId: String) {
        nm(ctx).cancel(notifyId("new_$videoId"))
    }
}
````


## קובץ 6/16: `app/src/main/java/com/example/download/DownloadService.kt` — **חדש**

````kotlin
package com.example.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.notify.Notifier

/**
 * Keeps the process alive while downloads run in the background.
 * Started by DownloadManager when the first download starts and stopped when the last one ends.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifier.foregroundNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this, Notifier.FG_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifier.FG_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException when started from background
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DownloadService::class.java))
        }
    }
}
````


## קובץ 7/16: `app/src/main/java/com/example/download/DownloadActionReceiver.kt` — **חדש**

````kotlin
package com.example.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.App
import com.example.data.model.UserSettings
import com.example.notify.Notifier

/** Handles the buttons inside notifications ("ביטול" and "הורד שמע"). */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        when (intent.action) {
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                app.downloadManager.cancelDownload(taskId)
            }
            ACTION_DOWNLOAD -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return
                app.downloadManager.startDownload(
                    videoId = videoId,
                    title = intent.getStringExtra(EXTRA_TITLE) ?: videoId,
                    channel = intent.getStringExtra(EXTRA_CHANNEL) ?: "",
                    thumbnailUrl = intent.getStringExtra(EXTRA_THUMB)
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    isLive = false,
                    trimRange = null,
                    settings = UserSettings()
                )
                Notifier.cancelNewVideo(context, videoId)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.example.action.CANCEL_DOWNLOAD"
        const val ACTION_DOWNLOAD = "com.example.action.DOWNLOAD_VIDEO"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_THUMB = "thumb"
    }
}
````


## קובץ 8/16: `app/src/main/java/com/example/work/ChannelCheckWorker.kt` — **חדש**

````kotlin
package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.network.YouTubeEngine
import com.example.notify.Notifier
import java.util.concurrent.TimeUnit

/**
 * Every ~15 minutes checks the RSS feed of each saved channel.
 * When a video appears that we haven't seen yet -> notification with a "download" button.
 */
class ChannelCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            val dao = AppDatabase.getDatabase(applicationContext).savedMediaDao()
            val channels = dao.getSavedChannelsOnce().filter { it.notifyNewVideos }

            for (ch in channels) {
                var channelId = ch.channelId
                if (channelId.isBlank() && ch.seedVideoId.isNotBlank()) {
                    channelId = YouTubeEngine.resolveChannelIdFromVideo(ch.seedVideoId).orEmpty()
                    if (channelId.isNotBlank()) dao.updateChannelId(ch.channelTitle, channelId)
                }
                if (channelId.isBlank()) continue

                val feed = YouTubeEngine.fetchChannelFeed(channelId)
                if (feed.isEmpty()) continue

                // First time we see this channel: remember the newest video, don't notify
                if (ch.lastSeenVideoId.isBlank()) {
                    dao.updateLastSeen(ch.channelTitle, feed.first().videoId)
                    continue
                }

                val fresh = feed.takeWhile { it.videoId != ch.lastSeenVideoId }
                if (fresh.isEmpty()) continue

                fresh.take(3).reversed().forEach { v ->
                    Notifier.newVideo(
                        applicationContext, v.videoId, v.title, ch.channelTitle,
                        "https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg"
                    )
                }
                dao.updateLastSeen(ch.channelTitle, feed.first().videoId)
            }
        } catch (e: Exception) {
            Log.w("ChannelCheckWorker", "check failed", e)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "channel_check"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<ChannelCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
````


## קובץ 9/16: `app/src/main/java/com/example/data/model/Models.kt` — **החלף**

````kotlin
package com.example.data.model

data class SearchResultItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val durationText: String,
    val durationMs: Long,
    val thumbnailUrl: String,
    val viewsText: String,
    val uploadedText: String,
    val isLive: Boolean = false,
    val channelId: String = ""
)

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    TRIMMING,
    FINISHING,
    COMPLETED,
    FAILED,
    RECORDING_LIVE
}

data class DownloadTask(
    val id: String,
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val isLiveStream: Boolean = false,
    val progressPercent: Int = 0,
    val downloadSpeedText: String = "0 KB/s",
    val downloadedSizeText: String = "0 MB",
    val totalSizeText: String = "-- MB",
    val hebrewStatusText: String = "ממתין בתור להורדה...",
    val logs: List<String> = emptyList(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val trimRange: TrimRange? = null,
    val recordedDurationSeconds: Long = 0L
)

data class TrimRange(
    val startMs: Long,
    val endMs: Long,
    val totalMs: Long
)

enum class AudioQuality(val labelHebrew: String, val bitrateText: String) {
    BEST("איכות מקורית (M4A)", "160 kbps"),
    HIGH("איכות גבוהה (M4A)", "128 kbps"),
    NORMAL("איכות חסכונית (M4A)", "96 kbps")
}

data class UserSettings(
    val allowCellularDownloads: Boolean = true,
    val isAmoledTheme: Boolean = true,
    val audioQuality: AudioQuality = AudioQuality.BEST,
    val autoEmbedCoverArt: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val bufferSizeMs: Int = 2000,
    val autoSaveToMusicDir: Boolean = true
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
````


## קובץ 10/16: `app/src/main/java/com/example/data/local/SavedEntities.kt` — **החלף**

````kotlin
package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_videos")
data class SavedVideoEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val channelTitle: String,
    val durationText: String,
    val durationMs: Long,
    val thumbnailUrl: String,
    val viewsText: String,
    val isLive: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_channels")
data class SavedChannelEntity(
    @PrimaryKey
    val channelTitle: String,
    val savedAt: Long = System.currentTimeMillis(),
    // --- new in DB v3: new-video notifications ---
    @ColumnInfo(defaultValue = "''")
    val channelId: String = "",          // UCxxxxxxxxxxxxxxxxxxxxxx (needed for the RSS feed)
    @ColumnInfo(defaultValue = "''")
    val seedVideoId: String = "",        // a video of this channel, used to discover channelId if missing
    @ColumnInfo(defaultValue = "''")
    val lastSeenVideoId: String = "",    // newest video we already know about
    @ColumnInfo(defaultValue = "1")
    val notifyNewVideos: Boolean = true
)
````


## קובץ 11/16: `app/src/main/java/com/example/data/local/SavedDaos.kt` — **החלף**

````kotlin
package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 30")
    fun getAllHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()
}

@Dao
interface SavedMediaDao {
    // Saved Videos
    @Query("SELECT * FROM saved_videos ORDER BY savedAt DESC")
    fun getAllSavedVideos(): Flow<List<SavedVideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_videos WHERE id = :id LIMIT 1)")
    fun isVideoSaved(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVideo(video: SavedVideoEntity)

    @Query("DELETE FROM saved_videos WHERE id = :id")
    suspend fun deleteSavedVideo(id: String)

    // Saved Channels
    @Query("SELECT * FROM saved_channels ORDER BY savedAt DESC")
    fun getAllSavedChannels(): Flow<List<SavedChannelEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_channels WHERE channelTitle = :channelTitle LIMIT 1)")
    fun isChannelSaved(channelTitle: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChannel(channel: SavedChannelEntity)

    @Query("DELETE FROM saved_channels WHERE channelTitle = :channelTitle")
    suspend fun deleteSavedChannel(channelTitle: String)

    // New-video notifications
    @Query("SELECT * FROM saved_channels")
    suspend fun getSavedChannelsOnce(): List<SavedChannelEntity>

    @Query("UPDATE saved_channels SET channelId = :channelId WHERE channelTitle = :channelTitle")
    suspend fun updateChannelId(channelTitle: String, channelId: String)

    @Query("UPDATE saved_channels SET lastSeenVideoId = :videoId WHERE channelTitle = :channelTitle")
    suspend fun updateLastSeen(channelTitle: String, videoId: String)

    @Query("UPDATE saved_channels SET notifyNewVideos = :enabled WHERE channelTitle = :channelTitle")
    suspend fun setChannelNotify(channelTitle: String, enabled: Boolean)
}
````


## קובץ 12/16: `app/src/main/java/com/example/data/local/AppDatabase.kt` — **החלף**

````kotlin
package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        SearchHistoryEntity::class,
        SavedVideoEntity::class,
        SavedChannelEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savedMediaDao(): SavedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Keeps the user's library: only adds columns to saved_channels
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_channels ADD COLUMN channelId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_channels ADD COLUMN seedVideoId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_channels ADD COLUMN lastSeenVideoId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_channels ADD COLUMN notifyNewVideos INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tunevault_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
````


## קובץ 13/16: `app/src/main/java/com/example/network/YouTubeEngine.kt` — **החלף**

````kotlin
package com.example.network

import android.util.Log
import com.example.data.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class StreamInfo(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val durationText: String,
    val thumbnailUrl: String,
    val streamUrl: String,
    val isLive: Boolean = false,
    val approxSizeBytes: Long = 0L,
    val audioBitrateKbps: Int = 160
)

object YouTubeEngine {
    private const val TAG = "YouTubeEngine"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val VIDEO_ID_PATTERNS = listOf(
        Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|/live/)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("^([a-zA-Z0-9_-]{11})$")
    )

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        for (pattern in VIDEO_ID_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    fun isPlaylistUrl(input: String): Boolean {
        return input.contains("list=")
    }

    fun extractPlaylistId(input: String): String? {
        val pattern = Pattern.compile("list=([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Fetches search suggestions matching YouTube autocomplete.
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val suggestionsArray = jsonArray.optJSONArray(1) ?: return@withContext emptyList()
                val results = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    results.add(suggestionsArray.getString(i))
                }
                results
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching suggestions", e)
            emptyList()
        }
    }

    /**
     * Executes YouTube search returning rich video cards.
     */
    suspend fun searchVideos(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // If the query is directly a YouTube URL or video ID, resolve it directly
        val directId = extractVideoId(trimmed)
        if (directId != null) {
            val basic = resolveBasicInfo(directId)
            if (basic != null) {
                return@withContext listOf(basic)
            }
        }

        // Search via YouTube Innertube public endpoint
        try {
            val endpoint = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
            val jsonPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20230526.09.00")
                        put("hl", "iw")
                        put("gl", "IL")
                    })
                })
                put("query", trimmed)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val items = parseSearchResponse(bodyString)
                    if (items.isNotEmpty()) {
                        return@withContext items
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Innertube search failed, falling back", e)
        }

        // Secondary fallback search via Invidious public instance
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val invidiousUrl = "https://invidious.nerdvpn.de/api/v1/search?q=$encoded&type=video"
            val request = Request.Builder()
                .url(invidiousUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val jsonArray = JSONArray(bodyString)
                    val list = mutableListOf<SearchResultItem>()
                    for (i in 0 until jsonArray.length().coerceAtMost(25)) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("videoId")
                        if (id.isNotBlank()) {
                            val title = obj.optString("title", "ללא כותרת")
                            val author = obj.optString("author", "ערוץ YouTube")
                            val lengthSeconds = obj.optLong("lengthSeconds", 0L)
                            val isLive = obj.optBoolean("liveNow", false)
                            val durationText = if (isLive) "LIVE" else formatDuration(lengthSeconds * 1000L)
                            val views = obj.optLong("viewCount", 0L)
                            val viewsFormatted = if (views > 0) formatViews(views) else "צפיות"
                            val publishedText = obj.optString("publishedText", "לאחרונה")
                            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                            list.add(
                                SearchResultItem(
                                    id = id,
                                    title = title,
                                    channelTitle = author,
                                    durationText = durationText,
                                    durationMs = lengthSeconds * 1000L,
                                    thumbnailUrl = thumb,
                                    viewsText = viewsFormatted,
                                    uploadedText = publishedText,
                                    isLive = isLive,
                                    channelId = obj.optString("authorId", "")
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Secondary search fallback failed", e)
        }

        emptyList()
    }

    private fun parseSearchResponse(jsonString: String): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return list

            for (i in 0 until contents.length()) {
                val itemSection = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                val sectionContents = itemSection?.optJSONArray("contents") ?: continue

                for (j in 0 until sectionContents.length()) {
                    val videoRenderer = sectionContents.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                    val videoId = videoRenderer.optString("videoId")
                    if (videoId.isBlank()) continue

                    val title = videoRenderer.optJSONObject("title")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: videoRenderer.optJSONObject("title")?.optString("simpleText", "סרטון") ?: "סרטון"

                    val channel = videoRenderer.optJSONObject("ownerText")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: videoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: "ערוץ יוטיוב"

                    val channelId = videoRenderer.optJSONObject("ownerText")
                        ?.optJSONArray("runs")?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId").orEmpty()

                    val isLive = videoRenderer.optJSONArray("badges")?.let { badges ->
                        var live = false
                        for (b in 0 until badges.length()) {
                            val label = badges.optJSONObject(b)?.optJSONObject("metadataBadgeRenderer")?.optString("label") ?: ""
                            if (label.contains("LIVE", ignoreCase = true) || label.contains("חי")) {
                                live = true
                            }
                        }
                        live
                    } ?: false

                    val durationText = if (isLive) "LIVE" else {
                        videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: "00:00"
                    }
                    val durationMs = parseDurationTextToMs(durationText)

                    val viewsText = videoRenderer.optJSONObject("viewCountText")?.optString("simpleText")
                        ?: videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText") ?: ""

                    val publishedText = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""
                    val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    list.add(
                        SearchResultItem(
                            id = videoId,
                            title = title,
                            channelTitle = channel,
                            durationText = durationText,
                            durationMs = durationMs,
                            thumbnailUrl = thumb,
                            viewsText = viewsText,
                            uploadedText = publishedText,
                            isLive = isLive,
                            channelId = channelId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing innertube search json", e)
        }
        return list
    }

    /**
     * Instant metadata for a pasted link. Uses YouTube oEmbed (one tiny request, no tokens needed),
     * so the card appears immediately. Duration is unknown at this stage.
     */
    suspend fun resolveBasicInfo(videoId: String): SearchResultItem? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/oembed?format=json&url=" +
                URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                SearchResultItem(
                    id = videoId,
                    title = json.optString("title", "סרטון"),
                    channelTitle = json.optString("author_name", "ערוץ יוטיוב"),
                    durationText = "--:--",
                    durationMs = 0L,
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    viewsText = "קישור ישיר",
                    uploadedText = "יוטיוב",
                    isLive = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "oEmbed failed", e)
            null
        }
    }

    data class FeedEntry(val videoId: String, val title: String, val published: String)

    private val CHANNEL_ID_REGEXES = listOf(
        Pattern.compile("/channel/(UC[0-9A-Za-z_-]{22})"),
        Pattern.compile("\"externalId\":\"(UC[0-9A-Za-z_-]{22})\""),
        Pattern.compile("\"channelId\":\"(UC[0-9A-Za-z_-]{22})\""),
        Pattern.compile("channel_id=(UC[0-9A-Za-z_-]{22})")
    )

    /** video -> oEmbed author_url -> channel page -> channelId (UC...). */
    suspend fun resolveChannelIdFromVideo(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val oembed = "https://www.youtube.com/oembed?format=json&url=" +
                URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val authorUrl = client.newCall(
                Request.Builder().url(oembed).header("User-Agent", "Mozilla/5.0").build()
            ).execute().use { r ->
                if (!r.isSuccessful) return@use null
                JSONObject(r.body?.string() ?: return@use null).optString("author_url", "")
            }
            if (authorUrl.isNullOrBlank()) return@withContext null

            // Already a /channel/UC... url
            for (p in CHANNEL_ID_REGEXES.take(1)) {
                val m = p.matcher(authorUrl)
                if (m.find()) return@withContext m.group(1)
            }

            // @handle url -> read the page
            val html = client.newCall(
                Request.Builder().url(authorUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cookie", "CONSENT=YES+1; SOCS=CAI")
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) return@use ""
                r.body?.string().orEmpty()
            }
            for (p in CHANNEL_ID_REGEXES) {
                val m = p.matcher(html)
                if (m.find()) return@withContext m.group(1)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "resolveChannelIdFromVideo failed", e)
            null
        }
    }

    /** Latest ~15 uploads of a channel from the public RSS feed (newest first). */
    suspend fun fetchChannelFeed(channelId: String): List<FeedEntry> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val xml = client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                r.body?.string().orEmpty()
            }
            val entryPattern = Pattern.compile(
                "<entry>.*?<yt:videoId>(.+?)</yt:videoId>.*?<title>(.*?)</title>.*?<published>(.+?)</published>",
                Pattern.DOTALL
            )
            val m = entryPattern.matcher(xml)
            val list = mutableListOf<FeedEntry>()
            while (m.find()) {
                list.add(FeedEntry(m.group(1)!!.trim(), decodeXml(m.group(2)!!.trim()), m.group(3)!!.trim()))
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "fetchChannelFeed failed", e)
            emptyList()
        }
    }

    private fun decodeXml(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    /**
     * Resolves metadata and direct audio stream info for a video ID or live stream.
     */
    suspend fun resolveVideoDetails(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            // First attempt: YouTube Innertube player endpoint
            val playerUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "18.19.35")
                        put("hl", "iw")
                        put("gl", "IL")
                    })
                })
                put("videoId", videoId)
            }

            val request = Request.Builder()
                .url(playerUrl)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "com.google.android.youtube/18.19.35 (Linux; U; Android 13)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = parsePlayerResponse(videoId, body)
                    if (parsed != null) return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube player call failed", e)
        }

        // Secondary resolver: Invidious audio stream endpoint
        try {
            val invidiousUrl = "https://invidious.nerdvpn.de/api/v1/videos/$videoId"
            val req = Request.Builder()
                .url(invidiousUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val title = root.optString("title", "שיר מיוטיוב")
                    val author = root.optString("author", "אמן")
                    val lengthSeconds = root.optLong("lengthSeconds", 0L)
                    val isLive = root.optBoolean("liveNow", false)

                    // Find M4A adaptive audio stream
                    val adaptiveFormats = root.optJSONArray("adaptiveFormats")
                    var bestAudioUrl: String? = null
                    var bestBitrate = 128
                    var contentLen = 0L

                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val type = format.optString("type", "")
                            if (type.contains("audio/mp4") || format.optString("container") == "m4a") {
                                val url = format.optString("url")
                                val bitrate = format.optInt("bitrate", 0) / 1000
                                if (url.isNotBlank() && (bestAudioUrl == null || bitrate >= bestBitrate)) {
                                    bestAudioUrl = url
                                    bestBitrate = bitrate.coerceAtLeast(128)
                                    contentLen = format.optLong("clen", 0L)
                                }
                            }
                        }
                    }

                    // Fallback to HLS playlist for live streams
                    if (isLive && bestAudioUrl == null) {
                        bestAudioUrl = root.optString("hlsUrl")
                    }

                    if (!bestAudioUrl.isNullOrBlank()) {
                        return@withContext StreamInfo(
                            videoId = videoId,
                            title = title,
                            channel = author,
                            durationMs = lengthSeconds * 1000L,
                            durationText = if (isLive) "LIVE" else formatDuration(lengthSeconds * 1000L),
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            streamUrl = bestAudioUrl,
                            isLive = isLive,
                            approxSizeBytes = contentLen,
                            audioBitrateKbps = bestBitrate
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invidious stream resolver failed", e)
        }

        null
    }

    private fun parsePlayerResponse(videoId: String, jsonString: String): StreamInfo? {
        return try {
            val root = JSONObject(jsonString)
            val videoDetails = root.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "רצועת שמע")
            val author = videoDetails.optString("author", "יוצר יוטיוב")
            val lengthSeconds = videoDetails.optLong("lengthSeconds", 0L)
            val isLive = videoDetails.optBoolean("isLive", false) || videoDetails.optBoolean("isLiveContent", false)

            val streamingData = root.optJSONObject("streamingData") ?: return null

            // Look for audio/mp4 (M4A) streams in adaptiveFormats
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            var selectedUrl: String? = null
            var selectedBitrate = 160
            var approxSize = 0L

            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("mimeType", "")
                    if (mimeType.contains("audio/mp4")) {
                        val url = format.optString("url")
                        val bitrate = format.optInt("bitrate", 0) / 1000
                        val contentLength = format.optLong("contentLength", 0L)
                        if (url.isNotBlank()) {
                            if (selectedUrl == null || bitrate >= selectedBitrate) {
                                selectedUrl = url
                                selectedBitrate = bitrate.coerceAtLeast(128)
                                approxSize = contentLength
                            }
                        }
                    }
                }
            }

            // If live stream, look for HLS manifest URL
            if (isLive && selectedUrl == null) {
                selectedUrl = streamingData.optString("hlsManifestUrl")
            }

            if (selectedUrl.isNullOrBlank()) return null

            StreamInfo(
                videoId = videoId,
                title = title,
                channel = author,
                durationMs = lengthSeconds * 1000L,
                durationText = if (isLive) "LIVE" else formatDuration(lengthSeconds * 1000L),
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                streamUrl = selectedUrl,
                isLive = isLive,
                approxSizeBytes = approxSize,
                audioBitrateKbps = selectedBitrate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing player response", e)
            null
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

    private fun parseDurationTextToMs(text: String): Long {
        if (text.isBlank() || text.equals("LIVE", ignoreCase = true)) return 0L
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> 0L
        }
    }

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM צפיות", views / 1_000_000f)
            views >= 1_000 -> String.format("%.1fK צפיות", views / 1_000f)
            else -> "$views צפיות"
        }
    }
}
````


## קובץ 14/16: `app/src/main/java/com/example/download/DownloadManager.kt` — **החלף**

````kotlin
package com.example.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.App
import com.example.data.local.AppDatabase
import com.example.data.local.TrackEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.TrimRange
import com.example.data.model.UserSettings
import com.example.notify.Notifier
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(private val context: Context) {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val database = AppDatabase.getDatabase(context)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val liveStopRequested = ConcurrentHashMap.newKeySet<String>()

    private val musicDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), "Music")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun startDownload(
        videoId: String,
        title: String,
        channel: String,
        thumbnailUrl: String,
        isLive: Boolean = false,
        trimRange: TrimRange? = null,
        settings: UserSettings
    ): String {
        val taskId = UUID.randomUUID().toString()
        val newTask = DownloadTask(
            id = taskId,
            videoId = videoId,
            title = title,
            channel = channel,
            thumbnailUrl = thumbnailUrl,
            isLiveStream = isLive,
            trimRange = trimRange,
            status = DownloadStatus.QUEUED,
            hebrewStatusText = "נוסף לתור ההורדות..."
        )

        _tasks.update { listOf(newTask) + it }

        // Keep the process alive + show "download started" notification
        DownloadService.start(context)
        Notifier.downloadStarted(context, taskId, title)

        val job = scope.launch {
            executeDownload(taskId, videoId, title, channel, thumbnailUrl, isLive, trimRange, settings)
        }
        activeJobs[taskId] = job
        return taskId
    }

    private suspend fun executeDownload(
        taskId: String,
        videoId: String,
        title: String,
        channel: String,
        thumbnailUrl: String,
        isLive: Boolean,
        trimRange: TrimRange?,
        settings: UserSettings
    ) {
        var lastPercent = 0

        fun log(msg: String) {
            _tasks.update { list ->
                list.map { if (it.id == taskId) it.copy(logs = it.logs + msg) else it }
            }
        }

        fun updateState(
            status: DownloadStatus,
            progress: Int = -1,
            speed: String? = null,
            downloadedSize: String? = null,
            totalSize: String? = null,
            hebrewStatus: String? = null,
            recordedSec: Long? = null
        ) {
            if (progress >= 0) lastPercent = progress
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) {
                        it.copy(
                            status = status,
                            progressPercent = if (progress >= 0) progress else it.progressPercent,
                            downloadSpeedText = speed ?: it.downloadSpeedText,
                            downloadedSizeText = downloadedSize ?: it.downloadedSizeText,
                            totalSizeText = totalSize ?: it.totalSizeText,
                            hebrewStatusText = hebrewStatus ?: it.hebrewStatusText,
                            recordedDurationSeconds = recordedSec ?: it.recordedDurationSeconds
                        )
                    } else it
                }
            }
            // ---- notifications follow the task state ----
            when (status) {
                DownloadStatus.FAILED ->
                    Notifier.downloadFailed(context, taskId, title, hebrewStatus ?: "שגיאה")
                DownloadStatus.COMPLETED -> Unit // handled at the end of the download
                DownloadStatus.RECORDING_LIVE ->
                    Notifier.downloadProgress(context, taskId, title, -1, hebrewStatus ?: "מקליט שידור חי...")
                DownloadStatus.DOWNLOADING ->
                    Notifier.downloadProgress(context, taskId, title, lastPercent, hebrewStatus ?: "מוריד...")
                else ->
                    Notifier.downloadProgress(context, taskId, title, if (status == DownloadStatus.FINISHING) 100 else -1, hebrewStatus ?: "")
            }
        }

        try {
            log("[yt-dlp] מתחיל תהליך הורדה עבור: $title")

            // Network check
            if (!settings.allowCellularDownloads && !isWifiConnected()) {
                log("[שגיאה] ההגדרה חוסמת הורדה ברשת סלולרית. אנא התחבר ל-Wi-Fi או אפשר נתונים בהגדרות.")
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: נדרש חיבור Wi-Fi לפי ההגדרות")
                return
            }

            val networkType = if (isWifiConnected()) "Wi-Fi" else "נתונים סלולריים"
            log("[רשת] חיבור פעיל: $networkType")

            updateState(DownloadStatus.CONNECTING, hebrewStatus = "מכין את מנוע yt-dlp...")

            val app = context.applicationContext as App
            if (!app.awaitYtdlp()) {
                log("[שגיאה] yt-dlp לא הופעל. בדוק שה-APK נבנה עם ספריות המנוע (abiFilters / extractNativeLibs).")
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: מנוע yt-dlp לא נטען")
                return
            }

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9א-ת _-]"), "").trim().ifEmpty { "Audio_$videoId" }
            val rawBase = File(musicDir, "${sanitizedTitle}_raw_${System.currentTimeMillis()}")

            updateState(DownloadStatus.CONNECTING, hebrewStatus = "מתחבר ליוטיוב ומחלץ שמע...")

            var rawFile: File? = null
            try {
                rawFile = runYtDlp(taskId, videoId, isLive, rawBase, ::log, ::updateState)
            } catch (e: YoutubeDLException) {
                // Most common cause of failures: yt-dlp is outdated. Update once and retry.
                log("[yt-dlp] הורדה נכשלה: ${shortError(e)}")
                log("[yt-dlp] מעדכן את yt-dlp ומנסה שוב...")
                updateState(DownloadStatus.CONNECTING, hebrewStatus = "מעדכן את yt-dlp ומנסה שוב...")
                app.updateYtdlp()
                rawFile = runYtDlp(taskId, videoId, isLive, rawBase, ::log, ::updateState)
            }

            if (rawFile == null || !rawFile.exists() || rawFile.length() == 0L) {
                updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה: קובץ השמע לא נוצר")
                return
            }

            val ext = rawFile.extension.ifEmpty { "m4a" }
            var finalFile: File = rawFile
            val isTrimmed = trimRange != null && trimRange.endMs > trimRange.startMs && ext in TRIMMABLE_EXT

            if (trimRange != null && trimRange.endMs > trimRange.startMs && !isTrimmed) {
                log("[חיתוך] הפורמט .$ext לא נתמך לחיתוך, נשמר הקובץ המלא.")
            }

            if (isTrimmed && trimRange != null) {
                updateState(DownloadStatus.TRIMMING, hebrewStatus = "חותך מקטע שמע נבחר...")
                log("[חיתוך] מפעיל MediaMuxer לחיתוך מקטע (${formatDuration(trimRange.startMs)} עד ${formatDuration(trimRange.endMs)})...")
                val trimmedFile = File(musicDir, "${sanitizedTitle}_${System.currentTimeMillis()}.$ext")

                val success = AudioTrimmer.trimM4A(
                    sourceFile = rawFile,
                    destinationFile = trimmedFile,
                    startMs = trimRange.startMs,
                    endMs = trimRange.endMs
                )

                if (success && trimmedFile.exists() && trimmedFile.length() > 0L) {
                    rawFile.delete()
                    finalFile = trimmedFile
                    log("[חיתוך] המקטע נחתך ונשמר בהצלחה ללא איבוד איכות!")
                } else {
                    log("[חיתוך] החיתוך נכשל, נשמר הקובץ המלא.")
                }
            }

            if (finalFile == rawFile) {
                val cleanFile = File(musicDir, "${sanitizedTitle}_${System.currentTimeMillis()}.$ext")
                if (rawFile.renameTo(cleanFile)) finalFile = cleanFile
            }

            updateState(DownloadStatus.FINISHING, progress = 100, hebrewStatus = "שומר בספרייה...")

            val fullDurationMs = fileDurationMs(finalFile)
            val track = TrackEntity(
                id = videoId + "_" + System.currentTimeMillis(),
                title = title,
                artist = channel,
                durationMs = if (isTrimmed && trimRange != null) (trimRange.endMs - trimRange.startMs) else fullDurationMs,
                filePath = finalFile.absolutePath,
                thumbnailUrl = thumbnailUrl,
                fileSizeBytes = finalFile.length(),
                isLiveCapture = isLive,
                isTrimmed = isTrimmed,
                trimStartMs = trimRange?.startMs ?: 0L,
                trimEndMs = trimRange?.endMs ?: 0L
            )

            database.trackDao().insertTrack(track)

            log("[הצלחה] הקובץ מוכן ונשמר בספריית המוזיקה!")
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            progressPercent = 100,
                            hebrewStatusText = "ההורדה הושלמה בהצלחה!",
                            filePath = finalFile.absolutePath
                        )
                    } else it
                }
            }
            Notifier.downloadFinished(context, taskId, title)

        } catch (e: YoutubeDL.CanceledException) {
            log("[ביטול] ההורדה בוטלה.")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "yt-dlp error", e)
            val reason = friendlyError(e)
            log("[שגיאה] ${shortError(e)}")
            updateState(DownloadStatus.FAILED, hebrewStatus = reason)
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            log("[שגיאה קריטית] ${e.localizedMessage ?: "שגיאה בלתי צפויה"}")
            updateState(DownloadStatus.FAILED, hebrewStatus = "שגיאה במהלך ההורדה")
        } finally {
            activeJobs.remove(taskId)
            liveStopRequested.remove(taskId)
            if (activeJobs.isEmpty()) DownloadService.stop(context)
        }
    }

    /**
     * Runs yt-dlp itself (extraction + download). Returns the downloaded file, or null.
     * yt-dlp is kept up to date, so it keeps working when YouTube changes its internals.
     */
    private suspend fun runYtDlp(
        taskId: String,
        videoId: String,
        isLive: Boolean,
        rawBase: File,
        log: (String) -> Unit,
        updateState: (DownloadStatus, Int, String?, String?, String?, String?, Long?) -> Unit
    ): File? {
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            // Prefer native m4a (AAC) so no re-encoding is needed and the trimmer keeps working
            addOption("-f", "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
            addOption("-o", rawBase.absolutePath + ".%(ext)s")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--retries", "5")
            addOption("--socket-timeout", "20")
            if (isLive) {
                addOption("--no-part")
                addOption("--hls-use-mpegts")
            }
        }

        var ticker: Job? = null
        if (isLive) {
            log("[שידור חי] מתחיל הקלטה (yt-dlp)...")
            updateState(DownloadStatus.RECORDING_LIVE, 0, "LIVE", "0 MB", "LIVE", "מקליט שידור חי...", 0L)
            ticker = scope.launch {
                var sec = 0L
                while (isActive) {
                    delay(1000)
                    sec++
                    updateState(
                        DownloadStatus.RECORDING_LIVE, -1, "LIVE", null, "LIVE",
                        "מקליט שידור חי: ${formatDuration(sec * 1000L)}", sec
                    )
                }
            }
        } else {
            updateState(DownloadStatus.DOWNLOADING, 0, null, null, null, "מתחיל להוריד...", null)
        }

        try {
            YoutubeDL.getInstance().execute(request, taskId) { progress, _, line ->
                if (!isLive && progress >= 0f) {
                    val percent = progress.toInt().coerceIn(0, 99)
                    val m = PROGRESS_LINE.find(line)
                    val total = m?.groupValues?.get(1)?.trim()
                    val speed = m?.groupValues?.get(2)?.trim()
                    val downloaded = total?.let { sizeToBytes(it) }?.let { bytesToText((it * percent / 100.0).toLong()) }
                    updateState(
                        DownloadStatus.DOWNLOADING, percent, speed, downloaded, total,
                        "מוריד נתוני שמע: $percent%" + (if (total != null) " מתוך $total" else ""),
                        null
                    )
                }
            }
        } catch (e: YoutubeDL.CanceledException) {
            // For live streams "stop" is a normal ending: keep what was recorded
            if (!(isLive && liveStopRequested.contains(taskId))) throw e
            log("[שידור חי] ההקלטה הופסקה ונשמרה.")
        } finally {
            ticker?.cancel()
        }

        return musicDir.listFiles { f ->
            f.name.startsWith(rawBase.name + ".") &&
                !f.name.endsWith(".part") && !f.name.endsWith(".ytdl")
        }?.maxByOrNull { it.length() }
    }

    fun stopLiveRecording(taskId: String) {
        liveStopRequested.add(taskId)
        YoutubeDL.getInstance().destroyProcessById(taskId)
    }

    fun cancelDownload(taskId: String) {
        liveStopRequested.remove(taskId)
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (e: Exception) {
            Log.w(TAG, "destroyProcess failed", e)
        }
        activeJobs[taskId]?.cancel()
        _tasks.update { it.filterNot { task -> task.id == taskId } }
        Notifier.cancel(context, taskId)
    }

    // ---------------- helpers ----------------

    private fun shortError(e: Exception): String =
        (e.message ?: "שגיאה לא ידועה").lines().lastOrNull { it.isNotBlank() }?.take(200) ?: "שגיאה לא ידועה"

    private fun friendlyError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Sign in to confirm", ignoreCase = true) ||
                msg.contains("not a bot", ignoreCase = true) -> "יוטיוב חוסמת (נדרש אימות). נסה שוב מאוחר יותר"
            msg.contains("Private video", ignoreCase = true) -> "הסרטון פרטי"
            msg.contains("unavailable", ignoreCase = true) -> "הסרטון לא זמין"
            msg.contains("age", ignoreCase = true) && msg.contains("restricted", ignoreCase = true) -> "הסרטון מוגבל גיל"
            msg.contains("Unable to download", ignoreCase = true) ||
                msg.contains("urlopen", ignoreCase = true) -> "בעיית רשת"
            else -> "שגיאה: חילוץ השמע נכשל"
        }
    }

    private fun fileDurationMs(file: File): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (e: Exception) {
        0L
    }

    private fun sizeToBytes(text: String): Long? {
        val m = Regex("([\\d.]+)\\s*([KMG]?i?B)", RegexOption.IGNORE_CASE).find(text.replace("~", "")) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].uppercase().first()) {
            'K' -> 1024.0
            'M' -> 1024.0 * 1024
            'G' -> 1024.0 * 1024 * 1024
            else -> 1.0
        }
        return (n * mult).toLong()
    }

    private fun bytesToText(bytes: Long): String = String.format("%.1f MB", bytes / (1024f * 1024f))

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

    companion object {
        private val TRIMMABLE_EXT = setOf("m4a", "mp4", "aac")

        // "[download]  45.3% of ~  3.42MiB at  1.20MiB/s ETA 00:02"
        private val PROGRESS_LINE = Regex("of\\s+~?\\s*([\\d.]+\\s*[KMG]?i?B)(?:\\s+at\\s+([\\d.]+\\s*[KMG]?i?B/s))?")
    }
}
````


## קובץ 15/16: `app/src/main/java/com/example/viewmodel/MainViewModel.kt` — **החלף**

````kotlin
package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.data.local.AppDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SavedChannelEntity
import com.example.data.local.SavedVideoEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TrackEntity
import com.example.data.model.AudioQuality
import com.example.data.model.DownloadTask
import com.example.data.model.RepeatMode
import com.example.data.model.SearchResultItem
import com.example.data.model.TrimRange
import com.example.data.model.UserSettings
import com.example.download.DownloadManager
import com.example.network.YouTubeEngine
import com.example.service.AudioPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    // One shared instance (also used by notification buttons)
    val downloadManager: DownloadManager = (application as App).downloadManager

    // Navigation Tab (0: Search, 1: Downloads, 2: Library, 3: Settings)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = database.searchHistoryDao().getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedVideos: StateFlow<List<SavedVideoEntity>> = database.savedMediaDao().getAllSavedVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedChannels: StateFlow<List<SavedChannelEntity>> = database.savedMediaDao().getAllSavedChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var suggestionDebounceJob: Job? = null
    private var searchJob: Job? = null

    // Trimmer Dialog state
    private val _trimTarget = MutableStateFlow<SearchResultItem?>(null)
    val trimTarget: StateFlow<SearchResultItem?> = _trimTarget.asStateFlow()

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(0L)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    // User Settings
    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    // Room Database Flows
    val tracks: StateFlow<List<TrackEntity>> = database.trackDao().getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = database.playlistDao().getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Downloads from DownloadManager
    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    // Player Service connection
    private var playerService: AudioPlayerService? = null
    private val _isPlayerBound = MutableStateFlow(false)

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioPlayerService.LocalBinder ?: return
            playerService = localBinder.getService()
            _isPlayerBound.value = true

            playerService?.let { s ->
                viewModelScope.launch { s.currentTrack.collect { _currentTrack.value = it } }
                viewModelScope.launch { s.isPlaying.collect { _isPlaying.value = it } }
                viewModelScope.launch { s.currentPositionMs.collect { _currentPositionMs.value = it } }
                viewModelScope.launch { s.durationMs.collect { _durationMs.value = it } }
                viewModelScope.launch { s.playbackSpeed.collect { _playbackSpeed.value = it } }
                viewModelScope.launch { s.repeatMode.collect { _repeatMode.value = it } }
                viewModelScope.launch { s.isShuffle.collect { _isShuffle.value = it } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            _isPlayerBound.value = false
        }
    }

    init {
        val intent = Intent(application, AudioPlayerService::class.java)
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** A real YouTube link (not just a word that happens to be 11 characters long). */
    private fun isYoutubeLink(text: String): Boolean =
        (text.contains("youtu.be/") || text.contains("youtube.com/")) &&
            YouTubeEngine.extractVideoId(text) != null

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        suggestionDebounceJob?.cancel()

        if (newQuery.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }

        // Pasted link: resolve immediately (no debounce, no suggestions, no search button)
        if (isYoutubeLink(newQuery)) {
            _searchSuggestions.value = emptyList()
            val url = Regex("https?://\\S+").find(newQuery)?.value ?: newQuery.trim()
            executeSearch(url)
            return
        }

        suggestionDebounceJob = viewModelScope.launch {
            delay(250)
            val suggestions = YouTubeEngine.getSearchSuggestions(newQuery)
            _searchSuggestions.value = suggestions
        }
    }

    fun executeSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        _searchQuery.value = q
        _searchSuggestions.value = emptyList()
        _isSearching.value = true

        // Persist to Room search history (links are not worth keeping)
        if (!isYoutubeLink(q)) {
            viewModelScope.launch {
                database.searchHistoryDao().insertSearch(SearchHistoryEntity(query = q))
            }
        }

        // A newer search/paste cancels the older one so stale results never overwrite fresh ones
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = YouTubeEngine.searchVideos(q)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            database.searchHistoryDao().deleteSearch(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            database.searchHistoryDao().clearAllHistory()
        }
    }

    fun toggleSaveVideo(item: SearchResultItem) {
        viewModelScope.launch {
            val isAlreadySaved = savedVideos.value.any { it.id == item.id }
            if (isAlreadySaved) {
                database.savedMediaDao().deleteSavedVideo(item.id)
            } else {
                database.savedMediaDao().saveVideo(
                    SavedVideoEntity(
                        id = item.id,
                        title = item.title,
                        channelTitle = item.channelTitle,
                        durationText = item.durationText,
                        durationMs = item.durationMs,
                        thumbnailUrl = item.thumbnailUrl,
                        viewsText = item.viewsText,
                        isLive = item.isLive
                    )
                )
            }
        }
    }

    /**
     * Saving a channel also turns on "new video" notifications for it
     * (checked every ~15 minutes by ChannelCheckWorker).
     */
    fun toggleSaveChannel(channelTitle: String) {
        if (channelTitle.isBlank()) return
        viewModelScope.launch {
            val dao = database.savedMediaDao()
            val isAlreadySaved = savedChannels.value.any { it.channelTitle == channelTitle }
            if (isAlreadySaved) {
                dao.deleteSavedChannel(channelTitle)
                return@launch
            }

            // Find what we know about this channel from the visible results / saved videos
            val fromResults = _searchResults.value.firstOrNull { it.channelTitle == channelTitle }
            val seedVideoId = fromResults?.id
                ?: savedVideos.value.firstOrNull { it.channelTitle == channelTitle }?.id
                ?: ""
            var channelId = fromResults?.channelId.orEmpty()

            dao.saveChannel(
                SavedChannelEntity(
                    channelTitle = channelTitle,
                    channelId = channelId,
                    seedVideoId = seedVideoId
                )
            )

            // Resolve the channel id + remember the newest video now, so only FUTURE uploads notify
            withContext(Dispatchers.IO) {
                if (channelId.isBlank() && seedVideoId.isNotBlank()) {
                    channelId = YouTubeEngine.resolveChannelIdFromVideo(seedVideoId).orEmpty()
                    if (channelId.isNotBlank()) dao.updateChannelId(channelTitle, channelId)
                }
                if (channelId.isNotBlank()) {
                    YouTubeEngine.fetchChannelFeed(channelId).firstOrNull()?.let {
                        dao.updateLastSeen(channelTitle, it.videoId)
                    }
                }
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        _searchResults.value = emptyList()
    }

    fun openTrimmerDialog(item: SearchResultItem) {
        _trimTarget.value = item
        _trimStartMs.value = 0L
        _trimEndMs.value = if (item.durationMs > 0) item.durationMs else 180000L
    }

    fun closeTrimmerDialog() {
        _trimTarget.value = null
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _trimStartMs.value = startMs
        _trimEndMs.value = endMs
    }

    fun startDownload(item: SearchResultItem, applyTrim: Boolean = false) {
        val trimRange = if (applyTrim) {
            TrimRange(startMs = _trimStartMs.value, endMs = _trimEndMs.value, totalMs = item.durationMs)
        } else null

        downloadManager.startDownload(
            videoId = item.id,
            title = item.title,
            channel = item.channelTitle,
            thumbnailUrl = item.thumbnailUrl,
            isLive = item.isLive,
            trimRange = trimRange,
            settings = _settings.value
        )

        closeTrimmerDialog()
        _currentTab.value = 1
    }

    // Playback Controls
    fun playTrack(track: TrackEntity) {
        playerService?.playTrackList(listOf(track), 0)
    }

    fun playTrackInList(trackList: List<TrackEntity>, index: Int) {
        playerService?.playTrackList(trackList, index)
    }

    fun togglePlayPause() {
        playerService?.togglePlayPause()
    }

    fun seekTo(posMs: Long) {
        playerService?.seekTo(posMs)
    }

    fun rewind10s() {
        playerService?.rewind10Seconds()
    }

    fun forward10s() {
        playerService?.forward10Seconds()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerService?.setPlaybackSpeed(speed)
    }

    fun toggleRepeat() {
        playerService?.toggleRepeatMode()
    }

    fun toggleShuffle() {
        playerService?.toggleShuffle()
    }

    fun playNext() {
        playerService?.playNext()
    }

    fun playPrevious() {
        playerService?.playPrevious()
    }

    fun setFullPlayerOpen(open: Boolean) {
        _isFullPlayerOpen.value = open
    }

    // Settings actions
    fun setAllowCellularDownloads(allow: Boolean) {
        _settings.value = _settings.value.copy(allowCellularDownloads = allow)
    }

    fun setAmoledTheme(isAmoled: Boolean) {
        _settings.value = _settings.value.copy(isAmoledTheme = isAmoled)
    }

    fun setAudioQuality(quality: AudioQuality) {
        _settings.value = _settings.value.copy(audioQuality = quality)
    }

    fun setAutoEmbedCoverArt(embed: Boolean) {
        _settings.value = _settings.value.copy(autoEmbedCoverArt = embed)
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            database.trackDao().deleteTrack(track)
            try {
                val file = java.io.File(track.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // Ignore file delete errors
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            database.playlistDao().insertPlaylist(PlaylistEntity(name = name.trim()))
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            database.playlistDao().addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
        }
    }

    override fun onCleared() {
        if (_isPlayerBound.value) {
            getApplication<Application>().unbindService(serviceConnection)
            _isPlayerBound.value = false
        }
        super.onCleared()
    }
}
````


## קובץ 16/16: `app/src/main/java/com/example/MainActivity.kt` — **החלף**

````kotlin
package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.DownloadStatus
import com.example.notify.Notifier
import com.example.ui.components.AudioTrimmerDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BorderDark
import com.example.ui.theme.ScarletRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TuneVaultTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Android 13+: notifications need a runtime permission
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIncomingShareIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()

            TuneVaultTheme(isAmoled = settings.isAmoledTheme) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        // Tap on a "new video" notification -> open the video card
        val openVideoId = intent?.getStringExtra(Notifier.EXTRA_OPEN_VIDEO_ID)
        if (!openVideoId.isNullOrBlank()) {
            intent?.removeExtra(Notifier.EXTRA_OPEN_VIDEO_ID)
            viewModel.onSearchQueryChanged("https://www.youtube.com/watch?v=$openVideoId")
            viewModel.selectTab(0)
            return
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedText.isNotBlank()) {
                viewModel.onSearchQueryChanged(sharedText)
                viewModel.executeSearch(sharedText)
                viewModel.selectTab(0)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val savedVideos by viewModel.savedVideos.collectAsState()
    val savedChannels by viewModel.savedChannels.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsState()

    val trimTarget by viewModel.trimTarget.collectAsState()
    val trimStartMs by viewModel.trimStartMs.collectAsState()
    val trimEndMs by viewModel.trimEndMs.collectAsState()

    val activeDownloadsCount = downloadTasks.count {
        it.status != DownloadStatus.COMPLETED && it.status != DownloadStatus.FAILED
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Docked MiniPlayer above bottom navigation
                AnimatedVisibility(
                    visible = currentTrack != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    currentTrack?.let { track ->
                        MiniPlayer(
                            track = track,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.playNext() },
                            onClick = { viewModel.setFullPlayerOpen(true) }
                        )
                    }
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.Search, contentDescription = "חיפוש") },
                        label = { Text("חיפוש", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_search_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = {
                            if (activeDownloadsCount > 0) {
                                BadgedBox(badge = { Badge { Text("$activeDownloadsCount") } }) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = "הורדות")
                                }
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = "הורדות")
                            }
                        },
                        label = { Text("הורדות", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_downloads_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "ספרייה") },
                        label = { Text("ספרייה", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_library_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "הגדרות") },
                        label = { Text("הגדרות", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScarletRed,
                            selectedTextColor = ScarletRed,
                            indicatorColor = ScarletRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> SearchScreen(
                    searchQuery = searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onSearch = { viewModel.executeSearch(it) },
                    onClear = { viewModel.clearSearch() },
                    suggestions = searchSuggestions,
                    history = searchHistory,
                    onDeleteHistoryItem = { viewModel.deleteSearchHistoryItem(it) },
                    onClearAllHistory = { viewModel.clearAllSearchHistory() },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    savedVideos = savedVideos,
                    savedChannels = savedChannels,
                    onToggleSaveVideo = { viewModel.toggleSaveVideo(it) },
                    onToggleSaveChannel = { viewModel.toggleSaveChannel(it) },
                    onDownload = { viewModel.startDownload(it, applyTrim = false) },
                    onTrim = { viewModel.openTrimmerDialog(it) }
                )
                1 -> DownloadsScreen(
                    tasks = downloadTasks,
                    onCancelTask = { viewModel.downloadManager.cancelDownload(it) },
                    onStopLiveRecording = { viewModel.downloadManager.stopLiveRecording(it) }
                )
                2 -> LibraryScreen(
                    tracks = tracks,
                    playlists = playlists,
                    onPlayTrack = { list, idx -> viewModel.playTrackInList(list, idx) },
                    onDeleteTrack = { viewModel.deleteTrack(it) },
                    onCreatePlaylist = { viewModel.createPlaylist(it) },
                    onAddTrackToPlaylist = { plId, trId -> viewModel.addTrackToPlaylist(plId, trId) }
                )
                3 -> SettingsScreen(
                    settings = settings,
                    onAllowCellularChanged = { viewModel.setAllowCellularDownloads(it) },
                    onAmoledChanged = { viewModel.setAmoledTheme(it) },
                    onQualityChanged = { viewModel.setAudioQuality(it) },
                    onEmbedCoverArtChanged = { viewModel.setAutoEmbedCoverArt(it) }
                )
            }
        }
    }

    // Audio Trimmer Dialog
    trimTarget?.let { item ->
        AudioTrimmerDialog(
            item = item,
            initialStartMs = trimStartMs,
            initialEndMs = trimEndMs,
            onDismiss = { viewModel.closeTrimmerDialog() },
            onConfirmTrim = { start, end ->
                viewModel.updateTrimRange(start, end)
                viewModel.startDownload(item, applyTrim = true)
            },
            onDownloadFull = {
                viewModel.startDownload(item, applyTrim = false)
            }
        )
    }

    // Full Player Modal Sheet
    if (isFullPlayerOpen && currentTrack != null) {
        FullPlayerSheet(
            track = currentTrack!!,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onRewind10s = { viewModel.rewind10s() },
            onForward10s = { viewModel.forward10s() },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onSetSpeed = { viewModel.setPlaybackSpeed(it) },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onDismiss = { viewModel.setFullPlayerOpen(false) }
        )
    }
}
````
