package com.example.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.presentation.components.keyboardAware
import com.example.notes.domain.CanvasObject
import com.example.notes.domain.TextStyleSpan
import com.example.notes.domain.decodeSpans
import com.example.notes.domain.encodeSpans
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput


private data class CharStyle(val bold: Boolean = false, val italic: Boolean = false)

private fun List<TextStyleSpan>.toCharStyles(length: Int): Array<CharStyle> {
    val arr = Array(length) { CharStyle() }
    for (span in this) {
        val s = span.start.coerceIn(0, length)
        val e = span.end.coerceIn(0, length)
        for (i in s until e) {
            arr[i] = CharStyle(arr[i].bold || span.bold, arr[i].italic || span.italic)
        }
    }
    return arr
}

private fun Array<CharStyle>.toSpans(): List<TextStyleSpan> {
    if (isEmpty()) return emptyList()
    val spans = mutableListOf<TextStyleSpan>()
    var runStart = 0
    var current = this[0]
    for (i in 1 until size) {
        if (this[i] != current) {
            if (current.bold || current.italic) spans.add(TextStyleSpan(runStart, i, current.bold, current.italic))
            runStart = i
            current = this[i]
        }
    }
    if (current.bold || current.italic) spans.add(TextStyleSpan(runStart, size, current.bold, current.italic))
    return spans
}

private fun Array<CharStyle>.toAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    var i = 0
    while (i < size) {
        val style = this@toAnnotatedString[i]
        var j = i
        while (j < size && this@toAnnotatedString[j] == style) j++
        if (style.bold || style.italic) {
            addStyle(
                SpanStyle(
                    fontWeight = if (style.bold) FontWeight.Bold else null,
                    fontStyle = if (style.italic) FontStyle.Italic else null
                ),
                i, j
            )
        }
        i = j
    }
}

