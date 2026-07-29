package com.example.notes.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ShepherdApplication
import com.example.notes.audio.TextToSpeechManager
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

    private val _exportStatus = MutableSharedFlow<String>()
    val exportStatus: SharedFlow<String> = _exportStatus.asSharedFlow()

    private val ttsManager = TextToSpeechManager(application)
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

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
    fun addStroke(id: String, points: List<InkPoint>, colorHex: String, brushWidth: Float) {
        val nextZ = (_canvasObjects.value.maxOfOrNull { it.zIndex } ?: -1) + 1
        val newObj = CanvasObject.StrokeObject(
            id = id,
            zIndex = nextZ,
            points = points,
            colorHex = colorHex,
            brushWidth = brushWidth,
            brushFamily = _canvasState.value.penType
        )
        addCanvasObject(newObj)
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

    fun insertTextAt(x: Float, y: Float, canvasWidthPx: Float, text: String = "") {
        val nextZ = (_canvasObjects.value.maxOfOrNull { it.zIndex } ?: -1) + 1
        val newObj = CanvasObject.RichTextObject(
            id = UUID.randomUUID().toString(),
            zIndex = nextZ,
            x = 0f,                // always flush left, ignore tapped x
            y = y,                 // keep tapped vertical position
            width = canvasWidthPx, // always span full canvas width
            height = 100f,         // your existing default; auto-expands with content presumably
            text = text
        )
        addCanvasObject(newObj)
        setFocusedText(newObj.id)
    }

    fun insertTextFromLibrary(text: String, canvasWidthPx: Float) {
        val activeTextObj = _canvasObjects.value.filterIsInstance<CanvasObject.RichTextObject>().lastOrNull()
        val boxWidth = canvasWidthPx - 100f

        if (activeTextObj != null) {
            val newText = activeTextObj.text + "\n" + text
            val updated = activeTextObj.copy(
                text = newText,
                width = boxWidth,
                height = estimateTextHeight(newText, boxWidth)
            )
            updateCanvasObject(updated)
        } else {
            val maxY = _canvasObjects.value.maxOfOrNull {
                when (it) {
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
                width = boxWidth,
                height = estimateTextHeight(text, boxWidth),
                text = text
            )
            addCanvasObject(newObj)
        }
    }

    fun insertIllustrationFromLibrary(illustration: Illustration, canvasWidthPx: Float) {
        val activeTextObj = _canvasObjects.value.filterIsInstance<CanvasObject.RichTextObject>().lastOrNull()

        val boxWidth = canvasWidthPx - 100f // leave ~50f margin each side, matches x = 50f below

        if (activeTextObj != null) {
            val newText = activeTextObj.text + "\n" + illustration.bodyText
            val updated = activeTextObj.copy(
                text = newText,
                width = boxWidth,
                height = estimateTextHeight(newText, boxWidth)
            )
            updateCanvasObject(updated)
        } else {
            val maxY = _canvasObjects.value.maxOfOrNull {
                when (it) {
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
                width = boxWidth,
                height = estimateTextHeight(illustration.bodyText, boxWidth),
                text = illustration.bodyText
            )
            addCanvasObject(newObj)
        }
    }

    private fun estimateTextHeight(
        text: String,
        boxWidth: Float,
        fontSizePx: Float = 42f,   // tune to match your actual RichTextObject font size
        lineHeightPx: Float = 52f, // tune to match your renderer's line spacing
        verticalPaddingPx: Float = 40f,
        minHeightPx: Float = 150f
    ): Float {
        val avgCharWidthPx = fontSizePx * 0.55f // rough average for typical fonts
        val charsPerLine = (boxWidth / avgCharWidthPx).coerceAtLeast(1f)

        // account for explicit newlines the user's text already has
        val explicitLines = text.split("\n")
        val totalWrappedLines = explicitLines.sumOf { line ->
            kotlin.math.ceil(line.length / charsPerLine).toInt().coerceAtLeast(1)
        }

        val calculatedHeight = totalWrappedLines * lineHeightPx + verticalPaddingPx
        return calculatedHeight.coerceAtLeast(minHeightPx)
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

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        ttsManager.setSpeechRate(rate)
    }

    fun stopReading() {
        ttsManager.stop()
        _isSpeaking.value = false
    }

    fun readPageAloud() {
        val objects = _canvasObjects.value.filterIsInstance<CanvasObject.RichTextObject>()
            .sortedBy { it.y }

        if (objects.isEmpty()) {
            viewModelScope.launch {
                _exportStatus.emit("No text found on this page to read.")
            }
            return
        }

        _isSpeaking.value = true
        ttsManager.stop() // Clear any existing queue

        val listRegex = "^([-*•]|\\d+\\.)".toRegex()

        objects.forEachIndexed { blockIndex, richText ->
            val text = richText.text
            val spans = richText.annotatedStringJson.decodeSpans().sortedBy { it.start }
            
            val lines = text.split("\n")
            lines.forEachIndexed { lineIndex, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachIndexed

                // Handle bold/heading emphasis with pauses
                // We check if the line falls within a bold span
                // For simplicity, if any part of the line is bold, we add emphasis
                // Calculating exact offsets in split lines:
                val lineStartInText = text.indexOf(line) // Approximate if same line appears multiple times
                val lineEndInText = lineStartInText + line.length
                val hasBold = spans.any { it.bold && it.start < lineEndInText && it.end > lineStartInText }

                if (hasBold) {
                    ttsManager.playSilence(200, "bold_start_${blockIndex}_${lineIndex}")
                }

                val isListItem = listRegex.containsMatchIn(trimmed)
                ttsManager.speak(trimmed, "line_${blockIndex}_${lineIndex}")
                
                if (hasBold) {
                    ttsManager.playSilence(200, "bold_end_${blockIndex}_${lineIndex}")
                }

                if (isListItem) {
                    ttsManager.playSilence(400, "list_pause_${blockIndex}_${lineIndex}")
                } else {
                    ttsManager.playSilence(100, "line_pause_${blockIndex}_${lineIndex}")
                }
            }

            // Longer pause between separate blocks
            if (blockIndex < objects.size - 1) {
                ttsManager.playSilence(600, "block_pause_$blockIndex")
            } else {
                // Last block
                ttsManager.playSilence(200, "final_pause") {
                    _isSpeaking.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
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
