package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.file.SafFileManager
import com.example.data.local.*
import com.example.data.remote.FormatMode
import com.example.data.remote.GeminiService
import com.example.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ShepherdRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val safFileManager: SafFileManager,
    private val geminiService: GeminiService
) {
    private val dao = database.dao()
    private val prefs = context.getSharedPreferences("shepherd_prefs", Context.MODE_PRIVATE)

    // --- Root Folder Management ---
    fun getRootFolderUri(): Uri? {
        val uriStr = prefs.getString("root_folder_uri", null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }

    fun setRootFolderUri(uri: Uri?) {
        prefs.edit().putString("root_folder_uri", uri?.toString()).apply()
        if (uri != null) {
            safFileManager.persistUriPermission(uri)
        }
    }

    // --- Preference Polish Settings ---
    fun getThemeMode(): String = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
    fun setThemeMode(mode: String) = prefs.edit().putString("theme_mode", mode).apply()

    fun getHapticsEnabled(): Boolean = prefs.getBoolean("haptics_enabled", true)
    fun setHapticsEnabled(enabled: Boolean) = prefs.edit().putBoolean("haptics_enabled", enabled).apply()

    fun getAnimationIntensity(): String = prefs.getString("anim_intensity", "FULL") ?: "FULL"
    fun setAnimationIntensity(intensity: String) = prefs.edit().putString("anim_intensity", intensity).apply()

    fun getPastorName(): String = prefs.getString("pastor_name", "Pastor David") ?: "Pastor David"
    fun setPastorName(name: String) = prefs.edit().putString("pastor_name", name).apply()

    fun getBibleVersion(): String = prefs.getString("bible_version", "NIV") ?: "NIV"
    fun setBibleVersion(ver: String) = prefs.edit().putString("bible_version", ver).apply()

    // --- Flow Streams ---
    fun getAllFiles(): Flow<List<ShepherdFile>> {
        return dao.getAllFilesFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllCategories(): Flow<List<Category>> {
        return dao.getAllCategoriesFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllHistory(): Flow<List<HistoryEntry>> {
        return dao.getAllHistoryFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllBookmarks(): Flow<List<Bookmark>> {
        return dao.getAllBookmarksFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllSermons(): Flow<List<Sermon>> {
        return dao.getAllSermonsFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllSeries(): Flow<List<Series>> {
        return dao.getAllSeriesFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllScriptureTags(): Flow<List<ScriptureTag>> {
        return dao.getAllScriptureTagsFlow().map { list -> list.map { it.toDomain() } }
    }

    // --- File Actions ---
    suspend fun syncFilesWithFileSystem() = withContext(Dispatchers.IO) {
        val rootUri = getRootFolderUri() ?: return@withContext
        try {
            val systemFiles = safFileManager.listFilesRecursive(rootUri)
            val dbFiles = database.dao().getAllFilesFlow().first()
            val dbFilesMap = dbFiles.associateBy { it.id }

            val filesToInsert = systemFiles.map { sysFile ->
                val existing = dbFilesMap[sysFile.id]
                ShepherdFileEntity(
                    id = sysFile.id,
                    name = sysFile.name,
                    extension = sysFile.extension,
                    uriString = sysFile.uriString,
                    categoryId = existing?.categoryId, // Retain existing category tag inside Room
                    parentPath = sysFile.parentPath,
                    sizeBytes = sysFile.sizeBytes,
                    lastModified = sysFile.lastModified,
                    isFavorite = existing?.isFavorite ?: 0
                )
            }

            // Sync database files
            database.dao().insertFiles(filesToInsert)

            // Prune deleted system files from database
            val systemUriStrings = systemFiles.map { it.uriString }.toSet()
            for (dbFile in dbFiles) {
                if (!systemUriStrings.contains(dbFile.uriString)) {
                    database.dao().deleteFile(dbFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun moveFile(file: ShepherdFile, targetCategory: Category) = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        val rootUri = getRootFolderUri() ?: return@withContext

        // Move on local device file system
        val result = safFileManager.moveFile(fileUri, rootUri)
        result.onSuccess { newUri ->
            val updatedFile = ShepherdFileEntity(
                id = newUri.toString(),
                name = file.name,
                extension = file.extension,
                uriString = newUri.toString(),
                categoryId = targetCategory.id,
                parentPath = targetCategory.name,
                sizeBytes = file.sizeBytes,
                lastModified = System.currentTimeMillis(),
                isFavorite = if (file.isFavorite) 1 else 0
            )

            dao.deleteFileById(file.id)
            dao.insertFile(updatedFile)

            // Log activity log
            val history = HistoryEntry(
                id = UUID.randomUUID().toString(),
                action = "Moved '${file.name}' to Category: ${targetCategory.name}",
                fileName = file.name,
                fromPath = file.parentPath,
                toPath = targetCategory.name,
                timestamp = System.currentTimeMillis(),
                actionType = ActionType.MOVED
            )
            dao.insertHistory(HistoryEntity.fromDomain(history))
        }
    }

    suspend fun updateFileCategory(fileId: String, categoryId: String?) = withContext(Dispatchers.IO) {
        dao.updateFileCategory(fileId, categoryId)
    }

    suspend fun renameFile(file: ShepherdFile, newName: String) = withContext(Dispatchers.IO) {
        val entry = ShepherdFileEntity.fromDomain(file).copy(name = newName)
        dao.insertFile(entry)

        val history = HistoryEntry(
            id = UUID.randomUUID().toString(),
            action = "Renamed file from '${file.name}' to '$newName'",
            fileName = newName,
            fromPath = file.parentPath,
            toPath = file.parentPath,
            timestamp = System.currentTimeMillis(),
            actionType = ActionType.RENAMED
        )
        dao.insertHistory(HistoryEntity.fromDomain(history))
    }

    suspend fun deleteFile(file: ShepherdFile) = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        val result = safFileManager.deleteFile(fileUri)
        
        // Remove locally from Room anyway (since fallback is safer)
        dao.deleteFileById(file.id)

        val history = HistoryEntry(
            id = UUID.randomUUID().toString(),
            action = "Deleted file '${file.name}'",
            fileName = file.name,
            fromPath = file.parentPath,
            toPath = null,
            timestamp = System.currentTimeMillis(),
            actionType = ActionType.DELETED
        )
        dao.insertHistory(HistoryEntity.fromDomain(history))
    }

    // --- Bookmarks ---
    suspend fun toggleBookmark(file: ShepherdFile, customLabel: String = "Sermon Reference") = withContext(Dispatchers.IO) {
        val existing = dao.getBookmarkByFileId(file.id)
        if (existing != null) {
            dao.deleteBookmark(existing)
            dao.insertFile(ShepherdFileEntity.fromDomain(file.copy(isFavorite = false)))
        } else {
            val list = dao.getAllBookmarksFlow().first()
            val newOrder = (list.maxOfOrNull { it.pinnedOrder } ?: 0) + 1
            dao.insertBookmark(
                BookmarkEntity(
                    id = UUID.randomUUID().toString(),
                    fileId = file.id,
                    label = customLabel,
                    pinnedOrder = newOrder
                )
            )
            dao.insertFile(ShepherdFileEntity.fromDomain(file.copy(isFavorite = true)))

            val history = HistoryEntry(
                id = UUID.randomUUID().toString(),
                action = "Bookmarked '${file.name}' as '$customLabel'",
                fileName = file.name,
                fromPath = file.parentPath,
                toPath = null,
                timestamp = System.currentTimeMillis(),
                actionType = ActionType.BOOKMARKED
            )
            dao.insertHistory(HistoryEntity.fromDomain(history))
        }
    }

    suspend fun reorderBookmarks(bookmarks: List<Bookmark>) = withContext(Dispatchers.IO) {
        bookmarks.forEachIndexed { index, bookmark ->
            dao.insertBookmark(
                BookmarkEntity(
                    id = bookmark.id,
                    fileId = bookmark.fileId,
                    label = bookmark.label,
                    pinnedOrder = index
                )
            )
        }
    }

    // --- Categories ---
    suspend fun createCategory(name: String, colorHex: String, iconEmoji: String) = withContext(Dispatchers.IO) {
        val rootUri = getRootFolderUri() ?: return@withContext
        val subfolderResult = safFileManager.createSubfolder(rootUri, name)
        
        val folderUriStr = subfolderResult.getOrNull()?.toString()
        val newCat = Category(
            id = UUID.randomUUID().toString(),
            name = name,
            colorHex = colorHex,
            iconEmoji = iconEmoji,
            parentFolderId = folderUriStr,
            createdAt = System.currentTimeMillis()
        )
        dao.insertCategory(CategoryEntity.fromDomain(newCat))

        val history = HistoryEntry(
            id = UUID.randomUUID().toString(),
            action = "Created Category folder '$name'",
            fileName = name,
            fromPath = null,
            toPath = name,
            timestamp = System.currentTimeMillis(),
            actionType = ActionType.CREATED
        )
        dao.insertHistory(HistoryEntity.fromDomain(history))
    }

    // --- AI Operations & DOCX ---
    fun formatTextStream(rawText: String, mode: FormatMode): Flow<String> {
        return geminiService.formatDocumentStream(rawText, mode)
    }

    suspend fun saveAiDocument(formattedText: String, title: String, mode: FormatMode, categoryId: String?) = withContext(Dispatchers.IO) {
        val generator = DocxGenerator(context)
        val tempFile = generator.generateDocx(formattedText, title, mode)
        val rootUri = getRootFolderUri() ?: return@withContext

        // Copy temporary POI file to the user-selected context folder
        try {
            val rootDir = DocumentFile.fromTreeUri(context, rootUri)
                ?: throw Exception("Root document tree is invalid")
            
            val cleanName = title.replace("/", "_").replace("\\", "_") + ".docx"
            val fileDoc = rootDir.createFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document", cleanName)
                ?: throw Exception("Failed to write DOCX on filesystem")

            context.contentResolver.openOutputStream(fileDoc.uri).use { out ->
                tempFile.inputStream().use { input ->
                    if (out != null) {
                        input.copyTo(out)
                    }
                }
            }

            // Sync doc to DB
            val shepherdFile = ShepherdFileEntity(
                id = fileDoc.uri.toString(),
                name = cleanName,
                extension = "docx",
                uriString = fileDoc.uri.toString(),
                categoryId = categoryId,
                parentPath = "Root",
                sizeBytes = tempFile.length(),
                lastModified = System.currentTimeMillis()
            )
            dao.insertFile(shepherdFile)

            // Log AI generation history
            val history = HistoryEntry(
                id = UUID.randomUUID().toString(),
                action = "Generated DOCX '$cleanName' via AI Editor [${mode.name}]",
                fileName = cleanName,
                fromPath = "AI Canvas",
                toPath = categoryId ?: "Root",
                timestamp = System.currentTimeMillis(),
                actionType = ActionType.AI_GENERATED
            )
            dao.insertHistory(HistoryEntity.fromDomain(history))

            // Delete temporary file
            tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Sermon & Series Operations ---
    suspend fun createSermon(title: String, scripture: String, seriesId: String?, datePreached: Long?, notes: String, fileId: String?) = withContext(Dispatchers.IO) {
        val sermon = Sermon(
            id = UUID.randomUUID().toString(),
            fileId = fileId,
            title = title,
            scriptureRef = scripture,
            seriesId = seriesId,
            datePreached = datePreached,
            notes = notes
        )
        dao.insertSermon(SermonEntity.fromDomain(sermon))

        // Auto tag scripture
        if (scripture.isNotEmpty()) {
            val tag = ScriptureTag(
                id = UUID.randomUUID().toString(),
                fileId = fileId ?: sermon.id,
                verseRef = scripture,
                book = scripture.substringBefore(" ").trim(),
                chapter = 1, // simplified representation
                verse = 1
            )
            dao.insertScriptureTag(ScriptureTagEntity.fromDomain(tag))
        }

        val history = HistoryEntry(
            id = UUID.randomUUID().toString(),
            action = "Scheduled sermon outline '$title' ($scripture)",
            fileName = title,
            fromPath = null,
            toPath = null,
            timestamp = System.currentTimeMillis(),
            actionType = ActionType.TAGGED
        )
        dao.insertHistory(HistoryEntity.fromDomain(history))
    }

    suspend fun createSeries(name: String, description: String, colorHex: String) = withContext(Dispatchers.IO) {
        val series = Series(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            colorHex = colorHex,
            createdAt = System.currentTimeMillis()
        )
        dao.insertSeries(SeriesEntity.fromDomain(series))
    }

    suspend fun saveSermonNotes(sermonId: String, text: String) = withContext(Dispatchers.IO) {
        val existing = dao.getSermonById(sermonId)
        if (existing != null) {
            dao.insertSermon(existing.copy(notes = text))
        }
    }

    // --- Sermon Calendar & Preaching Metrics ---
    fun getAllEvents() = dao.getAllSermonCalendarFlow()
    fun getEventsBetween(start: Long, end: Long) = dao.getSermonCalendarEventsBetweenFlow(start, end)
    fun getUpcomingEvents(nowMs: Long) = dao.getSermonCalendarUpcomingFlow(nowMs)
    suspend fun saveEvent(event: SermonCalendarEntity): Long = dao.insertSermonCalendar(event)
    suspend fun updateEvent(event: SermonCalendarEntity) = dao.updateSermonCalendar(event)
    suspend fun deleteEvent(event: SermonCalendarEntity) = dao.deleteSermonCalendar(event)
    suspend fun updateNotificationJobId(eventId: Int, jobId: String) = dao.updateSermonCalendarJobId(eventId, jobId)

    suspend fun logPreaching(log: PreachingLogEntity) = dao.insertPreachingLog(log)
    fun getPreachingHistory() = dao.getAllPreachingLogsFlow()
    fun getPreachingByDateRange(start: Long, end: Long) = dao.getPreachingLogsBetweenFlow(start, end)

    suspend fun checkVerseOverlap(refs: List<String>): List<VerseUsageEntity> = dao.findVerseUsageOverlaps(refs)
    suspend fun saveVerseUsage(verses: List<VerseUsageEntity>) = dao.insertVerseUsages(verses)
}
