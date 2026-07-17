package com.example.preachmode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.preachmode.model.PreachDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreachModeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = PreachRepository(application, database)

    private val _uiState = MutableStateFlow<PreachUiState>(PreachUiState.Loading)
    val uiState: StateFlow<PreachUiState> = _uiState.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    fun startPreaching(source: PreachContentSource, durationMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.emit(PreachUiState.Loading)
            
            try {
                val doc = source.extractDocument()
                if (doc.sections.isNotEmpty()) {
                    _uiState.emit(PreachUiState.Success(doc, isFallback = false))
                    startTimer(durationMinutes * 60 * 1000L)
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.e("PreachModeViewModel", "Failed to extract document", e)
            }

            val rawText = try {
                source.extractText()
            } catch (e: Exception) {
                _uiState.emit(PreachUiState.Error("Could not read content.", PreachDocument(emptyList())))
                return@launch
            }

            if (rawText.isBlank()) {
                _uiState.emit(PreachUiState.Empty)
                return@launch
            }

            val fileHash = repository.calculateFileHash(rawText)
            val result = repository.prepareDocument(rawText, fileHash)

            result.fold(
                onSuccess = { doc ->
                    _uiState.emit(PreachUiState.Success(doc, isFallback = false))
                },
                onFailure = { error ->
                    val fallbackDoc = repository.createFallbackDocument(rawText)
                    _uiState.emit(PreachUiState.Success(fallbackDoc, isFallback = true))
                }
            )

            startTimer(durationMinutes * 60 * 1000L)
        }
    }

    fun setEmptyState() {
        viewModelScope.launch {
            _uiState.emit(PreachUiState.Empty)
        }
    }

    private fun startTimer(totalMillis: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            TimerEngine.startTimer(totalMillis).collect { state ->
                _timerState.emit(state)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

sealed interface PreachUiState {
    object Loading : PreachUiState
    object Empty : PreachUiState
    data class Success(val document: PreachDocument, val isFallback: Boolean) : PreachUiState
    data class Error(val message: String, val emptyDoc: PreachDocument) : PreachUiState
}