private fun adjustStylesForEdit(oldText: String, newText: String, oldStyles: Array<CharStyle>): Array<CharStyle> {
    if (oldText == newText) return oldStyles

    val maxPrefix = minOf(oldText.length, newText.length)
    var prefix = 0
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++

    val maxSuffix = maxPrefix - prefix
    var suffix = 0
    while (suffix < maxSuffix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++

    val keepBefore = oldStyles.copyOfRange(0, prefix)
    val keepAfter = oldStyles.copyOfRange(oldText.length - suffix, oldText.length)

    val insertedLength = (newText.length - suffix) - prefix
    val inheritedStyle = keepBefore.lastOrNull() ?: CharStyle()
    val inserted = Array(insertedLength.coerceAtLeast(0)) { inheritedStyle }

    return keepBefore + inserted + keepAfter
}

private fun Array<CharStyle>.toggleBold(range: IntRange): Array<CharStyle> {
    if (range.isEmpty()) return this
    val turnOn = range.any { !this[it].bold }
    return Array(size) { i -> if (i in range) this[i].copy(bold = turnOn) else this[i] }
}

private fun Array<CharStyle>.toggleItalic(range: IntRange): Array<CharStyle> {
    if (range.isEmpty()) return this
    val turnOn = range.any { !this[it].italic }
    return Array(size) { i -> if (i in range) this[i].copy(italic = turnOn) else this[i] }
}

// ---- composable ----

@Composable
fun RichTextContainer(
    textObject: CanvasObject.RichTextObject,
    onUpdate: (CanvasObject.RichTextObject) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val density = LocalDensity.current

    var fieldValue by remember(textObject.id) {
        mutableStateOf(TextFieldValue(text = textObject.text, selection = TextRange(textObject.text.length)))
    }
    var charStyles by remember(textObject.id) {
        mutableStateOf(textObject.annotatedStringJson.decodeSpans().toCharStyles(textObject.text.length))
    }
    // Track "type-ahead" style when cursor is at a position but no text is selected
    var typingStyleOverride by remember { mutableStateOf<CharStyle?>(null) }
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(textObject.text, textObject.annotatedStringJson) {
        if (textObject.text != fieldValue.text) {
            val clampedCursor = fieldValue.selection.end.coerceIn(0, textObject.text.length)
            fieldValue = fieldValue.copy(text = textObject.text, selection = TextRange(clampedCursor))
        }
        val incoming = textObject.annotatedStringJson.decodeSpans().toCharStyles(textObject.text.length)
        if (!incoming.contentEquals(charStyles)) charStyles = incoming
    }

    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
        else typingStyleOverride = null
    }

    // Reset typing override when selection changes (unless it's just typing)
    LaunchedEffect(fieldValue.selection) {
        if (fieldValue.selection.collapsed) {
            // We keep it if it was just set by toolbar, but usually cursor movement clears it
        } else {
            typingStyleOverride = null
        }
    }

    fun commit(newText: String, newStyles: Array<CharStyle>, newSelection: TextRange) {
        fieldValue = fieldValue.copy(text = newText, selection = newSelection)
        charStyles = newStyles
        onUpdate(textObject.copy(text = newText, annotatedStringJson = newStyles.toSpans().encodeSpans()))
    }

    val xDp = with(density) { textObject.x.toDp() }
    val yDp = with(density) { textObject.y.toDp() }
    val widthDp = with(density) { textObject.width.toDp() }
    val heightDp = with(density) { textObject.height.toDp() }

    // Toolbar active states
    val currentSelection = fieldValue.selection
    val isBoldActive = if (!currentSelection.collapsed) {
        val range = currentSelection.min until currentSelection.max
        range.all { charStyles.getOrNull(it)?.bold == true }
    } else {
        typingStyleOverride?.bold ?: charStyles.getOrNull((currentSelection.start - 1).coerceAtLeast(0))?.bold ?: false
    }

    val isItalicActive = if (!currentSelection.collapsed) {
        val range = currentSelection.min until currentSelection.max
        range.all { charStyles.getOrNull(it)?.italic == true }
    } else {
        typingStyleOverride?.italic ?: charStyles.getOrNull((currentSelection.start - 1).coerceAtLeast(0))?.italic ?: false
    }
    val latestTextObject by rememberUpdatedState(textObject)

    Box(
        modifier = Modifier
            .offset(xDp, yDp)
            .width(widthDp)
            .heightIn(min = heightDp)
            .then(
                if (isActive || isSelected) Modifier.border(1.dp, Color.Blue)
                else Modifier.border(1.dp, Color.Transparent)
            )
            // Disable outer clickable when active so BasicTextField handles selection gestures
            .then(if (!isActive) Modifier.clickable { onClick() } else Modifier)
            // Drag-to-move: SELECT mode only, vertical movement only (box stays full-width, flush left)
            .then(
                if (isSelected && !isActive) {
                    Modifier.pointerInput(textObject.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val current = latestTextObject
                            onUpdate(current.copy(y = current.y + dragAmount.y))
                        }
                    }
                } else Modifier
            )
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp)
            .onGloballyPositioned { coords ->
                // Update the height in the domain model if it grows during typing
                val newHeight = with(density) { coords.size.height.toDp().value }
                if (kotlin.math.abs(newHeight - textObject.height) > 1f) {
                    onUpdate(textObject.copy(height = newHeight))
                }
            }
    ) {
        BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    if (newValue.text != fieldValue.text) {
                        // Text changed: use typing override or inherited style
                        val usedOverride = typingStyleOverride
                        val newStyles = adjustStylesWithTypingOverride(
                            fieldValue.text, 
                            newValue.text, 
                            charStyles, 
                            usedOverride
                        )
                        charStyles = newStyles
                        typingStyleOverride = null // consume override
                        onUpdate(textObject.copy(text = newValue.text, annotatedStringJson = newStyles.toSpans().encodeSpans()))
                    } else if (newValue.selection != fieldValue.selection) {
                        // Just selection/cursor moved: clear typing override
                        typingStyleOverride = null
                    }
                    fieldValue = newValue
                },
                visualTransformation = { text ->
                    TransformedText(charStyles.toAnnotatedString(text.text), OffsetMapping.Identity)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .keyboardAware()
                    .focusRequester(focusRequester),
                enabled = isActive
            )
        }

        if (isActive) {
            RichTextFormattingToolbar(
                xDp = xDp,
                boxTopDp = yDp,
                boxHeightDp = heightDp,
                isBoldActive = isBoldActive,
                isItalicActive = isItalicActive,
                onToggleBold = {
                    if (!fieldValue.selection.collapsed) {
                        val range = fieldValue.selection.min until fieldValue.selection.max
                        commit(fieldValue.text, charStyles.toggleBold(range), fieldValue.selection)
                    } else {
                        val current = typingStyleOverride ?: charStyles.getOrNull((fieldValue.selection.start - 1).coerceAtLeast(0)) ?: CharStyle()
                        typingStyleOverride = current.copy(bold = !isBoldActive)
                    }
                },
                onToggleItalic = {
                    if (!fieldValue.selection.collapsed) {
                        val range = fieldValue.selection.min until fieldValue.selection.max
                        commit(fieldValue.text, charStyles.toggleItalic(range), fieldValue.selection)
                    } else {
                        val current = typingStyleOverride ?: charStyles.getOrNull((fieldValue.selection.start - 1).coerceAtLeast(0)) ?: CharStyle()
                        typingStyleOverride = current.copy(italic = !isItalicActive)
                    }
                },
                onToggleBullets = {
                    val lines = fieldValue.text.lines()
                    val isBullet = lines.all { it.trimStart().startsWith("• ") }
                    val newText = if (isBullet) lines.joinToString("\n") { it.trimStart().removePrefix("• ") }
                    else lines.joinToString("\n") { "• $it" }
                    val newStyles = adjustStylesForEdit(fieldValue.text, newText, charStyles)
                    commit(newText, newStyles, TextRange(newText.length.coerceAtLeast(0)))
                },
                onToggleNumbers = {
                    val lines = fieldValue.text.lines()
                    val isNumbered = lines.firstOrNull()?.trimStart()
                        ?.let { it.isNotEmpty() && it[0].isDigit() && it.contains(". ") } ?: false
                    val newText = if (isNumbered) {
                        lines.joinToString("\n") { it.replaceFirst(Regex("^\\s*\\d+\\.\\s*"), "") }
                    } else {
                        lines.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                    }
                    val newStyles = adjustStylesForEdit(fieldValue.text, newText, charStyles)
                    commit(newText, newStyles, TextRange(newText.length.coerceAtLeast(0)))
                }
            )
        }
    }


