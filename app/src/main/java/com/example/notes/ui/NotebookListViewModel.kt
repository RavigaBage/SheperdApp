package com.example.notes.ui

import android.app.Application
import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notes.domain.Notebook
import com.example.notes.domain.NotesRepository
import com.example.notes.domain.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class NotebookListViewModel(
    application: Application,
    private val repository: NotesRepository
) : AndroidViewModel(application) {

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    val notebooks: StateFlow<List<Notebook>> = repository.observeNotebooks()
        .onEach { _isInitialLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedNotebook = MutableStateFlow<Notebook?>(null)
    val selectedNotebook: StateFlow<Notebook?> = _selectedNotebook.asStateFlow()

    private val _exportStatus = MutableSharedFlow<String>()
    val exportStatus: SharedFlow<String> = _exportStatus.asSharedFlow()

    fun selectNotebook(notebook: Notebook?) {
        _selectedNotebook.value = notebook
    }

    fun createNotebook(title: String, style: com.example.notes.domain.PageBackgroundStyle = com.example.notes.domain.PageBackgroundStyle.LINED, colorHex: String = "#FFFFFF") {
        viewModelScope.launch {
            repository.upsertNotebook(
                Notebook(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    backgroundStyle = style,
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateNotebookStyle(notebookId: String, newStyle: com.example.notes.domain.PageBackgroundStyle) {
        viewModelScope.launch {
            val notebook = repository.getNotebookById(notebookId)
            if (notebook != null) {
                repository.upsertNotebook(notebook.copy(
                    backgroundStyle = newStyle,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    fun deleteNotebook(id: String) {
        viewModelScope.launch {
            repository.deleteNotebook(id)
        }
    }

    fun createPage(notebookId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val notebook = repository.getNotebookById(notebookId)
            val existingPages = repository.observePagesForNotebook(notebookId).first()
            val nextIndex = (existingPages.maxOfOrNull { it.pageIndex } ?: -1) + 1
            
            val pageId = UUID.randomUUID().toString()
            repository.upsertPage(
                Page(
                    id = pageId,
                    notebookId = notebookId,
                    pageIndex = nextIndex,
                    thumbnailPath = null,
                    backgroundColorHex = notebook?.colorHex ?: "#FFFFFF",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            onCreated(pageId)
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            repository.deletePage(pageId)
        }
    }

    fun reorderPages(notebookId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val pages = repository.observePagesForNotebook(notebookId).first().sortedBy { it.pageIndex }.toMutableList()
            if (fromIndex !in pages.indices || toIndex !in pages.indices) return@launch
            
            val page = pages.removeAt(fromIndex)
            pages.add(toIndex, page)
            
            pages.forEachIndexed { index, p ->
                if (p.pageIndex != index) {
                    repository.upsertPage(p.copy(pageIndex = index))
                }
            }
        }
    }

    fun observePages(notebookId: String) = repository.observePagesForNotebook(notebookId)

    fun exportNotebookAsPdf(context: Context, notebookId: String, notebookTitle: String) {
        viewModelScope.launch {
            _exportStatus.emit("Exporting Notebook...")
            val success = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val notebook = repository.getNotebookById(notebookId) ?: return@withContext false
                    val pages = repository.observePagesForNotebook(notebookId).first()
                    val pdfDocument = PdfDocument()
                    
                    pages.sortedBy { it.pageIndex }.forEachIndexed { index, page ->
                        val elements = repository.getElementsForPage(page.id)
                        val width = 595 // A4 width in points
                        val height = 842 // A4 height in points
                        
                        val pageInfo = PdfDocument.PageInfo.Builder(width, height, index).create()
                        val pdfPage = pdfDocument.startPage(pageInfo)
                        
                        val canvas = pdfPage.canvas
                        // Scale 1080 -> 595
                        val scale = width.toFloat() / 1080f
                        canvas.scale(scale, scale)
                        
                        // Draw page background
                        val bgPaint = android.graphics.Paint().apply { 
                            color = try { android.graphics.Color.parseColor(page.backgroundColorHex) } catch(e: Exception) { android.graphics.Color.WHITE } 
                        }
                        canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)
                        PageBackgroundRenderer.draw(canvas, 1080f, 1920f, notebook.backgroundStyle)
                        
                        PageRenderer.render(context, elements, canvas)
                        pdfDocument.finishPage(pdfPage)
                    }
                    
                    val exportDir = File(context.cacheDir, "exports")
                    if (!exportDir.exists()) exportDir.mkdirs()
                    val file = File(exportDir, "${notebookTitle.replace(" ", "_")}.pdf")
                    
                    FileOutputStream(file).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()
                    
                    viewModelScope.launch(Dispatchers.Main) {
                        ExportHelper.shareFile(context, file, "application/pdf", "Share Notebook")
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            if (success) {
                _exportStatus.emit("Export Successful")
            } else {
                _exportStatus.emit("Export Failed")
            }
        }
    }

    class Factory(
        private val application: Application,
        private val repository: NotesRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotebookListViewModel(application, repository) as T
        }
    }
}
