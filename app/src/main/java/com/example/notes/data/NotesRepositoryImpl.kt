package com.example.notes.data

import com.example.notes.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import androidx.core.content.edit

class NotesRepositoryImpl(
    private val notebookDao: NotebookDao,
    private val pageDao: PageDao,
    private val elementDao: PageElementDao,
    private val illustrationDao: IllustrationDao,
    private val templateDao: SermonTemplateDao,
    private val mainDao: com.example.data.local.AppDatabaseDao
) : NotesRepository {

    override fun observeNotebooks(): Flow<List<Notebook>> {
        return notebookDao.observeNotebooks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getNotebookById(id: String): Notebook? {
        return notebookDao.getNotebookById(id)?.toDomain()
    }

    override suspend fun upsertNotebook(notebook: Notebook) {
        notebookDao.upsert(notebook.toEntity())
    }

    override suspend fun deleteNotebook(id: String) {
        val notebook = notebookDao.getNotebookById(id)
        if (notebook != null) {
            notebookDao.delete(notebook)
        }
    }

    override fun observePagesForNotebook(notebookId: String): Flow<List<Page>> {
        return pageDao.observePagesForNotebook(notebookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPageById(id: String): Page? {
        return pageDao.getPageById(id)?.toDomain()
    }

    override suspend fun upsertPage(page: Page) {
        pageDao.upsert(page.toEntity())
    }

    override suspend fun deletePage(id: String) {
        val page = pageDao.getPageById(id)
        if (page != null) {
            pageDao.delete(page)
        }
    }

    override fun observeElementsForPage(pageId: String): Flow<List<CanvasObject>> {
        return elementDao.observeElementsForPage(pageId).map { entities ->
            entities.map { it.toCanvasObject() }
        }
    }

    override suspend fun getElementsForPage(pageId: String): List<CanvasObject> {
        val entities = elementDao.getElementsForPage(pageId)
        return entities.map { it.toCanvasObject() }
    }

    override suspend fun upsertElement(pageId: String, element: CanvasObject) {
        elementDao.upsert(element.toEntity(pageId))
    }

    override suspend fun deleteElement(elementId: String) {
        elementDao.deleteById(elementId)
    }

    override suspend fun syncElements(pageId: String, elements: List<CanvasObject>) {
        val entities = elements.map { it.toEntity(pageId) }
        elementDao.syncElements(pageId, entities)
    }

    // Illustrations
    override fun observeIllustrations(query: String, categoryId: String?): Flow<List<Illustration>> {
        return illustrationDao.searchWithFilter(query, categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getIllustrationById(id: String): Illustration? {
        return illustrationDao.getById(id)?.toDomain()
    }

    override suspend fun upsertIllustration(illustration: Illustration) {
        illustrationDao.upsert(illustration.toEntity())
    }

    override suspend fun deleteIllustration(id: String) {
        val illustration = illustrationDao.getById(id)
        if (illustration != null) {
            illustrationDao.delete(illustration)
        }
    }

    // Sermon Templates
    override fun observeSermonTemplates(query: String, categoryId: String?): Flow<List<SermonTemplate>> {
        val flow: Flow<List<SermonTemplateEntity>> = if (query.isNotBlank()) {
            templateDao.searchByKeyword(query)
        } else if (categoryId != null) {
            templateDao.observeByCategory(categoryId)
        } else {
            templateDao.observeAll()
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getSermonTemplateById(id: String): SermonTemplate? {
        return templateDao.getById(id)?.toDomain()
    }

    override suspend fun upsertSermonTemplate(template: SermonTemplate) {
        templateDao.upsert(template.toEntity())
    }

    override suspend fun deleteSermonTemplate(id: String) {
        val template = templateDao.getById(id)
        if (template != null) {
            templateDao.delete(template)
        }
    }

    override suspend fun seedDataIfNeeded(context: android.content.Context) {
        val sharedPrefs = context.getSharedPreferences("notes_prefs", android.content.Context.MODE_PRIVATE)
        val currentVersion = sharedPrefs.getInt("illustration_content_version", 0)
        
        val jsonString = try {
            context.assets.open("illustrations.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            return 
        }
        
        val root = org.json.JSONObject(jsonString)
        val assetVersion = root.optInt("version", 1)
        
        if (assetVersion > currentVersion) {
            // Seed Categories
            val categoriesArr = root.optJSONArray("categories")
            if (categoriesArr != null) {
                for (i in 0 until categoriesArr.length()) {
                    try {
                        val obj = categoriesArr.getJSONObject(i)
                        val id = obj.getString("id")
                        mainDao.insertCategory(com.example.data.local.CategoryEntity(
                            id = id,
                            name = obj.getString("name"),
                            colorHex = obj.getString("colorHex"),
                            iconEmoji = obj.getString("iconEmoji"),
                            parentFolderId = null,
                            createdAt = System.currentTimeMillis()
                        ))
                    } catch (e: Exception) {
                        android.util.Log.e("NotesRepository", "Failed to seed category at index $i", e)
                    }
                }
            }
            
            // Seed Illustrations
            val itemsArr = root.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    try {
                        val obj = itemsArr.getJSONObject(i)
                        val id = obj.getString("id")
                        val catId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.getString("categoryId") else null
                        
                        // Defensive check: if categoryId is provided but not in DB, skip or null it to avoid foreign key crash
                        val finalCatId = if (catId != null && mainDao.getCategoryById(catId) == null) {
                            android.util.Log.w("NotesRepository", "Illustration $id references unknown category $catId. Nulling.")
                            null
                        } else catId

                        illustrationDao.upsert(IllustrationEntity(
                            id = id,
                            title = obj.getString("title"),
                            bodyText = obj.getString("bodyText"),
                            categoryId = finalCatId,
                            scriptureReference = if (obj.has("scriptureReference") && !obj.isNull("scriptureReference")) obj.getString("scriptureReference") else null,
                            source = if (obj.has("source") && !obj.isNull("source")) obj.getString("source") else null,
                            imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl") else null,
                            isUserCreated = false,
                            isHidden = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        ))
                    } catch (e: Exception) {
                        android.util.Log.e("NotesRepository", "Failed to seed illustration at index $i", e)
                    }
                }
            }
            
            sharedPrefs.edit {
                putInt("illustration_content_version", assetVersion)
            }
        }
    }

    override suspend fun syncWithRemote(jsonManifest: String) {
        val root = org.json.JSONObject(jsonManifest)
        val remoteItemsArr = root.optJSONArray("items") ?: return
        val remoteIds = mutableSetOf<String>()
        
        for (i in 0 until remoteItemsArr.length()) {
            val obj = remoteItemsArr.getJSONObject(i)
            val id = obj.getString("id")
            remoteIds.add(id)
            
            illustrationDao.upsert(IllustrationEntity(
                id = id,
                title = obj.getString("title"),
                bodyText = obj.getString("bodyText"),
                categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.getString("categoryId") else null,
                scriptureReference = if (obj.has("scriptureReference") && !obj.isNull("scriptureReference")) obj.getString("scriptureReference") else null,
                source = if (obj.has("source") && !obj.isNull("source")) obj.getString("source") else null,
                imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl") else null,
                isUserCreated = false,
                isHidden = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
        }
        
        val allLocalCurated = illustrationDao.observeAll().first().filter { !it.isUserCreated }
        allLocalCurated.forEach { local ->
            if (!remoteIds.contains(local.id)) {
                illustrationDao.upsert(local.copy(isHidden = true))
            }
        }
    }
}

fun NotebookEntity.toDomain() = Notebook(id, title, categoryId, backgroundStyle, colorHex, createdAt, updatedAt)
fun Notebook.toEntity() = NotebookEntity(id, title, categoryId, backgroundStyle, colorHex, createdAt, updatedAt)

fun PageEntity.toDomain() = Page(id, notebookId, pageIndex, thumbnailPath, backgroundColorHex, createdAt, updatedAt)
fun Page.toEntity() = PageEntity(id, notebookId, pageIndex, thumbnailPath, backgroundColorHex, createdAt, updatedAt)

fun IllustrationEntity.toDomain() = Illustration(
    id = id,
    title = title,
    bodyText = bodyText,
    categoryId = categoryId,
    scriptureReference = scriptureReference,
    source = source,
    imageUrl = imageUrl,
    isUserCreated = isUserCreated,
    isHidden = isHidden,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Illustration.toEntity() = IllustrationEntity(
    id = id,
    title = title,
    bodyText = bodyText,
    categoryId = categoryId,
    scriptureReference = scriptureReference,
    source = source,
    imageUrl = imageUrl,
    isUserCreated = isUserCreated,
    isHidden = isHidden,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SermonTemplateEntity.toDomain() = SermonTemplate(
    id = id,
    title = title,
    categoryId = categoryId,
    scriptureReference = scriptureReference,
    summary = summary,
    tags = tags,
    isAiSuggested = isAiSuggested,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SermonTemplate.toEntity() = SermonTemplateEntity(
    id = id,
    title = title,
    categoryId = categoryId,
    scriptureReference = scriptureReference,
    summary = summary,
    tags = tags,
    isAiSuggested = isAiSuggested,
    createdAt = createdAt,
    updatedAt = updatedAt
)
