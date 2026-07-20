package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.MarkdownLiteText
import com.example.presentation.components.keyboardAware
import com.example.presentation.viewmodel.ShepherdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDrafterScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit
) {
    var drafterTopic by remember { mutableStateOf("") }
    val urielThemeDraftResult by viewModel.urielThemeDraftResult.collectAsState()
    val urielThemeIsGenerating by viewModel.urielThemeIsGenerating.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    // Simple session history
    val draftHistory = remember { mutableStateListOf<Pair<String, String>>() }
    var displayedResult by remember { mutableStateOf("") }
    
    LaunchedEffect(urielThemeDraftResult) {
        if (urielThemeIsGenerating) {
            displayedResult = urielThemeDraftResult
        }
    }

    LaunchedEffect(urielThemeIsGenerating) {
        if (!urielThemeIsGenerating && urielThemeDraftResult.isNotEmpty()) {
            displayedResult = urielThemeDraftResult
            if (draftHistory.none { it.second == urielThemeDraftResult }) {
                draftHistory.add(0, drafterTopic to urielThemeDraftResult)
            }
        }
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = { Text("Theme Drafter", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Input Area
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "BRAINSTORM TOPIC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = drafterTopic,
                        onValueChange = { drafterTopic = it },
                        placeholder = { Text("e.g. David and Goliath, or Romans 12:1-2", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().keyboardAware(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.generateUrielThemeDraft(drafterTopic) },
                        enabled = drafterTopic.isNotBlank() && !urielThemeIsGenerating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Draft Themes", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Results
            if (urielThemeIsGenerating || displayedResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "BRAINSTORMED THEMES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                            if (urielThemeIsGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF8B5CF6)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Structured Parsing TODO: Attempt to split by numbers or headings
                        MarkdownLiteText(
                            text = displayedResult,
                            bodyColor = Color.DarkGray,
                            accentColor = Color(0xFF7C3AED)
                        )

                        if (displayedResult.isNotEmpty() && !urielThemeIsGenerating) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(displayedResult)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Themes")
                            }
                        }
                    }
                }
            } else {
                // Empty State
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFF5F3FF))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Input a story or passage to brainstorm thematic angles.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.width(240.dp)
                        )
                    }
                }
            }
            
            // Session History
            if (draftHistory.isNotEmpty()) {
                Text(
                    "PREVIOUS DRAFTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                draftHistory.forEach { (topic, result) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                drafterTopic = topic
                                displayedResult = result
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = topic,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}