private fun adjustStylesWithTypingOverride(
    oldText: String, 
    newText: String, 
    oldStyles: Array<CharStyle>,
    override: CharStyle?
): Array<CharStyle> {
    if (oldText == newText) return oldStyles

    val maxPrefix = minOf(oldText.length, newText.length)
    var prefix = 0
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++

    val maxSuffix = maxPrefix - prefix
    var suffix = 0
    while (suffix < maxSuffix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++

    val keepBefore = oldStyles.copyOfRange(0, prefix)
    val keepAfter = oldStyles.copyOfRange(oldText.length - suffix, oldText.length)

    val insertedLength = (newText.length - suffix) - prefix
    val inheritedStyle = override ?: keepBefore.lastOrNull() ?: CharStyle()
    val inserted = Array(insertedLength.coerceAtLeast(0)) { inheritedStyle }

    return keepBefore + inserted + keepAfter
}

@Composable
private fun RichTextFormattingToolbar(
    xDp: androidx.compose.ui.unit.Dp,
    boxTopDp: androidx.compose.ui.unit.Dp,
    boxHeightDp: androidx.compose.ui.unit.Dp,
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleBullets: () -> Unit,
    onToggleNumbers: () -> Unit
) {
    val toolbarHeight = 56.dp
    val gap = 8.dp
    val toolbarY = if (boxTopDp > toolbarHeight + gap) boxTopDp - toolbarHeight - gap
    else boxTopDp + boxHeightDp + gap

    Surface(
        modifier = Modifier.offset(xDp, toolbarY).wrapContentSize(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onToggleBold) {
                Icon(
                    Icons.Default.FormatBold,
                    contentDescription = "Bold",
                    tint = if (isBoldActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onToggleItalic) {
                Icon(
                    Icons.Default.FormatItalic,
                    contentDescription = "Italic",
                    tint = if (isItalicActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onToggleBullets) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bullet List")
            }
            IconButton(onClick = onToggleNumbers) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered List")
            }
        }
    }
}
