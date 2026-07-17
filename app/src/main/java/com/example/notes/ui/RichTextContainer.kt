package com.example.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.notes.domain.CanvasObject
import kotlin.math.roundToInt

@Composable
fun RichTextContainer(
    textObject: CanvasObject.RichTextObject,
    onUpdate: (CanvasObject.RichTextObject) -> Unit,
    isActive: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var textState by remember(textObject.id) { mutableStateOf(textObject.text) }
    
    // Sync text state when object updates from outside
    LaunchedEffect(textObject.text) {
        if (textObject.text != textState) {
            textState = textObject.text
        }
    }

    // Convert pixel coordinates/sizes to Dp for the Box modifiers
    val xDp = with(density) { textObject.x.toDp() }
    val yDp = with(density) { textObject.y.toDp() }
    val widthDp = with(density) { textObject.width.toDp() }
    val heightDp = with(density) { textObject.height.toDp() }

    Box(
        modifier = modifier
            .offset(xDp, yDp)
            .size(widthDp, heightDp)
            .then(
                if (isActive || isSelected) Modifier.border(1.dp, Color.Blue)
                else Modifier.border(1.dp, Color.Transparent)
            )
            .clickable(interactionSource = null, indication = null) {
                onClick()
            }
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp)
    ) {
        BasicTextField(
            value = textState,
            onValueChange = {
                textState = it
                onUpdate(textObject.copy(text = it))
            },
            modifier = Modifier.fillMaxSize(),
            enabled = isActive,
            textStyle = TextStyle(
                fontWeight = if (textObject.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (textObject.isItalic) FontStyle.Italic else FontStyle.Normal
            )
        )
    }
}
