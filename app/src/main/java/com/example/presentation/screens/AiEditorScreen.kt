package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FormatMode
import com.example.presentation.components.bounceClickable
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEditorScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val inputText by viewModel.aiInputText.collectAsState()
    val formattedResult by viewModel.aiFormattedTextResult.collectAsState()
    val isGenerating by viewModel.aiIsGenerating.collectAsState()
    val formatMode by viewModel.aiGenerateMode.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var showSaveSheet by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isSavedSuccess by remember { mutableStateOf(false) }

    var showMoreActions by remember { mutableStateOf(false) }
    var triggerBounce by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    val inputScale by animateFloatAsState(
        targetValue = if (triggerBounce) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "inputBounce"
    )

    LaunchedEffect(isListening) {
        if (isListening) {
            delay(2500) // simulate listening
            val simulatedThoughts = listOf(
                "For I know the plans I have for you, declares the Lord, plans for welfare and not for evil, to give you a future and a hope. (Jeremiah 29:11)",
                "Sermon Topic: Overcoming Anxiety through Prayer.\nKey Verse: Philippians 4:6-7\nIntroduction: Modern life is full of stress, but God calls us to a perfect peace.",
                "Let your light so shine before men, that they may see your good works, and glorify your Father which is in heaven. (Matthew 5:16)",
                "Notes: Deep study on the Hebrew word 'Hesed' - lovingkindness and covenant loyalty."
            ).random()
            viewModel.updateAiInput(simulatedThoughts)
            isListening = false
            triggerBounce = true
            delay(100)
            triggerBounce = false
        }
    }

    // Floating loading message cycler
    var loadingMessageIndex by remember { mutableStateOf(0) }
    val loadingMessages = listOf(
        "Reading your words...",
        "Bringing clarity to your message...",
        "Structuring your sermon...",
        "Almost ready..."
    )

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            loadingMessageIndex = 0
            while (true) {
                delay(3500)
                loadingMessageIndex = (loadingMessageIndex + 1) % loadingMessages.size
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI sermon Studio", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (formattedResult.isNotEmpty() && !isGenerating) {
                        IconButton(onClick = { viewModel.clearAiCanvas() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear draft", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            MinistryBottomBar(
                currentRoute = "ai_editor",
                onNavigate = onNavigate
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Redesigned AI Action List & Pill Input Section
            Text(
                "AI Template Studio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2B4B),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Horizontal Carousel with Snapping
            val carouselState = rememberLazyListState()
            val snapFlingBehavior = rememberSnapFlingBehavior(carouselState)
            val carouselModes = listOf(
                FormatMode.SERMON to "⛪ Sermon",
                FormatMode.GENERAL to "✨ Improve",
                FormatMode.LETTER to "✉️ Letter"
            )

            LazyRow(
                state = carouselState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(carouselModes.size) { index ->
                    val (mode, label) = carouselModes[index]
                    val isSelected = formatMode == mode
                    
                    val chipScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = tween(150),
                        label = "chipScale"
                    )

                    Box(
                        modifier = Modifier
                            .scale(chipScale)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF1B2B4B) else Color.White)
                            .border(1.dp, Color(0xFF1B2B4B), CircleShape)
                            .bounceClickable {
                                viewModel.setAiMode(mode)
                                scope.launch {
                                    triggerBounce = true
                                    delay(100)
                                    triggerBounce = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF1B2B4B)
                        )
                    }
                }

                // Trailing "More" Chip
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showMoreActions) Color(0xFF1B2B4B).copy(alpha = 0.1f) else Color.White)
                            .border(1.dp, Color(0xFF1B2B4B), CircleShape)
                            .bounceClickable {
                                showMoreActions = !showMoreActions
                                scope.launch {
                                    triggerBounce = true
                                    delay(100)
                                    triggerBounce = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (showMoreActions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF1B2B4B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "More",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B2B4B)
                            )
                        }
                    }
                }
            }

            // Expandable Grid (In-Flow, 2-Column Grid for the Remaining Actions)
            AnimatedVisibility(
                visible = showMoreActions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "More Templates",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val remainingModes = listOf(
                            FormatMode.REPORT to "📊 Report",
                            FormatMode.NOTES to "📝 Study Notes"
                        )

                        remainingModes.forEach { (mode, label) ->
                            val isSelected = formatMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF1B2B4B) else Color.White)
                                    .border(1.dp, Color(0xFF1B2B4B), RoundedCornerShape(12.dp))
                                    .bounceClickable {
                                        viewModel.setAiMode(mode)
                                        scope.launch {
                                            triggerBounce = true
                                            delay(100)
                                            triggerBounce = false
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF1B2B4B)
                                )
                            }
                        }
                    }
                }
            }

            // Auto-growing Rounded Pill Input Bar with Inset Mic & Send button
            val activePlaceholder = if (isListening) "Listening to voice dictation..." else when (formatMode) {
                FormatMode.SERMON -> "Sermon: paste sermon ideas, bible passages, or outlines..."
                FormatMode.GENERAL -> "Improve: paste your text to refine and polish..."
                FormatMode.LETTER -> "Letter: paste your correspondence draft or key points..."
                FormatMode.REPORT -> "Report: paste study data or ministry notes..."
                FormatMode.NOTES -> "Study Notes: paste biblical references or discussion guide..."
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(inputScale),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing Mic Icon
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val micAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "micAlpha"
                    )

                    IconButton(
                        onClick = { isListening = !isListening },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFF1B2B4B).copy(alpha = 0.15f) else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicNone else Icons.Default.Mic,
                            contentDescription = "Mic input",
                            tint = Color(0xFF1B2B4B),
                            modifier = Modifier
                                .size(20.dp)
                                .scale(if (isListening) micAlpha else 1.0f)
                        )
                    }

                    // Text Input (Auto-grows with content)
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.updateAiInput(it) },
                        placeholder = {
                            Text(activePlaceholder, fontSize = 13.sp, color = Color.Gray)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight(),
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent
                        )
                    )

                    // Send Button
                    val isSendEnabled = inputText.isNotBlank() && !isGenerating
                    IconButton(
                        onClick = {
                            if (isSendEnabled) {
                                viewModel.triggerGeminiFormat()
                            }
                        },
                        enabled = isSendEnabled,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSendEnabled) Color(0xFF1B2B4B) else Color(0xFFF1F5F9)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send text",
                            tint = if (isSendEnabled) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Preview pane for active generation stream
            if (isGenerating || formattedResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Generated Study Preview",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isGenerating && formattedResult.isEmpty()) {
                            // Cycle pulsing message
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = loadingMessages[loadingMessageIndex],
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.animateContentSize()
                                )
                            }
                        } else {
                            Text(
                                text = formattedResult,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            // Typewriter cursor blink
                            if (isGenerating) {
                                val cursorAlpha = remember { Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        cursorAlpha.animateTo(1f, tween(500))
                                        cursorAlpha.animateTo(0f, tween(500))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 8.dp, height = 16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha.value))
                                )
                            }
                        }

                        if (formattedResult.isNotEmpty() && !isGenerating) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    saveTitle = "Sermon_Outline_${System.currentTimeMillis() % 10000}"
                                    showSaveSheet = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .bounceClickable { },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export outline as docx", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Export DOCX details selection bottom sheet
    if (showSaveSheet) {
        ModalBottomSheet(onDismissRequest = { showSaveSheet = false }) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "Export formatted resource",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = saveTitle,
                    onValueChange = { saveTitle = it },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text("Allocate Category Directory Link", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSel = selectedCategoryId == cat.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .bounceClickable {
                                    selectedCategoryId = if (isSel) null else cat.id
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${cat.iconEmoji} ${cat.name}", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isSavedSuccess) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF1B2B4B),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Export created successfully!",
                            color = Color(0xFF1B2B4B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.saveAiDocument(saveTitle, selectedCategoryId) {
                                isSavedSuccess = true
                                scope.launch {
                                    delay(1000)
                                    isSavedSuccess = false
                                    showSaveSheet = false
                                    viewModel.clearAiCanvas()
                                    onBack()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .bounceClickable { },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Write word Document & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
