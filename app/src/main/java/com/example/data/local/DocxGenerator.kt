package com.example.data.local

import android.content.Context
import com.example.data.remote.FormatMode
import org.apache.poi.xwpf.usermodel.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

class DocxGenerator(val context: Context) {

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
                        document.createParagraph().apply { spacingAfter = 150 }
                        continue
                    }

                    val paragraph = document.createParagraph()
                    
                    when {
                        trimmed.startsWith("# ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.spacingAfter = 200
                            addStyledRuns(paragraph, trimmed.substring(2), isBoldHeader = true, fontSizeHeader = 18)
                        }
                        trimmed.startsWith("## ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.spacingAfter = 150
                            addStyledRuns(paragraph, trimmed.substring(3), isBoldHeader = true, fontSizeHeader = 15)
                        }
                        trimmed.startsWith("### ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.spacingAfter = 120
                            addStyledRuns(paragraph, trimmed.substring(4), isBoldHeader = true, fontSizeHeader = 13, isItalicHeader = true)
                        }
                        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.indentationLeft = 360
                            val bulletRun = paragraph.createRun().apply {
                                fontSize = 11
                                fontFamily = "Arial"
                                setText("• ")
                            }
                            addStyledRuns(paragraph, trimmed.substring(2))
                        }
                        trimmed.startsWith("> ") || (mode == FormatMode.SERMON && trimmed.startsWith("**Scripture Text:")) -> {
                            paragraph.alignment = ParagraphAlignment.LEFT
                            paragraph.indentationLeft = 540
                            val innerText = if (trimmed.startsWith("> ")) trimmed.substring(2) else trimmed
                            addStyledRuns(paragraph, innerText, isItalicHeader = true)
                        }
                        else -> {
                            paragraph.alignment = ParagraphAlignment.BOTH
                            paragraph.spacingAfter = 100
                            addStyledRuns(paragraph, trimmed)
                        }
                    }
                }

                FileOutputStream(tempFile).use { out ->
                    document.write(out)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                tempFile.writeText("=== TITLE: $title ===\nForm: ${mode.name}\n\n$formattedText")
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        return tempFile
    }

    private fun addStyledRuns(
        paragraph: XWPFParagraph,
        text: String,
        isBoldHeader: Boolean = false,
        isItalicHeader: Boolean = false,
        fontSizeHeader: Int = 11,
        fontFamilyHeader: String = "Arial"
    ) {
        val regex = """\*\*(.+?)\*\*""".toRegex()
        var lastIndex = 0
        
        regex.findAll(text).forEach { result ->
            // Add plain text before match
            val plainText = text.substring(lastIndex, result.range.first)
            if (plainText.isNotEmpty()) {
                paragraph.createRun().apply {
                    isBold = isBoldHeader
                    isItalic = isItalicHeader
                    fontSize = fontSizeHeader
                    fontFamily = if (isBoldHeader) "Georgia" else fontFamilyHeader
                    setText(plainText)
                }
            }
            
            // Add bold match
            val boldText = result.groupValues[1]
            paragraph.createRun().apply {
                isBold = true
                isItalic = isItalicHeader
                fontSize = fontSizeHeader
                fontFamily = if (isBoldHeader) "Georgia" else fontFamilyHeader
                setText(boldText)
            }
            
            lastIndex = result.range.last + 1
        }
        
        // Add remaining text
        val remaining = text.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            paragraph.createRun().apply {
                isBold = isBoldHeader
                isItalic = isItalicHeader
                fontSize = fontSizeHeader
                fontFamily = if (isBoldHeader) "Georgia" else fontFamilyHeader
                setText(remaining)
            }
        }
    }
}
