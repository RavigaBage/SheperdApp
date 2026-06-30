package com.example.domain.model

import java.io.Serializable

data class Board(
    val id: String,
    val title: String,
    val thumbnailUri: String?,
    val categoryId: String?,
    val createdAt: Long,
    val lastModified: Long,
    val templateType: BoardTemplate = BoardTemplate.BLANK,
    val isPaged: Boolean = false
) : Serializable

enum class BoardTemplate {
    BLANK, RULED, DOT, GRAPH
}

data class BoardStroke(
    val points: List<BoardPoint>,
    val color: Int,
    val width: Float,
    val toolType: BoardToolType
) : Serializable

data class BoardPoint(val x: Float, val y: Float, val pressure: Float = 1f) : Serializable

enum class BoardToolType {
    PEN_FINE, PEN_MEDIUM, PEN_BRUSH, HIGHLIGHTER, ERASER_STROKE, ERASER_PIXEL
}

sealed class BoardElement : Serializable {
    data class Text(
        val id: String,
        val text: String,
        val x: Float,
        val y: Float,
        val fontSize: Float,
        val isBold: Boolean,
        val color: Int
    ) : BoardElement()

    data class Shape(
        val id: String,
        val type: ShapeType,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val color: Int,
        val strokeWidth: Float
    ) : BoardElement()

    data class Image(
        val id: String,
        val uri: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) : BoardElement()
}

enum class ShapeType {
    LINE, RECTANGLE, CIRCLE, ARROW
}
