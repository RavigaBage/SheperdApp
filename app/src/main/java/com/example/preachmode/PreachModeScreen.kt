package com.example.preachmode

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.preachmode.model.*
import com.example.preachmode.ui.PreachNotificationHelper
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreachModeScreen(
    filePath: String,
    sermonTitle: String,
    durationMinutes: Int,
    onBack: () -> Unit,
    preachViewModel: PreachModeViewModel = viewModel()
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    val uiState by preachViewModel.uiState.collectAsState()
    val timerState by preachViewModel.timerState.collectAsState()
    val allAnnotations by preachViewModel.annotations.collectAsState()

    var activeScriptureReveal by remember { mutableStateOf<ScriptureBlock?>(null) }
    var showSlideSorter by remember { mutableStateOf(false) }
    var showTimerPanel by remember { mutableStateOf(false) }
    var isMirrorMode by remember { mutableStateOf(false) }
    var showSpeakerNotes by remember { mutableStateOf(false) }

    // Annotation State
    var isAnnotateMode by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf(PreachToolType.PEN) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    val activeStrokePoints = remember { mutableStateListOf<PreachPoint>() }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val totalDurationMillis = durationMinutes * 60 * 1000L

    // Keep screen on
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(Unit) {
        preachViewModel.startPreaching(filePath, durationMinutes)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAF7F0)
    ) {
        Box(modifier = Modifier.fillMaxSize().blur(if (activeScriptureReveal != null) 16.dp else 0.dp)) {
            when (val state = uiState) {
                is PreachUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF1B2B4B))
                    }
                }
                is PreachUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, textAlign = TextAlign.Center)
                        Button(onClick = onBack, modifier = Modifier.padding(top = 24.dp)) { Text("Go Back") }
                    }
                }
                is PreachUiState.Success -> {
                    val doc = state.document
                    val pagerState = rememberPagerState(pageCount = { doc.sections.size })

                    Column(modifier = Modifier.fillMaxSize()) {
                        PreachHeader(
                            title = sermonTitle,
                            currentSlide = pagerState.currentPage + 1,
                            totalSlides = doc.sections.size,
                            onBack = onBack,
                            onToggleMirror = { isMirrorMode = !isMirrorMode },
                            onToggleOrientation = {
                                val activity = context as? Activity
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            },
                            onOpenSorter = { showSlideSorter = true }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(
                                    width = if (isAnnotateMode) 2.dp else 0.dp,
                                    color = if (isAnnotateMode) currentColor.copy(alpha = 0.3f) else Color.Transparent
                                )
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = !isAnnotateMode,
                                beyondViewportPageCount = 1
                            ) { page ->
                                val section = doc.sections[page]
                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = 1f - pageOffset.coerceIn(0f, 1f)
                                            translationX = if (pagerState.currentPage > page) -pageOffset * 50.dp.value else pageOffset * 50.dp.value
                                        }
                                ) {
                                    // Slide Text Layer
                                    SlideContent(
                                        section = section,
                                        isLandscape = isLandscape,
                                        showSpeakerNotes = showSpeakerNotes,
                                        onToggleNotes = { showSpeakerNotes = !showSpeakerNotes },
                                        onRevealScripture = { if (!isAnnotateMode) activeScriptureReveal = it },
                                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
                                    )

                                    // Annotation Canvas Layer
                                    val pageAnnotations = allAnnotations[page] ?: emptyList()
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(isAnnotateMode, currentTool, currentColor) {
                                                if (!isAnnotateMode) return@pointerInput
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        activeStrokePoints.clear()
                                                        activeStrokePoints.add(PreachPoint(offset.x, offset.y))
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        activeStrokePoints.add(PreachPoint(change.position.x, change.position.y))
                                                    },
                                                    onDragEnd = {
                                                        if (activeStrokePoints.isNotEmpty()) {
                                                            preachViewModel.addStroke(
                                                                page,
                                                                PreachStroke(
                                                                    points = activeStrokePoints.toList(),
                                                                    color = currentColor.toArgb(),
                                                                    width = if (currentTool == PreachToolType.HIGHLIGHTER) 24f else 4f,
                                                                    toolType = currentTool
                                                                )
                                                            )
                                                            activeStrokePoints.clear()
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        // Draw existing strokes
                                        pageAnnotations.forEach { stroke ->
                                            drawPreachStroke(stroke)
                                        }
                                        // Draw active stroke
                                        if (activeStrokePoints.isNotEmpty()) {
                                            drawActivePreachStroke(activeStrokePoints, currentColor, if (currentTool == PreachToolType.HIGHLIGHTER) 24f else 4f, currentTool)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(84.dp))
                    }

                    // Floating UI Elements
                    PaginationStrip(
                        totalSlides = doc.sections.size,
                        currentSlide = pagerState.currentPage,
                        onJumpToSlide = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        onLongClick = { showSlideSorter = true }
                    )

                    TimerComponent(
                        timerState = timerState,
                        totalDurationMillis = totalDurationMillis,
                        currentSlide = pagerState.currentPage + 1,
                        totalSlides = doc.sections.size,
                        isExpanded = showTimerPanel,
                        onToggleExpand = { showTimerPanel = !showTimerPanel }
                    )

                    // Annotation Toolbar
                    AnnotationToolbar(
                        isActive = isAnnotateMode,
                        currentTool = currentTool,
                        currentColor = currentColor,
                        onToggleMode = { isAnnotateMode = !isAnnotateMode },
                        onSelectTool = { currentTool = it },
                        onSelectColor = { currentColor = it },
                        onUndo = { preachViewModel.undoStroke(pagerState.currentPage) },
                        onClear = { preachViewModel.clearAnnotations(pagerState.currentPage) }
                    )

                    if (showSlideSorter) {
                        SlideSorterOverlay(
                            doc = doc,
                            currentSlide = pagerState.currentPage,
                            onClose = { showSlideSorter = false },
                            onSelectSlide = { index ->
                                coroutineScope.launch { pagerState.scrollToPage(index) }
                                showSlideSorter = false
                            }
                        )
                    }
                }
            }
        }

        ScriptureRevealOverlay(
            block = activeScriptureReveal,
            onDismiss = { activeScriptureReveal = null }
        )
    }
}

