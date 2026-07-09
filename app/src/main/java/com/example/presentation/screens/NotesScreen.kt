package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.example.presentation.viewmodel.ShepherdViewModel
import com.example.presentation.viewmodel.DrawingStroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.domain.model.NoteType
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit
) {
    val notesText by viewModel.quickNoteDraft.collectAsState()
    val strokes by viewModel.notebookStrokes.collectAsState()
    val noteType = remember(notesText, strokes) { NoteType.from(notesText, strokes) }
    var exportAsDocx by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var notebookMode by remember { mutableStateOf("type") } // "type" or "stylus"
    var drawingColor by remember { mutableStateOf(0xFF1B2B4B) }
    var drawingWidth by remember { mutableStateOf(6f) }
    var isDrawingEraser by remember { mutableStateOf(false) }
    var isHighlighter by remember { mutableStateOf(false) }
    
    val currentPoints = remember { mutableStateListOf<androidx.compose.ui.geometry.Offset>() }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(notesText))
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(notesText) {
        if (notesText != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = notesText,
                selection = textFieldValue.selection
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pastoral Notes", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        noteTitle = "Pastoral Note ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())}"
                        showSaveDialog = true
                    }) {
                        Icon(Icons.Default.SaveAs, contentDescription = "Save")
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(notesText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = { viewModel.discardQuickNote() }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Clear all", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("type" to Icons.Default.Edit, "stylus" to Icons.Default.Brush).forEach { (mode, icon) ->
                    val isSelected = notebookMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { notebookMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.replaceFirstChar { it.uppercase() },
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(noteType.displayLabel, fontSize = 12.sp) },
                leadingIcon = {
                    val icon = when (noteType) {
                        NoteType.TEXT -> Icons.Default.TextFields
                        NoteType.HANDWRITTEN -> Icons.Default.Brush
                        NoteType.MIXED -> Icons.Default.AutoAwesome
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Stylus Toolbar
                    AnimatedVisibility(visible = notebookMode == "stylus") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val colors = listOf(0xFF1B2B4B, 0xFFDC2626, 0xFF2563EB, 0xFF16A34A, 0xFFF59E0B)
                                colors.forEach { colorVal ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorVal))
                                            .border(
                                                width = if (drawingColor == colorVal && !isDrawingEraser) 2.dp else 0.dp,
                                                color = Color.Black,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                drawingColor = colorVal
                                                isDrawingEraser = false
                                            }
                                    )
                                }
                                
                                VerticalDivider(modifier = Modifier.height(24.dp))
                                
                                // Highlighter Toggle
                                IconButton(
                                    onClick = { isHighlighter = !isHighlighter; isDrawingEraser = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Highlight,
                                        contentDescription = "Highlighter",
                                        tint = if (isHighlighter) Color(0xFFF59E0B) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Eraser
                                IconButton(
                                    onClick = { isDrawingEraser = !isDrawingEraser; isHighlighter = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoFixNormal,
                                        contentDescription = "Eraser",
                                        tint = if (isDrawingEraser) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { viewModel.undoLastStroke() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.clearNotebookStrokes() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val lineSpacing = 30.dp.toPx()
                                val linesCount = (size.height / lineSpacing).toInt()
                                for (i in 1..linesCount) {
                                    val y = i * lineSpacing
                                    drawLine(
                                        color = Color(0xFFF1F5F9),
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                drawLine(
                                    color = Color(0xFFFFD2D2),
                                    start = androidx.compose.ui.geometry.Offset(40.dp.toPx(), 0f),
                                    end = androidx.compose.ui.geometry.Offset(40.dp.toPx(), size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                    ) {
                        if (notebookMode == "type") {
                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    viewModel.updateQuickNoteDraft(it.text)
                                },
                                placeholder = { Text("Write your heart's message...") },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 44.dp, end = 12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    lineHeight = 30.sp,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B2B4B)
                                )
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().padding(start = 44.dp, end = 12.dp, top = 8.dp)) {
                                Text(
                                    text = notesText,
                                    fontSize = 15.sp,
                                    lineHeight = 30.sp,
                                    color = Color(0xFF1B2B4B).copy(alpha = 0.6f)
                                )
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (notebookMode == "stylus") {
                                        Modifier.pointerInput(drawingColor, drawingWidth, isDrawingEraser, isHighlighter) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    currentPoints.clear()
                                                    currentPoints.add(offset)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    currentPoints.add(change.position)
                                                },
                                                onDragEnd = {
                                                    val finalColor: Int = when {
                                                        isDrawingEraser -> 0xFFFFFFFF.toInt()
                                                        isHighlighter -> Color(drawingColor).copy(alpha = 0.3f).toArgb()
                                                        else -> drawingColor.toInt()
                                                    }
                                                    val stroke = DrawingStroke(
                                                        points = currentPoints.toList(),
                                                        color = finalColor,
                                                        strokeWidth = if (isHighlighter) 20f else drawingWidth,
                                                        isEraser = isDrawingEraser
                                                    )
                                                    viewModel.addStroke(stroke)
                                                    currentPoints.clear()
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            strokes.forEach { stroke ->
                                if (stroke.points.size > 1) {
                                    val path = Path().apply {
                                        moveTo(stroke.points[0].x, stroke.points[0].y)
                                        for (i in 1 until stroke.points.size) {
                                            lineTo(stroke.points[i].x, stroke.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(stroke.color),
                                        style = Stroke(
                                            width = stroke.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                            if (currentPoints.size > 1) {
                                val path = Path().apply {
                                    moveTo(currentPoints[0].x, currentPoints[0].y)
                                    for (i in 1 until currentPoints.size) {
                                        lineTo(currentPoints[i].x, currentPoints[i].y)
                                    }
                                }
                                val activeColor = when {
                                    isDrawingEraser -> Color.White
                                    isHighlighter -> Color(drawingColor).copy(alpha = 0.3f)
                                    else -> Color(drawingColor)
                                }
                                drawPath(
                                    path = path,
                                    color = activeColor,
                                    style = Stroke(
                                        width = if (isHighlighter) 20f else drawingWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            title = { Text("Save Pastoral Note", fontFamily = FontFamily.Serif) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Detected: ${noteType.displayLabel}") }
                    )
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Note Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSaving
                    )
                    if (notesText.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !isSaving) { exportAsDocx = !exportAsDocx }
                        ) {
                            Checkbox(checked = exportAsDocx, onCheckedChange = { exportAsDocx = it }, enabled = !isSaving)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Also export text as Word (.docx)", fontSize = 13.sp)
                        }
                    }
                    if (isSaving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        viewModel.savePastoralNote(
                            title = noteTitle,
                            text = notesText,
                            strokes = strokes,
                            noteType = noteType,
                            exportAsDocx = exportAsDocx
                        ) {
                            isSaving = false
                            showSaveDialog = false
                        }
                    },
                    enabled = noteTitle.isNotBlank() && !isSaving
                ) {
                    Text(if (exportAsDocx) "Save & Export .docx" else "Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }, enabled = !isSaving) {
                    Text("Cancel")
                }
            }
        )
    }
}
