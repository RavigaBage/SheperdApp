package com.example.notes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun observeNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: String): NotebookEntity?

    @Upsert
    suspend fun upsert(notebook: NotebookEntity)

    @Delete
    suspend fun delete(notebook: NotebookEntity)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    fun observePagesForNotebook(notebookId: String): Flow<List<PageEntity>>

    @Upsert
    suspend fun upsert(page: PageEntity)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: String): PageEntity?

    @Delete
    suspend fun delete(page: PageEntity)
}

@Dao
interface PageElementDao {
    @Query("SELECT * FROM page_elements WHERE pageId = :pageId ORDER BY zIndex ASC")
    fun observeElementsForPage(pageId: String): Flow<List<PageElementEntity>>

    @Query("SELECT * FROM page_elements WHERE pageId = :pageId ORDER BY zIndex ASC")
    suspend fun getElementsForPage(pageId: String): List<PageElementEntity>

    @Upsert
    suspend fun upsert(element: PageElementEntity)

    @Query("DELETE FROM page_elements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(element: PageElementEntity)

    @Transaction
    suspend fun syncElements(pageId: String, elements: List<PageElementEntity>) {
        val existing = getElementsForPage(pageId)
        val currentIds = elements.map { it.id }.toSet()
        
        // Delete removed elements
        existing.forEach { 
            if (!currentIds.contains(it.id)) {
                delete(it)
            }
        }
        
        // Upsert current elements
        elements.forEach {
            upsert(it)
        }
    }
}
