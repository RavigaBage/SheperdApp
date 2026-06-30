package com.example.preachmode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.preachmode.model.PreachDocument
import com.example.preachmode.model.PreachStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class PreachModeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = PreachRepository(application, database)

    private val _uiState = MutableStateFlow<PreachUiState>(PreachUiState.Loading)
    val uiState: StateFlow<PreachUiState> = _uiState.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _annotations = MutableStateFlow<Map<Int, List<PreachStroke>>>(emptyMap())
    val annotations: StateFlow<Map<Int, List<PreachStroke>>> = _annotations.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    fun addStroke(pageIndex: Int, stroke: PreachStroke) {
        _annotations.update { current ->
            val list = current[pageIndex]?.toMutableList() ?: mutableListOf()
            list.add(stroke)
            current + (pageIndex to list)
        }
    }

    fun undoStroke(pageIndex: Int) {
        _annotations.update { current ->
            val list = current[pageIndex]?.toMutableList() ?: return@update current
            if (list.isNotEmpty()) {
                list.removeAt(list.size - 1)
            }
            current + (pageIndex to list)
        }
    }

    fun clearAnnotations(pageIndex: Int) {
        _annotations.update { current ->
            current + (pageIndex to emptyList())
        }
    }

    fun clearAllAnnotations() {
        _annotations.value = emptyMap()
    }

    fun startPreaching(filePath: String, durationMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.emit(PreachUiState.Loading)
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.emit(PreachUiState.Error("File not found on device.", PreachDocument(emptyList())))
                return@launch
            }

            val ext = file.extension.uppercase()
            val rawText = when (ext) {
                "TXT" -> {
                    try { file.readText() } catch (e: Exception) { "" }
                }
                "DOCX" -> {
                    com.example.util.DocumentParser.parseDocx(filePath).joinToString("\n\n") { it.rawText }
                }
                "PDF" -> {
                    com.example.util.DocumentParser.extractPdfText(filePath).joinToString("\n\n") { it.rawText }
                }
                else -> ""
            }

            if (rawText.isBlank()) {
                _uiState.emit(PreachUiState.Error("Document has no readable text content.", PreachDocument(emptyList())))
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

            // Start timer
            startTimer(durationMinutes * 60 * 1000L)
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
    data class Success(val document: PreachDocument, val isFallback: Boolean) : PreachUiState
    data class Error(val message: String, val emptyDoc: PreachDocument) : PreachUiState
}
