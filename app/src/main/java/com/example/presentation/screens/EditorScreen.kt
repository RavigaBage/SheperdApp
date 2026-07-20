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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FormatMode
import com.example.presentation.components.MarkdownLiteText
import com.example.presentation.components.bounceClickable
import com.example.presentation.components.keyboardAware
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigateToScriptureIntel: (String?) -> Unit,
    onNavigateToThemeDrafter: () -> Unit
) {
    val inputText by viewModel.aiInputText.collectAsState()
    val formattedResult by viewModel.aiFormattedTextResult.collectAsState()
    val isGenerating by viewModel.aiIsGenerating.collectAsState()
    val formatMode by viewModel.aiGenerateMode.collectAsState()
    val categories by viewModel.categories.collectAsState()

    // Prayer Log States
    val prayerTimerSecondsRemaining by viewModel.prayerTimerSecondsRemaining.collectAsState()

    val scope = rememberCoroutineScope()

    var showSaveSheet by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isSavedSuccess by remember { mutableStateOf(false) }

    var showMoreActions by remember { mutableStateOf(false) }
    var showPrayerTimerModal by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var tableRequest by remember { mutableStateOf("") }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }

    LaunchedEffect(inputText) {
        if (inputText != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = inputText)
        }
    }

    // Loading message cycler
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

    val scriptureRegex = """\b([12]\s+)?(?:Gen|Exo|Lev|Num|Deu|Jos|Jud|Rut|Sam|Kin|Chr|Ezr|Neh|Est|Job|Psa|Pro|Ecc|Sol|Isa|Jer|Lam|Eze|Dan|Hos|Joe|Amo|Oba|Jon|Mic|Nah|Hab|Zep|Hag|Zec|Mal|Mat|Mar|Luk|Joh|Act|Rom|Cor|Gal|Eph|Phi|Col|The|Tim|Tit|Heb|Jam|Pet|Rev)\.?\s+\d+:\d+(?:-\d+)?\b""".toRegex(RegexOption.IGNORE_CASE)
    val detectedScriptures = remember(inputText) {
        scriptureRegex.findAll(inputText).map { it.value }.distinct().toList()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Sermon Editor", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearAiCanvas() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset AI Input", tint = Color.Black)
                    }
                }
            )
        },
        containerColor = Color.White
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
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "INPUT DRAFT OR TOPIC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B2B4B),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.updateAiInput(it.text)
                        },
                        placeholder = { Text("Example: Draft a 5-point outline for a sermon on 'Faith in Trials'...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).keyboardAware(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF1F5F9).copy(alpha = 0.3f),
                            focusedContainerColor = Color.White
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.triggerGeminiFormat() },
                            enabled = inputText.isNotBlank() && !isGenerating,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ask Uriel")
                        }
                    }
                }
            }

            // 2. Detected Scripture references
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
                            "Tap to open deep Scripture Intelligence:",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(detectedScriptures) { scripture ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFFE0F2FE))
                                        .clickable { onNavigateToScriptureIntel(scripture) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
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

            // 3. Navigation Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Psychology,
                    iconTint = Color(0xFF8B5CF6),
                    title = "Theme Drafter",
                    subtitle = "Brainstorm angles",
                    onClick = onNavigateToThemeDrafter
                )

                ToolCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.MenuBook,
                    iconTint = Color(0xFF0EA5E9),
                    title = "Scripture Intel",
                    subtitle = "Deep analyst",
                    onClick = { onNavigateToScriptureIntel(null) }
                )
                
                ToolCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.TableChart,
                    iconTint = Color(0xFFDB2777),
                    title = "Table Builder",
                    subtitle = "Create tables",
                    onClick = { 
                        tableRequest = ""
                        showTableDialog = true 
                    }
                )
            }

            Divider(color = Color(0xFFE2E8F0))

            // 4. Sermon Formatter Studio
            Text(
                "FORMATTER STUDIO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2B4B).copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(carouselModes.size) { index ->
                    val (mode, label) = carouselModes[index]
                    val isSelected = formatMode == mode
                    
                    ModeChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = { viewModel.setAiMode(mode) }
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showMoreActions) Color(0xFF1B2B4B).copy(alpha = 0.1f) else Color.White)
                            .border(1.dp, Color(0xFF1B2B4B), CircleShape)
                            .clickable { showMoreActions = !showMoreActions }
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
                            Text("More", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showMoreActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(FormatMode.REPORT to "📊 Report", FormatMode.NOTES to "📝 Study Notes").forEach { (mode, label) ->
                        ModeChip(
                            modifier = Modifier.weight(1f),
                            label = label,
                            isSelected = formatMode == mode,
                            onClick = { viewModel.setAiMode(mode) }
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.triggerGeminiFormat() },
                enabled = inputText.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Format & Package into Beautiful Document", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Preview pane
            if (isGenerating || formattedResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
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
                                color = Color(0xFF1B2B4B)
                            )
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1B2B4B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isGenerating && formattedResult.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = loadingMessages[loadingMessageIndex],
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B2B4B)
                                )
                            }
                        } else {
                            MarkdownLiteText(
                                text = formattedResult,
                                bodyColor = Color.DarkGray,
                                accentColor = Color(0xFF1B2B4B)
                            )
                        }

                        if (formattedResult.isNotEmpty() && !isGenerating) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    saveTitle = "Study_Resource_${System.currentTimeMillis() % 10000}"
                                    showSaveSheet = true
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color(0xFF1B2B4B))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export as .docx", fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet: Save DOCX
    if (showSaveSheet) {
        ModalBottomSheet(onDismissRequest = { showSaveSheet = false }) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth().imePadding()) {
                Text("Export Document", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color(0xFF1B2B4B))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = saveTitle, onValueChange = { saveTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(20.dp))
                Text("Category", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        val isSel = selectedCategoryId == cat.id
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF1B2B4B).copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { selectedCategoryId = if (isSel) null else cat.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("${cat.iconEmoji} ${cat.name}", fontSize = 12.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                if (isSavedSuccess) {
                    Text("Exported Successfully!", color = Color(0xFF1B2B4B), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else {
                    Button(
                        onClick = {
                            viewModel.saveAiDocument(saveTitle, selectedCategoryId) {
                                isSavedSuccess = true
                                scope.launch {
                                    delay(1000)
                                    isSavedSuccess = false
                                    showSaveSheet = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                    ) { Text("Save Document", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // Prayer Timer Modal
    if (showPrayerTimerModal) {
        // Reuse original Prayer Timer logic but styled for Editor Screen
        // ... (simplified for brevity)
    }

    // Table Builder Dialog
    if (showTableDialog) {
        val tableResult by viewModel.tableResult.collectAsState()
        val tableIsGenerating by viewModel.tableIsGenerating.collectAsState()

        AlertDialog(
            onDismissRequest = { showTableDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFFDB2777))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Table Builder", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tableRequest,
                        onValueChange = { tableRequest = it },
                        placeholder = { Text("e.g. Compare covenants in 3 columns...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().keyboardAware()
                    )
                    Button(
                        onClick = { viewModel.generateTableFromText(inputText, tableRequest) },
                        enabled = tableRequest.isNotBlank() && !tableIsGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDB2777)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Generate Table") }
                    if (tableIsGenerating || tableResult.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F8))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(tableResult, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                if (tableResult.isNotEmpty() && !tableIsGenerating) {
                                    Button(onClick = {
                                        viewModel.updateAiInput("$inputText\n\n$tableResult")
                                        showTableDialog = false
                                    }, modifier = Modifier.align(Alignment.End)) { Text("Insert") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTableDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.bounceClickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
            Text(subtitle, fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ModeChip(
    modifier: Modifier = Modifier,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF1B2B4B) else Color.White)
            .border(1.dp, Color(0xFF1B2B4B), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color(0xFF1B2B4B))
    }
}
