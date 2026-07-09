package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDatabaseDao {

    // --- ShepherdFile Queries ---
    @Query("SELECT * FROM shepherd_file")
    fun getAllFilesFlow(): Flow<List<ShepherdFileEntity>>

    @Query("SELECT * FROM shepherd_file WHERE categoryId = :catId")
    fun getFilesByCategoryFlow(catId: String): Flow<List<ShepherdFileEntity>>

    @Query("SELECT * FROM shepherd_file WHERE id = :fileId")
    suspend fun getFileById(fileId: String): ShepherdFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ShepherdFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ShepherdFileEntity>)

    @Delete
    suspend fun deleteFile(file: ShepherdFileEntity)

    @Query("DELETE FROM shepherd_file WHERE id = :fileId")
    suspend fun deleteFileById(fileId: String)


    // --- Category Queries ---
    @Query("SELECT * FROM category ORDER BY createdAt ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :catId")
    suspend fun getCategoryById(catId: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(cat: CategoryEntity)

    @Delete
    suspend fun deleteCategory(cat: CategoryEntity)


    // --- History Queries ---
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity)

    @Delete
    suspend fun deleteHistory(entry: HistoryEntity)


    // --- Sermon Queries ---
    @Query("SELECT * FROM sermon ORDER BY datePreached DESC")
    fun getAllSermonsFlow(): Flow<List<SermonEntity>>

    @Query("SELECT * FROM sermon WHERE seriesId = :sId ORDER BY datePreached ASC")
    fun getSermonsBySeriesFlow(sId: String): Flow<List<SermonEntity>>

    @Query("SELECT * FROM sermon WHERE id = :sermonId")
    suspend fun getSermonById(sermonId: String): SermonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSermon(sermon: SermonEntity)

    @Query("DELETE FROM sermon WHERE id = :sermonId")
    suspend fun deleteSermon(sermonId: String)


    // --- Series Queries ---
    @Query("SELECT * FROM series ORDER BY createdAt DESC")
    fun getAllSeriesFlow(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :seriesId")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: SeriesEntity)

    @Delete
    suspend fun deleteSeries(series: SeriesEntity)


    // --- Scripture Tag Queries ---
    @Query("SELECT * FROM scripture_tag WHERE fileId = :fileId")
    fun getScriptureTagsForFileFlow(fileId: String): Flow<List<ScriptureTagEntity>>

    @Query("SELECT * FROM scripture_tag")
    fun getAllScriptureTagsFlow(): Flow<List<ScriptureTagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptureTag(tag: ScriptureTagEntity)

    @Query("DELETE FROM scripture_tag WHERE id = :tagId")
    suspend fun deleteScriptureTag(tagId: String)


    // --- Bookmark Queries ---
    @Query("SELECT * FROM bookmark ORDER BY pinnedOrder ASC")
    fun getAllBookmarksFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark WHERE fileId = :fileId")
    suspend fun getBookmarkByFileId(fileId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE fileId = :fileId")
    suspend fun deleteBookmarkByFileId(fileId: String)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)


    // --- Sermon Calendar ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSermonCalendar(event: SermonCalendarEntity): Long

    @Update
    suspend fun updateSermonCalendar(event: SermonCalendarEntity)

    @Delete
    suspend fun deleteSermonCalendar(event: SermonCalendarEntity)

    @Query("SELECT * FROM sermon_calendar ORDER BY scheduledDateMs ASC")
    fun getAllSermonCalendarFlow(): Flow<List<SermonCalendarEntity>>

    @Query("SELECT * FROM sermon_calendar WHERE scheduledDateMs BETWEEN :startMs AND :endMs")
    fun getSermonCalendarEventsBetweenFlow(startMs: Long, endMs: Long): Flow<List<SermonCalendarEntity>>

    @Query("SELECT * FROM sermon_calendar WHERE scheduledDateMs >= :nowMs ORDER BY scheduledDateMs ASC LIMIT 5")
    fun getSermonCalendarUpcomingFlow(nowMs: Long): Flow<List<SermonCalendarEntity>>

    @Query("SELECT * FROM sermon_calendar WHERE id = :id")
    suspend fun getSermonCalendarById(id: Int): SermonCalendarEntity?

    @Query("UPDATE sermon_calendar SET notificationJobId = :jobId WHERE id = :eventId")
    suspend fun updateSermonCalendarJobId(eventId: Int, jobId: String)


    // --- Preaching Log ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreachingLog(log: PreachingLogEntity)

    @Query("SELECT * FROM preaching_log ORDER BY datePreachedMs DESC")
    fun getAllPreachingLogsFlow(): Flow<List<PreachingLogEntity>>

    @Query("SELECT * FROM preaching_log WHERE datePreachedMs BETWEEN :startMs AND :endMs")
    fun getPreachingLogsBetweenFlow(startMs: Long, endMs: Long): Flow<List<PreachingLogEntity>>

    @Query("SELECT * FROM preaching_log ORDER BY datePreachedMs DESC LIMIT 1")
    suspend fun getMostRecentPreachingLog(): PreachingLogEntity?


    // --- Verse Usage ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerseUsages(verses: List<VerseUsageEntity>)

    @Query("SELECT * FROM verse_usage WHERE verseReference IN (:refs)")
    suspend fun findVerseUsageOverlaps(refs: List<String>): List<VerseUsageEntity>

    @Query("DELETE FROM verse_usage WHERE sermonId = :sermonId")
    suspend fun deleteVerseUsagesForSermon(sermonId: String)

    // --- Preach Mode Cache ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreachCache(cache: PreachCacheEntity)

    @Query("SELECT * FROM preach_cache WHERE fileHash = :fileHash")
    suspend fun getPreachCache(fileHash: String): PreachCacheEntity?

    // --- Bible Cache ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBibleCache(cache: BibleCacheEntity)

    @Query("SELECT * FROM bible_cache WHERE LOWER(TRIM(REPLACE(verseReference, ' ', ''))) = LOWER(TRIM(REPLACE(:ref, ' ', ''))) AND LOWER(translation) = LOWER(:translation)")
    suspend fun getBibleCache(ref: String, translation: String): BibleCacheEntity?

    @Query("SELECT * FROM bible_cache ORDER BY timestamp DESC")
    fun getAllBibleCacheFlow(): Flow<List<BibleCacheEntity>>
}
