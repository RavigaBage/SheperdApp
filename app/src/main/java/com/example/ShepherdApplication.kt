package com.example

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.example.data.file.SafFileManager
import com.example.data.local.AppDatabase
import com.example.data.remote.GeminiService
import com.example.data.repository.ShepherdRepository
import com.example.notes.data.NotesRepositoryImpl
import com.example.notes.domain.NotesRepository
import kotlinx.coroutines.launch

class ShepherdApplication : Application() {

    // Central Dependency Container
    lateinit var repository: ShepherdRepository
        private set
    lateinit var safFileManager: SafFileManager
        private set
    lateinit var notesRepository: NotesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialise Jetpack Ink native libraries
        androidx.ink.nativeloader.NativeLoader.load()

        // Initialise PDFBox for Android
        PDFBoxResourceLoader.init(this)
        
        // Initialise notification channels
        com.example.util.SermonNotificationHelper.createChannels(this)
        
        // Initialise singletons
        val database = AppDatabase.getInstance(this)
        safFileManager = SafFileManager(this)
        val geminiService = GeminiService()
        
        repository = ShepherdRepository(
            context = this,
            database = database,
            safFileManager = safFileManager,
            geminiService = geminiService
        )

        notesRepository = NotesRepositoryImpl(
            notebookDao = database.notebookDao(),
            pageDao = database.pageDao(),
            elementDao = database.pageElementDao(),
            illustrationDao = database.illustrationDao(),
            templateDao = database.sermonTemplateDao(),
            mainDao = database.dao()
        )

        // Seed initial illustration data
        kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
            notesRepository.seedDataIfNeeded(this@ShepherdApplication)
            scheduleIllustrationSync()
        }
    }

    private fun scheduleIllustrationSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.notes.data.IllustrationSyncWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        ).setConstraints(constraints).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "illustration_sync",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
