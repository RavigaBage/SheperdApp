package com.example.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.notes.domain.Notebook
import com.example.notes.domain.Page
import java.io.File

import androidx.compose.material.icons.filled.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookListScreen(
    onBack: () -> Unit,
    onNavigateToPage: (pageId: String, notebookId: String) -> Unit,
    onNavigateToPreach: (String, String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.ShepherdApplication
    val viewModel: NotebookListViewModel = viewModel(
        factory = NotebookListViewModel.Factory(app, app.notesRepository)
    )

    val notebooks by viewModel.notebooks.collectAsState()
    val selectedNotebook by viewModel.selectedNotebook.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPageIds by remember { mutableStateOf(setOf<String>()) }

    val currentNotebook = selectedNotebook
    val pages by if (currentNotebook != null) {
        viewModel.observePages(currentNotebook.id).collectAsState(emptyList())
    } else {
        remember { mutableStateOf(emptyList<Page>()) }
    }
    val sortedPages = remember(pages) { pages.sortedBy { it.pageIndex } }

    var showStyleDialog by remember { mutableStateOf<com.example.notes.domain.Notebook?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedPageIds.size} Selected")
                    } else {
                        Text(selectedNotebook?.title ?: "Notebooks")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedPageIds = emptySet()
                        } else if (selectedNotebook != null) {
                            viewModel.selectNotebook(null)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack, 
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedPageIds.size == 1 && currentNotebook != null) {
                            val selectedId = selectedPageIds.first()
                            val page = sortedPages.find { it.id == selectedId }
                            val index = sortedPages.indexOf(page)
                            
                            IconButton(
                                onClick = { 
                                    viewModel.reorderPages(currentNotebook.id, index, index - 1)
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Move Back")
                            }
                            IconButton(
                                onClick = { 
                                    viewModel.reorderPages(currentNotebook.id, index, index + 1)
                                },
                                enabled = index < sortedPages.size - 1
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Move Forward")
                            }
                        }
                        IconButton(onClick = {
                            selectedPageIds.forEach { viewModel.deletePage(it) }
                            isSelectionMode = false
                            selectedPageIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Pages")
                        }
                    } else {
                        selectedNotebook?.let { notebook ->
                            IconButton(onClick = { showStyleDialog = notebook }) {
                                Icon(Icons.Default.Style, contentDescription = "Change Background Style")
                            }
                            IconButton(onClick = { onNavigateToPreach(notebook.id, notebook.title) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preach Notebook")
                            }
                            IconButton(onClick = { viewModel.exportNotebookAsPdf(context, notebook.id, notebook.title) }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export as PDF")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = {
                    val activeNotebook = selectedNotebook
                    if (activeNotebook != null) {
                        viewModel.createPage(activeNotebook.id) { newPageId ->
                            onNavigateToPage(newPageId, activeNotebook.id) // CHANGED
                        }
                    } else {
                        showCreateDialog = true
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Create")
                }
            }
        }
    ) { paddingValues ->
        if (currentNotebook == null) {
            NotebookGrid(
                notebooks = notebooks,
                onNotebookClick = { viewModel.selectNotebook(it) },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            PageGrid(
                pages = sortedPages,
                isSelectionMode = isSelectionMode,
                selectedPageIds = selectedPageIds,
                onPageClick = { page ->
                    if (isSelectionMode) {
                        selectedPageIds = if (selectedPageIds.contains(page.id)) {
                            selectedPageIds - page.id
                        } else {
                            selectedPageIds + page.id
                        }
                        if (selectedPageIds.isEmpty()) isSelectionMode = false
                    } else {
                        onNavigateToPage(page.id, currentNotebook.id)
                    }
                },
                onPageLongClick = { page ->
                    isSelectionMode = true
                    selectedPageIds = setOf(page.id)
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    if (showStyleDialog != null) {
        val notebook = showStyleDialog!!
        var selectedStyle by remember { mutableStateOf(notebook.backgroundStyle) }
        AlertDialog(
            onDismissRequest = { showStyleDialog = null },
            title = { Text("Notebook Style: ${notebook.title}") },
            text = {
                Column {
                    Text("Background Style", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedStyle == com.example.notes.domain.PageBackgroundStyle.LINED,
                            onClick = { selectedStyle = com.example.notes.domain.PageBackgroundStyle.LINED },
                            label = { Text("Lined") }
                        )
                        FilterChip(
                            selected = selectedStyle == com.example.notes.domain.PageBackgroundStyle.GRID,
                            onClick = { selectedStyle = com.example.notes.domain.PageBackgroundStyle.GRID },
                            label = { Text("Grid") }
                        )
                        FilterChip(
                            selected = selectedStyle == com.example.notes.domain.PageBackgroundStyle.PLAIN,
                            onClick = { selectedStyle = com.example.notes.domain.PageBackgroundStyle.PLAIN },
                            label = { Text("Plain") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNotebookStyle(notebook.id, selectedStyle)
                        showStyleDialog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStyleDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var selectedStyle by remember { mutableStateOf(com.example.notes.domain.PageBackgroundStyle.LINED) }
        val colors = listOf("#FFFFFF", "#F8BBD0", "#E1BEE7", "#D1C4E9", "#C5CAE9", "#BBDEFB", "#C8E6C9", "#FFF9C4")
        var selectedColor by remember { mutableStateOf(colors[0]) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Notebook") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Background Style", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedStyle == com.example.notes.domain.PageBackgroundStyle.LINED,
                            onClick = { selectedStyle = com.example.notes.domain.PageBackgroundStyle.LINED },
                            label = { Text("Lined") }
                        )
                        FilterChip(
                            selected = selectedStyle == com.example.notes.domain.PageBackgroundStyle.GRID,
                            onClick = { selectedStyle = com.example.notes.domain.PageBackgroundStyle.GRID },
                            label = { Text("Grid") }
                        )
                        FilterChip(
                            selected = selectedStyle == com.example.notes.domain.PageBackgroundStyle.PLAIN,
                            onClick = { selectedStyle = com.example.notes.domain.PageBackgroundStyle.PLAIN },
                            label = { Text("Plain") }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Notebook Color", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colors.forEach { colorHex ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .border(
                                        width = if (selectedColor == colorHex) 2.dp else 1.dp,
                                        color = if (selectedColor == colorHex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.createNotebook(title, selectedStyle, selectedColor)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NotebookGrid(
    notebooks: List<Notebook>,
    onNotebookClick: (Notebook) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(notebooks) { notebook ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clickable { onNotebookClick(notebook) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = notebook.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PageGrid(
    pages: List<Page>,
    isSelectionMode: Boolean,
    selectedPageIds: Set<String>,
    onPageClick: (Page) -> Unit,
    onPageLongClick: (Page) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(pages, key = { it.id }) { page ->
            val isSelected = selectedPageIds.contains(page.id)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onPageClick(page) },
                        onLongClick = { onPageLongClick(page) }
                    )
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (page.thumbnailPath != null) {
                        AsyncImage(
                            model = File(page.thumbnailPath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Empty Page", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "Page ${pages.indexOf(page) + 1}",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}
