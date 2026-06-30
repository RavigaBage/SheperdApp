package com.example.presentation.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ShepherdApplication
import com.example.data.repository.BoardRepository
import com.example.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoardViewModel(application: Application, private val repository: BoardRepository) : AndroidViewModel(application) {

    val allBoards = repository.getAllBoards()

    private val _currentStrokes = mutableStateListOf<BoardStroke>()
    val currentStrokes: List<BoardStroke> = _currentStrokes

    private val _currentElements = mutableStateListOf<BoardElement>()
    val currentElements: List<BoardElement> = _currentElements

    private val undoStack = mutableListOf<BoardAction>()
    private val redoStack = mutableListOf<BoardAction>()

    private val _currentTool = mutableStateOf(BoardToolType.PEN_MEDIUM)
    val currentTool: State<BoardToolType> = _currentTool

    private val _currentColor = mutableStateOf(0xFF1B2B4B.toInt()) // Navy
    val currentColor: State<Int> = _currentColor

    private val _currentWidth = mutableStateOf(5f)
    val currentWidth: State<Float> = _currentWidth

    private val _isSaving = mutableStateOf(false)
    val isSaving: State<Boolean> = _isSaving

    private var autoSaveJob: Job? = null
    private var currentBoardId: String? = null

    fun selectBoard(boardId: String) {
        currentBoardId = boardId
        viewModelScope.launch {
            val (strokes, elements) = repository.loadBoardContent(boardId)
            _currentStrokes.clear()
            _currentStrokes.addAll(strokes)
            _currentElements.clear()
            _currentElements.addAll(elements)
            undoStack.clear()
            redoStack.clear()
        }
    }

    fun addStroke(stroke: BoardStroke) {
        _currentStrokes.add(stroke)
        undoStack.add(BoardAction.AddStroke(stroke))
        redoStack.clear()
        triggerAutoSave()
    }

    fun setTool(tool: BoardToolType) {
        _currentTool.value = tool
    }

    fun setColor(color: Int) {
        _currentColor.value = color
    }

    fun setWidth(width: Float) {
        _currentWidth.value = width
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(action)
            applyAction(action, reverse = true)
            triggerAutoSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(action)
            applyAction(action, reverse = false)
            triggerAutoSave()
        }
    }

    private fun applyAction(action: BoardAction, reverse: Boolean) {
        when (action) {
            is BoardAction.AddStroke -> {
                if (reverse) _currentStrokes.remove(action.stroke)
                else _currentStrokes.add(action.stroke)
            }
            is BoardAction.AddElement -> {
                if (reverse) _currentElements.remove(action.element)
                else _currentElements.add(action.element)
            }
        }
    }

    private fun triggerAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1000)
            saveCurrentBoard()
        }
    }

    private suspend fun saveCurrentBoard() {
        val boardId = currentBoardId ?: return
        _isSaving.value = true
        repository.saveBoardContent(boardId, _currentStrokes.toList(), _currentElements.toList())
        delay(500) // Visual feedback
        _isSaving.value = false
    }

    fun createBoard(title: String, template: BoardTemplate, isPaged: Boolean) {
        viewModelScope.launch {
            val newBoard = repository.createBoard(title, template, isPaged)
            selectBoard(newBoard.id)
        }
    }

    sealed class BoardAction {
        data class AddStroke(val stroke: BoardStroke) : BoardAction()
        data class AddElement(val element: BoardElement) : BoardAction()
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = (application as ShepherdApplication).boardRepository
                return BoardViewModel(application, repo) as T
            }
        }
    }
}
