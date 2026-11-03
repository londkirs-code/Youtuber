package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String, // YouTube Video ID or custom ID
    val title: String,
    val artist: String,
    val durationMs: Long,
    val filePath: String,
    val thumbnailUrl: String,
    val fileSizeBytes: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val isLiveCapture: Boolean = false,
    val isTrimmed: Boolean = false,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val isPartial: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    val videoId: String = ""
)
