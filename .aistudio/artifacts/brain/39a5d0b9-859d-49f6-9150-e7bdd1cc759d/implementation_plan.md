# YouTube Audio Extractor & Music Player (TuneVault)

A high-performance, lightweight Android application for searching, extracting, trimming, and downloading audio-only streams from YouTube (including live streams) in pristine M4A format, featuring full cellular/Wi-Fi download freedom, rich notification player controls, fine-grained playback speed adjustment, YouTube-style discovery, and an offline library.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following additional specifications have been added to the architectural plan:
>
> 1. **Cellular & Wi-Fi Download Flexibility**: Downloads work seamlessly over both mobile data and Wi-Fi by default, with a user-toggleable setting in case a user wishes to restrict downloads to Wi-Fi.
> 2. **Complete Notification Player Experience**: Full Android `MediaStyle` notification with active playback controls (Play/Pause, Previous, Next, Seekbar progress, and 10-second Jump Forward / Rewind buttons) that update dynamically without opening the app.
> 3. **Precise Playback Speed Control**: Fine-tuned speed regulator allowing adjustments from 0.25x up to 3.00x in exact 0.05x increments (with pitch preservation) alongside one-tap preset chips (0.75x, 1.0x, 1.25x, 1.5x, 2.0x).
> 4. **YouTube-Style Search**: In-app search with suggestions, history, and rich result cards (views, upload date, channel badge, duration/live indicator).
> 5. **Hebrew yt-dlp Progress Diagnostics**: Real-time progress percentage, transfer speed (MB/s), ETA, and fully localized Hebrew status messages.
> 6. **Live Stream Audio Recording**: Audio capture from live broadcasts with live timer and stop/save functionality.
> 7. **Ultra-Lightweight & Smooth**: Stripped of heavy unused SDKs for instant launch, compact APK size, and 60/120fps UI performance.

---

## 1. Overview & Core Concept

- **What It Does**: Fast YouTube audio downloader and music player supporting live streams, regular videos, and playlists in original M4A, equipped with an interactive audio trimmer, rich notification controls, and exact speed management.
- **Target Audience**: Audio enthusiasts, podcast listeners, students, and language learners who need exact audio control, background listening, and offline access across any network connection.
- **Key Value**: Download anywhere (cellular or Wi-Fi), manage playback entirely from your lockscreen and notification shade, and fine-tune listening speed to the exact decimal.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Search & Instant Download**:
   - YouTube-style search with real-time suggestions and query history.
   - Result cards show thumbnail, duration / `LIVE` badge, channel, view count, and direct download / trim buttons.
   - Download starts immediately on whatever network is active (mobile data or Wi-Fi).
2. **Audio Trimmer (Lossless M4A)**:
   - Dual-thumb range slider with millisecond precision to trim intro/outro before or after downloading.
3. **Live Stream Capture**:
   - Automatic detection of live YouTube broadcasts with dedicated recording interface and live duration counter.
4. **Interactive Notification Player (Complete Controls)**:
   - Android `MediaStyle` notification pinned when active:
     - Album art thumbnail, Track title, and Channel/Artist name.
     - Controls: **Rewind 10s**, **Previous Track**, **Play / Pause**, **Next Track**, **Fast-Forward 10s**.
     - Embedded media seekbar for direct dragging/seeking inside the notification drawer (Android 10+).
5. **Exact Speed Controller in Full Player**:
   - Interactive slider and +/- 0.05x stepper buttons (e.g., `1.00x`, `1.05x`, `1.10x` up to `3.00x`).
   - One-tap quick presets: `0.75x`, `1.00x`, `1.25x`, `1.50x`, `2.00x`.
   - Audio pitch is preserved naturally via Android Media audio playback parameters.
6. **Download Logs with Hebrew yt-dlp Output**:
   - Live percentage bar, download speed, and translated status messages:
     - `הורדה דרך נתונים סלולריים / Wi-Fi`
     - `מחלץ ערוץ שמע M4A מקורי...`
     - `הורדת שמע: 62% (8.4 מתוך 13.5 מ"ב, 4.2 MB/s)`
     - `הטמעת עטיפת אלבום ותגיות שיר...`
     - `הקובץ נשמר בהצלחה בספרייה המקומית!`

### Visual Identity & Theme

- **Aesthetic Direction**: **Obsidian & Scarlet Audio** — High-contrast dark theme optimized for OLED screens with zero light glare.
- **Color Palette**:
  - `Background`: Pure Obsidian (`#08090C`)
  - `Card / Surface`: Carbon Glass (`#13161F`) and Borders (`#222736`)
  - `Primary Accent`: Electric Scarlet (`#FF2A4B`)
  - `Secondary Accent`: Cyan Wave (`#00E5FF`)
  - `Text`: Pure White (`#FFFFFF`) & Silver (`#9AA0A6`)
