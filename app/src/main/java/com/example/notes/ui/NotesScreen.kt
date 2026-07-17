package com.example.notes.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ShepherdApplication
import com.example.notes.domain.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    pageId: String,
    onBack: () -> Unit,
    notebookId: String,
    onNavigateToPreach: (Int) -> Unit,
    onNavigateToLibrary: () -> Unit,
    pendingInsertText: String? = null,
    onClearPendingInsertText: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as ShepherdApplication
    val viewModel: NotesViewModel = viewModel(
        factory = NotesViewModel.Factory(app, app.notesRepository, pageId, notebookId)
    )

    LaunchedEffect(pendingInsertText) {
        if (!pendingInsertText.isNullOrBlank()) {
            viewModel.insertTextFromLibrary(pendingInsertText)
            onClearPendingInsertText()
        }
    }

    val canvasObjects by viewModel.canvasObjects.collectAsState()
    val canvasState by viewModel.canvasState.collectAsState()
    val backgroundStyle by viewModel.backgroundStyle.collectAsState()
    val backgroundColorHex by viewModel.backgroundColor.collectAsState()
    val backgroundColor = remember(backgroundColorHex) { Color(backgroundColorHex.toColorInt()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val nextZ = (canvasObjects.maxOfOrNull { it.zIndex } ?: -1) + 1
            viewModel.addCanvasObject(CanvasObject.ImageObject(
                id = UUID.randomUUID().toString(),
                zIndex = nextZ,
                x = 100f,
                y = 100f,
                width = 300f,
                height = 300f,
                uri = it.toString()
            ))
        }
    }

    BackHandler {
        viewModel.savePage { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notebook Canvas") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.savePage { onBack() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.savePage() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    IconButton(onClick = { 
                        // Placeholder for PDF Export
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = onNavigateToLibrary) {
                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = "Library")
                    }
                    IconButton(onClick = { onNavigateToPreach(30) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preach")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp).navigationBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ToolIconButton(
                            selected = canvasState.activeMode == CanvasMode.PEN,
                            onClick = { viewModel.setCanvasMode(CanvasMode.PEN) },
                            icon = Icons.Default.Edit,
                            label = "Pen"
                        )
                        ToolIconButton(
                            selected = canvasState.activeMode == CanvasMode.TYPE,
                            onClick = { viewModel.setCanvasMode(CanvasMode.TYPE) },
                            icon = Icons.Default.TextFields,
                            label = "Type"
                        )
                        ToolIconButton(
                            selected = canvasState.activeMode == CanvasMode.ERASE,
                            onClick = { viewModel.setCanvasMode(CanvasMode.ERASE) },
                            icon = Icons.Default.AutoFixNormal,
                            label = "Eraser"
                        )
                        ToolIconButton(
                            selected = canvasState.activeMode == CanvasMode.SELECT,
                            onClick = { viewModel.setCanvasMode(CanvasMode.SELECT) },
                            icon = Icons.Default.NearMe,
                            label = "Select"
                        )
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Image")
                        }
                    }
                    
                    if (canvasState.activeMode == CanvasMode.PEN) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("#000000", "#FF0000", "#0000FF", "#008000").forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.toColorInt()))
                                        .clickable { viewModel.setBrushColor(color) }
                                        .then(if (canvasState.brushColor == color) Modifier.border(2.dp, Color.Gray, CircleShape) else Modifier)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Slider(
                                value = canvasState.brushSize,
                                onValueChange = { viewModel.setBrushSize(it) },
                                valueRange = 1f..50f,
                                modifier = Modifier.width(150.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(backgroundColor)) {
            InkCanvas(
                objects = canvasObjects,
                onStrokeFinished = { points, color, size ->
                    val nextZ = (canvasObjects.maxOfOrNull { it.zIndex } ?: -1) + 1
                    viewModel.addCanvasObject(CanvasObject.StrokeObject(
                        id = UUID.randomUUID().toString(),
                        zIndex = nextZ,
                        points = points,
                        colorHex = color,
                        brushWidth = size
                    ))
                },
                onObjectRemoved = { viewModel.removeCanvasObject(it) },
                onObjectUpdated = { viewModel.updateCanvasObject(it) },
                onEmptySpaceTapped = { x, y ->
                    viewModel.insertTextAt(x, y)
                },
                brushColor = canvasState.brushColor,
                brushSize = canvasState.brushSize,
                backgroundStyle = backgroundStyle,
                canvasMode = canvasState.activeMode,
                penType = canvasState.penType,
                focusedTextId = canvasState.focusedTextId,
                onFocusedTextChange = { viewModel.setFocusedText(it) },
                selectedObjectId = canvasState.selectedObjectId,
                onSelectedObjectChange = { viewModel.setSelectedObject(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ToolIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = label, fontSize = 10.sp)
    }
}
