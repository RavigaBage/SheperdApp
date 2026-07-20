package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun ScriptureIntelligenceScreen(
    viewModel: ShepherdViewModel,
    prefilledReference: String? = null,
    onBack: () -> Unit
) {
    var searchReference by remember { mutableStateOf(prefilledReference ?: "") }
    var selectedTopic by remember { mutableStateOf("Verse Explanation") }
    
    val urielResult by viewModel.urielResult.collectAsState()
    val urielIsGenerating by viewModel.urielIsGenerating.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(prefilledReference) {
        if (!prefilledReference.isNullOrBlank()) {
            viewModel.getUrielScriptureIntelligence(prefilledReference, selectedTopic)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scripture Intelligence", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            // Search Area
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "RESEARCH PASSAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = searchReference,
                        onValueChange = { searchReference = it },
                        placeholder = { Text("e.g. Romans 12:1-2", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().keyboardAware(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.getUrielScriptureIntelligence(searchReference, selectedTopic) },
                                enabled = searchReference.isNotBlank() && !urielIsGenerating
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Research", tint = Color(0xFF10B981))
                            }
                        }
                    )
                }
            }

            // Topics
            val intelOptions = listOf(
                "Verse Explanation", "Chapter Summary", "Historical Context",
                "Cultural Context", "Important Themes", "Original Language Insights",
                "Related Prophecies", "Cross References"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(intelOptions) { opt ->
                    val isSel = selectedTopic == opt
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSel) Color(0xFF10B981) else Color(0xFFECFDF5))
                            .border(1.dp, if (isSel) Color.Transparent else Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                            .clickable {
                                selectedTopic = opt
                                if (searchReference.isNotBlank()) {
                                    viewModel.getUrielScriptureIntelligence(searchReference, opt)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = opt,
                            color = if (isSel) Color.White else Color(0xFF065F46),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Result Area
            if (urielIsGenerating || urielResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    searchReference.uppercase(),
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF065F46)
                                )
                                Text(
                                    selectedTopic.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                            if (urielIsGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        MarkdownLiteText(
                            text = urielResult,
                            bodyColor = Color.DarkGray,
                            accentColor = Color(0xFF059669)
                        )

                        if (urielResult.isNotEmpty() && !urielIsGenerating) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(urielResult)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Research")
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
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFECFDF5))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Enter a passage like Romans 12:1-2 to begin research.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.width(240.dp)
                        )
                    }
                }
            }
        }
    }
}
