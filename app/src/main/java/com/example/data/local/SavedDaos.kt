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
