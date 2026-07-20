package com.example.notes.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString

data class CanvasState(
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val zoom: Float = 1f,
    val activeMode: CanvasMode = CanvasMode.PEN,
    val brushColor: String = "#000000",
    val brushSize: Float = 5f,
    val penType: String = "Pen",
    val focusedTextId: String? = null,
    val selectedObjectId: String? = null
)

enum class CanvasMode { PEN, TYPE, ERASE, SELECT }

sealed class CanvasObject {
    abstract val id: String
    abstract val zIndex: Int
    abstract val isVisible: Boolean

    data class StrokeObject(
        override val id: String,
        override val zIndex: Int,
        override val isVisible: Boolean = true,
        val points: List<InkPoint>,
        val colorHex: String,
        val brushWidth: Float,
        val brushFamily: String = "Pen"
    ) : CanvasObject()

    data class RichTextObject(
        override val id: String,
        override val zIndex: Int,
        override val isVisible: Boolean = true,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val text: String,
        val annotatedStringJson: String? = null // now holds encoded TextStyleSpan list — see TextStyleSpan.kt
    ) : CanvasObject()

    data class ImageObject(
        override val id: String,
        override val zIndex: Int,
        override val isVisible: Boolean = true,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val uri: String
    ) : CanvasObject()

    data class IllustrationObject(
        override val id: String,
        override val zIndex: Int,
        override val isVisible: Boolean = true,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val vectorResId: String
    ) : CanvasObject()
}
