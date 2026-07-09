package com.example.preachmode

import android.app.Application
import android.content.Context
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.preachmode.model.PreachDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PreachModeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = PreachRepository(application, database)

    private val _uiState = MutableStateFlow<PreachUiState>(PreachUiState.Loading)
    val uiState: StateFlow<PreachUiState> = _uiState.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    private fun resolveToFile(context: Context, pathOrUri: String): File? {
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = android.net.Uri.parse(pathOrUri)
                var displayName = "temp_file"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex) ?: "temp_file"
                    }
                }
                if (displayName == "temp_file") {
                    val lastSegment = uri.lastPathSegment
                    if (!lastSegment.isNullOrBlank()) {
                        displayName = lastSegment
                    }
                }
                val ext = displayName.substringAfterLast('.', "txt")
                val cacheFile = File(context.cacheDir, "preach_mode_temp_${pathOrUri.hashCode()}.$ext")
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return cacheFile
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        } else {
            val file = File(pathOrUri)
            if (file.exists()) {
                return file
            }
        }
        return null
    }

    fun startPreaching(filePath: String, durationMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.emit(PreachUiState.Loading)
            
            val context = getApplication<Application>()
            val resolvedFile = resolveToFile(context, filePath)
            if (resolvedFile == null || !resolvedFile.exists()) {
                _uiState.emit(PreachUiState.Error("File not found on device.", PreachDocument(emptyList())))
                return@launch
            }

            val rawText = try {
                com.example.util.DocumentParser.parseFile(resolvedFile.absolutePath).joinToString("\n\n") { it.rawText }
            } catch (e: com.example.util.DocumentParser.PasswordException) {
                _uiState.emit(PreachUiState.Error("This PDF is password-protected. Please open and unlock it first.", PreachDocument(emptyList())))
                return@launch
            } catch (e: com.example.util.DocumentParser.EncryptionException) {
                _uiState.emit(PreachUiState.Error("This file is encrypted and cannot be parsed.", PreachDocument(emptyList())))
                return@launch
            } catch (e: com.example.util.DocumentParser.CorruptFileException) {
                _uiState.emit(PreachUiState.Error("This file appears to be corrupted.", PreachDocument(emptyList())))
                return@launch
            } catch (e: com.example.util.DocumentParser.UnsupportedFormatException) {
                _uiState.emit(PreachUiState.Error("This format is not supported yet.", PreachDocument(emptyList())))
                return@launch
            } catch (e: Exception) {
                _uiState.emit(PreachUiState.Error("Could not read file.", PreachDocument(emptyList())))
                return@launch
            } finally {
                if (filePath.startsWith("content://") && resolvedFile.exists()) {
                    try {
                        resolvedFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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
