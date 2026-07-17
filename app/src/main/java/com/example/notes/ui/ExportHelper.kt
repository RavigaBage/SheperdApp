package com.example.notes.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import com.example.notes.domain.CanvasObject
import com.example.notes.domain.PageBackgroundStyle
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.brush.InputToolType
import java.io.File

object ExportHelper {

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }

    fun renderCanvasToPdf(
        context: Context,
        objects: List<CanvasObject>,
        backgroundColorHex: String,
        backgroundStyle: PageBackgroundStyle,
        outputUri: Uri
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1080, 1920, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val strokeRenderer = CanvasStrokeRenderer.create()

        // 1. Draw Background
        val bgPaint = Paint().apply { color = backgroundColorHex.toColorInt() }
        canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)
        PageBackgroundRenderer.draw(canvas, 1080f, 1920f, backgroundStyle)

        // 2. Draw Objects in Z-order
        objects.sortedBy { it.zIndex }.forEach { obj ->
            when (obj) {
                is CanvasObject.StrokeObject -> {
                    val stroke = obj.toStroke()
                    strokeRenderer.draw(canvas, stroke, android.graphics.Matrix())
                }
                is CanvasObject.RichTextObject -> {
                    val textPaint = Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 40f // Simplified
                    }
                    canvas.drawText(obj.text, obj.x, obj.y + 40f, textPaint)
                }
                is CanvasObject.ImageObject -> {
                    // Image drawing logic
                }
                is CanvasObject.IllustrationObject -> {
                    // Illustration drawing logic
                }
            }
        }

        pdfDocument.finishPage(page)

        try {
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    private fun CanvasObject.StrokeObject.toStroke(): Stroke {
        val brush = Brush.createWithColorIntArgb(
            family = StockBrushes.marker(StockBrushes.MarkerVersion.V1),
            colorIntArgb = colorHex.toColorInt(),
            size = brushWidth,
            epsilon = 0.1f
        )
        val builder = MutableStrokeInputBatch()
        points.forEach { p ->
            builder.add(InputToolType.STYLUS, p.x, p.y, p.timestampMs, p.pressure, p.tiltX, p.tiltY)
        }
        return Stroke(brush, builder)
    }
}
