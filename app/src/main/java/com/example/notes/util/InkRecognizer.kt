package com.example.notes.util

import com.example.notes.domain.InkPoint
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InkRecognizer {

    private val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
    private val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
    private val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
    private val remoteModelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()

    suspend fun ensureModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!remoteModelManager.isModelDownloaded(model).await()) {
                remoteModelManager.download(model, com.google.mlkit.common.model.DownloadConditions.Builder().build()).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun recognize(points: List<InkPoint>): Result<String> = withContext(Dispatchers.Default) {
        if (!ensureModelDownloaded()) {
            return@withContext Result.failure(Exception("Handwriting recognition model not ready."))
        }

        if (points.isEmpty()) return@withContext Result.success("")

        val inkBuilder = Ink.builder()
        val strokeBuilder = Ink.Stroke.builder()
        points.forEach { p ->
            strokeBuilder.addPoint(Ink.Point.create(p.x, p.y, p.timestampMs))
        }
        inkBuilder.addStroke(strokeBuilder.build())
        val ink = inkBuilder.build()

        try {
            val result = recognizer.recognize(ink).await()
            val text = result.candidates.firstOrNull()?.text ?: ""
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
