package com.example.preachmode.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.preachmode.model.HighlightSpan
import com.example.preachmode.model.HighlightStyle
import com.example.preachmode.model.PreachSection

val KeywordHighlight = Color(0xFFFFECB3) // Pastel yellow highlighter
val TitleHighlight = Color(0xFFE1F5FE)   // Faint blue highlighter

@Composable
fun HighlightedText(
    section: PreachSection,
    modifier: Modifier = Modifier,
    onWordTap: (HighlightSpan) -> Unit
) {
    val annotated = buildAnnotatedString {
        append(section.displayText)
        section.highlights.forEach { h ->
            val color = if (h.style == HighlightStyle.TITLE) TitleHighlight else KeywordHighlight
            addStyle(
                SpanStyle(
                    background = color,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                h.start,
                h.end
            )
            addStringAnnotation("word", h.wordId, h.start, h.end)
        }
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            fontSize = 24.sp,
            lineHeight = 36.sp,
            fontFamily = FontFamily.Serif,
            color = Color.Black
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("word", offset, offset).firstOrNull()?.let { ann ->
                val span = section.highlights.firstOrNull { it.wordId == ann.item }
                if (span != null) {
                    onWordTap(span)
                }
            }
        }
    )
}
