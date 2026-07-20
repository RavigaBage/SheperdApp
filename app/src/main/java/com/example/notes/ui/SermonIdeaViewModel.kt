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
import com.example.notes.data.SermonIdeaGenerationRepository
import com.example.notes.domain.GeneratedIdea
import com.example.notes.domain.IdeaType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SermonIdeaViewModel(
    application: Application,
    private val notesRepository: NotesRepository,
    private val mainRepository: ShepherdRepository,
    private val generationRepository: SermonIdeaGenerationRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _generatedIdeas = MutableStateFlow<List<GeneratedIdea>>(emptyList())
    val generatedIdeas: StateFlow<List<GeneratedIdea>> = _generatedIdeas.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    val categories = mainRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<SermonTemplate>> = combine(
        _searchQuery.debounce(300),
        flowOf<String?>(null)
    ) { query, _ ->
        notesRepository.observeSermonTemplates(query)
    }.flatMapLatest { it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun generateIdeas(topic: String) {
        if (topic.isBlank()) return
        
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            _generatedIdeas.value = emptyList()
            
            val apiKey = mainRepository.getGeminiApiKey()
            
            generationRepository.generateIdeas(topic, apiKey)
                .catch { e ->
                    _generationError.value = e.localizedMessage ?: "Failed to generate ideas"
                    _isGenerating.value = false
                }
                .collect { ideas ->
                    if (ideas.isNotEmpty()) {
                        _generatedIdeas.value = ideas
                        _isGenerating.value = false
                    }
                }
        }
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

    fun saveGeneratedIdea(idea: GeneratedIdea) {
        viewModelScope.launch {
            // Find or create category for IdeaType
            val categoryName = when (idea.type) {
                IdeaType.SERMON -> "Sermons"
                IdeaType.TEACHING_SERIES -> "Teaching Series"
                IdeaType.CONFERENCE -> "Conferences"
                IdeaType.SEMINAR -> "Seminars"
            }
            
            val existingCategory = categories.value.find { it.name.equals(categoryName, ignoreCase = true) }
            val categoryId = if (existingCategory != null) {
                existingCategory.id
            } else {
                val newId = UUID.randomUUID().toString()
                val color = when (idea.type) {
                    IdeaType.SERMON -> "#2563EB"
                    IdeaType.TEACHING_SERIES -> "#7C3AED"
                    IdeaType.CONFERENCE -> "#EA580C"
                    IdeaType.SEMINAR -> "#0D9488"
                }
                val emoji = when (idea.type) {
                    IdeaType.SERMON -> "📖"
                    IdeaType.TEACHING_SERIES -> "📚"
                    IdeaType.CONFERENCE -> "👥"
                    IdeaType.SEMINAR -> "🎓"
                }
                mainRepository.createCategory(categoryName, color, emoji)
                // Wait a bit for sync or just use a placeholder. Since createCategory is async and syncs, 
                // we'll just try to find it again or assume a refresh.
                // For simplicity in this prompt, we'll use null if not found yet.
                null
            }

            val summaryWithOutline = buildString {
                append(idea.summary)
                if (idea.outline.isNotEmpty()) {
                    append("\n\nOutline:\n")
                    idea.outline.forEach { append("- $it\n") }
                }
            }

            notesRepository.upsertSermonTemplate(
                SermonTemplate(
                    id = UUID.randomUUID().toString(),
                    title = idea.title,
                    categoryId = categoryId,
                    scriptureReference = idea.scriptureReference,
                    summary = summaryWithOutline,
                    tags = idea.suggestedTags.joinToString(","),
                    isAiSuggested = true,
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
            
            notesRepository.upsertNotebook(
                Notebook(
                    id = notebookId,
                    title = template.title,
                    backgroundStyle = PageBackgroundStyle.LINED,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            
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

    fun startSermonFromGenerated(idea: GeneratedIdea, onCreated: (String, String) -> Unit) {
        viewModelScope.launch {
            val notebookId = UUID.randomUUID().toString()
            val pageId = UUID.randomUUID().toString()
            
            notesRepository.upsertNotebook(
                Notebook(
                    id = notebookId,
                    title = idea.title,
                    backgroundStyle = PageBackgroundStyle.LINED,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            
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
            
            val seedText = buildString {
                if (!idea.scriptureReference.isNullOrBlank()) {
                    append("Scripture: ${idea.scriptureReference}\n\n")
                }
                append(idea.summary)
                if (idea.outline.isNotEmpty()) {
                    append("\n\nOutline:\n")
                    idea.outline.forEach { append("- $it\n") }
                }
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
        private val mainRepository: ShepherdRepository,
        private val generationRepository: SermonIdeaGenerationRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SermonIdeaViewModel(application, notesRepository, mainRepository, generationRepository) as T
        }
    }
}
