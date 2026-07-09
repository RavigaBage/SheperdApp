package com.example.preachmode

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.preachmode.model.HighlightSpan
import com.example.preachmode.model.PreachDocument
import com.example.preachmode.model.PreachSection
import com.example.preachmode.model.SectionType
import com.example.preachmode.model.ScriptureBlock
import com.example.preachmode.ui.*
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

    var activeTranslationSpan by remember { mutableStateOf<HighlightSpan?>(null) }
    var activeScriptureReveal by remember { mutableStateOf<ScriptureBlock?>(null) }
    var showSlideSorter by remember { mutableStateOf(false) }
    var showTimerPanel by remember { mutableStateOf(false) }
    var isMirrorMode by remember { mutableStateOf(false) }
    var showSpeakerNotes by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(true) } // Start in full screen

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Trigger haptic tick on critical timer checkpoints
    var triggeredHalfwayHaptic by remember { mutableStateOf(false) }
    var triggeredWarningHaptic by remember { mutableStateOf(false) }

    val totalDurationMillis = durationMinutes * 60 * 1000L

    LaunchedEffect(timerState.elapsed) {
        val remaining = timerState.remaining
        // Halfway checkpoint (50% remaining)
        if (remaining in (totalDurationMillis / 2 - 5000)..(totalDurationMillis / 2 + 5000) && !triggeredHalfwayHaptic) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            triggeredHalfwayHaptic = true
        }
        // 5 Minutes left checkpoint
        val fiveMinutesMillis = 5 * 60 * 1000L
        if (remaining in (fiveMinutesMillis - 5000)..(fiveMinutesMillis + 5000) && !triggeredWarningHaptic) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            triggeredWarningHaptic = true
        }
    }

    // Keep screen on while in preach mode
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Permission launcher for Android 13+ notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        preachViewModel.startPreaching(filePath, durationMinutes)
    }

    // Fire standard Android alert notification when remaining hits 0
    LaunchedEffect(timerState.isUp) {
        if (timerState.isUp) {
            PreachNotificationHelper.fireTimeUpNotification(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAF7F0) // Soft ivory/cream sanctuary-friendly neutral
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is PreachUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF1B2B4B),
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Preparing Your Pulpit Sections",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B2B4B),
                                    fontFamily = FontFamily.Serif,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Parsing document pages, analyzing structural sections, and calculating reading speed metrics for live sermon presentation.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(28.dp))
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.LightGray.copy(alpha = 0.4f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.95f)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.LightGray.copy(alpha = 0.4f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.LightGray.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }
                    }
                }
                is PreachUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                        ) {
                            Text("Go Back")
                        }
                    }
                }
                is PreachUiState.Success -> {
                    val doc = state.document
                    if (doc.sections.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No sections available in this document.",
                                color = Color.Black,
                                fontSize = 18.sp
                            )
                        }
                    } else {
                        val pagerState = rememberPagerState(pageCount = { doc.sections.size })

                        // Standard pulpit mode with visual pagination and floating timer
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top Utility Bar (Hidden in Full Screen / Mirror Mode)
                                AnimatedVisibility(
                                    visible = !isFullScreen && !isMirrorMode,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = onBack) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color(0xFF1B2B4B)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = sermonTitle,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1B2B4B),
                                                fontFamily = FontFamily.Serif,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Section ${pagerState.currentPage + 1} of ${doc.sections.size}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { isMirrorMode = true; isFullScreen = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Tv,
                                                    contentDescription = "Mirror mode",
                                                    tint = Color(0xFF1B2B4B)
                                                )
                                            }
                                            IconButton(onClick = { showSlideSorter = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.GridView,
                                                    contentDescription = "Slide sorter",
                                                    tint = Color(0xFF1B2B4B)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Sermon Slide / Section Horizontal Pager with custom transition
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(if (isMirrorMode) Color.White else Color.Transparent)
                                ) { page ->
                                    val section = doc.sections[page]

                                    // Custom crossfade + slight slide animation on swipe
                                    val pageOffset =
                                        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                    val pageAlpha = 1f - pageOffset.coerceIn(0f, 1f)
                                    val slideTranslationX = if (pagerState.currentPage > page) {
                                        -pageOffset * 60.dp.value
                                    } else {
                                        pageOffset * 60.dp.value
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = pageAlpha
                                                translationX = slideTranslationX
                                            }
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (isFullScreen || isMirrorMode) {
                                                            // Toggle full screen off on tap if not in mirror mode,
                                                            // or just keep it for navigation if desired.
                                                            // isFullScreen = !isFullScreen
                                                        }
                                                    },
                                                    onDoubleTap = {
                                                        if (isMirrorMode) {
                                                            isMirrorMode = false
                                                            isFullScreen = false
                                                        } else {
                                                            isFullScreen = !isFullScreen
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(
                                                horizontal = if (isMirrorMode) 24.dp else 48.dp,
                                                vertical = if (isMirrorMode) 24.dp else 24.dp
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Slide layout
                                            Text(
                                                text = section.displayText,
                                                fontSize = if (isMirrorMode) {
                                                    if (isLandscape) 40.sp else 32.sp
                                                } else {
                                                    if (isLandscape) 34.sp else 26.sp
                                                },
                                                lineHeight = if (isMirrorMode) {
                                                    if (isLandscape) 54.sp else 44.sp
                                                } else {
                                                    if (isLandscape) 48.sp else 38.sp
                                                },
                                                fontWeight = if (section.type == SectionType.TITLE || isMirrorMode) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = FontFamily.Serif,
                                                color = Color.Black,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(bottom = 16.dp)
                                            )

                                            if (!isMirrorMode) {
                                                // Highlight scripture reference buttons if available
                                                if (section.hiddenScripture != null) {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Button(
                                                        onClick = {
                                                            activeScriptureReveal =
                                                                section.hiddenScripture
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFF1B2B4B).copy(
                                                                alpha = 0.08f
                                                            )
                                                        ),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            Color(0xFF1B2B4B)
                                                        ),
                                                        shape = RoundedCornerShape(24.dp),
                                                        contentPadding = PaddingValues(
                                                            horizontal = 16.dp,
                                                            vertical = 8.dp
                                                        )
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.Book,
                                                                contentDescription = null,
                                                                tint = Color(0xFF1B2B4B),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "📖 Reveal ${section.hiddenScripture.reference}",
                                                                color = Color(0xFF1B2B4B),
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                // Two Content Layers toggle (Speaker Notes)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(
                                                    onClick = {
                                                        showSpeakerNotes = !showSpeakerNotes
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color.Transparent
                                                    ),
                                                    contentPadding = PaddingValues(
                                                        horizontal = 12.dp,
                                                        vertical = 4.dp
                                                    )
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = if (showSpeakerNotes) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = null,
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            "Pastor Speaker Notes",
                                                            fontSize = 12.sp,
                                                            color = Color.Gray,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }

                                                // Speaker Notes panel
                                                AnimatedVisibility(
                                                    visible = showSpeakerNotes,
                                                    enter = expandVertically() + fadeIn(),
                                                    exit = shrinkVertically() + fadeOut()
                                                ) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 12.dp),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = Color.White
                                                        ),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            Color(0xFF1B2B4B).copy(alpha = 0.15f)
                                                        )
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Text(
                                                                "Speaker Guide & Points:",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF1B2B4B)
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = "• Speak with clarity and passion.\n• Emphasize key scripture.\n• Keep a comfortable vocal cadence.",
                                                                fontSize = 12.sp,
                                                                lineHeight = 18.sp,
                                                                color = Color.DarkGray
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.height(24.dp))
                                                Text(
                                                    "Slide ${page + 1} / ${doc.sections.size} • Double tap to exit Mirror",
                                                    fontSize = 12.sp,
                                                    color = Color.LightGray,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom placeholder spacer
                                Spacer(modifier = Modifier.height(100.dp))
                            }

                            // Navigation touch zones
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(56.dp)
                                    .align(Alignment.CenterStart)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        }
                                    }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(56.dp)
                                    .align(Alignment.CenterEnd)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (pagerState.currentPage < doc.sections.size - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    }
                            )

                            // Vertical navigation dots strip
                            if (!isMirrorMode) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 16.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.03f))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                showSlideSorter = true
                                            }
                                        )
                                        .padding(vertical = 12.dp, horizontal = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        doc.sections.forEachIndexed { index, section ->
                                            val isCurrent = pagerState.currentPage == index
                                            val dotScale by animateFloatAsState(
                                                targetValue = if (isCurrent) 1.3f else 1.0f,
                                                animationSpec = tween(200),
                                                label = "dotScale"
                                            )
                                            
                                            var showTooltip by remember { mutableStateOf(false) }

                                            Box(contentAlignment = Alignment.CenterStart) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .scale(dotScale)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isCurrent) Color(0xFF1B2B4B)
                                                            else Color.Gray.copy(alpha = 0.4f)
                                                        )
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    showTooltip = true
                                                                    tryAwaitRelease()
                                                                    showTooltip = false
                                                                },
                                                                onTap = {
                                                                    coroutineScope.launch {
                                                                        pagerState.animateScrollToPage(index)
                                                                    }
                                                                }
                                                            )
                                                        }
                                                )
                                                
                                                DropdownMenu(
                                                    expanded = showTooltip,
                                                    onDismissRequest = { showTooltip = false },
                                                    offset = androidx.compose.ui.unit.DpOffset((-120).dp, 0.dp),
                                                    modifier = Modifier.background(Color(0xFF1B2B4B))
                                                ) {
                                                    Text(
                                                        text = section.displayText.take(20) + "...",
                                                        modifier = Modifier.padding(8.dp),
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Fixed Timer Icon & Component bottom, ~8% from the bottom edge
                            val isOvertime = timerState.remaining <= 0
                            val ringColor = if (isOvertime) {
                                Color(0xFFC62828) // Red visual warning
                            } else if (timerState.remaining < 5 * 60 * 1000) {
                                Color(0xFFFFA000) // Amber visual warning
                            } else {
                                Color(0xFF1B2B4B) // Calm primary Navy
                            }

                            // Timer pulse animation on warnings
                            val timerPulseTransition = rememberInfiniteTransition(label = "timerPulse")
                            val timerPulseScale by timerPulseTransition.animateFloat(
                                initialValue = 0.95f,
                                targetValue = 1.08f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "timerPulseScale"
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 60.dp, end = 24.dp) // ~8% from edge
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Timer expanded info panel
                                    AnimatedVisibility(
                                        visible = showTimerPanel,
                                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .width(260.dp)
                                                .padding(bottom = 8.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.5.dp, ringColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                    "Sermon Pace Assistant",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                // Elapsed and Remaining displays
                                                val elapsedMins = timerState.elapsed / 60000
                                                val elapsedSecs = (timerState.elapsed / 1000) % 60
                                                val remainingMins = timerState.remaining / 60000
                                                val remainingSecs = (timerState.remaining / 1000) % 60
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Elapsed: ${String.format("%02d:%02d", elapsedMins, elapsedSecs)}", fontSize = 13.sp, color = Color.Black)
                                                    Text(
                                                        text = if (isOvertime) "Overtime: ${String.format("%02d:%02d", -remainingMins, remainingSecs.absoluteValue)}" 
                                                               else "Remaining: ${String.format("%02d:%02d", remainingMins, remainingSecs)}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ringColor
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Smooth progress indicator
                                                val elapsedRatio = (timerState.elapsed.toFloat() / totalDurationMillis).coerceIn(0f, 1f)
                                                LinearProgressIndicator(
                                                    progress = { elapsedRatio },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                                    color = ringColor,
                                                    trackColor = ringColor.copy(alpha = 0.15f)
                                                )

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Auto time-pacing calculation (Speech speed coach)
                                                val slideRatio = (pagerState.currentPage + 1).toFloat() / doc.sections.size
                                                val pacingText = if (slideRatio > elapsedRatio + 0.12f) {
                                                    "⏱️ Speaking Pace: Fast (Ahead of schedule)"
                                                } else if (slideRatio < elapsedRatio - 0.12f) {
                                                    "⚠️ Speaking Pace: Slow (Please speak faster)"
                                                } else {
                                                    "✓ Speaking Pace: Perfect on track"
                                                }

                                                Text(
                                                    text = pacingText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (slideRatio < elapsedRatio - 0.12f) Color(0xFFC62828) else Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    }

                                    // Floating Clock trigger button
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .scale(if (isOvertime) timerPulseScale else 1.0f)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(if (isOvertime) 3.dp else 2.dp, ringColor, CircleShape)
                                            .clickable { showTimerPanel = !showTimerPanel },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = "Timer Bar Toggle",
                                            tint = ringColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Powerpoint-style Slide Sorter Grid Overlay
                        AnimatedVisibility(
                            visible = showSlideSorter,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFFAF7F0))
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Sorter header bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Sermon Slide Sorter Grid",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Serif,
                                            color = Color(0xFF1B2B4B)
                                        )
                                        IconButton(onClick = { showSlideSorter = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close sorter",
                                                tint = Color(0xFF1B2B4B)
                                            )
                                        }
                                    }

                                    // 2-Column PowerPoint-style slide grid representation
                                    val chunkedSections = doc.sections.chunked(2)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        chunkedSections.forEachIndexed { rowIndex, pair ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                pair.forEachIndexed { colIndex, section ->
                                                    val slideIdx = rowIndex * 2 + colIndex
                                                    val isSelected = pagerState.currentPage == slideIdx
                                                    
                                                    Card(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(110.dp)
                                                            .clickable {
                                                                // Jump to slide directly
                                                                coroutineScope.launch {
                                                                    pagerState.scrollToPage(slideIdx)
                                                                }
                                                                showSlideSorter = false
                                                            },
                                                        border = if (isSelected) BorderStroke(2.dp, Color(0xFF1B2B4B)) else null,
                                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text(
                                                                "Slide ${slideIdx + 1}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Gray
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = section.displayText,
                                                                fontSize = 12.sp,
                                                                maxLines = 3,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = Color.Black
                                                            )
                                                        }
                                                    }
                                                }
                                                if (pair.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // High-fidelity Scripture Reveal Centered Glass Overlay
            activeScriptureReveal?.let { block ->
                var swipeOffsetY by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { activeScriptureReveal = null }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .offset { IntOffset(0, swipeOffsetY.roundToInt()) }
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFF1B2B4B).copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        swipeOffsetY = (swipeOffsetY + dragAmount.y).coerceAtLeast(0f)
                                    },
                                    onDragEnd = {
                                        if (swipeOffsetY > 150f) {
                                            activeScriptureReveal = null
                                        }
                                        swipeOffsetY = 0f
                                    }
                                )
                            }
                            .clickable(enabled = false) {} // block click propagation
                            .padding(24.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.reference,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = Color(0xFF1B2B4B)
                                )
                                IconButton(onClick = { activeScriptureReveal = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close scripture")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = block.text.trim(),
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                                fontStyle = FontStyle.Italic,
                                fontFamily = FontFamily.Serif,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Swipe down or tap outside to close",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Translation bottom sheet overlay
            activeTranslationSpan?.let { span ->
                TranslationBottomSheet(
                    span = span,
                    onDismiss = { activeTranslationSpan = null }
                )
            }
        }
    }
}
