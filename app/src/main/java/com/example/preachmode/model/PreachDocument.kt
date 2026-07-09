package com.example.preachmode.model

data class PreachDocument(val sections: List<PreachSection>)

data class PreachSection(
    val id: String,
    val type: SectionType,
    val displayText: String,
    val hiddenScripture: ScriptureBlock? = null,
    val highlights: List<HighlightSpan> = emptyList()
)

enum class SectionType {
    TITLE, PARAGRAPH, SCRIPTURE
}

data class ScriptureBlock(val reference: String, val text: String)

data class HighlightSpan(
    val wordId: String,
    val start: Int,
    val end: Int,
    val style: HighlightStyle,
    val translations: Translations
)

enum class HighlightStyle {
    KEYWORD, TITLE
}

data class Translations(
    val english: String,
    val french: String,
    val twi: String,
    val arabic: String
)
