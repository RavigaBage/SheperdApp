package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FormatMode
import com.example.presentation.components.bounceClickable
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
    
    // Uriel States
    val bibleCache by viewModel.bibleCache.collectAsState()
    val urielResult by viewModel.urielResult.collectAsState()
    val urielIsGenerating by viewModel.urielIsGenerating.collectAsState()
    val urielSelectedWord by viewModel.urielSelectedWord.collectAsState()
    val urielActiveType by viewModel.urielActiveType.collectAsState()
    
    val urielTopic by viewModel.urielTopic.collectAsState()
    val urielThemeDraftResult by viewModel.urielThemeDraftResult.collectAsState()
    val urielThemeIsGenerating by viewModel.urielThemeIsGenerating.collectAsState()
    
    // Prayer Log States
    val hasPrayedToday by viewModel.hasPrayedToday.collectAsState()
    val prayerTimerSecondsRemaining by viewModel.prayerTimerSecondsRemaining.collectAsState()
    val isPrayerTimerRunning by viewModel.isPrayerTimerRunning.collectAsState()

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var showSaveSheet by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isSavedSuccess by remember { mutableStateOf(false) }

    var showMoreActions by remember { mutableStateOf(false) }
    var triggerBounce by remember { mutableStateOf(false) }
    var showPrayerTimerModal by remember { mutableStateOf(false) }
    var showThemeDrafterDialog by remember { mutableStateOf(false) }
    var showScriptureIntelDialog by remember { mutableStateOf(false) }
    var activeIntelScripture by remember { mutableStateOf("") }

    // AI Generation States
    var isListening by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }

    LaunchedEffect(inputText) {
        if (inputText != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = inputText)
        }
    }

    val selectedText = remember(textFieldValue.selection) {
        if (textFieldValue.selection.length > 0) {
            textFieldValue.text.substring(textFieldValue.selection.start, textFieldValue.selection.end)
        } else ""
    }

    // Loading message cycler for generation
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

    // Scripture reference scanner
    val scriptureRegex = """\b([12]\s+)?(?:Gen|Exo|Lev|Num|Deu|Jos|Jud|Rut|Sam|Kin|Chr|Ezr|Neh|Est|Job|Psa|Pro|Ecc|Sol|Isa|Jer|Lam|Eze|Dan|Hos|Joe|Amo|Oba|Jon|Mic|Nah|Hab|Zep|Hag|Zec|Mal|Mat|Mar|Luk|Joh|Act|Rom|Cor|Gal|Eph|Phi|Col|The|Tim|Tit|Heb|Jam|Pet|Rev)\.?\s+\d+:\d+(?:-\d+)?\b""".toRegex(RegexOption.IGNORE_CASE)
    val detectedScriptures = remember(inputText) {
        scriptureRegex.findAll(inputText).map { it.value }.distinct().toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uriel AI Study Tools", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearAiCanvas() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset AI Input", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        bottomBar = { },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. AI Task Input Area
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Describe your study task or paste draft text:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.updateAiInput(it.text)
                        },
                        placeholder = { Text("Example: Draft a 5-point outline for a sermon on 'Faith in Trials' based on James 1:2-4...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Black
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Use voice input button
                        IconButton(
                            onClick = { isListening = !isListening },
                            modifier = Modifier.clip(CircleShape).background(if (isListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        ) {
                            Icon(
                                if (isListening) Icons.Default.MicNone else Icons.Default.Mic,
                                contentDescription = "Voice prompt",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.triggerGeminiFormat() },
                            enabled = inputText.isNotBlank() && !isGenerating,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ask Uriel")
                        }
                    }
                }
            }

            // 3. Uriel Word Selection suggestions panel
            AnimatedVisibility(
                visible = selectedText.trim().isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    border = BorderStroke(1.5.dp, Color(0xFF3B82F6))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Uriel Assistant Selection Recognition",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target Word: \"$selectedText\"",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Consult Uriel's Research Modules:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Colorful suggestion chips from specification
                        val suggestionsList = listOf(
                            Triple("Translation", "🟦", Color(0xFF2563EB)),
                            Triple("Biblical Meaning", "🟩", Color(0xFF16A34A)),
                            Triple("Hebrew/Greek Origin", "🟨", Color(0xFFD97706)),
                            Triple("Bible Map", "🟧", Color(0xFFEA580C)),
                            Triple("Cross References", "🟪", Color(0xFF7C3AED)),
                            Triple("Related Scriptures", "🟥", Color(0xFFDC2626)),
                            Triple("Characters Connected", "🟫", Color(0xFF78350F)),
                            Triple("Timeline", "⬜", Color(0xFF4B5563)),
                            Triple("Historical Background", "🟦", Color(0xFF0D9488)),
                            Triple("Ask Uriel", "🟩", Color(0xFF059669))
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suggestionsList) { (title, symbol, color) ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable {
                                            viewModel.getUrielWordInsight(selectedText, title)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(symbol, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Floating Active Uriel streaming output block
            if (urielIsGenerating || urielResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Book, contentDescription = null, tint = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Uriel Insights: $urielActiveType",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    fontSize = 13.sp
                                )
                            }
                            if (urielIsGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFF59E0B), strokeWidth = 2.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reference Word: \"$urielSelectedWord\"",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = urielResult,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        if (urielResult.isNotEmpty() && !urielIsGenerating) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Append researched notes straight into notes field!
                                Button(
                                    onClick = {
                                        val separator = if (inputText.trim().isEmpty()) "" else "\n\n"
                                        val insightHeader = "### Uriel Research on '$urielSelectedWord' ($urielActiveType):\n"
                                        viewModel.updateAiInput("$inputText$separator$insightHeader$urielResult")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add to Notes", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(urielResult))
                                    }
                                ) {
                                    Text("Copy", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Detected Scripture references (Scripture Intelligence)
            if (detectedScriptures.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Detected Scriptures",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B2B4B)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We found these scripture references in your notebook. Tap to open Uriel's deep Scripture Intelligence study modules:",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(detectedScriptures) { scripture ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE0F2FE))
                                        .clickable {
                                            activeIntelScripture = scripture
                                            showScriptureIntelDialog = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = Color(0xFF0369A1), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(scripture, fontSize = 11.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Practical Tool buttons: Theme Drafter & Bible Cache List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable { showThemeDrafterDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Theme Drafter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                        Text("Brainstorm thematic angles", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable {
                            // Quick scan notes for scriptures
                            if (detectedScriptures.isNotEmpty()) {
                                activeIntelScripture = detectedScriptures.first()
                                showScriptureIntelDialog = true
                            } else {
                                activeIntelScripture = "John 3:16"
                                showScriptureIntelDialog = true
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Scripture Intel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                        Text("Deep scripture analyst", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            }

            // 7. Local Bible Cache System (Offline persistence list)
            if (bibleCache.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Storage, contentDescription = null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Local Scripture Database Cache",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${bibleCache.size} verses", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }

                        if (expanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(bibleCache) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(item.verseReference, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                                                Text(item.translation, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(item.scriptureText, fontSize = 11.sp, color = Color.DarkGray, lineHeight = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            // 8. Central Premium Sermon Formatter Studio
            Text(
                "Sermon Formatter & Studio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2B4B)
            )

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

            // Large Formatter Run Button
            Button(
                onClick = { viewModel.triggerGeminiFormat() },
                enabled = inputText.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .bounceClickable { },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Format & Package into Beautiful Document", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                "Generated Document Preview",
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = loadingMessages[loadingMessageIndex],
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
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
                        }

                        if (formattedResult.isNotEmpty() && !isGenerating) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    saveTitle = "Study_Resource_${System.currentTimeMillis() % 10000}"
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

    // Modal Sheet: Save DOCX Details
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

    // Modal: Prayer Timer
    if (showPrayerTimerModal) {
        AlertDialog(
            onDismissRequest = {
                viewModel.stopPrayerTimer()
                showPrayerTimerModal = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = Color(0xFFD97706))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sanctuary Prayer Prep", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val minutes = prayerTimerSecondsRemaining / 60
                    val seconds = prayerTimerSecondsRemaining % 60
                    val progress = prayerTimerSecondsRemaining.toFloat() / (10 * 60f)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            .padding(10.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            color = Color(0xFFD97706),
                            trackColor = Color(0xFFFEF3C7)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                            Text("remains", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "\"Be still, and know that I am God.\"",
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF78350F)
                    )
                    Text("- Psalm 46:10", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markAsPrayedToday()
                        viewModel.stopPrayerTimer()
                        showPrayerTimerModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Amen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.stopPrayerTimer()
                        showPrayerTimerModal = false
                    }
                ) {
                    Text("Pause Study")
                }
            }
        )
    }

    // Modal: Theme Drafter Dialog
    if (showThemeDrafterDialog) {
        var drafterTopic by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showThemeDrafterDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF8B5CF6))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uriel Theme Drafter", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Input a story, central scripture passage, or topic, and Uriel will draft 4-5 core preaching themes, central truths, and applications for you.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    OutlinedTextField(
                        value = drafterTopic,
                        onValueChange = { drafterTopic = it },
                        placeholder = { Text("e.g. David and Goliath, or Romans 12:1-2", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.generateUrielThemeDraft(drafterTopic) },
                        enabled = drafterTopic.isNotBlank() && !urielThemeIsGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Draft Themes")
                    }

                    if (urielThemeIsGenerating || urielThemeDraftResult.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                            border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Brainstormed Themes Angle", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF6D28D9))
                                    if (urielThemeIsGenerating) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF8B5CF6), strokeWidth = 2.dp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(urielThemeDraftResult, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 17.sp)

                                if (urielThemeDraftResult.isNotEmpty() && !urielThemeIsGenerating) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = {
                                                val sep = if (inputText.trim().isEmpty()) "" else "\n\n"
                                                viewModel.updateAiInput("$inputText$sep### Uriel Brainstormed Preaching Themes:\n$urielThemeDraftResult")
                                                showThemeDrafterDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Insert into Notebook", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDrafterDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal: Scripture Intelligence Dialog
    if (showScriptureIntelDialog) {
        var intelOption by remember { mutableStateOf("Verse Explanation") }
        AlertDialog(
            onDismissRequest = { showScriptureIntelDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scripture Intelligence", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Scripture Reference: \"$activeIntelScripture\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46)
                    )

                    Text(
                        "Select research topic for active Scripture:",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    val intelOptions = listOf(
                        "Verse Explanation",
                        "Chapter Summary",
                        "Historical Context",
                        "Cultural Context",
                        "Important Themes",
                        "Original Language Insights",
                        "Related Prophecies",
                        "Cross References"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(intelOptions) { opt ->
                            val isSel = intelOption == opt
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSel) Color(0xFF10B981) else Color(0xFFE2E8F0))
                                    .clickable {
                                        intelOption = opt
                                        viewModel.getUrielScriptureIntelligence(activeIntelScripture, opt)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(opt, color = if (isSel) Color.White else Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (urielIsGenerating || urielResult.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Uriel Scripture Research ($intelOption)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF047857))
                                    if (urielIsGenerating) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(urielResult, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 17.sp)

                                if (urielResult.isNotEmpty() && !urielIsGenerating) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = {
                                                val sep = if (inputText.trim().isEmpty()) "" else "\n\n"
                                                viewModel.updateAiInput("$inputText$sep### Uriel Scripture Research on $activeIntelScripture ($intelOption):\n$urielResult")
                                                showScriptureIntelDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Insert into Notebook", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScriptureIntelDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
