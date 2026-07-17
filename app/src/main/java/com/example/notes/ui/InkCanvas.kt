package com.example.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.AffineTransform
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import coil.compose.AsyncImage
import com.example.notes.domain.*
import kotlin.math.roundToInt

@Composable
fun InkCanvas(
    objects: List<CanvasObject>,
    onStrokeFinished: (List<InkPoint>, colorHex: String, brushWidth: Float) -> Unit,
    onObjectRemoved: (String) -> Unit,
    onObjectUpdated: (CanvasObject) -> Unit,
    onEmptySpaceTapped: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    brushColor: String = "#000000",
    brushSize: Float = 5f,
    backgroundStyle: PageBackgroundStyle = PageBackgroundStyle.LINED,
    canvasMode: CanvasMode = CanvasMode.PEN,
    penType: String = "Pen",
    focusedTextId: String? = null,
    onFocusedTextChange: (String?) -> Unit = {},
    selectedObjectId: String? = null,
    onSelectedObjectChange: (String?) -> Unit = {}
) {
    val density = LocalDensity.current
    val strokeRenderer = remember { CanvasStrokeRenderer.create() }
    
    val currentBrush = remember(brushColor, brushSize, canvasMode, penType) {
        val family = when (penType) {
            "Pen" -> StockBrushes.pressurePen()
            "Brush" -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
            else -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
        }
        Brush.createWithColorIntArgb(
            family = family,
            colorIntArgb = android.graphics.Color.parseColor(brushColor),
            size = brushSize,
            epsilon = 0.1f
        )
    }

    // Cache strokes to avoid re-calculating expensive Stroke objects
    val finishedStrokes = remember(objects) {
        objects.filterIsInstance<CanvasObject.StrokeObject>().mapNotNull { strokeObj ->
            try {
                strokeObj.id to strokeObj.toStroke()
            } catch (e: Exception) {
                android.util.Log.e("InkCanvas", "Failed to convert stroke ${strokeObj.id} to Ink Stroke", e)
                null
            }
        }.toMap()
    }

    // Stability for callbacks
    val updatedObjects by rememberUpdatedState(objects)
    val updatedBrushColor by rememberUpdatedState(brushColor)
    val updatedBrushSize by rememberUpdatedState(brushSize)
    val updatedOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val updatedOnObjectRemoved by rememberUpdatedState(onObjectRemoved)
    val updatedOnEmptySpaceTapped by rememberUpdatedState(onEmptySpaceTapped)
    val updatedOnFocusedTextChange by rememberUpdatedState(onFocusedTextChange)
    val updatedOnSelectedObjectChange by rememberUpdatedState(onSelectedObjectChange)
    val updatedOnObjectUpdated by rememberUpdatedState(onObjectUpdated)

    Box(modifier = modifier.fillMaxSize()) {
        // LAYER 1: Unified Rendering
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { composeCanvas ->
                val androidCanvas = composeCanvas.nativeCanvas
                PageBackgroundRenderer.draw(androidCanvas, size.width, size.height, backgroundStyle, density.density)

                // Draw strokes in Z-order
                objects.filterIsInstance<CanvasObject.StrokeObject>().forEach { strokeObj ->
                    finishedStrokes[strokeObj.id]?.let { stroke ->
                        strokeRenderer.draw(androidCanvas, stroke, android.graphics.Matrix())
                    }
                }
            }
        }

        // LAYER 2: Non-Stroke Objects (RichText, Images)
        objects.filter { it !is CanvasObject.StrokeObject }.sortedBy { it.zIndex }.forEach { obj ->
            when (obj) {
                is CanvasObject.RichTextObject -> {
                    RichTextContainer(
                        textObject = obj,
                        onUpdate = updatedOnObjectUpdated,
                        isActive = canvasMode == CanvasMode.TYPE && focusedTextId == obj.id,
                        isSelected = canvasMode == CanvasMode.SELECT && selectedObjectId == obj.id,
                        onClick = { 
                            if (canvasMode == CanvasMode.TYPE) updatedOnFocusedTextChange(obj.id)
                            else if (canvasMode == CanvasMode.SELECT) updatedOnSelectedObjectChange(obj.id)
                        }
                    )
                }
                is CanvasObject.ImageObject -> {
                    AsyncImage(
                        model = obj.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .offset { IntOffset(obj.x.roundToInt(), obj.y.roundToInt()) }
                            .size(with(density) { obj.width.toDp() }, with(density) { obj.height.toDp() })
                            .clickable { if (canvasMode == CanvasMode.SELECT) updatedOnSelectedObjectChange(obj.id) }
                            .then(
                                if (canvasMode == CanvasMode.SELECT && selectedObjectId == obj.id) 
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                                else Modifier
                            )
                    )
                }
                else -> {}
            }
        }

        // LAYER 3: Gesture Interaction
        if (canvasMode == CanvasMode.PEN) {
            InProgressStrokes(
                defaultBrush = currentBrush,
                onStrokesFinished = { strokes ->
                    strokes.forEach { stroke ->
                        val points = stroke.inputs.toInkPoints()
                        if (points.isNotEmpty()) {
                            updatedOnStrokeFinished(points, updatedBrushColor, updatedBrushSize)
                        }
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(canvasMode) {
                        if (canvasMode == CanvasMode.ERASE) {
                            detectDragGestures { change, _ -> /* ERASE Logic (out of scope) */ }
                        } else if (canvasMode == CanvasMode.TYPE) {
                            detectTapGestures { offset ->
                                val hit = updatedObjects.filterIsInstance<CanvasObject.RichTextObject>()
                                    .sortedByDescending { it.zIndex }
                                    .firstOrNull { obj ->
                                        offset.x in obj.x..(obj.x + obj.width) &&
                                                offset.y in obj.y..(obj.y + obj.height)
                                    }
                                if (hit != null) {
                                    updatedOnFocusedTextChange(hit.id)
                                } else {
                                    updatedOnFocusedTextChange(null)
                                    updatedOnEmptySpaceTapped(offset.x, offset.y)
                                }
                            }
                        } else if (canvasMode == CanvasMode.SELECT) {
                            detectTapGestures { offset ->
                                val hit = updatedObjects.filter { it !is CanvasObject.StrokeObject }
                                    .sortedByDescending { it.zIndex }
                                    .firstOrNull { obj ->
                                        when (obj) {
                                            is CanvasObject.RichTextObject -> offset.x in obj.x..(obj.x + obj.width) && offset.y in obj.y..(obj.y + obj.height)
                                            is CanvasObject.ImageObject -> offset.x in obj.x..(obj.x + obj.width) && offset.y in obj.y..(obj.y + obj.height)
                                            else -> false
                                        }
                                    }
                                updatedOnSelectedObjectChange(hit?.id)
                            }
                        }
                    }
                    .pointerInput(canvasMode, selectedObjectId) {
                        if (canvasMode == CanvasMode.SELECT && selectedObjectId != null) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                updatedObjects.find { it.id == selectedObjectId }?.let { obj ->
                                    val updated = when (obj) {
                                        is CanvasObject.RichTextObject -> obj.copy(x = obj.x + dragAmount.x, y = obj.y + dragAmount.y)
                                        is CanvasObject.ImageObject -> obj.copy(x = obj.x + dragAmount.x, y = obj.y + dragAmount.y)
                                        else -> obj
                                    }
                                    if (updated !== obj) updatedOnObjectUpdated(updated)
                                }
                            }
                        }
                    }
            )
        }

        // LAYER 4: Overlays (Formatting & Selection)
        if (canvasMode == CanvasMode.TYPE && focusedTextId != null) {
            updatedObjects.filterIsInstance<CanvasObject.RichTextObject>()
                .find { it.id == focusedTextId }?.let { obj ->
                    FormattingToolbar(
                        textObject = obj,
                        onUpdate = updatedOnObjectUpdated,
                        density = density
                    )
                }
        }

        if (canvasMode == CanvasMode.SELECT && selectedObjectId != null) {
            updatedObjects.find { it.id == selectedObjectId }?.let { obj ->
                val rect = when (obj) {
                    is CanvasObject.RichTextObject -> Rect(obj.x, obj.y, obj.width, obj.height)
                    is CanvasObject.ImageObject -> Rect(obj.x, obj.y, obj.width, obj.height)
                    else -> null
                } ?: return@let

                SelectionOverlay(
                    id = obj.id,
                    x = rect.x,
                    y = rect.y,
                    width = rect.width,
                    height = rect.height,
                    onResize = { dx, dy, dw, dh ->
                        val updated = when (obj) {
                            is CanvasObject.RichTextObject -> obj.copy(x = obj.x + dx, y = obj.y + dy, width = (obj.width + dw).coerceAtLeast(50f), height = (obj.height + dh).coerceAtLeast(30f))
                            is CanvasObject.ImageObject -> obj.copy(x = obj.x + dx, y = obj.y + dy, width = (obj.width + dw).coerceAtLeast(50f), height = (obj.height + dh).coerceAtLeast(50f))
                            else -> obj
                        }
                        updatedOnObjectUpdated(updated)
                    },
                    onRemove = { updatedOnObjectRemoved(obj.id); updatedOnSelectedObjectChange(null) },
                    density = density
                )
            }
        }
    }
}

