package com.example.notes.data

import androidx.room.*
import com.example.notes.domain.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(
    tableName = "notebooks",
    foreignKeys = [ForeignKey(
        entity = com.example.data.local.CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
data class NotebookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryId: String?,
    val backgroundStyle: PageBackgroundStyle = PageBackgroundStyle.LINED,
    val colorHex: String = "#FFFFFF",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = NotebookEntity::class,
        parentColumns = ["id"],
        childColumns = ["notebookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("notebookId")]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val notebookId: String,
    val pageIndex: Int,
    val thumbnailPath: String?,
    val backgroundColorHex: String,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ElementType { STROKE, RICH_TEXT, IMAGE, ILLUSTRATION, LEGACY_INK, LEGACY_TEXT }

@Entity(
    tableName = "page_elements",
    foreignKeys = [ForeignKey(
        entity = PageEntity::class,
        parentColumns = ["id"],
        childColumns = ["pageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("pageId")]
)
data class PageElementEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val type: ElementType,
    val zIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val payloadJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

class ElementTypeConverter {
    @TypeConverter fun fromString(value: String) = ElementType.valueOf(value)
    @TypeConverter fun toString(type: ElementType) = type.name
}

class PageBackgroundStyleConverter {
    @TypeConverter fun fromString(value: String) = PageBackgroundStyle.valueOf(value)
    @TypeConverter fun toString(style: PageBackgroundStyle) = style.name
}

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

@JsonClass(generateAdapter = true)
data class StrokePayload(
    val points: List<InkPoint>,
    val colorHex: String,
    val brushWidth: Float,
    val brushFamily: String
)

@JsonClass(generateAdapter = true)
data class RichTextPayload(
    val text: String,
    val annotatedStringJson: String?
)

@JsonClass(generateAdapter = true)
data class ImagePayload(
    val uri: String
)

@JsonClass(generateAdapter = true)
data class IllustrationPayload(
    val vectorResId: String
)

fun PageElementEntity.toCanvasObject(): CanvasObject {
    return when (type) {
        ElementType.STROKE -> {
            val payload = moshi.adapter(StrokePayload::class.java).fromJson(payloadJson)!!
            CanvasObject.StrokeObject(id, zIndex, true, payload.points, payload.colorHex, payload.brushWidth, payload.brushFamily)
        }
        ElementType.RICH_TEXT -> {
            val payload = moshi.adapter(RichTextPayload::class.java).fromJson(payloadJson)!!
            CanvasObject.RichTextObject(id, zIndex, true, x, y, width, height, payload.text, payload.annotatedStringJson)
        }
        ElementType.IMAGE -> {
            val payload = moshi.adapter(ImagePayload::class.java).fromJson(payloadJson)!!
            CanvasObject.ImageObject(id, zIndex, true, x, y, width, height, payload.uri)
        }
        ElementType.ILLUSTRATION -> {
            val payload = moshi.adapter(IllustrationPayload::class.java).fromJson(payloadJson)!!
            CanvasObject.IllustrationObject(id, zIndex, true, x, y, width, height, payload.vectorResId)
        }
        ElementType.LEGACY_INK -> {
            // Backward compatibility for InkPayload
            val payload = moshi.adapter(StrokePayload::class.java).fromJson(payloadJson)!!
            CanvasObject.StrokeObject(id, zIndex, true, payload.points, payload.colorHex, payload.brushWidth, "Pen")
        }
        ElementType.LEGACY_TEXT -> {
            CanvasObject.RichTextObject(id, zIndex, true, x, y, width, height, "Legacy Text", null)
        }
    }
}

fun CanvasObject.toEntity(pageId: String): PageElementEntity {
    val type = when (this) {
        is CanvasObject.StrokeObject -> ElementType.STROKE
        is CanvasObject.RichTextObject -> ElementType.RICH_TEXT
        is CanvasObject.ImageObject -> ElementType.IMAGE
        is CanvasObject.IllustrationObject -> ElementType.ILLUSTRATION
    }
    
    val payloadJson = when (this) {
        is CanvasObject.StrokeObject -> moshi.adapter(StrokePayload::class.java).toJson(StrokePayload(points, colorHex, brushWidth, brushFamily))
        is CanvasObject.RichTextObject -> moshi.adapter(RichTextPayload::class.java).toJson(RichTextPayload(text, annotatedStringJson))
        is CanvasObject.ImageObject -> moshi.adapter(ImagePayload::class.java).toJson(ImagePayload(uri))
        is CanvasObject.IllustrationObject -> moshi.adapter(IllustrationPayload::class.java).toJson(IllustrationPayload(vectorResId))
    }

    return PageElementEntity(
        id = id,
        pageId = pageId,
        type = type,
        zIndex = zIndex,
        x = when (this) {
            is CanvasObject.RichTextObject -> x
            is CanvasObject.ImageObject -> x
            is CanvasObject.IllustrationObject -> x
            else -> 0f
        },
        y = when (this) {
            is CanvasObject.RichTextObject -> y
            is CanvasObject.ImageObject -> y
            is CanvasObject.IllustrationObject -> y
            else -> 0f
        },
        width = when (this) {
            is CanvasObject.RichTextObject -> width
            is CanvasObject.ImageObject -> width
            is CanvasObject.IllustrationObject -> width
            else -> 0f
        },
        height = when (this) {
            is CanvasObject.RichTextObject -> height
            is CanvasObject.ImageObject -> height
            is CanvasObject.IllustrationObject -> height
            else -> 0f
        },
        payloadJson = payloadJson,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
