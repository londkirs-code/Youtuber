package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Immutable
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

@Immutable
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