@Composable
fun FormattingToolbar(
    textObject: CanvasObject.RichTextObject,
    onUpdate: (CanvasObject.RichTextObject) -> Unit,
    density: Density
) {
    val xDp = with(density) { textObject.x.toDp() }
    val yDp = with(density) { (textObject.y - 50f).toDp() }

    Surface(
        modifier = Modifier
            .offset(xDp, yDp)
            .wrapContentSize(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { onUpdate(textObject.copy(isBold = !textObject.isBold)) }) {
                Icon(
                    Icons.Default.FormatBold,
                    contentDescription = "Bold",
                    tint = if (textObject.isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onUpdate(textObject.copy(isItalic = !textObject.isItalic)) }) {
                Icon(
                    Icons.Default.FormatItalic,
                    contentDescription = "Italic",
                    tint = if (textObject.isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { 
                val lines = textObject.text.lines()
                val isBullet = lines.all { it.trimStart().startsWith("• ") }
                val newText = if (isBullet) {
                    lines.joinToString("\n") { it.trimStart().removePrefix("• ") }
                } else {
                    lines.joinToString("\n") { "• $it" }
                }
                onUpdate(textObject.copy(text = newText))
            }) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List")
            }
            IconButton(onClick = {
                val lines = textObject.text.lines()
                val isNumbered = lines.firstOrNull()?.trimStart()?.let { 
                    it.isNotEmpty() && it[0].isDigit() && it.contains(". ")
                } ?: false
                
                val newText = if (isNumbered) {
                    lines.joinToString("\n") { it.replaceFirst(Regex("^\\s*\\d+\\.\\s*"), "") }
                } else {
                    lines.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                }
                onUpdate(textObject.copy(text = newText))
            }) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered List")
            }
        }
    }
}

