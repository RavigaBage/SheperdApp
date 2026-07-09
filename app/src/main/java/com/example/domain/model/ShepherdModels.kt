package com.example.domain.model

import java.io.Serializable

enum class ActionType {
    MOVED, DELETED, CREATED, RENAMED, AI_GENERATED, BOOKMARKED, TAGGED,
    RESTORED, PURGED // new: Trash restore / auto-purge history entries
}

data class ShepherdFile(
    val id: String,
    val name: String,
    val extension: String, // docx, doc, pptx, pdf, txt
    val uriString: String,
    val categoryId: String?,
    val parentPath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFavorite: Boolean = false,
    val usageCount: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) : Serializable

data class Category(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconEmoji: String,
    val parentFolderId: String?,
    val createdAt: Long
) : Serializable

data class HistoryEntry(
    val id: String,
    val action: String,
    val fileName: String,
    val fromPath: String?,
    val toPath: String?,
    val timestamp: Long,
    val actionType: ActionType
) : Serializable

data class Sermon(
    val id: String,
    val fileId: String?, // can link to any ShepherdFile
    val title: String,
    val scriptureRef: String, // e.g. "John 3:16"
    val seriesId: String?, // can belong to a Series
    val datePreached: Long?, // Nullable timestamp
    val notes: String
) : Serializable

data class Series(
    val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
    val createdAt: Long,
    val artworkUri: String? = null
) : Serializable

data class ScriptureTag(
    val id: String,
    val fileId: String,
    val verseRef: String,
    val book: String,
    val chapter: Int,
    val verse: Int
) : Serializable

data class Bookmark(
    val id: String,
    val fileId: String,
    val label: String,
    val pinnedOrder: Int
) : Serializable