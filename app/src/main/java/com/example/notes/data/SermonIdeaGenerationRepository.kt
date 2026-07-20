package com.example.notes.data

import com.example.data.remote.GeminiService
import com.example.notes.domain.GeneratedIdea
import com.example.notes.domain.IdeaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

interface SermonIdeaGenerationRepository {
    fun generateIdeas(topic: String, apiKey: String): Flow<List<GeneratedIdea>>
}

class SermonIdeaGenerationRepositoryImpl(
    private val geminiService: GeminiService
) : SermonIdeaGenerationRepository {

    /**
     * Exact Gemini prompt template used is defined in GeminiService.generateIdeasStream.
     * It requests a strict JSON array of objects matching GeneratedIdea.
     */
    override fun generateIdeas(topic: String, apiKey: String): Flow<List<GeneratedIdea>> {
        var accumulatedJson = ""
        return geminiService.generateIdeasStream(topic, apiKey).map { chunk ->
            if (chunk.startsWith("ERROR:")) {
                throw Exception(chunk)
            }
            accumulatedJson += chunk
            
            val cleaned = accumulatedJson.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
                
            try {
                val array = JSONArray(cleaned)
                val results = mutableListOf<GeneratedIdea>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val outlineArray = obj.getJSONArray("outline")
                    val outline = List(outlineArray.length()) { outlineArray.getString(it) }
                    
                    val tagsArray = obj.getJSONArray("suggestedTags")
                    val tags = List(tagsArray.length()) { tagsArray.getString(it) }
                    
                    results.add(
                        GeneratedIdea(
                            type = IdeaType.valueOf(obj.getString("type")),
                            title = obj.getString("title"),
                            scriptureReference = obj.optString("scriptureReference", null),
                            summary = obj.getString("summary"),
                            outline = outline,
                            suggestedTags = tags
                        )
                    )
                }
                results
            } catch (e: Exception) {
                // If it's not a complete JSON yet, we return what we have so far if possible, 
                // but usually we wait for full completion or return empty for now.
                emptyList<GeneratedIdea>()
            }
        }
    }
}
