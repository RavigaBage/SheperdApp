package com.example.presentation.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.presentation.viewmodel.BoardViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoardCanvasScreen(
    viewModel: BoardViewModel,
    onBack: () -> Unit
) {
    val strokes = viewModel.currentStrokes
    val currentTool by viewModel.currentTool
    val currentColor by viewModel.currentColor
    val currentWidth by viewModel.currentWidth
    val isSaving by viewModel.isSaving

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activeStrokePoints = remember { mutableStateListOf<BoardPoint>() }
    
    // Zoom and Pan state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    var showZoomIndicator by remember { mutableStateOf(false) }
    
    LaunchedEffect(scale) {
        showZoomIndicator = true
        kotlinx.coroutines.delay(1000)
        showZoomIndicator = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // --- THE CANVAS ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentTool) {
                    if (currentTool != BoardToolType.ERASER_STROKE) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                activeStrokePoints.clear()
                                activeStrokePoints.add(BoardPoint(
                                    (startOffset.x - offset.x) / scale,
                                    (startOffset.y - offset.y) / scale
                                ))
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                activeStrokePoints.add(BoardPoint(
                                    (change.position.x - offset.x) / scale,
                                    (change.position.y - offset.y) / scale,
                                    change.pressure
                                ))
                            },
                            onDragEnd = {
                                if (activeStrokePoints.isNotEmpty()) {
                                    viewModel.addStroke(
                                        BoardStroke(
                                            points = activeStrokePoints.toList(),
                                            color = currentColor,
                                            width = currentWidth,
                                            toolType = currentTool
                                        )
                                    )
                                    activeStrokePoints.clear()
                                }
                            }
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offset += pan
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Draw Templates
                drawTemplate(BoardTemplate.RULED)

                // Draw existing strokes
                strokes.forEach { stroke ->
                    drawStroke(stroke)
                }
                
                // Draw active stroke
                if (activeStrokePoints.isNotEmpty()) {
                    drawActiveStroke(activeStrokePoints, currentColor, currentWidth, currentTool)
                }
            }
        }

        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSaving) {
                    Text("Saving...", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                }
                IconButton(onClick = { 
                    val activity = context as? Activity
                    activity?.requestedOrientation = if (isLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }) { 
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Screen")
                }
                IconButton(onClick = { viewModel.undo() }) { Icon(Icons.Default.Undo, contentDescription = "Undo") }
                IconButton(onClick = { viewModel.redo() }) { Icon(Icons.Default.Redo, contentDescription = "Redo") }
            }
        }

        // --- ZOOM INDICATOR ---
        AnimatedVisibility(
            visible = showZoomIndicator,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "${(scale * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // --- BOTTOM TOOLBAR ---
        BoardToolbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            currentTool = currentTool,
            onToolSelected = { viewModel.setTool(it) },
            currentColor = currentColor,
            onColorSelected = { viewModel.setColor(it) },
            currentWidth = currentWidth,
            onWidthChanged = { viewModel.setWidth(it) }
        )
    }
}

fun DrawScope.drawStroke(stroke: BoardStroke) {
    if (stroke.points.size < 2) return
    
    val path = Path()
    path.moveTo(stroke.points[0].x, stroke.points[0].y)
    for (i in 1 until stroke.points.size) {
        path.lineTo(stroke.points[i].x, stroke.points[i].y)
    }
    
    drawPath(
        path = path,
        color = Color(stroke.color),
        style = Stroke(
            width = stroke.width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        alpha = if (stroke.toolType == BoardToolType.HIGHLIGHTER) 0.4f else 1.0f
    )
}

fun DrawScope.drawActiveStroke(points: List<BoardPoint>, color: Int, width: Float, tool: BoardToolType) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    drawPath(
        path = path,
        color = Color(color),
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        alpha = if (tool == BoardToolType.HIGHLIGHTER) 0.4f else 1.0f
    )
}

fun DrawScope.drawTemplate(template: BoardTemplate) {
    when (template) {
        BoardTemplate.RULED -> {
            val step = 40.dp.toPx()
            for (y in 0 until size.height.toInt() step step.toInt()) {
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }
        else -> {}
    }
}

@Composable
fun BoardToolbar(
    modifier: Modifier = Modifier,
    currentTool: BoardToolType,
    onToolSelected: (BoardToolType) -> Unit,
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    currentWidth: Float,
    onWidthChanged: (Float) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LineWeight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Slider(
                    value = currentWidth,
                    onValueChange = onWidthChanged,
                    valueRange = 1f..50f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF1B2B4B), activeTrackColor = Color(0xFF1B2B4B))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolButton(Icons.Default.Create, BoardToolType.PEN_MEDIUM, currentTool, onToolSelected)
                    ToolButton(Icons.Default.Highlight, BoardToolType.HIGHLIGHTER, currentTool, onToolSelected)
                    ToolButton(Icons.Default.AutoFixNormal, BoardToolType.ERASER_STROKE, currentTool, onToolSelected)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = listOf(0xFF1B2B4B.toInt(), 0xFFC9A84C.toInt(), 0xFFC0392B.toInt(), 0xFF2D6A4F.toInt(), 0xFF000000.toInt())
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (currentColor == color) 2.dp else 0.dp,
                                    color = if (currentColor == color) Color.Gray else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tool: BoardToolType,
    currentTool: BoardToolType,
    onSelect: (BoardToolType) -> Unit
) {
    val isSelected = currentTool == tool
    IconButton(
        onClick = { onSelect(tool) },
        modifier = Modifier
            .size(40.dp)
            .background(
                if (isSelected) Color(0xFF1B2B4B).copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF1B2B4B) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
    }
}
