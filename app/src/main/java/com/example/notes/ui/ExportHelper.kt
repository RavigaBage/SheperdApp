package com.example.notes.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import com.example.notes.domain.CanvasObject
import com.example.notes.domain.PageBackgroundStyle
import com.example.notes.domain.TextStyleSpan
import com.example.notes.domain.decodeSpans
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
                    drawRichText(canvas, obj)
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

    private fun stylesAt(spans: List<TextStyleSpan>, index: Int): Pair<Boolean, Boolean> {
        var bold = false
        var italic = false
        for (span in spans) {
            if (index >= span.start && index < span.end) {
                bold = bold || span.bold
                italic = italic || span.italic
            }
        }
        return bold to italic
    }

    private fun drawRichText(canvas: android.graphics.Canvas, obj: CanvasObject.RichTextObject) {
        val spans = obj.annotatedStringJson.decodeSpans()
        val lineHeight = 48f
        var charOffset = 0

        obj.text.split("\n").forEachIndexed { lineIndex, line ->
            var x = obj.x
            val y = obj.y + 40f + (lineIndex * lineHeight)
            var runStart = 0
            while (runStart < line.length) {
                val (bold, italic) = stylesAt(spans, charOffset + runStart)
                var runEnd = runStart
                while (runEnd < line.length) {
                    val (b2, i2) = stylesAt(spans, charOffset + runEnd)
                    if (b2 != bold || i2 != italic) break
                    runEnd++
                }
                val runText = line.substring(runStart, runEnd)
                val paint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 40f
                    isAntiAlias = true
                    typeface = Typeface.create(
                        Typeface.DEFAULT,
                        when {
                            bold && italic -> Typeface.BOLD_ITALIC
                            bold -> Typeface.BOLD
                            italic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                    )
                }
                canvas.drawText(runText, x, y, paint)
                x += paint.measureText(runText)
                runStart = runEnd
            }
            charOffset += line.length + 1 // +1 accounts for the '\n' stripped by split()
        }
    }

    private fun CanvasObject.StrokeObject.toStroke(): Stroke {
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
        return Stroke(brush, builder)
    }
}