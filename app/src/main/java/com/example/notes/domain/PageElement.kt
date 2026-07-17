package com.example.notes.domain

import java.util.UUID

sealed class PageElement {
    abstract val id: String
    abstract val orderIndex: Int
    abstract val bounds: ElementBounds

    data class Ink(
        override val id: String,
        override val orderIndex: Int,
        override val bounds: ElementBounds,
        val points: List<InkPoint>,
        val colorHex: String,
        val brushWidth: Float,
        val recognizedText: String? = null
    ) : PageElement()

    data class RichText(
        override val id: String,
        override val orderIndex: Int,
        override val bounds: ElementBounds,
        val text: String, // Plain text fallback
        val richTextJson: String? = null, // Serialized AnnotatedString or internal format
        val colorHex: String = "#000000"
    ) : PageElement()

    data class Image(
        override val id: String,
        override val orderIndex: Int,
        override val bounds: ElementBounds,
        val imageUri: String
    ) : PageElement()

    data class Illustration(
        override val id: String,
        override val orderIndex: Int,
        override val bounds: ElementBounds,
        val vectorResId: String
    ) : PageElement()

    // Keep Sticky for backward compatibility if needed, or map it to RichText
    data class Sticky(
        override val id: String,
        override val orderIndex: Int,
        override val bounds: ElementBounds,
        val text: String,
        val colorHex: String
    ) : PageElement()
}

data class ElementBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float,
    val tiltY: Float,
    val timestampMs: Long
)
