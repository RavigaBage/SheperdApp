package com.example.notes.domain

import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    // Notebooks
    fun observeNotebooks(): Flow<List<Notebook>>
    suspend fun getNotebookById(id: String): Notebook?
    suspend fun upsertNotebook(notebook: Notebook)
    suspend fun deleteNotebook(id: String)

    // Pages
    fun observePagesForNotebook(notebookId: String): Flow<List<Page>>
    suspend fun getPageById(id: String): Page?
    suspend fun upsertPage(page: Page)
    suspend fun deletePage(id: String)

    // Elements (Refactored to CanvasObject)
    fun observeElementsForPage(pageId: String): Flow<List<CanvasObject>>
    suspend fun getElementsForPage(pageId: String): List<CanvasObject>
    suspend fun upsertElement(pageId: String, element: CanvasObject)
    suspend fun deleteElement(elementId: String)
    suspend fun syncElements(pageId: String, elements: List<CanvasObject>)

    // Illustrations
    fun observeIllustrations(query: String = "", categoryId: String? = null): Flow<List<Illustration>>
    suspend fun getIllustrationById(id: String): Illustration?
    suspend fun upsertIllustration(illustration: Illustration)
    suspend fun deleteIllustration(id: String)

    // Sermon Templates
    fun observeSermonTemplates(query: String = "", categoryId: String? = null): Flow<List<SermonTemplate>>
    suspend fun getSermonTemplateById(id: String): SermonTemplate?
    suspend fun upsertSermonTemplate(template: SermonTemplate)
    suspend fun deleteSermonTemplate(id: String)

    // Initial Seeding & Sync
    suspend fun seedDataIfNeeded(context: android.content.Context)
    suspend fun syncWithRemote(jsonManifest: String)
}

enum class PageBackgroundStyle { LINED, GRID, PLAIN }

data class Notebook(
    val id: String,
    val title: String,
    val categoryId: String? = null,
    val backgroundStyle: PageBackgroundStyle = PageBackgroundStyle.LINED,
    val colorHex: String = "#FFFFFF",
    val createdAt: Long,
    val updatedAt: Long
)

data class Page(
    val id: String,
    val notebookId: String,
    val pageIndex: Int,
    val thumbnailPath: String?,
    val backgroundColorHex: String = "#FFFFFF",
    val createdAt: Long,
    val updatedAt: Long
)

data class Illustration(
    val id: String,
    val title: String,
    val bodyText: String,
    val categoryId: String?,
    val scriptureReference: String?,
    val source: String?,
    val imageUrl: String? = null,
    val isUserCreated: Boolean,
    val isHidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

data class SermonTemplate(
    val id: String,
    val title: String,
    val categoryId: String?,
    val scriptureReference: String?,
    val summary: String,
    val tags: String?,
    val isAiSuggested: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
