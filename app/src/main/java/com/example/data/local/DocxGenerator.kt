package com.example.data.local

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

    private fun cleanMarkdown(text: String): String {
        return text.replace("**", "").replace("*", "").replace("`", "")
    }
}
