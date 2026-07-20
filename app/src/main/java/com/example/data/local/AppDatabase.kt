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
        BibleCacheEntity::class,
        PreachCacheEntity::class,
        com.example.notes.data.NotebookEntity::class,
        com.example.notes.data.PageEntity::class,
        com.example.notes.data.PageElementEntity::class,
        com.example.notes.data.IllustrationEntity::class,
        com.example.notes.data.SermonTemplateEntity::class
    ],
    version = 14,
    exportSchema = false
)
@androidx.room.TypeConverters(
    com.example.notes.data.ElementTypeConverter::class,
    com.example.notes.data.PageBackgroundStyleConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDatabaseDao
    abstract fun notebookDao(): com.example.notes.data.NotebookDao
    abstract fun pageDao(): com.example.notes.data.PageDao
    abstract fun pageElementDao(): com.example.notes.data.PageElementDao
    abstract fun illustrationDao(): com.example.notes.data.IllustrationDao
    abstract fun sermonTemplateDao(): com.example.notes.data.SermonTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shepherd_db"
                )
                // TODO: Before production release, replace with a proper Migration
                // if there is real user data to preserve. For now, during rebuild,
                // destructive migration is fine.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
