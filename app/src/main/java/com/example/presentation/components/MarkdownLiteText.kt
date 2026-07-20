package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownLiteText(
    text: String,
    modifier: Modifier = Modifier,
    bodyColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            val headingMatch = HEADING_REGEX.find(line)

            when {
                line.isEmpty() -> {
                    // Paragraph spacing handled by Arrangement.spacedBy(8.dp)
                }

                headingMatch != null -> {
                    val level = headingMatch.groupValues[1].length
                    val content = headingMatch.groupValues[2]
                    val (fontSize, topPadding) = when (level) {
                        1 -> 18.sp to 14.dp
                        2 -> 17.sp to 12.dp
                        3 -> 15.sp to 12.dp
                        else -> 14.sp to 10.dp // #### and beyond
                    }
                    Text(
                        text = parseInlineMarkdown(content, bodyColor),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(top = topPadding),
                        fontFamily = FontFamily.Serif
                    )
                }

                line.startsWith(">") -> {
                    val content = line.removePrefix(">").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(accentColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(content, bodyColor),
                            color = bodyColor.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 19.sp
                        )
                    }
                }

                line.startsWith("- ") || line.startsWith("* ") -> {
                    val content = line.drop(2)
                    Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.Top) {
                        Text("• ", color = bodyColor, fontSize = 14.sp)
                        Text(
                            text = parseInlineMarkdown(content, bodyColor),
                            color = bodyColor,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                NUMBERED_REGEX.matches(line) -> {
                    val dotIndex = line.indexOf(". ")
                    val number = line.substring(0, dotIndex + 2)
                    val content = line.substring(dotIndex + 2)
                    Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.Top) {
                        Text(number, color = bodyColor, fontSize = 14.sp)
                        Text(
                            text = parseInlineMarkdown(content, bodyColor),
                            color = bodyColor,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                line == "---" || line == "***" || line == "___" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(1.dp)
                            .background(bodyColor.copy(alpha = 0.15f))
                    )
                }

                else -> {
                    Text(
                        text = parseInlineMarkdown(line, bodyColor),
                        color = bodyColor,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
private val NUMBERED_REGEX = Regex("""^\d+\.\s+.*$""")

// Order matters: code, then **bold**/__bold__, THEN single *italic*/_italic_.
// Bold must be checked before italic or "**x**" would get swallowed as "*" + "*x*" + "*".
private val INLINE_REGEX = Regex(
    "`([^`]+)`" +
            "|\\*\\*(.+?)\\*\\*" +
            "|__(.+?)__" +
            "|\\*(.+?)\\*" +
            "|_(.+?)_"
)

private fun parseInlineMarkdown(text: String, bodyColor: Color): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    INLINE_REGEX.findAll(text).forEach { result ->
        append(text.substring(lastIndex, result.range.first))
        val (code, bold1, bold2, italic1, italic2) = result.destructured
        when {
            code.isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = bodyColor.copy(alpha = 0.08f))
            ) { append(code) }
            bold1.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold1) }
            bold2.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold2) }
            italic1.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic1) }
            italic2.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic2) }
        }
        lastIndex = result.range.last + 1
    }
    append(text.substring(lastIndex))
}

private operator fun MatchResult.component6() = groupValues.getOrElse(5) { "" }