package com.example.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notes.domain.SermonTemplate
import com.example.domain.model.Category
import com.example.ShepherdApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonIdeaBrowseScreen(
    onBack: () -> Unit,
    onNavigateToPage: (String, String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ShepherdApplication
    val viewModel: SermonIdeaViewModel = viewModel(
        factory = SermonIdeaViewModel.Factory(app, app.notesRepository, app.repository)
    )

    val templates by viewModel.templates.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<SermonTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sermon Ideas", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Idea")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search ideas, scriptures, tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (templates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sermon ideas yet. Add one to begin.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Grouped by Category
                    categories.forEach { category ->
                        val categoryTemplates = templates.filter { it.categoryId == category.id }
                        if (categoryTemplates.isNotEmpty()) {
                            item(key = category.id) {
                                IdeaRow(
                                    category = category,
                                    templates = categoryTemplates,
                                    onTemplateClick = { selectedTemplate = it }
                                )
                            }
                        }
                    }

                    // Uncategorized
                    val uncategorized = templates.filter { it.categoryId == null }
                    if (uncategorized.isNotEmpty()) {
                        item(key = "uncategorized") {
                            IdeaRow(
                                category = Category("uncategorized", "General Ideas", "#7F8C8D", "💡", null, 0),
                                templates = uncategorized,
                                onTemplateClick = { selectedTemplate = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSermonIdeaDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, catId, scripture, summary, tags ->
                viewModel.addTemplate(title, catId, scripture, summary, tags)
                showAddDialog = false
            }
        )
    }

    selectedTemplate?.let { template ->
        IdeaDetailDialog(
            template = template,
            category = categories.find { it.id == template.categoryId },
            onDismiss = { selectedTemplate = null },
            onStartSermon = {
                viewModel.startSermon(template) { pageId, notebookId ->
                    onNavigateToPage(pageId, notebookId)
                }
                selectedTemplate = null
            }
        )
    }
}

@Composable
fun IdeaRow(
    category: Category,
    templates: List<SermonTemplate>,
    onTemplateClick: (SermonTemplate) -> Unit
) {
    val categoryColor = try { Color(android.graphics.Color.parseColor(category.colorHex)) } catch (e: Exception) { Color.Gray }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.iconEmoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = categoryColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(templates) { template ->
                IdeaCard(
                    template = template,
                    categoryColor = categoryColor,
                    onClick = { onTemplateClick(template) }
                )
            }
        }
    }
}

@Composable
fun IdeaCard(
    template: SermonTemplate,
    categoryColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = template.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            
            if (!template.scriptureReference.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = categoryColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = template.scriptureReference,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = template.summary,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSermonIdeaDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var scripture by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Sermon Idea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                
                Text("Category", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { selectedCategoryId = if (selectedCategoryId == category.id) null else category.id },
                            label = { Text(category.name) }
                        )
                    }
                }

                OutlinedTextField(value = scripture, onValueChange = { scripture = it }, label = { Text("Scripture Reference") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Summary / Angle") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, selectedCategoryId, scripture, summary, tags) },
                enabled = title.isNotBlank() && summary.isNotBlank()
            ) {
                Text("Add to Library")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IdeaDetailDialog(
    template: SermonTemplate,
    category: Category?,
    onDismiss: () -> Unit,
    onStartSermon: () -> Unit
) {
    val categoryColor = try { Color(android.graphics.Color.parseColor(category?.colorHex ?: "#7F8C8D")) } catch (e: Exception) { Color.Gray }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                if (category != null) {
                    Text(
                        text = category.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(template.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!template.scriptureReference.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = categoryColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(template.scriptureReference, fontWeight = FontWeight.Bold, color = categoryColor)
                    }
                }
                
                Text(template.summary, style = MaterialTheme.typography.bodyLarge)
                
                if (!template.tags.isNullOrBlank()) {
                    FlowRow(mainAxisSpacing = 8.dp, crossAxisSpacing = 4.dp) {
                        template.tags.split(",").forEach { tag ->
                            SuggestionChip(onClick = {}, label = { Text(tag.trim()) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onStartSermon) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start this Sermon")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val spacing = mainAxisSpacing.roundToPx()
        val crossSpacing = crossAxisSpacing.roundToPx()
        
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        placeholders.forEach { placeable ->
            if (currentRowWidth + placeable.width + spacing > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + spacing
        }
        rows.add(currentRow)
        
        val totalHeight = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * crossSpacing
        
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + spacing
                }
                y += rowHeight + crossSpacing
            }
        }
    }
}
