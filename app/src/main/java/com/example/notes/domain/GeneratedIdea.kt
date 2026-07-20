package com.example.notes.domain

enum class IdeaType {
    SERMON, TEACHING_SERIES, CONFERENCE, SEMINAR
}

data class GeneratedIdea(
    val type: IdeaType,
    val title: String,
    val scriptureReference: String?,
    val summary: String,
    val outline: List<String>,
    val suggestedTags: List<String>
)
