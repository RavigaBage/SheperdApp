package com.example.notes.domain

data class TextStyleSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false
)

// Compact manual encoding — avoids pulling in a JSON dependency for 4 ints/bools.
// Format: "start,end,bold,italic;start,end,bold,italic;..."
private const val SPAN_DELIM = ";"
private const val FIELD_DELIM = ","

fun List<TextStyleSpan>.encodeSpans(): String? =
    if (isEmpty()) null
    else joinToString(SPAN_DELIM) {
        "${it.start}$FIELD_DELIM${it.end}$FIELD_DELIM${if (it.bold) 1 else 0}$FIELD_DELIM${if (it.italic) 1 else 0}"
    }

fun String?.decodeSpans(): List<TextStyleSpan> {
    if (this.isNullOrEmpty()) return emptyList()
    return split(SPAN_DELIM).mapNotNull { part ->
        val f = part.split(FIELD_DELIM)
        if (f.size != 4) return@mapNotNull null
        try {
            TextStyleSpan(f[0].toInt(), f[1].toInt(), f[2] == "1", f[3] == "1")
        } catch (e: Exception) {
            null
        }
    }
}