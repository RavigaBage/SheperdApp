package com.example.util

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

object DocumentParser {

    class PasswordException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class CorruptFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class UnsupportedFormatException(message: String) : Exception(message)

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

    /** Detect charset and read file as plain text safely */
    fun readTextWithCharsetDetection(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""

        // Check BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
        }

        // Try decoding as UTF-8 strictly first
        try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            // Fallback to Windows-1252
            try {
                return String(bytes, java.nio.charset.Charset.forName("windows-1252"))
            } catch (e2: Exception) {
                return String(bytes, Charsets.ISO_8859_1)
            }
        }
    }

    /** Parse a .txt or .md file — split on blank lines */
    fun parseTxt(content: String): List<AnnotatedParagraph> {
        return content.split(Regex("\\n{2,}"))
            .filter { it.isNotBlank() }
            .map { annotate(it.trim()) }
    }

    /** Parse a .docx file using Apache POI */
    fun parseDocx(filePath: String): List<AnnotatedParagraph> {
        val paragraphs = mutableListOf<AnnotatedParagraph>()
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
        return paragraphs
    }

    /** Parse a .pptx file using Apache POI */
    fun parsePptx(filePath: String): List<AnnotatedParagraph> {
        val paragraphs = mutableListOf<AnnotatedParagraph>()
        FileInputStream(filePath).use { fis ->
            org.apache.poi.xslf.usermodel.XMLSlideShow(fis).use { pptx ->
                for (slide in pptx.slides) {
                    for (shape in slide.shapes) {
                        if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                            val text = shape.text.trim()
                            if (text.isNotBlank()) {
                                paragraphs.add(annotate(text))
                            }
                        }
                    }
                }
            }
        }
        return paragraphs
    }

    /** Parse a .xlsx or .xls file using Apache POI WorkbookFactory */
    fun parseXlsx(filePath: String): List<AnnotatedParagraph> {
        val paragraphs = mutableListOf<AnnotatedParagraph>()
        FileInputStream(filePath).use { fis ->
            org.apache.poi.ss.usermodel.WorkbookFactory.create(fis).use { workbook ->
                val textBuilder = java.lang.StringBuilder()
                for (s in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(s)
                    for (r in 0 until sheet.physicalNumberOfRows) {
                        val row = sheet.getRow(sheet.firstRowNum + r) ?: continue
                        val rowCells = mutableListOf<String>()
                        for (c in 0 until row.physicalNumberOfCells) {
                            val cell = row.getCell(row.firstCellNum + c) ?: continue
                            val value = when (cell.cellType) {
                                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                        cell.dateCellValue.toString()
                                    } else {
                                        cell.numericCellValue.toString()
                                    }
                                }
                                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                                    try {
                                        when (cell.cachedFormulaResultType) {
                                            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                                            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
                                            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                            else -> cell.cellFormula
                                        }
                                    } catch (e: Exception) {
                                        cell.cellFormula
                                    }
                                }
                                org.apache.poi.ss.usermodel.CellType.BLANK -> ""
                                else -> cell.toString()
                            }
                            if (value.isNotBlank()) {
                                rowCells.add(value)
                            }
                        }
                        if (rowCells.isNotEmpty()) {
                            textBuilder.append(rowCells.joinToString("\t")).append("\n")
                        }
                    }
                }
                val text = textBuilder.toString().trim()
                if (text.isNotBlank()) {
                    paragraphs.add(annotate(text))
                }
            }
        }
        return paragraphs
    }

    /** Extract text from PDF using PDFBox with Owner-Password bypass, Password promotion, and ML Kit OCR fallback */
    fun extractPdfText(filePath: String, password: String = ""): List<AnnotatedParagraph> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()

        var doc: PDDocument? = null
        try {
            try {
                // First attempt loading with password (defaults to empty "")
                doc = PDDocument.load(file, password)
            } catch (e: Exception) {
                val isPasswordError = e is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException ||
                        e.javaClass.simpleName == "InvalidPasswordException" ||
                        e.message?.contains("password", ignoreCase = true) == true
                if (isPasswordError) {
                    throw PasswordException("Password required or invalid password", e)
                }
                if (e.message?.contains("encrypted", ignoreCase = true) == true) {
                    throw EncryptionException("Document is encrypted", e)
                }
                throw CorruptFileException("Failed to load PDF document", e)
            }

            // Strip copy restrictions (owner restrictions)
            try {
                doc.isAllSecurityToBeRemoved = true
            } catch (secEx: Exception) {
                secEx.printStackTrace()
            }

            val stripper = PDFTextStripper()
            val text = stripper.getText(doc).trim()

            // For scanned PDFs (image-only), fallback to ML Kit Text Recognition on each page
            if (text.length < 50 || text.all { it.isWhitespace() }) {
                val ocrText = performOcrOnPdf(filePath)
                if (ocrText.isNotBlank()) {
                    return parseTxt(ocrText)
                }
            }

            return if (text.isNotBlank()) {
                parseTxt(text)
            } else {
                emptyList()
            }
        } catch (pe: PasswordException) {
            throw pe
        } catch (ee: EncryptionException) {
            throw ee
        } catch (ce: CorruptFileException) {
            throw ce
        } catch (e: Exception) {
            throw CorruptFileException("Failed to read PDF file", e)
        } finally {
            try {
                doc?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /** Performs ML Kit OCR on each page of a scanned/image-only PDF */
    fun performOcrOnPdf(filePath: String): String {
        val textBuilder = java.lang.StringBuilder()
        val file = File(filePath)
        if (!file.exists()) return ""
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)

                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val image = InputImage.fromBitmap(bitmap, 0)
                    try {
                        val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                        textBuilder.append(result.text).append("\n\n")
                    } catch (ocrException: Exception) {
                        ocrException.printStackTrace()
                    } finally {
                        bitmap.recycle()
                        page.close()
                    }
                }
                renderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return textBuilder.toString()
    }

    /** Route file parsing with proper error wrapping */
    fun parseFile(filePath: String, pdfPassword: String = ""): List<AnnotatedParagraph> {
        val file = File(filePath)
        if (!file.exists()) throw CorruptFileException("This file appears to be corrupted")
        val ext = file.extension.lowercase()
        return when (ext) {
            "txt", "md" -> {
                val text = try {
                    readTextWithCharsetDetection(file)
                } catch (e: Exception) {
                    throw CorruptFileException("Failed to read text file", e)
                }
                parseTxt(text)
            }
            "docx" -> {
                try {
                    parseDocx(filePath)
                } catch (e: org.apache.poi.ooxml.POIXMLException) {
                    throw CorruptFileException("This file appears to be corrupted", e)
                } catch (e: Exception) {
                    throw CorruptFileException("Could not read file", e)
                }
            }
            "doc" -> {
                throw UnsupportedFormatException("Format .doc not supported yet")
            }
            "pdf" -> {
                extractPdfText(filePath, pdfPassword)
            }
            "pptx" -> {
                try {
                    parsePptx(filePath)
                } catch (e: org.apache.poi.ooxml.POIXMLException) {
                    throw CorruptFileException("This file appears to be corrupted", e)
                } catch (e: Exception) {
                    throw CorruptFileException("Could not read file", e)
                }
            }
            "ppt" -> {
                throw UnsupportedFormatException("Format .ppt not supported yet")
            }
            "xlsx", "xls" -> {
                try {
                    parseXlsx(filePath)
                } catch (e: org.apache.poi.ooxml.POIXMLException) {
                    throw CorruptFileException("This file appears to be corrupted", e)
                } catch (e: Exception) {
                    throw CorruptFileException("Could not read file", e)
                }
            }
            else -> {
                throw UnsupportedFormatException("Format not supported yet")
            }
        }
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
