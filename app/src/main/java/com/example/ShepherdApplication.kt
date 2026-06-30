package com.example

import android.app.Application
import com.example.data.file.SafFileManager
import com.example.data.local.AppDatabase
import com.example.data.remote.GeminiService
import com.example.data.repository.ShepherdRepository

class ShepherdApplication : Application() {

    // Central Dependency Container
    lateinit var repository: ShepherdRepository
        private set
    lateinit var boardRepository: com.example.data.repository.BoardRepository
        private set
    lateinit var safFileManager: SafFileManager
        private set

    override fun onCreate() {
        super.onCreate()
        
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
        
        boardRepository = com.example.data.repository.BoardRepository(this, database)
    }
}
