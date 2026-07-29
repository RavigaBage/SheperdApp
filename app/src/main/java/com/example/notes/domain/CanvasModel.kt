package com.example.notes.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke

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

fun CanvasObject.StrokeObject.toStroke(): Stroke {
    val family = when (brushFamily) {
        "Pen" -> StockBrushes.pressurePen()
        "Brush" -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
        else -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
    }
    val brush = Brush.createWithColorIntArgb(
        family = family,
        colorIntArgb = try {
            android.graphics.Color.parseColor(colorHex)
        } catch (e: Exception) {
            android.graphics.Color.BLACK
        },
        size = brushWidth,
        epsilon = 0.1f
    )
    val builder = MutableStrokeInputBatch()
    if (points.isEmpty()) {
        builder.add(InputToolType.STYLUS, 0f, 0f, 0L, 0f, 0f, 0f)
    } else {
        points.forEach { p ->
            val safeUnitLength = if (p.strokeUnitLength > 0 && p.strokeUnitLength.isFinite()) p.strokeUnitLength else 1f
            builder.add(
                type = InputToolType.STYLUS,
                x = p.x,
                y = p.y,
                elapsedTimeMillis = p.timestampMs,
                strokeUnitLengthCm = safeUnitLength,
                pressure = p.pressure,
                tiltRadians = p.tiltX,
                orientationRadians = p.tiltY
            )
        }
    }
    return Stroke(brush, builder)
}
