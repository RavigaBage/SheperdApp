package com.example.notes.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ShepherdApplication
import com.example.notes.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(FlowPreview::class)
class NotesViewModel(
    application: Application,
    private val repository: NotesRepository,
    private val pageId: String,
    private val notebookId: String
) : AndroidViewModel(application) {

    private val _canvasObjects = MutableStateFlow<List<CanvasObject>>(emptyList())
    val canvasObjects: StateFlow<List<CanvasObject>> = _canvasObjects.asStateFlow()

    private val _canvasState = MutableStateFlow(CanvasState())
    val canvasState: StateFlow<CanvasState> = _canvasState.asStateFlow()

    private val _backgroundStyle = MutableStateFlow(com.example.notes.domain.PageBackgroundStyle.LINED)
    val backgroundStyle: StateFlow<com.example.notes.domain.PageBackgroundStyle> = _backgroundStyle.asStateFlow()

    private val _backgroundColor = MutableStateFlow("#FFFFFF")
    val backgroundColor: StateFlow<String> = _backgroundColor.asStateFlow()

    private var initialObjects: List<CanvasObject> = emptyList()
    private var saveJob: Job? = null
    private var isLoaded = false

    init {
        loadPage()

        _canvasObjects
            .drop(1)
            .debounce(2000L)
            .onEach { if (isLoaded) savePage() }
            .launchIn(viewModelScope)
    }

    private fun loadPage() {
        viewModelScope.launch {
            var page = repository.getPageById(pageId)

            if (page == null) {
                val notebook = repository.getNotebookById(notebookId)
                page = Page(
                    id = pageId,
                    notebookId = notebookId,
                    pageIndex = 0,
                    thumbnailPath = null,
                    backgroundColorHex = notebook?.colorHex ?: "#FFFFFF",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.upsertPage(page)
            }

            if (page != null) {
                _backgroundColor.value = page.backgroundColorHex
                val notebook = repository.getNotebookById(notebookId)
                if (notebook != null) {
                    _backgroundStyle.value = notebook.backgroundStyle
                }
            }

            val loadedObjects = repository.getElementsForPage(pageId)
            _canvasObjects.value = loadedObjects.sortedBy { it.zIndex }
            initialObjects = loadedObjects
            isLoaded = true
        }
    }

    fun addCanvasObject(obj: CanvasObject) {
        _canvasObjects.value = (_canvasObjects.value + obj).sortedBy { it.zIndex }
    }

    fun updateCanvasObject(updatedObj: CanvasObject) {
        _canvasObjects.value = _canvasObjects.value.map {
            if (it.id == updatedObj.id) updatedObj else it
        }.sortedBy { it.zIndex }
    }

    fun removeCanvasObject(objId: String) {
        _canvasObjects.value = _canvasObjects.value.filterNot { it.id == objId }
    }

    fun setCanvasMode(mode: CanvasMode) {
        _canvasState.value = _canvasState.value.copy(activeMode = mode)
    }

    fun setBrushColor(color: String) {
        _canvasState.value = _canvasState.value.copy(brushColor = color)
    }

    fun setBrushSize(size: Float) {
        _canvasState.value = _canvasState.value.copy(brushSize = size)
    }

    fun setPenType(type: String) {
        _canvasState.value = _canvasState.value.copy(penType = type)
    }

    fun setFocusedText(id: String?) {
        _canvasState.value = _canvasState.value.copy(focusedTextId = id)
        if (id != null) {
            _canvasState.value = _canvasState.value.copy(selectedObjectId = null)
        }
    }

    fun setSelectedObject(id: String?) {
        _canvasState.value = _canvasState.value.copy(selectedObjectId = id)
        if (id != null) {
            _canvasState.value = _canvasState.value.copy(focusedTextId = null)
            // Bring to front
            _canvasObjects.value.find { it.id == id }?.let { obj ->
                val maxZ = (_canvasObjects.value.maxOfOrNull { it.zIndex } ?: 0)
                if (obj.zIndex < maxZ) {
                    updateCanvasObject(when(obj) {
                        is CanvasObject.RichTextObject -> obj.copy(zIndex = maxZ + 1)
                        is CanvasObject.ImageObject -> obj.copy(zIndex = maxZ + 1)
                        is CanvasObject.IllustrationObject -> obj.copy(zIndex = maxZ + 1)
                        is CanvasObject.StrokeObject -> obj.copy(zIndex = maxZ + 1)
                    })
                }
            }
        }
    }

    fun insertTextAt(x: Float, y: Float, text: String = "") {
        val nextZ = (_canvasObjects.value.maxOfOrNull { it.zIndex } ?: -1) + 1
        val newObj = CanvasObject.RichTextObject(
            id = UUID.randomUUID().toString(),
            zIndex = nextZ,
            x = x,
            y = y,
            width = 400f,
            height = 100f,
            text = text
        )
        addCanvasObject(newObj)
        setFocusedText(newObj.id)
    }

    fun insertTextFromLibrary(text: String) {
        insertTextAt(100f, 100f, text)
    }

    fun insertIllustrationFromLibrary(illustration: Illustration) {
        val activeTextObj = _canvasObjects.value.filterIsInstance<CanvasObject.RichTextObject>().lastOrNull() 
        
        if (activeTextObj != null) {
            val updated = activeTextObj.copy(text = activeTextObj.text + "\n" + illustration.bodyText)
            updateCanvasObject(updated)
        } else {
            val maxY = _canvasObjects.value.maxOfOrNull { 
                when(it) {
                    is CanvasObject.RichTextObject -> it.y + it.height
                    is CanvasObject.ImageObject -> it.y + it.height
                    is CanvasObject.IllustrationObject -> it.y + it.height
                    else -> 0f
                }
            } ?: 100f
            
            val nextZ = (_canvasObjects.value.maxOfOrNull { it.zIndex } ?: -1) + 1
            val newObj = CanvasObject.RichTextObject(
                id = UUID.randomUUID().toString(),
                zIndex = nextZ,
                x = 50f,
                y = maxY + 20f,
                width = 500f,
                height = 200f,
                text = illustration.bodyText
            )
            addCanvasObject(newObj)
        }
    }

    fun saveAsIllustration(title: String, elementId: String, categoryId: String?, scripture: String?) {
        val element = _canvasObjects.value.find { it.id == elementId } ?: return
        val body = when (element) {
            is CanvasObject.RichTextObject -> element.text
            is CanvasObject.StrokeObject -> ""
            else -> ""
        }
        
        if (body.isBlank()) return

        viewModelScope.launch {
            repository.upsertIllustration(
                com.example.notes.domain.Illustration(
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

    fun savePage(onComplete: () -> Unit = {}) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val currentObjects = _canvasObjects.value
            kotlinx.coroutines.withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                val page = repository.getPageById(pageId)
                repository.upsertPage(
                    (page ?: Page(
                        id = pageId,
                        notebookId = notebookId,
                        pageIndex = 0,
                        thumbnailPath = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )).copy(
                        notebookId = notebookId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                repository.syncElements(pageId, currentObjects)
            }
            onComplete()
        }
    }

    fun exportToPdf(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            ExportHelper.renderCanvasToPdf(context, _canvasObjects.value, _backgroundColor.value, _backgroundStyle.value, uri)
        }
    }

    class Factory(
        private val application: Application,
        private val repository: NotesRepository,
        private val pageId: String,
        private val notebookId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotesViewModel(application, repository, pageId, notebookId) as T
        }
    }
}