@Composable
fun BoxScope.AnnotationToolbar(
    isActive: Boolean,
    currentTool: PreachToolType,
    currentColor: Color,
    onToggleMode: () -> Unit,
    onSelectTool: (PreachToolType) -> Unit,
    onSelectColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    val toolbarWidth = 56.dp
    val expandedWidth = 240.dp
    val width by animateDpAsState(if (isActive) expandedWidth else toolbarWidth, tween(200))

    Surface(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 12.dp)
            .width(width)
            .height(if (isActive) 340.dp else 56.dp)
            .clip(RoundedCornerShape(28.dp)),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, if (isActive) Color(0xFF1B2B4B).copy(alpha = 0.1f) else Color.Transparent)
    ) {
        if (!isActive) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { onToggleMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Annotate", tint = Color(0xFF1B2B4B))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header/Close
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Annotation", tint = Color.Gray)
                }

                // Tools
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolIconButton(Icons.Outlined.Create, PreachToolType.PEN, currentTool) { onSelectTool(it) }
                    ToolIconButton(Icons.Outlined.Highlight, PreachToolType.HIGHLIGHTER, currentTool) { onSelectTool(it) }
                    ToolIconButton(Icons.Outlined.AutoFixNormal, PreachToolType.ERASER, currentTool) { onSelectTool(it) }
                }

                // Colors
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = listOf(Color.Red, Color(0xFFF4D35E), Color(0xFF1B2B4B))
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (currentColor == color) 2.dp else 0.dp,
                                    color = if (currentColor == color) Color.Gray else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(color) }
                        )
                    }
                }

                // Actions
                Row {
                    IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onClear) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
fun ToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tool: PreachToolType,
    currentTool: PreachToolType,
    onSelect: (PreachToolType) -> Unit
) {
    val isSelected = currentTool == tool
    IconButton(
        onClick = { onSelect(tool) },
        modifier = Modifier
            .size(40.dp)
            .background(if (isSelected) Color(0xFF1B2B4B).copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) Color(0xFF1B2B4B) else Color.Gray)
    }
}

fun DrawScope.drawPreachStroke(stroke: PreachStroke) {
    if (stroke.points.size < 2) return
    val path = Path()
    path.moveTo(stroke.points[0].x, stroke.points[0].y)
    for (i in 1 until stroke.points.size) {
        path.lineTo(stroke.points[i].x, stroke.points[i].y)
    }
    drawPath(
        path = path,
        color = Color(stroke.color),
        style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        alpha = if (stroke.toolType == PreachToolType.HIGHLIGHTER) 0.4f else 1.0f
    )
}

fun DrawScope.drawActivePreachStroke(points: List<PreachPoint>, color: Color, width: Float, tool: PreachToolType) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        alpha = if (tool == PreachToolType.HIGHLIGHTER) 0.4f else 1.0f
    )
}

@Composable
fun PreachHeader(
    title: String,
    currentSlide: Int,
    totalSlides: Int,
    onBack: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenSorter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B2B4B))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color(0xFF1B2B4B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Slide $currentSlide of $totalSlides", fontSize = 12.sp, color = Color.Gray)
        }

        Row {
            IconButton(onClick = onToggleMirror) { Icon(Icons.Default.Tv, contentDescription = "Mirror", tint = Color(0xFF1B2B4B)) }
            IconButton(onClick = onToggleOrientation) { 
                Icon(
                    imageVector = Icons.Default.ScreenRotation, 
                    contentDescription = "Rotate Screen", 
                    tint = Color(0xFF1B2B4B)
                ) 
            }
            IconButton(onClick = onOpenSorter) { Icon(Icons.Default.GridView, contentDescription = "Sorter", tint = Color(0xFF1B2B4B)) }
        }
    }
}

