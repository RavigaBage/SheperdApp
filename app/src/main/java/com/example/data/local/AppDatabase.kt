package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShepherdFileEntity::class,
        CategoryEntity::class,
        HistoryEntity::class,
        SermonEntity::class,
        SeriesEntity::class,
        ScriptureTagEntity::class,
        BookmarkEntity::class,
        SermonCalendarEntity::class,
        PreachingLogEntity::class,
        VerseUsageEntity::class,
        PreachCacheEntity::class,
        BibleCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDatabaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shepherd_db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
