package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.*

@Entity(tableName = "shepherd_file")
data class ShepherdFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val extension: String,
    val uriString: String,
    val categoryId: String?,
    val parentPath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFavorite: Int = 0 // Room compatibility standard
) {
    fun toDomain() = ShepherdFile(
        id = id,
        name = name,
        extension = extension,
        uriString = uriString,
        categoryId = categoryId,
        parentPath = parentPath,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        isFavorite = isFavorite > 0
    )

    companion object {
        fun fromDomain(file: ShepherdFile) = ShepherdFileEntity(
            id = file.id,
            name = file.name,
            extension = file.extension,
            uriString = file.uriString,
            categoryId = file.categoryId,
            parentPath = file.parentPath,
            sizeBytes = file.sizeBytes,
            lastModified = file.lastModified,
            isFavorite = if (file.isFavorite) 1 else 0
        )
    }
}

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val iconEmoji: String,
    val parentFolderId: String?,
    val createdAt: Long
) {
    fun toDomain() = Category(
        id = id,
        name = name,
        colorHex = colorHex,
        iconEmoji = iconEmoji,
        parentFolderId = parentFolderId,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(cat: Category) = CategoryEntity(
            id = cat.id,
            name = cat.name,
            colorHex = cat.colorHex,
            iconEmoji = cat.iconEmoji,
            parentFolderId = cat.parentFolderId,
            createdAt = cat.createdAt
        )
    }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val action: String,
    val fileName: String,
    val fromPath: String?,
    val toPath: String?,
    val timestamp: Long,
    val actionTypeString: String
) {
    fun toDomain() = HistoryEntry(
        id = id,
        action = action,
        fileName = fileName,
        fromPath = fromPath,
        toPath = toPath,
        timestamp = timestamp,
        actionType = ActionType.valueOf(actionTypeString)
    )

    companion object {
        fun fromDomain(history: HistoryEntry) = HistoryEntity(
            id = history.id,
            action = history.action,
            fileName = history.fileName,
            fromPath = history.fromPath,
            toPath = history.toPath,
            timestamp = history.timestamp,
            actionTypeString = history.actionType.name
        )
    }
}

@Entity(tableName = "sermon")
data class SermonEntity(
    @PrimaryKey val id: String,
    val fileId: String?,
    val title: String,
    val scriptureRef: String,
    val seriesId: String?,
    val datePreached: Long?,
    val notes: String
) {
    fun toDomain() = Sermon(
        id = id,
        fileId = fileId,
        title = title,
        scriptureRef = scriptureRef,
        seriesId = seriesId,
        datePreached = datePreached,
        notes = notes
    )

    companion object {
        fun fromDomain(sermon: Sermon) = SermonEntity(
            id = sermon.id,
            fileId = sermon.fileId,
            title = sermon.title,
            scriptureRef = sermon.scriptureRef,
            seriesId = sermon.seriesId,
            datePreached = sermon.datePreached,
            notes = sermon.notes
        )
    }
}

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
    val createdAt: Long,
    val artworkUri: String?
) {
    fun toDomain() = Series(
        id = id,
        name = name,
        description = description,
        colorHex = colorHex,
        createdAt = createdAt,
        artworkUri = artworkUri
    )

    companion object {
        fun fromDomain(series: Series) = SeriesEntity(
            id = series.id,
            name = series.name,
            description = series.description,
            colorHex = series.colorHex,
            createdAt = series.createdAt,
            artworkUri = series.artworkUri
        )
    }
}

@Entity(tableName = "scripture_tag")
data class ScriptureTagEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val verseRef: String,
    val book: String,
    val chapter: Int,
    val verse: Int
) {
    fun toDomain() = ScriptureTag(
        id = id,
        fileId = fileId,
        verseRef = verseRef,
        book = book,
        chapter = chapter,
        verse = verse
    )

    companion object {
        fun fromDomain(tag: ScriptureTag) = ScriptureTagEntity(
            id = tag.id,
            fileId = tag.fileId,
            verseRef = tag.verseRef,
            book = tag.book,
            chapter = tag.chapter,
            verse = tag.verse
        )
    }
}

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val label: String,
    val pinnedOrder: Int
) {
    fun toDomain() = Bookmark(
        id = id,
        fileId = fileId,
        label = label,
        pinnedOrder = pinnedOrder
    )

    companion object {
        fun fromDomain(b: Bookmark) = BookmarkEntity(
            id = b.id,
            fileId = b.fileId,
            label = b.label,
            pinnedOrder = b.pinnedOrder
        )
    }
}

@Entity(tableName = "sermon_calendar")
data class SermonCalendarEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sermonId: String,               // matches SermonEntity.id (String in existing DB)
    val sermonTitle: String,         
    val scheduledDateMs: Long,       
    val eventName: String?,          
    val reminderHoursBefore: Int = 24, 
    val notes: String?,
    val notificationJobId: String? = null,
    
    // Redesign/Calendar expansion fields
    val endDateMs: Long = scheduledDateMs,
    val eventType: String = "Sunday Service", // "Sunday Service", "Special Engagement", "Conference"
    val venueName: String? = "Sanctuary Main Hall",
    val coSpeakersCount: Int = 0,
    val coSpeakersNamesJson: String? = null, // Comma-separated names for initials avatars
    val description: String? = null,
    val scriptureFocus: String? = null,
    val intendedAudience: String? = null,
    val notesReady: Boolean = false,
    val scripturePulled: Boolean = false,
    val slidesBuilt: Boolean = false,
    val isRecurring: Boolean = false,
    val travelMinutes: Int = 30
)

@Entity(tableName = "preaching_log")
data class PreachingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sermonId: String,               // matches SermonEntity.id
    val sermonTitle: String,
    val datePreachedMs: Long,
    val eventName: String?,
    val durationMinutes: Int?,
    val versesJson: String?          
)

@Entity(tableName = "verse_usage", indices = [androidx.room.Index("verseReference")])
data class VerseUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val verseReference: String,      
    val sermonId: String,               // matches SermonEntity.id
    val sermonTitle: String,
    val datePreachedMs: Long?        
)

@Entity(tableName = "preach_cache")
data class PreachCacheEntity(
    @PrimaryKey val fileHash: String,
    val jsonText: String
)

@Entity(tableName = "board")
data class BoardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUri: String?,
    val categoryId: String?,
    val createdAt: Long,
    val lastModified: Long,
    val templateType: String = "BLANK",
    val isPaged: Int = 0
) {
    fun toDomain() = Board(
        id = id,
        title = title,
        thumbnailUri = thumbnailUri,
        categoryId = categoryId,
        createdAt = createdAt,
        lastModified = lastModified,
        templateType = BoardTemplate.valueOf(templateType),
        isPaged = isPaged > 0
    )

    companion object {
        fun fromDomain(board: Board) = BoardEntity(
            id = board.id,
            title = board.title,
            thumbnailUri = board.thumbnailUri,
            categoryId = board.categoryId,
            createdAt = board.createdAt,
            lastModified = board.lastModified,
            templateType = board.templateType.name,
            isPaged = if (board.isPaged) 1 else 0
        )
    }
}

