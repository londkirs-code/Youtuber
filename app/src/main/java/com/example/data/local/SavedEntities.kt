package com.example.data.local

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
    val savedAt: Long = System.currentTimeMillis()
)
