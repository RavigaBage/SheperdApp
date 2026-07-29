package com.example.notes.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitComplete: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                setupVoice()
                onInitComplete?.invoke(true)
            } else {
                Log.e("TTS", "Initialization failed")
                onInitComplete?.invoke(false)
            }
        }
    }

    private fun setupVoice() {
        tts?.let { tts ->
            val locale = Locale.getDefault()
            tts.language = locale
            
            // Try to find a high-quality voice for the current locale
            try {
                val voices = tts.voices
                val highQualityVoice = voices?.filter { it.locale == locale }
                    ?.maxByOrNull { it.quality }
                
                if (highQualityVoice != null) {
                    tts.voice = highQualityVoice
                }
            } catch (e: Exception) {
                Log.e("TTS", "Error setting voice: ${e.message}")
            }
        }
    }

    fun speak(text: String, utteranceId: String, onDone: () -> Unit = {}) {
        if (!isInitialized) return

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onDone()
            }
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("TTS", "Error speaking: $errorCode")
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun playSilence(durationMs: Long, utteranceId: String, onDone: () -> Unit = {}) {
        if (!isInitialized) return
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onDone()
            }
            override fun onError(utteranceId: String?) {}
        })
        
        tts?.playSilentUtterance(durationMs, TextToSpeech.QUEUE_ADD, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
