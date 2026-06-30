package com.example.util

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

object DocumentParser {

    val VERSE_REGEX = Regex(
        """(?:(?:1|2|3)\s)?[A-Za-z]+\.?\s\d{1,3}:\d{1,3}(?:-\d{1,3})?""",
        RegexOption.IGNORE_CASE
    )

    val CUE_REGEX = Regex(
        """\[(PAUSE|RAISE VOICE|LOOK UP|ALTAR CALL)\]""",
        RegexOption.IGNORE_CASE
    )

    enum class CueType { PAUSE, RAISE_VOICE, LOOK_UP, ALTAR_CALL }

    data class AnnotatedParagraph(
        val rawText: String,
        val verseSpans: List<VerseSpan>,
        val cueType: CueType?,
        val headingLevel: Int = 0   // 0 = body, 1 = H1, 2 = H2
    )

    data class VerseSpan(
        val reference: String,
        val startIndex: Int,
        val endIndex: Int
    )

    /** Parse a .txt file — split on blank lines */
    fun parseTxt(content: String): List<AnnotatedParagraph> {
        return content.split(Regex("\\n{2,}"))
            .filter { it.isNotBlank() }
            .map { annotate(it.trim()) }
    }

    /** Parse a .docx file using Apache POI */
    fun parseDocx(filePath: String): List<AnnotatedParagraph> {
        val paragraphs = mutableListOf<AnnotatedParagraph>()
        try {
            FileInputStream(filePath).use { fis ->
                XWPFDocument(fis).use { doc ->
                    doc.paragraphs.forEach { para ->
                        val text = para.text.trim()
                        if (text.isBlank()) return@forEach
                        val style = para.styleID?.uppercase() ?: ""
                        val level = when {
                            style.contains("HEADING1") || style.contains("H1") -> 1
                            style.contains("HEADING2") || style.contains("H2") -> 2
                            else -> 0
                        }
                        paragraphs.add(annotate(text, level))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return paragraphs
    }

    /** Extract text from PDF using raw byte stream scanner */
    fun extractPdfText(filePath: String): List<AnnotatedParagraph> {
        val paragraphs = mutableListOf<AnnotatedParagraph>()
        try {
            val file = File(filePath)
            if (!file.exists()) return paragraphs
            val bytes = file.readBytes()
            val textBuilder = StringBuilder()

            var index = 0
            while (index < bytes.size - 2) {
                if (bytes[index] == '('.code.toByte()) {
                    val start = index + 1
                    var end = start
                    while (end < bytes.size && bytes[end] != ')'.code.toByte()) {
                        end++
                    }
                    if (end < bytes.size) {
                        val length = end - start
                        if (length in 2..500) {
                            val str = String(bytes, start, length, Charsets.UTF_8)
                                .replace(Regex("[^a-zA-Z0-9\\s.,!?;:()'\"\\-–—]"), "")
                            if (str.isNotBlank() && !str.startsWith("/") && !str.startsWith("Page") && !str.startsWith("font")) {
                                textBuilder.append(str).append(" ")
                            }
                        }
                    }
                    index = end
                }
                index++
            }

            val extractedText = textBuilder.toString()
            if (extractedText.isNotBlank()) {
                return parseTxt(extractedText)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return paragraphs
    }

    /** Annotate a paragraph — detect verses and cues */
    fun annotate(text: String, headingLevel: Int = 0): AnnotatedParagraph {
        val spans = VERSE_REGEX.findAll(text).map {
            VerseSpan(it.value, it.range.first, it.range.last + 1)
        }.toList()

        val cueMatch = CUE_REGEX.find(text)
        val cueType = cueMatch?.groupValues?.get(1)?.uppercase()?.let {
            when (it) {
                "PAUSE"       -> CueType.PAUSE
                "RAISE VOICE" -> CueType.RAISE_VOICE
                "LOOK UP"     -> CueType.LOOK_UP
                "ALTAR CALL"  -> CueType.ALTAR_CALL
                else          -> null
            }
        }
        val cleanText = if (cueMatch != null) text.replace(cueMatch.value, "").trim() else text

        return AnnotatedParagraph(cleanText, spans, cueType, headingLevel)
    }

    /** Collect all unique verse references from a paragraph list */
    fun extractAllRefs(paragraphs: List<AnnotatedParagraph>): List<String> {
        return paragraphs.flatMap { it.verseSpans }.map { it.reference }.distinct()
    }
}
