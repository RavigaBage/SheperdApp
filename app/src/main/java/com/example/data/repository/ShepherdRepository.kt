package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.data.file.SafFileManager
import com.example.data.local.*
import com.example.data.remote.FormatMode
import com.example.data.remote.GeminiService
import com.example.domain.model.*
import com.example.presentation.viewmodel.DrawingStroke
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
        // Active (non-trashed) files only — this is what the main library screen sees.
        return dao.getAllFilesFlow().map { list ->
            list.filter { !it.isDeleted }.map { it.toDomain() }
        }
    }

    fun getTrashedFiles(): Flow<List<ShepherdFile>> {
        // Same underlying query, just the other half of the split — no new Dao method needed.
        return dao.getAllFilesFlow().map { list ->
            list.filter { it.isDeleted }.map { it.toDomain() }
        }
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
                    // Don't let a trashed item's ".Trash/..." scan path clobber its
                    // original location — we need that to restore it correctly later.
                    parentPath = if (existing?.isDeleted == true) existing.parentPath else sysFile.parentPath,
                    sizeBytes = sysFile.sizeBytes,
                    lastModified = sysFile.lastModified,
                    isFavorite = existing?.isFavorite ?: 0,
                    isDeleted = existing?.isDeleted ?: false,
                    deletedAt = existing?.deletedAt
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

    /**
     * Moves a file into a category. This now physically relocates the document into
     * that category's own folder (falls back to root only if the category somehow has
     * no folder of its own) — previously this always moved the file back to root and
     * only updated the DB tag, so the physical file never actually showed up inside the
     * category's folder when browsed outside the app.
     */
    suspend fun moveFile(file: ShepherdFile, targetCategory: Category) = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        val rootUri = getRootFolderUri() ?: return@withContext
        val destinationFolderUri = targetCategory.parentFolderId?.let { Uri.parse(it) } ?: rootUri

        val result = safFileManager.moveFile(fileUri, destinationFolderUri)
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
                isFavorite = if (file.isFavorite) 1 else 0,
                isDeleted = false,
                deletedAt = null
            )

            database.withTransaction {
                dao.deleteFileById(file.id)
                dao.insertFile(updatedFile)
            }

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
        }.onFailure { error ->
            error.printStackTrace()
        }
    }

    /**
     * Renames the file on disk (via SAF) and only updates the DB once that succeeds.
     * Previously this only ever touched the Room row's name column — the document on
     * disk kept its old name forever.
     * Returns true on success so the caller (ViewModel) can surface a failure to the UI.
     */
    suspend fun renameFile(file: ShepherdFile, newName: String): Boolean = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        val docFile = DocumentFile.fromSingleUri(context, fileUri)

        val renamed = try {
            docFile?.renameTo(newName) ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (!renamed) return@withContext false

        // DocumentFile updates its own held Uri/name in place after a successful rename.
        val finalUri = docFile?.uri ?: fileUri
        val finalName = docFile?.name ?: newName

        val entry = ShepherdFileEntity.fromDomain(file).copy(
            id = finalUri.toString(),
            name = finalName,
            uriString = finalUri.toString()
        )

        database.withTransaction {
            dao.deleteFileById(file.id)
            dao.insertFile(entry)
        }

        val history = HistoryEntry(
            id = UUID.randomUUID().toString(),
            action = "Renamed file from '${file.name}' to '$finalName'",
            fileName = finalName,
            fromPath = file.parentPath,
            toPath = file.parentPath,
            timestamp = System.currentTimeMillis(),
            actionType = ActionType.RENAMED
        )
        dao.insertHistory(HistoryEntity.fromDomain(history))
        true
    }

    /**
     * Soft-delete: physically moves the file into a hidden ".Trash" folder under root
     * (created lazily, its URI cached in prefs) and flags it isDeleted/deletedAt.
     * categoryId + parentPath are deliberately preserved so restoreFile() knows where
     * the file came from. Falls back to a hard delete only if there's no root folder
     * to build a Trash folder under.
     */
    suspend fun deleteFile(file: ShepherdFile) = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        val trashFolderUri = getOrCreateTrashFolderUri()

        if (trashFolderUri == null) {
            safFileManager.deleteFile(fileUri)
            dao.deleteFileById(file.id)
            dao.insertHistory(HistoryEntity.fromDomain(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    action = "Deleted file '${file.name}'",
                    fileName = file.name,
                    fromPath = file.parentPath,
                    toPath = null,
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionType.DELETED
                )
            ))
            return@withContext
        }

        val result = safFileManager.moveFile(fileUri, trashFolderUri)
        val newUriString = result.getOrNull()?.toString() ?: file.uriString

        val entry = ShepherdFileEntity.fromDomain(file).copy(
            id = newUriString,
            uriString = newUriString,
            isDeleted = true,
            deletedAt = System.currentTimeMillis()
        )

        database.withTransaction {
            dao.deleteFileById(file.id)
            dao.insertFile(entry)
        }

        dao.insertHistory(HistoryEntity.fromDomain(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                action = "Moved '${file.name}' to Trash",
                fileName = file.name,
                fromPath = file.parentPath,
                toPath = "Trash",
                timestamp = System.currentTimeMillis(),
                actionType = ActionType.DELETED
            )
        ))
    }

    /** Moves a trashed file back to its original category folder (or root). */
    suspend fun restoreFile(file: ShepherdFile, targetCategory: Category?) = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        val rootUri = getRootFolderUri() ?: return@withContext
        val destinationFolderUri = targetCategory?.parentFolderId?.let { Uri.parse(it) } ?: rootUri

        val result = safFileManager.moveFile(fileUri, destinationFolderUri)
        result.onSuccess { newUri ->
            val entry = ShepherdFileEntity.fromDomain(file).copy(
                id = newUri.toString(),
                uriString = newUri.toString(),
                isDeleted = false,
                deletedAt = null
            )
            database.withTransaction {
                dao.deleteFileById(file.id)
                dao.insertFile(entry)
            }
            dao.insertHistory(HistoryEntity.fromDomain(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    action = "Restored '${file.name}' from Trash",
                    fileName = file.name,
                    fromPath = "Trash",
                    toPath = targetCategory?.name ?: "Root",
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionType.RESTORED
                )
            ))
        }
    }

    /** Hard delete — used from the Trash screen's "Delete forever". */
    suspend fun permanentlyDeleteFile(file: ShepherdFile) = withContext(Dispatchers.IO) {
        val fileUri = Uri.parse(file.uriString)
        safFileManager.deleteFile(fileUri)
        dao.deleteFileById(file.id)
        dao.insertHistory(HistoryEntity.fromDomain(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                action = "Permanently deleted '${file.name}'",
                fileName = file.name,
                fromPath = "Trash",
                toPath = null,
                timestamp = System.currentTimeMillis(),
                actionType = ActionType.DELETED
            )
        ))
    }

    /** Call periodically (e.g. on every sync) to auto-empty anything older than [thresholdDays]. */
    suspend fun purgeExpiredTrash(thresholdDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (thresholdDays * 24L * 60L * 60L * 1000L)
        val trashed = dao.getAllFilesFlow().first().filter { it.isDeleted }
        val expired = trashed.filter { (it.deletedAt ?: 0L) < cutoff }
        expired.forEach { entity ->
            safFileManager.deleteFile(Uri.parse(entity.uriString))
            dao.deleteFileById(entity.id)
            dao.insertHistory(HistoryEntity.fromDomain(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    action = "Auto-purged '${entity.name}' from Trash after $thresholdDays days",
                    fileName = entity.name,
                    fromPath = "Trash",
                    toPath = null,
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionType.PURGED
                )
            ))
        }
    }

    private suspend fun getOrCreateTrashFolderUri(): Uri? {
        val stored = prefs.getString("trash_folder_uri", null)
        if (stored != null) {
            val existing = DocumentFile.fromTreeUri(context, Uri.parse(stored))
            if (existing != null && existing.exists()) return Uri.parse(stored)
        }
        val rootUri = getRootFolderUri() ?: return null
        val result = safFileManager.createSubfolder(rootUri, ".Trash")
        return result.getOrNull()?.also {
            prefs.edit().putString("trash_folder_uri", it.toString()).apply()
        }
    }

    /** Creates a brand-new empty file (txt) or a minimal generated doc (docx) inside a category (or root). */
    suspend fun createNewFile(name: String, extension: String, categoryId: String?): Boolean = withContext(Dispatchers.IO) {
        val rootUri = getRootFolderUri() ?: return@withContext false
        val category = categoryId?.let { id -> dao.getAllCategoriesFlow().first().find { it.id == id } }
        val targetFolderUri = category?.parentFolderId?.let { Uri.parse(it) } ?: rootUri
        val targetDir = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false

        val cleanExt = extension.lowercase()
        val mimeType = when (cleanExt) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pdf" -> "application/pdf"
            else -> "text/plain"
        }
        val cleanName = "${name.trim().ifEmpty { "Untitled" }}.$cleanExt"

        val newDoc = try {
            targetDir.createFile(mimeType, cleanName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return@withContext false

        if (cleanExt == "docx") {
            try {
                val generator = DocxGenerator(context)
                val tempFile = generator.generateDocx("", cleanName, FormatMode.SERMON)
                context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                }
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // .txt (and anything else) is left empty on disk — perfectly valid, opens fine.

        dao.insertFile(
            ShepherdFileEntity(
                id = newDoc.uri.toString(),
                name = cleanName,
                extension = cleanExt,
                uriString = newDoc.uri.toString(),
                categoryId = categoryId,
                parentPath = category?.name ?: "Root",
                sizeBytes = newDoc.length(),
                lastModified = System.currentTimeMillis(),
                isFavorite = 0,
                isDeleted = false,
                deletedAt = null
            )
        )

        dao.insertHistory(HistoryEntity.fromDomain(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                action = "Created new file '$cleanName'",
                fileName = cleanName,
                fromPath = null,
                toPath = category?.name ?: "Root",
                timestamp = System.currentTimeMillis(),
                actionType = ActionType.CREATED
            )
        ))
        true
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

    suspend fun savePastoralNote(
        title: String,
        text: String,
        strokes: List<DrawingStroke>,
        noteType: NoteType,
        exportAsDocx: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val rootUri = getRootFolderUri() ?: return@withContext null
        val rootDir = DocumentFile.fromTreeUri(context, rootUri)
            ?: return@withContext null

        val typeTag = when (noteType) {
            NoteType.TEXT -> "Typed"
            NoteType.HANDWRITTEN -> "Handwritten"
            NoteType.MIXED -> "Text+Handwritten"
        }
        val safeTitle = title.replace("/", "_").replace("\\", "_")

        // 1. PDF save (unchanged generator, now tagged with note type in the filename)
        val pdfGenerator = NotesGenerator(context)
        val tempPdf = pdfGenerator.generateNotesPdf(title, text, strokes)
        try {
            val cleanPdfName = "$safeTitle [$typeTag].pdf"
            val pdfDoc = rootDir.createFile("application/pdf", cleanPdfName)
                ?: throw Exception("Failed to write PDF on filesystem")

            context.contentResolver.openOutputStream(pdfDoc.uri).use { out ->
                tempPdf.inputStream().use { input -> if (out != null) input.copyTo(out) }
            }

            dao.insertFile(
                ShepherdFileEntity(
                    id = pdfDoc.uri.toString(),
                    name = cleanPdfName,
                    extension = "pdf",
                    uriString = pdfDoc.uri.toString(),
                    categoryId = null,
                    parentPath = "Root",
                    sizeBytes = tempPdf.length(),
                    lastModified = System.currentTimeMillis()
                )
            )

            dao.insertHistory(HistoryEntity.fromDomain(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    action = "Saved Pastoral Note '$cleanPdfName' ($typeTag)",
                    fileName = cleanPdfName,
                    fromPath = "Notes",
                    toPath = "Root",
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionType.CREATED
                )
            ))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempPdf.delete()
        }

        // 2. Optional .docx export — text content, embeds the drawing image when MIXED
        var docxUriString: String? = null
        if (exportAsDocx && text.isNotBlank()) {
            val docxGenerator = DocxGenerator(context)
            val tempDocx = docxGenerator.generateNotesDocx(
                title = title,
                text = text,
                strokes = strokes,
                includeDrawing = noteType == NoteType.MIXED
            )
            try {
                val cleanDocxName = "$safeTitle [$typeTag].docx"
                val docxDoc = rootDir.createFile(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    cleanDocxName
                ) ?: throw Exception("Failed to write DOCX on filesystem")

                context.contentResolver.openOutputStream(docxDoc.uri).use { out ->
                    tempDocx.inputStream().use { input -> if (out != null) input.copyTo(out) }
                }

                dao.insertFile(
                    ShepherdFileEntity(
                        id = docxDoc.uri.toString(),
                        name = cleanDocxName,
                        extension = "docx",
                        uriString = docxDoc.uri.toString(),
                        categoryId = null,
                        parentPath = "Root",
                        sizeBytes = tempDocx.length(),
                        lastModified = System.currentTimeMillis()
                    )
                )

                dao.insertHistory(HistoryEntity.fromDomain(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        action = "Exported Pastoral Note '$cleanDocxName' as Word doc",
                        fileName = cleanDocxName,
                        fromPath = "Notes",
                        toPath = "Root",
                        timestamp = System.currentTimeMillis(),
                        actionType = ActionType.CREATED
                    )
                ))

                docxUriString = docxDoc.uri.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                tempDocx.delete()
            }
        }

        docxUriString
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

    suspend fun getBibleVerse(ref: String, translation: String): String = withContext(Dispatchers.IO) {
        val cached = dao.getBibleCache(ref, translation)
        if (cached != null && cached.scriptureText.isNotEmpty()) {
            return@withContext cached.scriptureText
        }

        val apiTrans = when (translation) {
            "KJV" -> "kjv"
            "ESV" -> "web" // fallback to beautiful World English Bible
            "AMP" -> "bbe" // Bible in Basic English
            else -> "web"
        }

        val sanitizedRef = ref.trim().replace(" ", "+")
        val url = "https://bible-api.com/$sanitizedRef?translation=$apiTrans"
        var verseText = ""

        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    val text = json.optString("text", "").trim()
                    if (text.isNotEmpty()) {
                        verseText = text
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (verseText.isEmpty()) {
            verseText = getOfflineFallback(ref, translation)
        }

        // Cache it!
        val cacheEntity = BibleCacheEntity(
            verseReference = ref,
            translation = translation,
            scriptureText = verseText,
            timestamp = System.currentTimeMillis()
        )
        dao.insertBibleCache(cacheEntity)

        return@withContext verseText
    }

    fun getAllBibleCacheFlow(): Flow<List<BibleCacheEntity>> = dao.getAllBibleCacheFlow()

    private fun getOfflineFallback(ref: String, trans: String): String {
        val clean = ref.lowercase()
        return when {
            clean.contains("john 3:16") -> {
                if (trans == "KJV") "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."
                else "For God so loved the world that he gave his one and only Son, that whoever believes in him shall not perish but have eternal life."
            }
            clean.contains("psalm 23:1") || clean.contains("ps. 23:1") -> {
                if (trans == "KJV") "The LORD is my shepherd; I shall not want."
                else "The LORD is my shepherd, I lack nothing."
            }
            clean.contains("romans 8:28") || clean.contains("rom 8:28") -> {
                "And we know that in all things God works for the good of those who love him, who have been called according to his purpose."
            }
            clean.contains("romans 10:13") || clean.contains("rom 10:13") -> {
                "Everyone who calls on the name of the Lord will be saved."
            }
            clean.contains("matthew 11:28") || clean.contains("matt 11:28") -> {
                "Come to me, all you who are weary and burdened, and I will give you rest."
            }
            clean.contains("revelation 3:20") || clean.contains("rev 3:20") -> {
                "Here I am! I stand at the door and knock. If anyone hears my voice and opens the door, I will come in..."
            }
            else -> "Scripture reference: $ref ($trans). For full verse retrieval, ensure active internet connection."
        }
    }

    fun getUrielWordInsightStream(word: String, type: String, customPrompt: String = ""): Flow<String> =
        geminiService.getUrielWordInsightStream(word, type, customPrompt)

    fun getUrielScriptureIntelligenceStream(scriptureRef: String, type: String): Flow<String> =
        geminiService.getUrielScriptureIntelligenceStream(scriptureRef, type)

    fun getUrielThemeDraftStream(topic: String): Flow<String> =
        geminiService.getUrielThemeDraftStream(topic)
}