package com.example.notes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ShepherdRepository
import com.example.notes.domain.NotesRepository
import com.example.notes.domain.SermonTemplate
import com.example.notes.domain.Notebook
import com.example.notes.domain.Page
import com.example.notes.domain.CanvasObject
import com.example.notes.domain.PageBackgroundStyle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SermonIdeaViewModel(
    application: Application,
    private val notesRepository: NotesRepository,
    private val mainRepository: ShepherdRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val categories = mainRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<SermonTemplate>> = combine(
        _searchQuery.debounce(300),
        flowOf<String?>(null) // category filter handled by UI grouping for now
    ) { query, _ ->
        notesRepository.observeSermonTemplates(query)
    }.flatMapLatest { it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addTemplate(title: String, categoryId: String?, scripture: String?, summary: String, tags: String?) {
        viewModelScope.launch {
            notesRepository.upsertSermonTemplate(
                SermonTemplate(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    categoryId = categoryId,
                    scriptureReference = scripture,
                    summary = summary,
                    tags = tags,
                    isAiSuggested = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun startSermon(template: SermonTemplate, onCreated: (String, String) -> Unit) {
        viewModelScope.launch {
            val notebookId = UUID.randomUUID().toString()
            val pageId = UUID.randomUUID().toString()
            
            // 1. Create Notebook
            notesRepository.upsertNotebook(
                Notebook(
                    id = notebookId,
                    title = template.title,
                    backgroundStyle = PageBackgroundStyle.LINED,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            
            // 2. Create first Page
            notesRepository.upsertPage(
                Page(
                    id = pageId,
                    notebookId = notebookId,
                    pageIndex = 0,
                    thumbnailPath = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            
            // 3. Seed with RichTextObject containing scripture and summary
            val seedText = buildString {
                if (!template.scriptureReference.isNullOrBlank()) {
                    append("Scripture: ${template.scriptureReference}\n\n")
                }
                append(template.summary)
            }
            
            notesRepository.upsertElement(
                pageId,
                CanvasObject.RichTextObject(
                    id = UUID.randomUUID().toString(),
                    zIndex = 0,
                    x = 40f,
                    y = 40f,
                    width = 400f,
                    height = 200f,
                    text = seedText
                )
            )
            
            onCreated(pageId, notebookId)
        }
    }

    class Factory(
        private val application: Application,
        private val notesRepository: NotesRepository,
        private val mainRepository: ShepherdRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SermonIdeaViewModel(application, notesRepository, mainRepository) as T
        }
    }
}
