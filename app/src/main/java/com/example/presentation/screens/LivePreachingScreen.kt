package com.example.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.bounceClickable
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun LivePreachingScreen(
    viewModel: ShepherdViewModel,
    sermonId: String,
    filePath: String,
    sermonTitle: String,
    durationMinutes: Int,
    scrollSpeed: Int,
    fontScale: Float,
    onBack: () -> Unit,
    onNavigateToAltarCall: () -> Unit
) {
    val paragraphs by viewModel.paragraphs.collectAsState()
    val listState = rememberLazyListState()

    var isScrolling by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(durationMinutes * 60) }

    // Countdown Timer logic
    LaunchedEffect(true) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    // Auto-scroll loop utilizing thread-safe raw-delta dispatching
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            // Adjust delta scaling based on scrollSpeed (1..5)
            val stepSize = scrollSpeed * 0.45f
            while (true) {
                listState.dispatchRawDelta(stepSize)
                delay(16) // ~60 FPS
            }
        }
    }

    // Calculate progression index percentage
    val scrollPercentage = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) 0
            else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                ((lastVisible.toFloat() / totalItems.toFloat()) * 100).toInt().coerceIn(0, 100)
            }
        }
    }

    // Capture preaching completion on back transition
    fun finishPreaching() {
        val uniqueVerses = paragraphs.flatMap { it.verseSpans }.map { it.reference }.distinct()
        // Save to verse usage catalog
        viewModel.saveVerseUsage(sermonId, sermonTitle, uniqueVerses)
        // Log sermon preaching history metrics
        viewModel.logPreachingActivity(sermonId, sermonTitle, "Live Service", durationMinutes, uniqueVerses)
        onBack()
    }

    BackHandler {
        finishPreaching()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Dark eye-safe presenter canvas
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // CONTROL HEAD STRIP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Return chevron
                IconButton(onClick = { finishPreaching() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Exit to Editor", tint = Color.White)
                }

                // Play/Pause Action
                IconButton(
                    onClick = { isScrolling = !isScrolling },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isScrolling) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color(0xFF2E2E2E)
                        )
                ) {
                    Icon(
                        imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Scroll speed",
                        tint = if (isScrolling) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Title
                Text(
                    text = sermonTitle,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Countdown Timer (colored dynamically based on remaining ratio)
                val totalSeconds = durationMinutes * 60
                val pct = remainingSeconds.toFloat() / totalSeconds.toFloat()
                val timerColor = when {
                    pct > 0.5f -> Color(0xFF4CAF50)  // green
                    pct > 0.25f -> Color(0xFFFF9800) // amber
                    else -> Color(0xFFF44336)        // red
                }

                val h = remainingSeconds / 3600
                val m = (remainingSeconds % 3600) / 60
                val s = remainingSeconds % 60
                val timerString = String.format("%02d:%02d:%02d", h, m, s)

                Text(
                    text = timerString,
                    color = timerColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Progress Indicator
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(60.dp)
                ) {
                    Text(
                        "${scrollPercentage.value}%",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { scrollPercentage.value.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFF3E3E3E),
                    )
                }
            }

            // CORE TELEPROMPTER CANVAS
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 100.dp, bottom = 300.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                itemsIndexed(paragraphs) { index, paragraph ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Giant cues for quick visual scanning
                        paragraph.cueType?.let { cue ->
                            val (badgeBg, badgeText) = when (cue) {
                                com.example.util.DocumentParser.CueType.PAUSE -> Color(0xFF546E7A) to Color(0xFFCFD8DC)
                                com.example.util.DocumentParser.CueType.RAISE_VOICE -> Color(0xFFC62828) to Color(0xFFFFCDD2)
                                com.example.util.DocumentParser.CueType.LOOK_UP -> Color(0xFF1565C0) to Color(0xFFBBDEFB)
                                com.example.util.DocumentParser.CueType.ALTAR_CALL -> Color(0xFF6A1B9A) to Color(0xFFE1BEE7)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cue.name.replace("_", " "),
                                    color = badgeText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }

                        // Super sized, scaled font for live preaching
                        val baseScale = when (paragraph.headingLevel) {
                            1 -> 32.sp
                            2 -> 26.sp
                            else -> 22.sp
                        }
                        val finalSize = (baseScale.value * fontScale).sp
                        val textFontWeight = if (paragraph.headingLevel > 0) FontWeight.Bold else FontWeight.Medium

                        Text(
                            text = paragraph.rawText,
                            fontSize = finalSize,
                            fontWeight = textFontWeight,
                            color = if (paragraph.headingLevel > 0) MaterialTheme.colorScheme.primary else Color.White,
                            lineHeight = (finalSize.value * 1.55).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON - ALTAR CALL CARD
        LargeFloatingActionButton(
            onClick = onNavigateToAltarCall,
            containerColor = Color(0xFFFFD54F), // Bright Gold
            contentColor = Color(0xFF3E2723), // Dark brown
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(72.dp)
                .bounceClickable { onNavigateToAltarCall() }
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = "Trigger Altar Call",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
