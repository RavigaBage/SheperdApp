package com.example.preachmode

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.PreachCacheEntity
import com.example.preachmode.model.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class PreachRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val dao = database.dao()

    private val apiKey: String = try {
        BuildConfig.GEMINI_API_KEY
    } catch (e: Throwable) {
        ""
    }

    private val promptInstructions = """
You are a text-processing engine for a sermon and devotional reading app.
You will receive raw plain text extracted from a document. Return ONLY
valid JSON matching the schema below — no markdown fences, no commentary.

Steps:
1. Split the text into an ordered list of sections. A session MUST contain a maximum of 20 sections total. Each section is one of:
   "title" (a heading), "paragraph" (body text), or "scripture" (a section
   whose main content is a Bible passage).
2. Detect Bible scripture references anywhere in the text (e.g. "John 3:16",
   "Romans 8:28-30", or quoted verse text without a citation).
   - Keep the reference citation visible in the normal reading flow.
   - Provide the full verse text in a separate "hiddenScripture" field —
     this will be hidden in the UI until the user taps it. If the verse
     text isn't already in the source, supply the standard KJV text for
     that reference.
3. Identify important keywords and titles worth highlighting: proper nouns,
   theological terms, names of people/places, and section headings. Mark
   each occurrence with its exact character start/end offset within that
   section's displayText. Ensure the offsets are mathematically correct and correspond exactly to the substring in displayText.
4. For every highlighted keyword/title, provide a translation into English,
   French, Twi (Akan), and Arabic. Translate the term in context, not word-for-word, so it makes sense to a reader of that language.
5. Output strictly this JSON shape:

{
  "sections": [
    {
      "id": "string",
      "type": "title" | "paragraph" | "scripture",
      "displayText": "string",
      "hiddenScripture": { "reference": "string", "text": "string" } | null,
      "highlights": [
        {
          "wordId": "string",
          "start": 0,
          "end": 0,
          "style": "keyword" | "title",
          "translations": {
            "english": "string",
            "french": "string",
            "twi": "string",
            "arabic": "string"
          }
        }
      ]
    }
  ]
}
""".trimIndent()

    suspend fun prepareDocument(rawText: String, fileHash: String): Result<PreachDocument> = withContext(Dispatchers.IO) {
        try {
            // 1. Check local DB Cache
            val cached = dao.getPreachCache(fileHash)
            if (cached != null) {
                val parsed = parsePreachDocument(cached.jsonText)
                if (parsed.sections.isNotEmpty()) {
                    return@withContext Result.success(parsed)
                }
            }

            // 2. Cache MISS: Check API key
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("API Key not found or empty"))
            }

            // 3. Call Gemini
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )

            val fullPrompt = "$promptInstructions\n\nDOCUMENT TEXT:\n$rawText"
            val response = model.generateContent(fullPrompt)
            val responseText = response.text.orEmpty()
            val cleaned = responseText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val doc = parsePreachDocument(cleaned)
            if (doc.sections.isEmpty()) {
                return@withContext Result.failure(Exception("Gemini returned empty or invalid schema"))
            }

            // 4. Save to Room Cache
            val serialized = serializePreachDocument(doc)
            dao.insertPreachCache(PreachCacheEntity(fileHash = fileHash, jsonText = serialized))

            Result.success(doc)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun parsePreachDocument(jsonStr: String): PreachDocument {
        val sectionsList = mutableListOf<PreachSection>()
        try {
            val root = JSONObject(jsonStr)
            val sectionsArr = root.optJSONArray("sections") ?: JSONArray()
            val limit = minOf(sectionsArr.length(), 20)
            for (i in 0 until limit) {
                val sObj = sectionsArr.optJSONObject(i) ?: continue
                val id = sObj.optString("id", UUID.randomUUID().toString())
                val typeStr = sObj.optString("type", "paragraph").uppercase()
                val type = when (typeStr) {
                    "TITLE" -> SectionType.TITLE
                    "SCRIPTURE" -> SectionType.SCRIPTURE
                    else -> SectionType.PARAGRAPH
                }
                val displayText = sObj.optString("displayText", "")

                val hiddenScriptureObj = sObj.optJSONObject("hiddenScripture")
                val hiddenScripture = if (hiddenScriptureObj != null) {
                    ScriptureBlock(
                        reference = hiddenScriptureObj.optString("reference", ""),
                        text = hiddenScriptureObj.optString("text", "")
                    )
                } else null

                val highlightsList = mutableListOf<HighlightSpan>()
                val highlightsArr = sObj.optJSONArray("highlights") ?: JSONArray()
                for (j in 0 until highlightsArr.length()) {
                    val hObj = highlightsArr.optJSONObject(j) ?: continue
                    val wordId = hObj.optString("wordId", UUID.randomUUID().toString())
                    val start = hObj.optInt("start", 0)
                    val end = hObj.optInt("end", 0)
                    val styleStr = hObj.optString("style", "keyword").uppercase()
                    val style = if (styleStr == "TITLE") HighlightStyle.TITLE else HighlightStyle.KEYWORD

                    val transObj = hObj.optJSONObject("translations")
                    val translations = if (transObj != null) {
                        Translations(
                            english = transObj.optString("english", ""),
                            french = transObj.optString("french", ""),
                            twi = transObj.optString("twi", ""),
                            arabic = transObj.optString("arabic", "")
                        )
                    } else {
                        Translations("", "", "", "")
                    }
                    
                    // Safely validate character indices inside bounds
                    if (start >= 0 && end <= displayText.length && start < end) {
                        highlightsList.add(HighlightSpan(wordId, start, end, style, translations))
                    }
                }

                sectionsList.add(
                    PreachSection(
                        id = id,
                        type = type,
                        displayText = displayText,
                        hiddenScripture = hiddenScripture,
                        highlights = highlightsList
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return PreachDocument(sectionsList)
    }

    fun serializePreachDocument(doc: PreachDocument): String {
        val root = JSONObject()
        val sectionsArr = JSONArray()
        for (sec in doc.sections) {
            val sObj = JSONObject()
            sObj.put("id", sec.id)
            sObj.put("type", sec.type.name.lowercase())
            sObj.put("displayText", sec.displayText)
            if (sec.hiddenScripture != null) {
                val hsObj = JSONObject()
                hsObj.put("reference", sec.hiddenScripture.reference)
                hsObj.put("text", sec.hiddenScripture.text)
                sObj.put("hiddenScripture", hsObj)
            }
            val highlightsArr = JSONArray()
            for (hl in sec.highlights) {
                val hObj = JSONObject()
                hObj.put("wordId", hl.wordId)
                hObj.put("start", hl.start)
                hObj.put("end", hl.end)
                hObj.put("style", hl.style.name.lowercase())
                val transObj = JSONObject()
                transObj.put("english", hl.translations.english)
                transObj.put("french", hl.translations.french)
                transObj.put("twi", hl.translations.twi)
                transObj.put("arabic", hl.translations.arabic)
                hObj.put("translations", transObj)
                highlightsArr.put(hObj)
            }
            sObj.put("highlights", highlightsArr)
            sectionsArr.put(sObj)
        }
        root.put("sections", sectionsArr)
        return root.toString()
    }

    fun createFallbackDocument(rawText: String): PreachDocument {
        val paragraphs = rawText.split(Regex("\\n{2,}"))
            .filter { it.isNotBlank() }
            .take(20)
        val sections = paragraphs.mapIndexed { index, text ->
            val cleanText = text.trim()
            val isHeading = cleanText.startsWith("#") || cleanText.startsWith("##") || cleanText.uppercase().startsWith("INTRODUCTION") || cleanText.uppercase().startsWith("CONCLUSION")
            PreachSection(
                id = "sec_$index",
                type = if (isHeading) SectionType.TITLE else SectionType.PARAGRAPH,
                displayText = cleanText.removePrefix("#").trim(),
                hiddenScripture = null,
                highlights = emptyList()
            )
        }
        return PreachDocument(sections)
    }

    fun calculateFileHash(rawText: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(rawText.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
}
