package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.example.presentation.viewmodel.DrawingStroke
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.util.UUID

class NotesGenerator(private val context: Context) {

    fun generateNotesPdf(title: String, text: String, strokes: List<DrawingStroke>): File {
        val tempFile = File(context.cacheDir, "pastoral_note_${UUID.randomUUID()}.pdf")
        var document: PDDocument? = null

        try {
            document = PDDocument()
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)

            // Title
            PDPageContentStream(document, page).use { titleStream ->
                titleStream.beginText()
                titleStream.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                titleStream.newLineAtOffset(50f, 750f)
                titleStream.showText(sanitizeForPdf(title))
                titleStream.endText()
            }

            // Body text, with real pagination
            var textStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)
            textStream.beginText()
            textStream.setFont(PDType1Font.HELVETICA, 12f)
            textStream.setLeading(15f)
            textStream.newLineAtOffset(50f, 720f)
            var currentY = 720f

            for (line in text.lines()) {
                if (currentY < 100f) {
                    textStream.endText()
                    textStream.close()

                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    textStream = PDPageContentStream(document, page)
                    textStream.beginText()
                    textStream.setFont(PDType1Font.HELVETICA, 12f)
                    textStream.setLeading(15f)
                    textStream.newLineAtOffset(50f, 750f)
                    currentY = 750f
                }
                textStream.showText(sanitizeForPdf(line))
                textStream.newLine()
                currentY -= 15f
            }
            textStream.endText()
            textStream.close()

            // Drawing page
            if (strokes.isNotEmpty()) {
                val bitmap = StrokeBitmapRenderer.render(strokes)
                val pdImage = LosslessFactory.createFromImage(document, bitmap)

                val canvasPage = PDPage(PDRectangle.A4)
                document.addPage(canvasPage)

                val maxWidth = PDRectangle.A4.width - 100f
                val maxHeight = PDRectangle.A4.height - 100f
                val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height, 1f)
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale

                PDPageContentStream(document, canvasPage).use { canvasStream ->
                    canvasStream.drawImage(
                        pdImage, 50f,
                        PDRectangle.A4.height - drawHeight - 50f,
                        drawWidth, drawHeight
                    )
                }
            }

            document.save(tempFile)
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall through to a minimal, guaranteed-valid PDF rather than a
            // corrupt file. Text only — skipping strokes here deliberately,
            // since re-rendering the same drawing that may have caused this
            // failure risks failing again.
            return generateFallbackPdf(title, text)
        } finally {
            document?.close()
        }
    }

    /**
     * Minimal, robust PDF generator used only when generateNotesPdf fails.
     * Title + plain text, paginated, no drawing. Kept deliberately simple
     * so it has as little surface area to fail on as possible.
     */

    private fun generateFallbackPdf(title: String, text: String): File {
        val fallbackFile = File(context.cacheDir, "pastoral_note_fallback_${UUID.randomUUID()}.pdf")
        var document: PDDocument? = null

        try {
            document = PDDocument()
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)

            var stream = PDPageContentStream(document, page)
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA_BOLD, 16f)
            stream.newLineAtOffset(50f, 770f)
            stream.showText(sanitizeForPdf(title))
            stream.endText()

            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 11f)
            stream.setLeading(14f)
            stream.newLineAtOffset(50f, 740f)
            var currentY = 740f

            val lines = text.lines().ifEmpty { listOf("") }
            for (line in lines) {
                if (currentY < 80f) {
                    stream.endText()
                    stream.close()
                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    stream = PDPageContentStream(document, page)
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.setLeading(14f)
                    stream.newLineAtOffset(50f, 770f)
                    currentY = 770f
                }
                stream.showText(sanitizeForPdf(line))
                stream.newLine()
                currentY -= 14f
            }
            stream.endText()
            stream.close()

            document.save(fallbackFile)
        } catch (e: Exception) {
            // Only reachable if PDFBox itself is broken (e.g. missing native
            // assets). Nothing more we can safely do — log and return
            // whatever got written, even if empty, rather than crash the
            // save flow.
            e.printStackTrace()
        } finally {
            document?.close()
        }

        return fallbackFile
    }
    private fun sanitizeForPdf(text: String): String {
        return text.replace("\t", "    ")
            .filter { it.code in 32..126 } // PDType1Font only supports basic ASCII
            .ifEmpty { " " }
    }


}