- **Notification Theme**: Adapts dynamically using Android System Media notification styling with high-res cover art color extraction.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Network Policy (Cellular + Wi-Fi by Default)**
  - *Chosen Approach*: Allow unrestricted downloads over any active network connection (mobile data and Wi-Fi), with an explicit opt-in toggle in Settings to "Restrict to Wi-Fi only" if the user has a limited data plan.
  - *Why*: Eliminates unexpected download blocks while traveling or away from home.
- **Decision 2: Comprehensive MediaStyle Notification Architecture**
  - *Chosen Approach*: Implement a foreground `PlaybackService` supporting `MediaSessionCompat` / Jetpack Media3 with full action actions (Rewind 10s, Fast-Forward 10s, Skip Prev, Skip Next, Play/Pause) and notification action slots.
  - *Why*: Gives users complete control of their playback directly from the lockscreen and notification drawer without having to switch apps.
- **Decision 3: Precise Playback Speed Engine**
  - *Chosen Approach*: Use Android `PlaybackParameters(speed, 1.0f)` with 0.05x granular step calculation.
  - *Why*: Allows students and podcast listeners to set precise speeds (e.g. 1.15x or 1.35x) with flawless voice clarity.
- **Decision 4: Dependency Pruning for Speed & Weight**
  - *Chosen Approach*: Remove heavy unused dependencies (Firebase AI Logic, CameraX, Moshi reflection codegen) to keep the app blazing fast, under a tiny footprint, with instant startup.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TuneVault Mobile App                            │
└────────────────────────────────────────────────────────────────────────┘
          │                                              │
          ▼                                              ▼
┌─────────────────────────────────────┐        ┌─────────────────────────┐
│     YouTube Search & Discovery      │        │   Settings & Network    │
│  - Real-time suggestions & history  │        │  - Cellular / Wi-Fi     │
│  - Rich video cards & LIVE badges   │        │  - Audio Bitrate Presets│
└──────────────────┬──────────────────┘        └────────────┬────────────┘
                   │                                        │
                   ▼                                        ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 Download & Audio Stream Engine                         │
│  - M4A Stream extraction (OkHttp Chunks across Cellular & Wi-Fi)       │
│  - Live Broadcast HLS Capture with live duration counter               │
│  - Lossless MediaMuxer Audio Trimmer                                   │
│  - Real-time Hebrew yt-dlp Status & Percentage Logger                  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│               Background Playback & Notification Engine                │
│  - Android MediaStyle Notification (Seekbar, Skip, 10s Jump, Play)     │
│  - Exact Playback Speed Controller (0.25x - 3.00x in 0.05x steps)      │
│  - MediaSessionCompat for Lockscreen, Bluetooth, and Headset controls  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│               Room Database & Storage (Music Directory)                │
│  - TrackEntity  - PlaylistEntity  - UserSettings (Room/DataStore)      │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **PlaybackState**:
  - `currentTrack: TrackEntity?`
  - `isPlaying: Boolean`, `positionMs: Long`, `durationMs: Long`
  - `playbackSpeed: Float` (0.25f - 3.00f, stepping 0.05f)
  - `repeatMode: OFF | ALL | ONE`, `isShuffle: Boolean`
- **DownloadTask**:
  - `id: String`, `title: String`, `channel: String`, `thumbnailUrl: String`
  - `progressPercent: Int`, `downloadSpeed: String`, `downloadedSizeText: String`
  - `hebrewStatusText: String`, `isLiveStream: Boolean`, `status: State`
- **UserSettings**:
  - `allowCellularDownloads: Boolean = true` (Enabled by default)
  - `audioQuality: BEST_M4A | HIGH_160K | NORMAL_128K`
  - `theme: AMOLED | DARK | SYSTEM`
  - `defaultPlaybackSpeed: Float = 1.0f`
  - `autoEmbedCoverArt: Boolean = true`

---

## 5. Implementation Roadmap (Post-Approval)

1. **Build Configuration & Permissions**:
   - Clean up dependencies in `app/build.gradle.kts`.
   - Setup `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and `INTERNET`.
2. **Foreground Audio Service & Notification**:
   - Implement `AudioPlaybackService` with `MediaStyle` notification, rewind/forward 10s actions, interactive seekbar, and lockscreen integration.
   - Implement accurate playback speed adjustment (0.25x - 3.00x in 0.05x increments).
3. **Download Engine & Hebrew yt-dlp Logger**:
   - Unrestricted network handling (Cellular + Wi-Fi).
   - Live stream HLS recording and M4A video stream extraction.
   - Lossless audio trimming via `MediaMuxer`.
   - Real-time Hebrew status updates.
4. **YouTube Search & Discovery Screen**:
   - YouTube mobile UI layout with instant suggestions, history, and search result cards.
5. **Library, Playlists & Settings**:
   - Offline library with Room database.
   - Deep customization settings screen.
