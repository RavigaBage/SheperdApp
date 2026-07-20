package com.example.notes.ui

import android.annotation.SuppressLint
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
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.MutableBox
import androidx.ink.geometry.PartitionedMesh
import androidx.ink.strokes.StrokeInput
import androidx.ink.geometry.Angle
import androidx.ink.geometry.AffineTransform
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import coil.compose.AsyncImage
import com.example.notes.domain.*
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun InkCanvas(
    objects: List<CanvasObject>,
    onStrokeFinished: (id: String, points: List<InkPoint>, colorHex: String, brushWidth: Float) -> Unit,
    onObjectRemoved: (String) -> Unit,
    onObjectUpdated: (CanvasObject) -> Unit,
    onEmptySpaceTapped: (x: Float, y: Float, canvasWidthPx: Float) -> Unit,
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

    // Local cache for strokes to prevent flickering during save/load
    val localPendingStrokes = remember { mutableStateMapOf<String, Stroke>() }

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
        val strokeMap = objects.filterIsInstance<CanvasObject.StrokeObject>().mapNotNull { strokeObj ->
            try {
                strokeObj.id to strokeObj.toStroke()
            } catch (e: Exception) {
                android.util.Log.e("InkCanvas", "Failed to convert stroke ${strokeObj.id} to Ink Stroke", e)
                null
            }
        }.toMap()

        // Only drop a pending stroke once its persisted counterpart has *successfully*
        // been converted and will actually render from `objects`. If conversion fails,
        // the id won't be in strokeMap.keys, so the local pending copy stays on screen
        // as a fallback instead of vanishing.
        localPendingStrokes.keys.retainAll { id -> id !in strokeMap.keys }

        strokeMap
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Canvas width in px, used so tap-created text objects can span edge-to-edge
        val canvasWidthPx = with(density) { maxWidth.toPx() }

        // LAYER 1: Unified Rendering (Background & Strokes)
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

                // Draw local pending strokes (not yet synced to ViewModel)
                localPendingStrokes.values.forEach { stroke ->
                    strokeRenderer.draw(androidCanvas, stroke, android.graphics.Matrix())
                }
            }
        }

        // LAYER 2: Gesture Interaction Overlay for Canvas-level events
        // In TYPE/SELECT mode, this sits BELOW objects to catch background taps
        if (canvasMode == CanvasMode.PEN) {
            InProgressStrokes(
                defaultBrush = currentBrush,
                onStrokesFinished = { strokes ->
                    strokes.forEach { stroke ->
                        val points = stroke.inputs.toInkPoints()
                        if (points.isNotEmpty()) {
                            val id = java.util.UUID.randomUUID().toString()
                            localPendingStrokes[id] = stroke
                            updatedOnStrokeFinished(id, points, updatedBrushColor, updatedBrushSize)
                        }
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(canvasMode, canvasWidthPx) {
                        if (canvasMode == CanvasMode.ERASE) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val touchPoint = change.position
                                val eraserRect = MutableBox()
                                eraserRect.setXBounds(touchPoint.x - 20f, touchPoint.x + 20f)
                                eraserRect.setYBounds(touchPoint.y - 20f, touchPoint.y + 20f)

                                updatedObjects.filterIsInstance<CanvasObject.StrokeObject>().forEach { strokeObj ->
                                    finishedStrokes[strokeObj.id]?.let { stroke ->
                                        if (stroke.shape.computeCoverageIsGreaterThan(eraserRect, 0f)) {
                                            updatedOnObjectRemoved(strokeObj.id)
                                        }
                                    }
                                }
                            }
                        } else if (canvasMode == CanvasMode.TYPE) {
                            // Tapping background in TYPE mode deselects or creates new text
                            detectTapGestures { offset ->
                                updatedOnFocusedTextChange(null)
                                updatedOnEmptySpaceTapped(offset.x, offset.y, canvasWidthPx)
                            }
                        } else if (canvasMode == CanvasMode.SELECT) {
                            // Tapping background in SELECT mode clears selection
                            detectTapGestures {
                                updatedOnSelectedObjectChange(null)
                            }
                        }
                    }
            )
        }

        // LAYER 3: Non-Stroke Objects (RichText, Images)
        // Sitting ABOVE the background gesture layer so they can handle their own selection/drags.
        // We sort by zIndex but put the active/focused object on the very top for gesture priority.
        objects.filter { it !is CanvasObject.StrokeObject }
            .sortedBy {
                if (it.id == focusedTextId || it.id == selectedObjectId) 99999
                else it.zIndex
            }
            .forEach { obj ->
                key(obj.id) {
                    when (obj) {
                        is CanvasObject.RichTextObject -> {
                            rememberUpdatedState(obj)
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
                            val latestObj by rememberUpdatedState(obj)

                            AsyncImage(
                                model = obj.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .offset { IntOffset(obj.x.roundToInt(), obj.y.roundToInt()) }
                                    .size(with(density) { obj.width.toDp() }, with(density) { obj.height.toDp() })
                                    .clickable {
                                        if (canvasMode == CanvasMode.SELECT) updatedOnSelectedObjectChange(obj.id)
                                        else if (canvasMode == CanvasMode.TYPE) updatedOnFocusedTextChange(null)
                                    }
                                    .then(
                                        if (canvasMode == CanvasMode.SELECT && selectedObjectId == obj.id)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                                        else Modifier
                                    )
                                    .then(
                                        if (canvasMode == CanvasMode.SELECT && selectedObjectId == obj.id) {
                                            Modifier.pointerInput(obj.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val current = latestObj
                                                    updatedOnObjectUpdated(
                                                        current.copy(x = current.x + dragAmount.x, y = current.y + dragAmount.y)
                                                    )
                                                }
                                            }
                                        } else Modifier
                                    )
                            )
                        }
                        else -> {}
                    }
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
    val family = when (brushFamily) {
        "Pen" -> StockBrushes.pressurePen()
        "Brush" -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
        else -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
    }
    val brush = Brush.createWithColorIntArgb(
        family = family,
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
            val safeUnitLength = if (p.strokeUnitLength > 0 && p.strokeUnitLength.isFinite()) p.strokeUnitLength else 1f
            builder.add(
                type = InputToolType.STYLUS,
                x = p.x,
                y = p.y,
                elapsedTimeMillis = p.timestampMs,
                strokeUnitLengthCm = safeUnitLength,
                pressure = p.pressure,
                tiltRadians = p.tiltX,
                orientationRadians = p.tiltY
            )
        }
    }
    return Stroke(brush, builder)
}

private fun StrokeInputBatch.toInkPoints(): List<InkPoint> {
    val points = mutableListOf<InkPoint>()
    for (i in 0 until size) {
        val input = get(i)
        val rawUnitLength = input.strokeUnitLengthCm
        val safeUnitLength = if (rawUnitLength > 0 && rawUnitLength.isFinite()) rawUnitLength else 1f
        points.add(
            InkPoint(
                input.x,
                input.y,
                input.pressure,
                input.tiltRadians,
                input.orientationRadians,
                input.elapsedTimeMillis,
                safeUnitLength
            )
        )
    }
    return points
}