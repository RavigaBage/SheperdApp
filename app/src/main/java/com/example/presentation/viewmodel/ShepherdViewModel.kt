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
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

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

    // --- Recycle Bin / Trash ---
    val trashedFiles: StateFlow<List<ShepherdFile>> = repository.getTrashedFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Sweep anything that's been sitting in Trash past the retention window.
        viewModelScope.launch { repository.purgeExpiredTrash() }

        // Trigger automatic sync on startup if a root folder is already connected
        if (repository.getRootFolderUri() != null) {
            syncFiles()
        }

        viewModelScope.launch {
            delay(1000) // Artificial delay to ensure UI transition or just to show skeleton
            _isInitialLoading.value = false
        }
    }

    fun restoreFile(file: ShepherdFile) {
        viewModelScope.launch {
            val targetCategory = file.categoryId?.let { id -> categories.value.find { it.id == id } }
            repository.restoreFile(file, targetCategory)
            syncFiles()
        }
    }

    fun permanentlyDeleteFile(file: ShepherdFile) {
        viewModelScope.launch {
            repository.permanentlyDeleteFile(file)
            syncFiles()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashedFiles.value.forEach { repository.permanentlyDeleteFile(it) }
            syncFiles()
        }
    }

    /** Physically renames the file on disk; [onResult] reports true/false so the UI can show an error. */
    fun renameFile(file: ShepherdFile, newName: String, onResult: (Boolean) -> Unit = {}) {
        if (newName.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = repository.renameFile(file, newName.trim())
            if (success) syncFiles()
            onResult(success)
        }
    }

    /** Creates a new empty file inside a category (or root if categoryId is null). */
    fun createFile(name: String, extension: String, categoryId: String?, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.createNewFile(name, extension, categoryId)
            syncFiles()
            onComplete(success)
        }
    }

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
    var activeViewerIsNote by androidx.compose.runtime.mutableStateOf(false)
    var activeViewerIsNotebookScope by androidx.compose.runtime.mutableStateOf(false)
    var activeViewerAttachmentUris by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())

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

    // --- Uriel Assistant States ---
    val bibleCache: StateFlow<List<com.example.data.local.BibleCacheEntity>> = repository.getAllBibleCacheFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getBibleVerse(ref: String, translation: String): String = repository.getBibleVerse(ref, translation)

    private val _urielResult = MutableStateFlow("")
    val urielResult: StateFlow<String> = _urielResult.asStateFlow()

    private val _urielIsGenerating = MutableStateFlow(false)
    val urielIsGenerating: StateFlow<Boolean> = _urielIsGenerating.asStateFlow()

    private val _urielSelectedWord = MutableStateFlow("")
    val urielSelectedWord: StateFlow<String> = _urielSelectedWord.asStateFlow()

    private val _urielActiveType = MutableStateFlow("")
    val urielActiveType: StateFlow<String> = _urielActiveType.asStateFlow()

    private val _urielTopic = MutableStateFlow("")
    val urielTopic: StateFlow<String> = _urielTopic.asStateFlow()

    private val _urielThemeDraftResult = MutableStateFlow("")
    val urielThemeDraftResult: StateFlow<String> = _urielThemeDraftResult.asStateFlow()

    private val _urielThemeIsGenerating = MutableStateFlow(false)
    val urielThemeIsGenerating: StateFlow<Boolean> = _urielThemeIsGenerating.asStateFlow()

    fun getUrielWordInsight(word: String, type: String, customPrompt: String = "") {
        if (word.trim().isEmpty()) return
        aiStreamingJob?.cancel()
        _urielResult.value = ""
        _urielIsGenerating.value = true
        _urielSelectedWord.value = word
        _urielActiveType.value = type
        viewModelScope.launch {
            repository.getUrielWordInsightStream(word, type, customPrompt).collect { chunk ->
                _urielResult.value = _urielResult.value + chunk
            }
            _urielIsGenerating.value = false
        }
    }

    fun getUrielScriptureIntelligence(scriptureRef: String, type: String) {
        if (scriptureRef.trim().isEmpty()) return
        aiStreamingJob?.cancel()
        _urielResult.value = ""
        _urielIsGenerating.value = true
        _urielSelectedWord.value = scriptureRef
        _urielActiveType.value = type
        viewModelScope.launch {
            repository.getUrielScriptureIntelligenceStream(scriptureRef, type).collect { chunk ->
                _urielResult.value = _urielResult.value + chunk
            }
            _urielIsGenerating.value = false
        }
    }

    fun generateUrielThemeDraft(topic: String) {
        if (topic.trim().isEmpty()) return
        _urielTopic.value = topic
        _urielThemeDraftResult.value = ""
        _urielThemeIsGenerating.value = true
        viewModelScope.launch {
            repository.getUrielThemeDraftStream(topic).collect { chunk ->
                _urielThemeDraftResult.value = _urielThemeDraftResult.value + chunk
            }
            _urielThemeIsGenerating.value = false
        }
    }

    // --- Daily Prayer Log States ---
    private val prayerPrefs by lazy {
        application.getSharedPreferences("shepherd_prayer_prefs", Application.MODE_PRIVATE)
    }

    private val _hasPrayedToday = MutableStateFlow(false)
    val hasPrayedToday: StateFlow<Boolean> = _hasPrayedToday.asStateFlow()

    private val _prayerTimerSecondsRemaining = MutableStateFlow(0)
    val prayerTimerSecondsRemaining: StateFlow<Int> = _prayerTimerSecondsRemaining.asStateFlow()

    private val _isPrayerTimerRunning = MutableStateFlow(false)
    val isPrayerTimerRunning: StateFlow<Boolean> = _isPrayerTimerRunning.asStateFlow()

    private var prayerTimerJob: Job? = null

    init {
        // Initialize daily prayer log state
        val key = getTodayPrayerKey()
        _hasPrayedToday.value = prayerPrefs.getBoolean(key, false)
    }

    private fun getTodayPrayerKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return "prayed_" + sdf.format(java.util.Date())
    }

    fun markAsPrayedToday() {
        val key = getTodayPrayerKey()
        prayerPrefs.edit().putBoolean(key, true).apply()
        _hasPrayedToday.value = true
        addNotification("Spiritual Prep Done", "You've logged prayer for today. Study Uriel Companion is ready.")
    }

    fun resetPrayerLog() {
        val key = getTodayPrayerKey()
        prayerPrefs.edit().putBoolean(key, false).apply()
        _hasPrayedToday.value = false
    }

    fun startPrayerTimer(durationMinutes: Int = 10) {
        prayerTimerJob?.cancel()
        _prayerTimerSecondsRemaining.value = durationMinutes * 60
        _isPrayerTimerRunning.value = true
        prayerTimerJob = viewModelScope.launch {
            while (_prayerTimerSecondsRemaining.value > 0) {
                delay(1000)
                _prayerTimerSecondsRemaining.value -= 1
            }
            _isPrayerTimerRunning.value = false
            markAsPrayedToday()
        }
    }

    fun stopPrayerTimer() {
        prayerTimerJob?.cancel()
        _isPrayerTimerRunning.value = false
        _prayerTimerSecondsRemaining.value = 0
    }

    fun saveVerseUsage(sermonId: String, sermonTitle: String, verses: List<String>) {
        // Placeholder for future verse usage tracking logic
        android.util.Log.d("ShepherdViewModel", "Saving verse usage for $sermonTitle: $verses")
    }

    fun logPreachingActivity(sermonId: String, sermonTitle: String, type: String, duration: Int, verses: List<String>) {
        viewModelScope.launch {
            repository.logActivity(
                fileName = sermonTitle,
                action = "Preached for $duration mins using ${verses.size} verses",
                actionType = ActionType.PREACH
            )
        }
    }

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

    private val _geminiApiKey = MutableStateFlow(repository.getGeminiApiKey())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

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

    fun updateGeminiApiKey(key: String) {
        repository.setGeminiApiKey(key)
        _geminiApiKey.value = key
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

    fun deleteSelectedFiles() {
        // repository.deleteFile() now soft-deletes into Trash instead of removing outright.
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

    // --- Calendar & Verse Usage State ---
    val upcomingEvents: StateFlow<List<com.example.data.local.SermonCalendarEntity>> = repository.getUpcomingEvents(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDayEvents = MutableStateFlow<List<com.example.data.local.SermonCalendarEntity>>(emptyList())
    val selectedDayEvents: StateFlow<List<com.example.data.local.SermonCalendarEntity>> = _selectedDayEvents.asStateFlow()

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

    // --- State for active Sermon Document Viewer ---
    private val _paragraphs = MutableStateFlow<List<com.example.util.DocumentParser.AnnotatedParagraph>>(emptyList())
    val paragraphs: StateFlow<List<com.example.util.DocumentParser.AnnotatedParagraph>> = _paragraphs.asStateFlow()

    private val _alreadyPreachedWarning = MutableStateFlow<String?>(null)
    val alreadyPreachedWarning: StateFlow<String?> = _alreadyPreachedWarning.asStateFlow()

    private val _isDocumentLoading = MutableStateFlow(false)
    val isDocumentLoading: StateFlow<Boolean> = _isDocumentLoading.asStateFlow()

    private val _documentLoadingStatus = MutableStateFlow("")
    val documentLoadingStatus: StateFlow<String> = _documentLoadingStatus.asStateFlow()
    private val _tableResult = MutableStateFlow("")
    val tableResult: StateFlow<String> = _tableResult.asStateFlow()

    private val _tableIsGenerating = MutableStateFlow(false)
    val tableIsGenerating: StateFlow<Boolean> = _tableIsGenerating.asStateFlow()

    fun generateTableFromText(sourceText: String, instruction: String) {
        if (instruction.trim().isEmpty()) return
        _tableResult.value = ""
        _tableIsGenerating.value = true
        viewModelScope.launch {
            repository.getTableStream(sourceText, instruction).collect { chunk ->
                _tableResult.value = _tableResult.value + chunk
            }
            _tableIsGenerating.value = false
        }
    }
    fun loadDocumentFromUri(sermonId: String, uriString: String, title: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isDocumentLoading.emit(true)
            _documentLoadingStatus.emit("Copying file from source...")
            _paragraphs.emit(emptyList()) // show loading
            _alreadyPreachedWarning.emit(null)

            try {
                val uri = android.net.Uri.parse(uriString)
                val ext = try {
                    val lastDot = title.lastIndexOf('.')
                    if (lastDot != -1 && lastDot < title.length - 1) {
                        title.substring(lastDot + 1).lowercase()
                    } else {
                        val uriLastDot = uriString.lastIndexOf('.')
                        if (uriLastDot != -1 && uriLastDot < uriString.length - 1) {
                            uriString.substring(uriLastDot + 1).lowercase()
                        } else {
                            "txt"
                        }
                    }
                } catch (e: Exception) {
                    "txt"
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
                _isDocumentLoading.emit(false)
            }
        }
    }

    fun loadDocument(sermonId: String, filePath: String, title: String) {
        // Guard: if a content:// URI slips in here (e.g. from a call site that
        // hasn't been updated to use loadDocumentFromUri), redirect automatically
        // instead of silently failing the "file.exists()" check below.
        if (filePath.startsWith("content://")) {
            loadDocumentFromUri(sermonId, filePath, title)
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isDocumentLoading.emit(true)
            _documentLoadingStatus.emit("Reading file structure...")
            val file = java.io.File(filePath)
            if (!file.exists()) {
                _paragraphs.emit(emptyList())
                _alreadyPreachedWarning.emit(null)
                _isDocumentLoading.emit(false)
                return@launch
            }
            _documentLoadingStatus.emit("Parsing $title content...")
            val parsed = try {
                com.example.util.DocumentParser.parseFile(filePath)
            } catch (e: com.example.util.DocumentParser.PasswordException) {
                _alreadyPreachedWarning.emit("This PDF is password-protected. Please unlock it first.")
                _paragraphs.emit(emptyList())
                addNotification("Document Parsing Error", "The file \"$title\" is password-protected.")
                _isDocumentLoading.emit(false)
                return@launch
            } catch (e: com.example.util.DocumentParser.EncryptionException) {
                _alreadyPreachedWarning.emit("This file is encrypted and cannot be parsed.")
                _paragraphs.emit(emptyList())
                addNotification("Document Parsing Error", "The file \"$title\" is encrypted.")
                _isDocumentLoading.emit(false)
                return@launch
            } catch (e: com.example.util.DocumentParser.CorruptFileException) {
                _alreadyPreachedWarning.emit("This file appears to be corrupted.")
                _paragraphs.emit(emptyList())
                addNotification("Document Parsing Error", "The file \"$title\" appears to be corrupted.")
                _isDocumentLoading.emit(false)
                return@launch
            } catch (e: com.example.util.DocumentParser.UnsupportedFormatException) {
                _alreadyPreachedWarning.emit("This file format is not supported yet.")
                _paragraphs.emit(emptyList())
                addNotification("Document Parsing Error", "The format of \"$title\" is not supported yet.")
                _isDocumentLoading.emit(false)
                return@launch
            } catch (e: Exception) {
                _alreadyPreachedWarning.emit("Could not parse file.")
                _paragraphs.emit(emptyList())
                addNotification("Document Parsing Error", "Failed to parse \"$title\".")
                _isDocumentLoading.emit(false)
                return@launch
            }
            _paragraphs.emit(parsed)
            _alreadyPreachedWarning.emit(null)
            _isDocumentLoading.emit(false)
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