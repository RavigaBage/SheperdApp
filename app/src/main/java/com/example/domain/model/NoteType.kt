package com.example.domain.model

enum class NoteType(val displayLabel: String) {
    TEXT("Typed"),
    HANDWRITTEN("Handwritten"),
    MIXED("Text + Handwritten");

    companion object {
        fun from(text: String, strokes: List<*>): NoteType {
            val hasText = text.isNotBlank()
            val hasStrokes = strokes.isNotEmpty()
            return when {
                hasText && hasStrokes -> MIXED
                hasStrokes -> HANDWRITTEN
                else -> TEXT
            }
        }
    }
}