package com.example.data.remote

import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

enum class FormatMode {
    SERMON, LETTER, REPORT, NOTES, GENERAL
}

class GeminiService {

    private fun getEffectiveApiKey(providedKey: String): String {
        return when {
            providedKey.isNotBlank() -> providedKey
            else -> try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                ""
            }
        }
    }

    private fun getApiModel(): String {
        return if (!BuildConfig.GEMINI_MODEL.isNullOrEmpty()) BuildConfig.GEMINI_MODEL else "gemini-3.5-flash"
    }


    private fun getSystemPrompt(mode: FormatMode): String {
        return when (mode) {
            FormatMode.SERMON -> """
You are a professional sermon editor assisting a pastor. 
Format the following raw notes or text into a well-structured sermon document.

Structure it precisely as:
# **SERMON TITLE** (infer from content or use [Title])
**Scripture Text:** [detected or inferred bible passage verse, e.g. John 3:16]
**Introduction:** (2-3 engaging opening paragraphs with real-world context)

## **I. [First Main Point]**
   - Supporting sermon point
   - Illustration, metaphor, or brief narrative story
   
## **II. [Second Main Point]**
   - Supporting theological point  
   - Direct personal application to daily life
   
## **III. [Third Main Point]**
   - Supporting point
   - Congregational real-world application
   
**Conclusion:** (bring it all together, summarizing main takeaways, 1-2 powerful paragraphs)
**Altar Call / Application:** (provocative practical next step or prayer/reflection question for the assembly)

Fix all spelling, grammar, and punctuation errors. 
Preserve the pastor's voice and theological intent.
Do not add other scripture verses not implied by the original raw text.
Return the formatted markdown text only, with no introductory commentary, conversational filler, or wrap-around notes.
""".trimIndent()

            FormatMode.LETTER -> """
You are a professional pastoral correspondence editor.
Format the following text as a formal, elegant pastoral letter.

Structure:
[Current Date]
[Recipient Name/Address if detectable, otherwise use Dear Church Family,]

Dear [Salutation],

[Letter Body — formatted into clear, highly readable paragraphs with perfect spacing, corrected grammar, and graceful transitions]

Yours in Christ,
[Pastor's Signature Line]

Fix all spelling, punctuation, and wording errors. Keep the tone warm, comforting, authoritative, and deeply pastoral.
Return the formatted text only. No conversational wrap-around text.
""".trimIndent()

            FormatMode.REPORT -> """
You are a professional administrative editor for church ministries.
Format the following into a clean, structured ministry report.

Structure:
# **EXECUTIVE SUMMARY**
[Brief paragraph summarizing progress, statistics, or general ministry state]

# **KEY OBJECTIVES & HIGHLIGHTS**
[Bullet points of primary accomplishments and completed metrics]

# **MINISTRY SECTIONS & FEEDBACK**
[Detailed paragraph blocks with sub-headers outlining each department or feedback group]

# **CONCLUSION & FUTURE OUTLOOK**
[Clear summary statement and list of upcoming key timelines or goals]

Ensure proper professional style, absolute correctness of grammar, crisp formatting, and clean layout.
Return the formatted text only.
""".trimIndent()

            FormatMode.NOTES -> """
You are a professional theological researcher and note-taker.
Convert the raw transcripts, thoughts, or text into polished, structured study notes.

Structure:
- Use clear visual bold headers (## **Section Name**)
- Use hierarchical bullet points for supporting arguments
- Embed relevant scripture citations in bold parentheses **(e.g., Romans 12:1-2)**
- Keep explanations clear, logically nested, and highly scannable for study or review

Provide only the note structure itself, with zero conversational filler.
""".trimIndent()

            FormatMode.GENERAL -> """
You are an expert editorial writer. Correct all grammatical, spelling, and syntactic issues in the text below.
Improve general sentence flow, structural layouts, and textual clarity while preserving the exact theological context, core preaching voice, and thematic focus.

Provide only the corrected and polished text outputs.
""".trimIndent()
        }
    }

    fun formatDocumentStream(rawText: String, mode: FormatMode, apiKey: String = ""): Flow<String> = flow {
        val key = getEffectiveApiKey(apiKey)
        val model_api = getApiModel()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            emit("API KEY ERROR: Please open Settings and enter your valid Gemini API Key to enable premium formatting capabilities.")
            return@flow
        }

        try {
            val systemPrompt = getSystemPrompt(mode)
            val fullPrompt = "$systemPrompt\n\nINPUT TEXT TO FORMAT:\n$rawText"
            
            // Initialise Gemini Model
            val model = GenerativeModel(
                modelName = model_api,
                apiKey = key
            )

            // Stream response
            model.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text
                if (text != null) {
                    emit(text)
                }
            }
        } catch (e: Exception) {
            emit("Error generating content: ${e.localizedMessage ?: "Please verify your API key and Internet connection."}")
        }
    }.flowOn(Dispatchers.IO)

    fun getUrielWordInsightStream(word: String, type: String, customPrompt: String = "", apiKey: String = ""): Flow<String> = flow {
        val key = getEffectiveApiKey(apiKey)
        val model_api = getApiModel()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            emit("API KEY ERROR: Please open Settings and enter your valid Gemini API Key to enable Uriel's assistance.")
            return@flow
        }
        try {
            val systemInstruction = """
                You are Uriel, an AI study assistant for preachers and Bible study students.
                Your mission is to provide deep biblical research, historical context, original language insight, and geographical context.
                
                CRITICAL SAFETY RULES:
                - Assist, never replace, the user's own study, preparation, and prayer.
                - NEVER generate a complete sermon or sermon outline on behalf of the user. If the user asks for a sermon or sermon outline, respectfully decline and offer to help study the key passages or brainstorm themes instead.
                - NEVER claim divine revelation or direct inspiration.
                - NEVER invent Bible verses or modify Scripture text.
                
                You are currently asked to provide: "$type"
                For the word/phrase: "$word"
                ${if (customPrompt.isNotEmpty()) "User's specific question: $customPrompt" else ""}
                
                Provide a polished, educational, beautifully formatted markdown response. Use bolding, clear visual bullet points, and elegant structure. Keep it highly focused and informative.
            """.trimIndent()

            val model = GenerativeModel(
                modelName = model_api,
                apiKey = key
            )

            val fullPrompt = "$systemInstruction\n\nWord/Phrase: $word"
            model.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text
                if (text != null) {
                    emit(text)
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown error occurred."}")
        }
    }.flowOn(Dispatchers.IO)

    fun getUrielScriptureIntelligenceStream(scriptureRef: String, type: String, apiKey: String = ""): Flow<String> = flow {
        val key = getEffectiveApiKey(apiKey)
        val model_api = getApiModel()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            emit("API KEY ERROR: Please open Settings and enter your valid Gemini API Key to enable Uriel's assistance.")
            return@flow
        }
        try {
            val systemInstruction = """
                You are Uriel, an AI study assistant for preachers and Bible study students.
                Your mission is to provide deep scripture analysis, context, and research.
                
                CRITICAL SAFETY RULES:
                - Assist, never replace, the user's own study, preparation, and prayer.
                - NEVER generate a complete sermon or sermon outline on behalf of the user.
                - NEVER claim divine revelation or direct inspiration.
                - NEVER invent Bible verses or modify Scripture text.
                
                Provide a deep, insightful analysis of type: "$type" for the scripture reference: "$scriptureRef".
                
                Provide a polished, educational, beautifully formatted markdown response. Use bolding, bullet points, and elegant structure.
            """.trimIndent()

            val model = GenerativeModel(
                modelName = model_api,
                apiKey = key
            )

            val fullPrompt = "$systemInstruction\n\nScripture: $scriptureRef"
            model.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text
                if (text != null) {
                    emit(text)
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage}")
        }
    }.flowOn(Dispatchers.IO)

    fun getUrielThemeDraftStream(topic: String, apiKey: String = ""): Flow<String> = flow {
        val key = getEffectiveApiKey(apiKey)
        val model_api = getApiModel()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            emit("API KEY ERROR: Please open Settings and enter your valid Gemini API Key to enable Uriel's assistance.")
            return@flow
        }
        try {
            val systemInstruction = """
                You are Uriel, an AI study assistant for preachers.
                Your task is to help the preacher brainstorm preaching themes, angles, and concepts based on a story or topic.
                
                CRITICAL SAFETY RULES:
                - DO NOT write a complete sermon.
                - DO NOT write a sermon outline.
                - Only provide a list of 4 to 5 potential preaching themes/angles. Each theme should include a descriptive title, a key central scripture passage, a core theological truth, and a real-world modern-day application point.
                - If the input is empty or invalid, suggest standard biblical themes.
                
                Provide the brainstormed themes in elegant, polished markdown with bold headers and lists.
            """.trimIndent()

            val model = GenerativeModel(
                modelName = model_api,
                apiKey = key
            )

            val fullPrompt = "$systemInstruction\n\nTopic/Story: $topic"
            model.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text
                if (text != null) {
                    emit(text)
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage}")
        }
    }.flowOn(Dispatchers.IO)
}
