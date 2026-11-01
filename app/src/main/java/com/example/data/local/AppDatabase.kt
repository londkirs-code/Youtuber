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
