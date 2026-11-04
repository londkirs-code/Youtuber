package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.MainActivity
import com.example.data.local.TrackEntity
import com.example.data.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class AudioPlayerService : Service() {
    private val TAG = "AudioPlayerService"
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null

    private var playlist: List<TrackEntity> = emptyList()
    private var currentIndex: Int = -1

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

    private var positionTrackerJob: Job? = null
    private var currentCoverBitmap: Bitmap? = null

    companion object {
        const val CHANNEL_ID = "tunevault_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.example.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.action.PREVIOUS"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_REWIND_10S = "com.example.action.REWIND_10S"
        const val ACTION_FORWARD_10S = "com.example.action.FORWARD_10S"
        const val ACTION_CYCLE_SPEED = "com.example.action.CYCLE_SPEED"
        const val ACTION_STOP = "com.example.action.STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_REWIND_10S -> rewind10Seconds()
            ACTION_FORWARD_10S -> forward10Seconds()
            ACTION_CYCLE_SPEED -> cyclePlaybackSpeed()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "TuneVaultMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { togglePlayPause() }
                override fun onPause() { togglePlayPause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
                override fun onFastForward() { forward10Seconds() }
                override fun onRewind() { rewind10Seconds() }
                override fun onStop() { stopPlayback() }
                override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                    when (action) {
                        ACTION_CYCLE_SPEED -> cyclePlaybackSpeed()
                        ACTION_STOP -> stopPlayback()
                    }
                }
            })
            isActive = true
        }
    }

    fun playTrackList(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        playlist = tracks
        currentIndex = startIndex.coerceIn(0, tracks.lastIndex)
        playTrack(playlist[currentIndex])
    }

    fun playTrack(track: TrackEntity) {
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            var playableFile: File? = File(track.filePath)
            if (playableFile?.exists() != true) {
                val partFile = File(track.filePath + ".part")
                if (partFile.exists() && partFile.length() > 32000L) {
                    playableFile = partFile
                } else {
                    val basePrefix = File(track.filePath).nameWithoutExtension
                    val candidate = File(track.filePath).parentFile?.listFiles { f ->
                        f.name.startsWith(basePrefix) && f.length() > 32000L
                    }?.maxByOrNull { it.length() }
                    if (candidate != null) playableFile = candidate
                }
            }

            if (playableFile == null || !playableFile.exists() || playableFile.length() == 0L) {
                Log.e(TAG, "File does not exist or is empty: ${track.filePath}")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(playableFile.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    applyPlaybackSpeed(_playbackSpeed.value)
                    _isPlaying.value = true
                    _durationMs.value = mp.duration.toLong()
                    startPositionTracker()
                    updateMediaMetadata(track, _durationMs.value, currentCoverBitmap)
                    updateMediaSessionState(PlaybackState.STATE_PLAYING)
                    loadArtworkAndShowNotification(track)
                }
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    _isPlaying.value = false
                    false
                }
                prepareAsync()
            }

            _currentTrack.value = track
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track", e)
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                updateMediaSessionState(PlaybackState.STATE_PAUSED)
            } else {
                player.start()
                _isPlaying.value = true
                updateMediaSessionState(PlaybackState.STATE_PLAYING)
            }
            _currentTrack.value?.let { updateNotification(it) }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
            updateMediaSessionState(if (player.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        }
    }

    fun rewind10Seconds() {
        val newPos = (_currentPositionMs.value - 10000L).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun forward10Seconds() {
        val newPos = (_currentPositionMs.value + 10000L).coerceAtMost(_durationMs.value)
        seekTo(newPos)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = ((speed * 100).toInt() / 100f).coerceIn(0.25f, 3.00f)
        _playbackSpeed.value = clampedSpeed
        applyPlaybackSpeed(clampedSpeed)
    }

    fun cyclePlaybackSpeed() {
        val current = _playbackSpeed.value
        val next = when {
            current < 1.15f -> 1.25f
            current < 1.4f -> 1.50f
            current < 1.8f -> 2.00f
            else -> 1.00f
        }
        setPlaybackSpeed(next)
        updateMediaSessionState(if (_isPlaying.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        _currentTrack.value?.let { updateNotification(it) }
    }

    private fun applyPlaybackSpeed(speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    val isCurrentlyPlaying = player.isPlaying
                    val params = PlaybackParams()
                    params.speed = speed
                    player.playbackParams = params
                    if (!isCurrentlyPlaying && player.isPlaying) {
                        player.pause()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback speed", e)
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        if (_isShuffle.value) {
            currentIndex = playlist.indices.random()
        } else {
            currentIndex = (currentIndex + 1) % playlist.size
        }
        playTrack(playlist[currentIndex])
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        currentIndex = if (currentIndex - 1 < 0) playlist.lastIndex else currentIndex - 1
        playTrack(playlist[currentIndex])
    }

    private fun handleTrackCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                mediaPlayer?.start()
                _isPlaying.value = true
            }
            RepeatMode.ALL -> playNext()
            RepeatMode.OFF -> {
                if (currentIndex < playlist.lastIndex) {
                    playNext()
                } else {
                    _isPlaying.value = false
                    updateMediaSessionState(PlaybackState.STATE_STOPPED)
                }
            }
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = serviceScope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPositionMs.value = player.currentPosition.toLong()
                    }
                }
                delay(300)
            }
        }
    }

    private fun updateMediaSessionState(state: Int) {
        val speedFormatted = String.format("%.2fx", _playbackSpeed.value).replace(".00", "")
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_CYCLE_SPEED,
                    "מהירות: $speedFormatted",
                    android.R.drawable.ic_menu_recent_history
                ).build()
            )
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_STOP,
                    "סגור נגן",
                    android.R.drawable.ic_menu_close_clear_cancel
                ).build()
            )
            .setState(state, _currentPositionMs.value, _playbackSpeed.value)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMediaMetadata(track: TrackEntity, durationMs: Long, cover: Bitmap?) {
        val metaBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, durationMs)
        if (cover != null) {
            metaBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
            metaBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, cover)
        }
        mediaSession?.setMetadata(metaBuilder.build())
    }

    private fun loadArtworkAndShowNotification(track: TrackEntity) {
        serviceScope.launch {
            currentCoverBitmap = withContext(Dispatchers.IO) {
                try {
                    if (track.thumbnailUrl.isNotBlank()) {
                        val stream = URL(track.thumbnailUrl).openStream()
                        BitmapFactory.decodeStream(stream)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            updateMediaMetadata(track, _durationMs.value, currentCoverBitmap)
            updateNotification(track)
        }
    }

    private fun updateNotification(track: TrackEntity) {
        val notification = buildNotification(track)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(track: TrackEntity): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_REWIND_10S },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = PendingIntent.getService(
            this,
            5,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_FORWARD_10S },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedIntent = PendingIntent.getService(
            this,
            6,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_CYCLE_SPEED },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = PendingIntent.getService(
            this,
            7,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (_isPlaying.value) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val speedFormatted = String.format("%.2fx", _playbackSpeed.value).replace(".00", "")

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText("Youtuber • ${(track.durationMs / 60000)}:${((track.durationMs % 60000) / 1000).toString().padStart(2, '0')}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(_isPlaying.value)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        currentCoverBitmap?.let {
            builder.setLargeIcon(it)
        }

        // Actions: [Play/Pause, Speed, Close, Previous, Next]
        // Primary compact view actions: 0 (Play/Pause), 1 (Speed), 2 (Close)
        builder.addAction(Notification.Action.Builder(playPauseIcon, if (_isPlaying.value) "השהה" else "נגן", playPauseIntent).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_recent_history, speedFormatted, speedIntent).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "סגור", closeIntent).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "הקודם", prevIntent).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "הבא", nextIntent).build())

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        builder.style = mediaStyle
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "נגן שמע Youtuber",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "השמעת שמע והודעות שליטה ברקע"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun stopPlayback() {
        positionTrackerJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mediaPlayer", e)
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentTrack.value = null
        _currentPositionMs.value = 0L
        updateMediaSessionState(PlaybackState.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        positionTrackerJob?.cancel()
        mediaPlayer?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
