package com.example.preachmode

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.example.notes.domain.NotesRepository
import com.example.notes.domain.CanvasObject
import com.example.util.DocumentParser
import kotlinx.coroutines.flow.first
import java.io.File
import com.example.preachmode.model.PreachDocument
import com.example.preachmode.model.PreachSection
import com.example.preachmode.model.SectionType
import com.example.notes.ui.PageRenderer

interface PreachContentSource {
    suspend fun extractText(): String
    suspend fun extractDocument(): PreachDocument = PreachDocument(emptyList())
}

class FileContentSource(
    private val context: Context,
    private val filePath: String
) : PreachContentSource {
    override suspend fun extractText(): String {
        val resolvedFile = resolveToFile(context, filePath) ?: throw Exception("File not found")
        val paragraphs = DocumentParser.parseFile(resolvedFile.absolutePath)
        
        if (filePath.startsWith("content://") && resolvedFile.exists()) {
            resolvedFile.delete()
        }
        
        return paragraphs.joinToString("\n\n") { it.rawText }
    }

    private fun resolveToFile(context: Context, pathOrUri: String): File? {
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = pathOrUri.toUri()
                var displayName = "temp_file"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex) ?: "temp_file"
                    }
                }
                val ext = displayName.substringAfterLast('.', "txt")
                val cacheFile = File(context.cacheDir, "preach_mode_temp_${pathOrUri.hashCode()}.$ext")
                if (cacheFile.exists()) cacheFile.delete()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return cacheFile
            } catch (_: Exception) {
                return null
            }
        } else {
            val file = File(pathOrUri)
            if (file.exists()) return file
        }
        return null
    }
}

class NotesContentSource(
    private val context: Context,
    private val repository: NotesRepository,
    private val scope: NotesContentScope
) : PreachContentSource {

    sealed class NotesContentScope {
        data class Page(val pageId: String) : NotesContentScope()
        data class Notebook(val notebookId: String) : NotesContentScope()
    }

    override suspend fun extractText(): String = StringBuilder().apply {
        when (scope) {
            is NotesContentScope.Page -> {
                append(extractPageText(scope.pageId))
            }
            is NotesContentScope.Notebook -> {
                val pages = repository.observePagesForNotebook(scope.notebookId).first()
                pages.sortedBy { it.pageIndex }.forEach { page ->
                    val pageText = extractPageText(page.id)
                    if (pageText.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(pageText)
                    }
                }
            }
        }
    }.toString()

    private suspend fun extractPageText(pageId: String): String {
        val elements = repository.getElementsForPage(pageId)
        return elements.sortedBy { it.zIndex }.mapNotNull { element ->
            when (element) {
                is CanvasObject.RichTextObject -> element.text
                is CanvasObject.StrokeObject -> "[Handwriting]"
                else -> ""
            }
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    override suspend fun extractDocument(): PreachDocument {
        val pages = when (scope) {
            is NotesContentScope.Page -> listOfNotNull(repository.getPageById(scope.pageId))
            is NotesContentScope.Notebook -> repository.observePagesForNotebook(scope.notebookId).first()
        }

        val sections = pages.sortedBy { it.pageIndex }.map { page ->
            val elements = repository.getElementsForPage(page.id)
            val notebook = repository.getNotebookById(page.notebookId)
            val bitmap = PageRenderer.renderToBitmap(
                context = context,
                elements = elements,
                width = 1080,
                height = 1920,
                backgroundStyle = notebook?.backgroundStyle ?: com.example.notes.domain.PageBackgroundStyle.LINED,
                backgroundColorInt = page.backgroundColorHex.toColorInt()
            )
            
            val file = File(context.cacheDir, "preach_page_${page.id}.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            
            PreachSection(
                id = page.id,
                type = SectionType.PARAGRAPH,
                displayText = file.absolutePath,
                hiddenScripture = null,
                highlights = emptyList()
            )
        }
        
        return PreachDocument(sections)
    }
}

class MultiContentSource(
    private val sources: List<PreachContentSource>
) : PreachContentSource {
    override suspend fun extractText(): String {
        return sources.map { it.extractText() }.joinToString("\n\n---\n\n")
    }

    override suspend fun extractDocument(): PreachDocument {
        val allSections = sources.flatMap { it.extractDocument().sections }
        return PreachDocument(allSections)
    }
}
