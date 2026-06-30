package com.example.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ShepherdApplication
import com.example.data.remote.FormatMode
import com.example.data.repository.ShepherdRepository
import com.example.domain.model.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.UUID

@OptIn(FlowPreview::class)
class ShepherdViewModel(
    private val application: Application,
    private val repository: ShepherdRepository
) : AndroidViewModel(application) {

    // --- Core Files State ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _rawFiles = repository.getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fileUsageCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val fileUsageCounts: StateFlow<Map<String, Int>> = _fileUsageCounts.asStateFlow()

    val files: StateFlow<List<ShepherdFile>> = combine(_rawFiles, _fileUsageCounts) { rawList, usageMap ->
        rawList.map { file ->
            file.copy(usageCount = usageMap[file.id] ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Uriel Notifications ---
    private val _notifications = MutableStateFlow<List<ShepherdNotification>>(
        listOf(
            ShepherdNotification(
                id = "welcome",
                title = "Welcome back, Pastor!",
                message = "Uriel is ready to assist you in preparing and delivering powerful messages today.",
                timestamp = System.currentTimeMillis()
            )
        )
    )
    val notifications: StateFlow<List<ShepherdNotification>> = _notifications.asStateFlow()

    fun addNotification(title: String, message: String) {
        val list = _notifications.value.toMutableList()
        list.add(
            0,
            ShepherdNotification(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                timestamp = System.currentTimeMillis()
            )
        )
        _notifications.value = list
    }

    fun markNotificationsAsRead() {
        val list = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = list
    }

    fun incrementFileUsage(fileId: String) {
        val current = _fileUsageCounts.value.toMutableMap()
        current[fileId] = (current[fileId] ?: 0) + 1
        _fileUsageCounts.value = current
    }

    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = repository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = repository.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Document Presentation Parameters ---
    var activeViewerSermonId by androidx.compose.runtime.mutableStateOf("")
    var activeViewerFilePath by androidx.compose.runtime.mutableStateOf("")
    var activeViewerTitle by androidx.compose.runtime.mutableStateOf("")

    var livePreachDurationMinutes by androidx.compose.runtime.mutableStateOf(45)
    var livePreachScrollSpeed by androidx.compose.runtime.mutableStateOf(2)
    var livePreachFontScale by androidx.compose.runtime.mutableStateOf(1.3f)

    val sermons: StateFlow<List<Sermon>> = repository.getAllSermons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesList: StateFlow<List<Series>> = repository.getAllSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scriptureTags: StateFlow<List<ScriptureTag>> = repository.getAllScriptureTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Folder Selection Onboarding ---
    private val _rootFolderUri = MutableStateFlow<Uri?>(repository.getRootFolderUri())
    val rootFolderUri: StateFlow<Uri?> = _rootFolderUri.asStateFlow()

    // --- Onboarding Flow Tracker ---
    private val _isOnboarded = MutableStateFlow(
        application.getSharedPreferences("shepherd_prefs", Application.MODE_PRIVATE)
            .getBoolean("is_onboarded_completed", false)
    )
    val isOnboarded: StateFlow<Boolean> = _isOnboarded.asStateFlow()

    fun completeOnboarding() {
        application.getSharedPreferences("shepherd_prefs", Application.MODE_PRIVATE)
            .edit().putBoolean("is_onboarded_completed", true).apply()
        _isOnboarded.value = true
    }

    fun resetOnboarding() {
        application.getSharedPreferences("shepherd_prefs", Application.MODE_PRIVATE)
            .edit().putBoolean("is_onboarded_completed", false).apply()
        _isOnboarded.value = false
        repository.setRootFolderUri(null)
        _rootFolderUri.value = null
    }

    // --- Multi-Select Mode ---
    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    val isMultiSelectActive: StateFlow<Boolean> = _selectedFileIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleFileSelection(fileId: String) {
        val current = _selectedFileIds.value
        _selectedFileIds.value = if (current.contains(fileId)) {
            current - fileId
        } else {
            current + fileId
        }
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    // --- File List Filters ---
    private val _selectedCategoryIdForFilter = MutableStateFlow<String?>(null)
    val selectedCategoryIdForFilter: StateFlow<String?> = _selectedCategoryIdForFilter.asStateFlow()

    private val _selectedExtensionForFilter = MutableStateFlow<String?>(null)
    val selectedExtensionForFilter: StateFlow<String?> = _selectedExtensionForFilter.asStateFlow()

    fun selectCategoryFilter(categoryId: String?) {
        _selectedCategoryIdForFilter.value = categoryId
    }

    fun selectExtensionFilter(ext: String?) {
        _selectedExtensionForFilter.value = ext
    }

    val filteredFiles: StateFlow<List<ShepherdFile>> = combine(
        files, _selectedCategoryIdForFilter, _selectedExtensionForFilter
    ) { fileList, categoryId, ext ->
        var result = fileList
        if (categoryId != null) {
            result = result.filter { it.categoryId == categoryId }
        }
        if (ext != null) {
            result = result.filter { it.extension.lowercase() == ext.lowercase() }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Dynamic Search Screen States ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<ShepherdFile>> = _searchQuery
        .debounce(300)
        .combine(files) { query, fileList ->
            if (query.trim().isEmpty()) {
                emptyList()
            } else {
                fileList.filter { file ->
                    file.name.contains(query, ignoreCase = true) ||
                    file.parentPath.contains(query, ignoreCase = true) ||
                    file.extension.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Gemini AI Editor States ---
    private val _aiInputText = MutableStateFlow("")
    val aiInputText: StateFlow<String> = _aiInputText.asStateFlow()

    private val _aiFormattedTextResult = MutableStateFlow("")
    val aiFormattedTextResult: StateFlow<String> = _aiFormattedTextResult.asStateFlow()

    private val _aiIsGenerating = MutableStateFlow(false)
    val aiIsGenerating: StateFlow<Boolean> = _aiIsGenerating.asStateFlow()

    private val _aiGenerateMode = MutableStateFlow(FormatMode.SERMON)
    val aiGenerateMode: StateFlow<FormatMode> = _aiGenerateMode.asStateFlow()

    private var aiStreamingJob: Job? = null

    fun updateAiInput(text: String) {
        _aiInputText.value = text
    }

    fun setAiMode(mode: FormatMode) {
        _aiGenerateMode.value = mode
    }

    fun triggerGeminiFormat() {
        val input = _aiInputText.value
        val mode = _aiGenerateMode.value
        if (input.trim().isEmpty()) return

        aiStreamingJob?.cancel()
        _aiFormattedTextResult.value = ""
        _aiIsGenerating.value = true

        aiStreamingJob = viewModelScope.launch {
            repository.formatTextStream(input, mode)
                .collect { chunk ->
                    _aiFormattedTextResult.value = _aiFormattedTextResult.value + chunk
                }
            _aiIsGenerating.value = false
        }
    }

    fun clearAiCanvas() {
        aiStreamingJob?.cancel()
        _aiFormattedTextResult.value = ""
        _aiInputText.value = ""
        _aiIsGenerating.value = false
    }

    fun saveAiDocument(title: String, categoryId: String?, onComplete: () -> Unit) {
        viewModelScope.launch {
            val formatted = _aiFormattedTextResult.value
            val mode = _aiGenerateMode.value
            repository.saveAiDocument(formatted, title, mode, categoryId)
            syncFiles()
            onComplete()
        }
    }

    // --- Quick Note State & Autosave ---
    private val _quickNoteDraft = MutableStateFlow(
        application.getSharedPreferences("shepherd_prefs", Application.MODE_PRIVATE)
            .getString("quick_note_draft", "") ?: ""
    )
    val quickNoteDraft: StateFlow<String> = _quickNoteDraft.asStateFlow()

    init {
        // Autosave Quick Note draft every 20 seconds if changed
        viewModelScope.launch {
            var lastSavedText = _quickNoteDraft.value
            while (true) {
                delay(20000)
                val currentText = _quickNoteDraft.value
                if (currentText != lastSavedText) {
                    application.getSharedPreferences("shepherd_prefs", Application.MODE_PRIVATE)
                        .edit().putString("quick_note_draft", currentText).apply()
                    lastSavedText = currentText
                }
            }
        }
    }

    fun updateQuickNoteDraft(text: String) {
        _quickNoteDraft.value = text
    }

    fun discardQuickNote() {
        _quickNoteDraft.value = ""
        application.getSharedPreferences("shepherd_prefs", Application.MODE_PRIVATE)
            .edit().remove("quick_note_draft").apply()
    }

    // --- Settings and Polish ---
    private val _themeMode = MutableStateFlow(repository.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(repository.getHapticsEnabled())
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _animIntensity = MutableStateFlow(repository.getAnimationIntensity())
    val animIntensity: StateFlow<String> = _animIntensity.asStateFlow()

    private val _pastorName = MutableStateFlow(repository.getPastorName())
    val pastorName: StateFlow<String> = _pastorName.asStateFlow()

    private val _bibleVer = MutableStateFlow(repository.getBibleVersion())
    val bibleVer: StateFlow<String> = _bibleVer.asStateFlow()

    fun updateThemeMode(mode: String) {
        repository.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun updateHaptics(enabled: Boolean) {
        repository.setHapticsEnabled(enabled)
        _hapticsEnabled.value = enabled
    }

    fun updateAnimIntensity(intensity: String) {
        repository.setAnimationIntensity(intensity)
        _animIntensity.value = intensity
    }

    fun updatePastorName(name: String) {
        repository.setPastorName(name)
        _pastorName.value = name
    }

    fun updateBibleVersion(ver: String) {
        repository.setBibleVersion(ver)
        _bibleVer.value = ver
    }

    // --- Base File System Queries ---
    fun selectRootFolder(uri: Uri) {
        repository.setRootFolderUri(uri)
        _rootFolderUri.value = uri
        syncFiles()
    }

    fun syncFiles() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncError.value = null
        viewModelScope.launch {
            try {
                repository.syncFilesWithFileSystem()
            } catch (e: Exception) {
                _syncError.value = e.localizedMessage ?: "Sync error occurred"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun createCategory(name: String, colorHex: String, iconEmoji: String) {
        viewModelScope.launch {
            repository.createCategory(name, colorHex, iconEmoji)
            syncFiles()
        }
    }

    fun toggleBookmark(file: ShepherdFile, customLabel: String) {
        viewModelScope.launch {
            repository.toggleBookmark(file, customLabel)
        }
    }

    fun moveSelectedFilesToCategory(targetCategory: Category) {
        val selectedIds = _selectedFileIds.value
        viewModelScope.launch {
            val allFiles = files.value.associateBy { it.id }
            selectedIds.forEach { id ->
                val file = allFiles[id]
                if (file != null) {
                    repository.moveFile(file, targetCategory)
                }
            }
            clearSelection()
            syncFiles()
        }
    }

    fun updateFileCategory(fileId: String, categoryId: String?) {
        viewModelScope.launch {
            val file = files.value.find { it.id == fileId } ?: return@launch
            if (categoryId == null) {
                // If moving to null (uncategorized), we might just update DB or move to root
                // For simplicity, let's update categoryId in DB if your repo supports it
                repository.updateFileCategory(fileId, null)
            } else {
                val category = categories.value.find { it.id == categoryId } ?: return@launch
                repository.moveFile(file, category)
            }
            syncFiles()
        }
    }

    fun deleteSelectedFiles() {
        val selectedIds = _selectedFileIds.value
        viewModelScope.launch {
            val allFiles = files.value.associateBy { it.id }
            selectedIds.forEach { id ->
                val file = allFiles[id]
                if (file != null) {
                    repository.deleteFile(file)
                }
            }
            clearSelection()
            syncFiles()
        }
    }

    // --- Sermon Planning Operations ---
    fun createSermon(title: String, scripture: String, seriesId: String?, datePreached: Long?, notes: String, fileId: String?) {
        viewModelScope.launch {
            repository.createSermon(title, scripture, seriesId, datePreached, notes, fileId)
        }
    }

    fun createSeries(name: String, description: String, colorHex: String) {
        viewModelScope.launch {
            repository.createSeries(name, description, colorHex)
        }
    }

    // --- Calendar, Preaching Log, & Verse Usage State ---
    val upcomingEvents: StateFlow<List<com.example.data.local.SermonCalendarEntity>> = repository.getUpcomingEvents(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDayEvents = MutableStateFlow<List<com.example.data.local.SermonCalendarEntity>>(emptyList())
    val selectedDayEvents: StateFlow<List<com.example.data.local.SermonCalendarEntity>> = _selectedDayEvents.asStateFlow()

    private val _calendarSearchResults = MutableStateFlow<List<com.example.data.local.PreachingLogEntity>>(emptyList())
    val calendarSearchResults: StateFlow<List<com.example.data.local.PreachingLogEntity>> = _calendarSearchResults.asStateFlow()

    val preachingHistory: StateFlow<List<com.example.data.local.PreachingLogEntity>> = repository.getPreachingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scheduleEvent(context: android.content.Context, event: com.example.data.local.SermonCalendarEntity) {
        viewModelScope.launch {
            val eventId = repository.saveEvent(event).toInt()
            val saved = event.copy(id = eventId)
            com.example.util.SermonCalendarScheduler.schedule(context, saved) { reminderId, nowId ->
                val combinedId = "$reminderId|$nowId"
                viewModelScope.launch { repository.updateNotificationJobId(eventId, combinedId) }
            }
        }
    }

    fun deleteEvent(context: android.content.Context, event: com.example.data.local.SermonCalendarEntity) {
        viewModelScope.launch {
            com.example.util.SermonCalendarScheduler.cancel(context, event.id)
            repository.deleteEvent(event)
        }
    }

    fun onDaySelected(dateMs: Long) {
        val startOfDay = dateMs - (dateMs % 86400000)
        val endOfDay   = startOfDay + 86400000
        viewModelScope.launch {
            repository.getEventsBetween(startOfDay, endOfDay)
                .collect { _selectedDayEvents.value = it }
        }
    }

    fun searchByNaturalDate(query: String) {
        val cal = java.util.Calendar.getInstance()
        val (startMs, endMs) = when {
            query.contains("easter", true) -> {
                val y = cal.get(java.util.Calendar.YEAR)
                val start = java.util.Calendar.getInstance().apply { set(y, 2, 1, 0, 0, 0) }.timeInMillis
                val end = java.util.Calendar.getInstance().apply { set(y, 3, 30, 23, 59, 59) }.timeInMillis
                Pair(start, end)
            }
            query.contains("christmas", true) -> {
                val y = cal.get(java.util.Calendar.YEAR)
                val start = java.util.Calendar.getInstance().apply { set(y, 11, 15, 0, 0, 0) }.timeInMillis
                val end = java.util.Calendar.getInstance().apply { set(y, 11, 31, 23, 59, 59) }.timeInMillis
                Pair(start, end)
            }
            query.contains("last year", true) -> {
                cal.add(java.util.Calendar.YEAR, -1)
                val y = cal.get(java.util.Calendar.YEAR)
                val start = java.util.Calendar.getInstance().apply { set(y, 0, 1, 0, 0, 0) }.timeInMillis
                val end = java.util.Calendar.getInstance().apply { set(y, 11, 31, 23, 59, 59) }.timeInMillis
                Pair(start, end)
            }
            else -> {
                val yearMatch = Regex("\\b(20\\d{2})\\b").find(query)
                if (yearMatch != null) {
                    val y = yearMatch.value.toInt()
                    val start = java.util.Calendar.getInstance().apply { set(y, 0, 1, 0, 0, 0) }.timeInMillis
                    val end = java.util.Calendar.getInstance().apply { set(y, 11, 31, 23, 59, 59) }.timeInMillis
                    Pair(start, end)
                } else {
                    Pair(0L, 0L)
                }
            }
        }
        if (startMs == 0L) return
        viewModelScope.launch {
            repository.getPreachingByDateRange(startMs, endMs).collect { _calendarSearchResults.emit(it) }
        }
    }

    // --- State for active Sermon Document Viewer ---
    private val _paragraphs = MutableStateFlow<List<com.example.util.DocumentParser.AnnotatedParagraph>>(emptyList())
    val paragraphs: StateFlow<List<com.example.util.DocumentParser.AnnotatedParagraph>> = _paragraphs.asStateFlow()

    private val _alreadyPreachedWarning = MutableStateFlow<String?>(null)
    val alreadyPreachedWarning: StateFlow<String?> = _alreadyPreachedWarning.asStateFlow()

    fun loadDocumentFromUri(sermonId: String, uriString: String, title: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _paragraphs.emit(emptyList()) // show loading
            _alreadyPreachedWarning.emit(null)

            try {
                val uri = android.net.Uri.parse(uriString)
                val ext = when {
                    uriString.lowercase().contains(".pdf") || title.lowercase().contains(".pdf") -> "pdf"
                    uriString.lowercase().contains(".docx") || title.lowercase().contains(".docx") -> "docx"
                    else -> "txt"
                }

                val cacheFile = java.io.File(application.cacheDir, "temp_sermon_${sermonId.hashCode()}.$ext")
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                application.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                loadDocument(sermonId, cacheFile.absolutePath, title)
            } catch (e: Exception) {
                e.printStackTrace()
                _paragraphs.emit(emptyList())
            }
        }
    }

    fun loadDocument(sermonId: String, filePath: String, title: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                _paragraphs.emit(emptyList())
                _alreadyPreachedWarning.emit(null)
                return@launch
            }
            val ext = file.extension.uppercase()
            val parsed = when (ext) {
                "TXT"  -> com.example.util.DocumentParser.parseTxt(file.readText())
                "DOCX" -> com.example.util.DocumentParser.parseDocx(filePath)
                "PDF"  -> com.example.util.DocumentParser.extractPdfText(filePath)
                else   -> emptyList()
            }
            _paragraphs.emit(parsed)

            val refs = com.example.util.DocumentParser.extractAllRefs(parsed)
            if (refs.isNotEmpty()) {
                val overlaps = repository.checkVerseOverlap(refs)
                if (overlaps.isNotEmpty()) {
                    val overlap = overlaps.first()
                    val dateStr = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(overlap.datePreachedMs ?: 0L))
                    _alreadyPreachedWarning.emit(
                        "${overlap.verseReference} was used in \"${overlap.sermonTitle}\" on $dateStr"
                    )
                } else {
                    _alreadyPreachedWarning.emit(null)
                }
            } else {
                _alreadyPreachedWarning.emit(null)
            }
        }
    }

    fun saveVerseUsage(sermonId: String, title: String, verses: List<String>) {
        viewModelScope.launch {
            val list = verses.map {
                com.example.data.local.VerseUsageEntity(
                    verseReference = it,
                    sermonId = sermonId,
                    sermonTitle = title,
                    datePreachedMs = System.currentTimeMillis()
                )
            }
            repository.saveVerseUsage(list)
        }
    }

    fun logPreachingActivity(sermonId: String, title: String, eventName: String?, durationMinutes: Int, verses: List<String>) {
        viewModelScope.launch {
            val log = com.example.data.local.PreachingLogEntity(
                sermonId = sermonId,
                sermonTitle = title,
                datePreachedMs = System.currentTimeMillis(),
                eventName = eventName,
                durationMinutes = durationMinutes,
                versesJson = com.squareup.moshi.Moshi.Builder().build().adapter(List::class.java).toJson(verses)
            )
            repository.logPreaching(log)
        }
    }

    // --- Provider Factory ---
    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = (application as ShepherdApplication).repository
                return ShepherdViewModel(application, repo) as T
            }
        }
    }
}

data class ShepherdNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false
)