@Composable
fun SelectionOverlay(
    id: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    onResize: (dx: Float, dy: Float, dw: Float, dh: Float) -> Unit,
    onRemove: () -> Unit,
    density: Density
) {
    val xDp = with(density) { x.toDp() }
    val yDp = with(density) { y.toDp() }
    val widthDp = with(density) { width.toDp() }
    val heightDp = with(density) { height.toDp() }

    Box(
        modifier = Modifier
            .offset(xDp, yDp)
            .size(widthDp, heightDp)
    ) {
        // Main bounding box (visual only)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )

        // Delete button (Top Right)
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(8.dp, (-8).dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // Resize handles (4 corners)
        ResizeHandle(Alignment.TopStart, onResize = { dx, dy -> onResize(dx, dy, -dx, -dy) })
        ResizeHandle(Alignment.TopEnd, onResize = { dx, dy -> onResize(0f, dy, dx, -dy) })
        ResizeHandle(Alignment.BottomStart, onResize = { dx, dy -> onResize(dx, 0f, -dx, dy) })
        ResizeHandle(Alignment.BottomEnd, onResize = { dx, dy -> onResize(0f, 0f, dx, dy) })
    }
}

@Composable
fun BoxScope.ResizeHandle(
    alignment: Alignment,
    onResize: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .align(alignment)
            .offset(
                x = if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart) (-8).dp else 8.dp,
                y = if (alignment == Alignment.TopStart || alignment == Alignment.TopEnd) (-8).dp else 8.dp
            )
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResize(dragAmount.x, dragAmount.y)
                }
            }
    )
}

private data class Rect(val x: Float, val y: Float, val width: Float, val height: Float)

private fun CanvasObject.StrokeObject.toStroke(): Stroke {
    val brush = Brush.createWithColorIntArgb(
        family = StockBrushes.marker(StockBrushes.MarkerVersion.V1),
        colorIntArgb = try {
            android.graphics.Color.parseColor(colorHex)
        } catch (e: Exception) {
            android.graphics.Color.BLACK
        },
        size = brushWidth,
        epsilon = 0.1f
    )
    val builder = MutableStrokeInputBatch()
    if (points.isEmpty()) {
        builder.add(InputToolType.STYLUS, 0f, 0f, 0L, 0f, 0f, 0f)
    } else {
        points.forEach { p ->
            builder.add(InputToolType.STYLUS, p.x, p.y, p.timestampMs, p.pressure, p.tiltX, p.tiltY)
        }
    }
    return Stroke(brush, builder)
}

private fun StrokeInputBatch.toInkPoints(): List<InkPoint> {
    val points = mutableListOf<InkPoint>()
    for (i in 0 until size) {
        val input = get(i)
        points.add(InkPoint(input.x, input.y, input.pressure, input.tiltRadians, input.orientationRadians, input.elapsedTimeMillis))
    }
    return points
}
