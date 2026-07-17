package com.example.notes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notes.domain.Illustration
import com.example.notes.domain.NotesRepository
import com.example.domain.model.Category
import com.example.data.repository.ShepherdRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class IllustrationLibraryViewModel(
    application: Application,
    private val notesRepository: NotesRepository,
    private val mainRepository: ShepherdRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<Category>> = mainRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedIllustrations: StateFlow<Map<Category, List<Illustration>>> = combine(
        _searchQuery.debounce(300),
        categories,
        notesRepository.observeIllustrations("", null)
    ) { query, allCategories, allIllustrations ->
        val filtered = if (query.isBlank()) {
            allIllustrations
        } else {
            allIllustrations.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.bodyText.contains(query, ignoreCase = true) ||
                it.scriptureReference?.contains(query, ignoreCase = true) == true
            }
        }
        
        filtered.groupBy { ill -> 
            allCategories.find { it.id == ill.categoryId } ?: Category("uncategorized", "General", "#808080", "📚", null, 0L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val illustrations: StateFlow<List<Illustration>> = combine(
        _searchQuery.debounce(300),
        _selectedCategoryId
    ) { query, categoryId ->
        notesRepository.observeIllustrations(query, categoryId)
    }.flatMapLatest { it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun saveAsIllustration(title: String, body: String, categoryId: String?, scripture: String?) {
        viewModelScope.launch {
            notesRepository.upsertIllustration(
                Illustration(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    bodyText = body,
                    categoryId = categoryId,
                    scriptureReference = scripture,
                    source = null,
                    isUserCreated = true,
                    isHidden = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    class Factory(
        private val application: Application,
        private val notesRepository: NotesRepository,
        private val mainRepository: ShepherdRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return IllustrationLibraryViewModel(application, notesRepository, mainRepository) as T
        }
    }
}