@Composable
fun SlideContent(
    section: PreachSection,
    isLandscape: Boolean,
    showSpeakerNotes: Boolean,
    onToggleNotes: () -> Unit,
    onRevealScripture: (ScriptureBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = section.displayText,
            fontSize = if (isLandscape) 34.sp else 26.sp,
            lineHeight = if (isLandscape) 48.sp else 38.sp,
            fontWeight = if (section.type == SectionType.TITLE) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Serif,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        if (section.hiddenScripture != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = section.hiddenScripture.reference,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B2B4B).copy(alpha = 0.08f))
                    .clickable { onRevealScripture(section.hiddenScripture) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color(0xFF1B2B4B),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        TextButton(onClick = onToggleNotes) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (showSpeakerNotes) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Speaker Notes", fontSize = 12.sp, color = Color.Gray)
            }
        }

        AnimatedVisibility(visible = showSpeakerNotes) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "• Emphasize key points.\n• Pause for reflection.\n• Keep eye contact.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.PaginationStrip(
    totalSlides: Int,
    currentSlide: Int,
    onJumpToSlide: (Int) -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.02f))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalSlides) { index ->
                val isCurrent = currentSlide == index
                val scale by animateFloatAsState(if (isCurrent) 1.3f else 1.0f, tween(200))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(if (isCurrent) Color(0xFF1B2B4B) else Color.Gray.copy(alpha = 0.3f))
                        .clickable { onJumpToSlide(index) }
                )
            }
        }
    }
}

@Composable
fun BoxScope.TimerComponent(
    timerState: TimerState,
    totalDurationMillis: Long,
    currentSlide: Int,
    totalSlides: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val isOvertime = timerState.remaining <= 0
    val ringColor = when {
        isOvertime -> Color(0xFFC62828)
        timerState.remaining < 5 * 60 * 1000 -> Color(0xFFFFA000)
        else -> Color(0xFF1B2B4B)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = isExpanded, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Card(
                modifier = Modifier.width(240.dp).padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val remainingMins = timerState.remaining.absoluteValue / 60000
                    val remainingSecs = (timerState.remaining.absoluteValue / 1000) % 60
                    Text(
                        text = if (isOvertime) "OVERTIME" else "REMAINING",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ringColor
                    )
                    Text(
                        text = String.format("%02d:%02d", remainingMins, remainingSecs),
                        fontSize = 24.sp, fontWeight = FontWeight.Black, color = ringColor
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val elapsedRatio = (timerState.elapsed.toFloat() / totalDurationMillis).coerceIn(0f, 1f)
                    val slideRatio = currentSlide.toFloat() / totalSlides
                    val isBehind = slideRatio < elapsedRatio - 0.1f
                    
                    Text(
                        text = if (isBehind) "⚠️ Speaking Pace: SLOW" else "✓ Pace: ON TRACK",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (isBehind) Color(0xFFC62828) else Color(0xFF2E7D32)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(if (isOvertime) pulseScale else 1f)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, ringColor, CircleShape)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = ringColor)
        }
    }
}

@Composable
fun ScriptureRevealOverlay(
    block: ScriptureBlock?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = block != null,
        enter = fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.95f),
        exit = fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 0.95f)
    ) {
        var swipeOffsetY by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            block?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                        .offset { IntOffset(0, swipeOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    swipeOffsetY = (swipeOffsetY + dragAmount.y).coerceAtLeast(0f)
                                },
                                onDragEnd = {
                                    if (swipeOffsetY > 150f) {
                                        onDismiss()
                                    }
                                    swipeOffsetY = 0f
                                }
                            )
                        }
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(it.reference, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color(0xFF1B2B4B))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Swipe down to close", tint = Color.LightGray)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it.text, fontSize = 18.sp, lineHeight = 26.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tap outside or swipe down to dismiss", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun SlideSorterOverlay(
    doc: PreachDocument,
    currentSlide: Int,
    onClose: () -> Unit,
    onSelectSlide: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF7F0))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Slide Grid", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = null) }
            }
            
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                doc.sections.chunked(2).forEachIndexed { rowIndex, pair ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { colIndex, section ->
                            val index = rowIndex * 2 + colIndex
                            Card(
                                modifier = Modifier.weight(1f).height(100.dp).clickable { onSelectSlide(index) },
                                border = if (currentSlide == index) BorderStroke(2.dp, Color(0xFF1B2B4B)) else null,
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Box(modifier = Modifier.padding(12.dp)) {
                                    Text(section.displayText, maxLines = 3, fontSize = 12.sp, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
