package com.example.data.local
import android.graphics.Bitmap
import com.example.presentation.viewmodel.DrawingStroke
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.content.Context
import com.example.data.remote.FormatMode
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocxGenerator(private val context: Context) {

    fun generateDocx(formattedText: String, title: String, mode: FormatMode): File {
        val tempFile = File(context.cacheDir, "shepherd_${UUID.randomUUID()}.docx")
        try {
            XWPFDocument().use { document ->
                // Add margins and basic metadata styling
                val titleParagraph = document.createParagraph().apply {
                    alignment = ParagraphAlignment.CENTER
                    spacingAfter = 300
                }
                val titleRun = titleParagraph.createRun().apply {
                    isBold = true
                    fontSize = 24
                    fontFamily = "Georgia"
                    setText(title)
                }

                // Add mode subtitle
                val subParagraph = document.createParagraph().apply {
                    alignment = ParagraphAlignment.CENTER
                    spacingAfter = 400
                }
                val subRun = subParagraph.createRun().apply {
                    isItalic = true
                    fontSize = 12
                    fontFamily = "Arial"
                    setText("Formatted as: ${mode.name} via Shepherd AI Editor")
                }

                // Parse guidelines line-by-line to convert Markdown to DOCX styles
                val lines = formattedText.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        // Create spacing paragraph
                        document.createParagraph().apply {
                            spacingAfter = 150
                        }
                        continue
                    }

                    val paragraph = document.createParagraph()
                    
                    when {
                        trimmed.startsWith("# ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.spacingAfter = 200
                            val run = paragraph.createRun().apply {
                                isBold = true
                                fontSize = 18
                                fontFamily = "Georgia"
                                setText(cleanMarkdown(trimmed.substring(2)))
                            }
                        }
                        trimmed.startsWith("## ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.spacingAfter = 150
                            val run = paragraph.createRun().apply {
                                isBold = true
                                fontSize = 15
                                fontFamily = "Georgia"
                                setText(cleanMarkdown(trimmed.substring(3)))
                            }
                        }
                        trimmed.startsWith("### ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.spacingAfter = 120
                            val run = paragraph.createRun().apply {
                                isBold = true
                                isItalic = true
                                fontSize = 13
                                fontFamily = "Georgia"
                                setText(cleanMarkdown(trimmed.substring(4)))
                            }
                        }
                        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.indentationLeft = 360 // Indent bullet points
                            val run = paragraph.createRun().apply {
                                fontSize = 11
                                fontFamily = "Arial"
                                setText("• " + cleanMarkdown(trimmed.substring(2)))
                            }
                        }
                        trimmed.startsWith("> ") || (mode == FormatMode.SERMON && trimmed.startsWith("**Scripture Text:")) -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.indentationLeft = 540 // Blockquote style
                            val run = paragraph.createRun().apply {
                                isItalic = true
                                fontSize = 11
                                fontFamily = "Georgia"
                                val innerText = if (trimmed.startsWith("> ")) trimmed.substring(2) else trimmed
                                setText(cleanMarkdown(innerText))
                            }
                        }
                        else -> {
                            paragraph.alignment = ParagraphAlignment.BOTH
                            paragraph.spacingAfter = 100
                            val run = paragraph.createRun().apply {
                                fontSize = 11
                                fontFamily = "Arial"
                                setText(cleanMarkdown(trimmed))
                            }
                        }
                    }
                }

                FileOutputStream(tempFile).use { out ->
                    document.write(out)
                }
            }
        } catch (e: Throwable) {
            // Absolute crash resilience: if Apache POI throws a library or linking error, we write a text file
            e.printStackTrace()
            try {
                tempFile.writeText("=== TITLE: $title ===\nForm: ${mode.name}\n\n$formattedText")
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        return tempFile
    }



        /**
         * Builds a .docx for a Pastoral Note: title, plain-text body (one paragraph
         * per line — no markdown parsing, since notes are free-form rather than
         * AI-formatted), and an optional embedded image of the handwriting when
         * includeDrawing is true (used for MIXED notes).
         */
        fun generateNotesDocx(
            title: String,
            text: String,
            strokes: List<DrawingStroke> = emptyList(),
            includeDrawing: Boolean = false
        ): File {
            val tempFile = File(context.cacheDir, "pastoral_note_${UUID.randomUUID()}.docx")
            try {
                XWPFDocument().use { document ->
                    val titleParagraph = document.createParagraph().apply {
                        alignment = ParagraphAlignment.CENTER
                        spacingAfter = 300
                    }
                    titleParagraph.createRun().apply {
                        isBold = true
                        fontSize = 22
                        fontFamily = "Georgia"
                        setText(title)
                    }

                    if (text.isNotBlank()) {
                        for (line in text.lines()) {
                            val paragraph = document.createParagraph().apply {
                                alignment = ParagraphAlignment.LEFT
                                spacingAfter = 100
                            }
                            paragraph.createRun().apply {
                                fontSize = 11
                                fontFamily = "Arial"
                                setText(line)
                            }
                        }
                    }

                    if (includeDrawing && strokes.isNotEmpty()) {
                        val bitmap = StrokeBitmapRenderer.render(strokes)
                        val imageBytes = ByteArrayOutputStream().use { bos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                            bos.toByteArray()
                        }

                        // Cap width at ~6in (576px @ 96dpi) so it fits the page margins;
                        // scale height proportionally to preserve aspect ratio.
                        val maxWidthPx = 576
                        val scale = if (bitmap.width > maxWidthPx) maxWidthPx.toDouble() / bitmap.width else 1.0
                        val displayWidthPx = (bitmap.width * scale).toInt()
                        val displayHeightPx = (bitmap.height * scale).toInt()

                        val imgParagraph = document.createParagraph().apply {
                            alignment = ParagraphAlignment.CENTER
                            spacingBefore = 300
                        }
                        ByteArrayInputStream(imageBytes).use { imgStream ->
                            imgParagraph.createRun().addPicture(
                                imgStream,
                                Document.PICTURE_TYPE_PNG,
                                "handwriting.png",
                                Units.pixelToEMU(displayWidthPx),
                                Units.pixelToEMU(displayHeightPx)
                            )
                        }
                    }

                    FileOutputStream(tempFile).use { out -> document.write(out) }
                }
            } catch (e: Throwable) {
                // Same crash-resilience pattern as generateDocx: fall back to plain text
                // rather than leaving a corrupt/empty .docx if POI throws.
                e.printStackTrace()
                try {
                    tempFile.writeText("=== TITLE: $title ===\n\n$text")
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            return tempFile
        }

    private fun cleanMarkdown(text: String): String {
        return text.replace("**", "").replace("*", "").replace("`", "")
    }
}
