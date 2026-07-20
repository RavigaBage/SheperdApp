package com.example.notes.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notes.domain.SermonTemplate
import com.example.domain.model.Category
import com.example.ShepherdApplication
import com.example.notes.data.SermonIdeaGenerationRepositoryImpl
import com.example.notes.domain.GeneratedIdea
import com.example.notes.domain.IdeaType
import com.example.presentation.components.keyboardAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonIdeaBrowseScreen(
    onBack: () -> Unit,
    onNavigateToPage: (String, String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ShepherdApplication
    
    // Wire up the new repository implementation
    val generationRepo = remember { SermonIdeaGenerationRepositoryImpl(app.repository.geminiService) }
    
    val viewModel: SermonIdeaViewModel = viewModel(
        factory = SermonIdeaViewModel.Factory(app, app.notesRepository, app.repository, generationRepo)
    )

    val templates by viewModel.templates.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val generatedIdeas by viewModel.generatedIdeas.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val generationError by viewModel.generationError.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<SermonTemplate?>(null) }
    var selectedGeneratedIdea by remember { mutableStateOf<GeneratedIdea?>(null) }
    
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sermon Idea Co-Creation", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
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
            // Dual-Purpose Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .keyboardAware(),
                placeholder = { Text("Search library or type topic for AI...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { 
                            viewModel.generateIdeas(searchQuery)
                            focusManager.clearFocus()
                        },
                        enabled = searchQuery.isNotBlank() && !isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generate with AI",
                            tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.generateIdeas(searchQuery)
                    focusManager.clearFocus()
                }),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // AI Generated Ideas Section
                if (isGenerating || generatedIdeas.isNotEmpty() || generationError != null) {
                    item {
                        AIIdeasSection(
                            ideas = generatedIdeas,
                            isGenerating = isGenerating,
                            error = generationError,
                            onIdeaClick = { selectedGeneratedIdea = it }
                        )
                        Divider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)
                    }
                }

                // Categorized Library
                if (templates.isEmpty() && !isGenerating && generatedIdeas.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No sermon ideas yet. Add one or ask Uriel.", color = Color.Gray)
                        }
                    }
                } else {
                    item {
                        Text(
                            "Personal Library",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
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
    
    selectedGeneratedIdea?.let { idea ->
        GeneratedIdeaDetailDialog(
            idea = idea,
            onDismiss = { selectedGeneratedIdea = null },
            onSave = {
                viewModel.saveGeneratedIdea(idea)
                selectedGeneratedIdea = null
            },
            onStart = {
                viewModel.startSermonFromGenerated(idea) { pageId, notebookId ->
                    onNavigateToPage(pageId, notebookId)
                }
                selectedGeneratedIdea = null
            }
        )
    }
}

@Composable
fun AIIdeasSection(
    ideas: List<GeneratedIdea>,
    isGenerating: Boolean,
    error: String?,
    onIdeaClick: (GeneratedIdea) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Uriel AI Suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2B4B)
            )
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isGenerating && ideas.isEmpty()) {
                items(3) { SkeletonIdeaCard() }
            } else {
                items(ideas) { idea ->
                    GeneratedIdeaCard(idea = idea, onClick = { onIdeaClick(idea) })
                }
            }
        }
    }
}

@Composable
fun GeneratedIdeaCard(idea: GeneratedIdea, onClick: () -> Unit) {
    val (icon, color) = when (idea.type) {
        IdeaType.SERMON -> Icons.Default.MenuBook to Color(0xFF2563EB)
        IdeaType.TEACHING_SERIES -> Icons.Default.Layers to Color(0xFF7C3AED)
        IdeaType.CONFERENCE -> Icons.Default.Groups to Color(0xFFEA580C)
        IdeaType.SEMINAR -> Icons.Default.School to Color(0xFF0D9488)
    }

    Card(
        modifier = Modifier
            .width(220.dp)
            .height(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = idea.type.name.replace("_", " "),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = Color(0xFF8B5CF6).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("AI", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 8.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = idea.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = idea.summary,
                fontSize = 11.sp,
                color = Color.DarkGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun SkeletonIdeaCard() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "skeleton"
    )

    Card(
        modifier = Modifier.width(220.dp).height(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = alpha))
    ) {
        Box(modifier = Modifier.fillMaxSize())
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
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth().keyboardAware())
                
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

                OutlinedTextField(value = scripture, onValueChange = { scripture = it }, label = { Text("Scripture Reference") }, modifier = Modifier.fillMaxWidth().keyboardAware())
                OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Summary / Angle") }, modifier = Modifier.fillMaxWidth().keyboardAware(), minLines = 2)
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth().keyboardAware())
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
fun GeneratedIdeaDetailDialog(
    idea: GeneratedIdea,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit
) {
    val color = when (idea.type) {
        IdeaType.SERMON -> Color(0xFF2563EB)
        IdeaType.TEACHING_SERIES -> Color(0xFF7C3AED)
        IdeaType.CONFERENCE -> Color(0xFFEA580C)
        IdeaType.SEMINAR -> Color(0xFF0D9488)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = idea.type.name.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = Color(0xFF8B5CF6).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text("AI SUGGESTED", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 8.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Black)
                    }
                }
                Text(idea.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!idea.scriptureReference.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(idea.scriptureReference, fontWeight = FontWeight.Bold, color = color)
                    }
                }
                
                Text(idea.summary, style = MaterialTheme.typography.bodyLarge)
                
                if (idea.outline.isNotEmpty()) {
                    Text("Potential Outline:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    idea.outline.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text(point, fontSize = 13.sp)
                        }
                    }
                }
                
                if (idea.suggestedTags.isNotEmpty()) {
                    FlowRow(mainAxisSpacing = 8.dp, crossAxisSpacing = 4.dp) {
                        idea.suggestedTags.forEach { tag ->
                            SuggestionChip(onClick = {}, label = { Text(tag) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save to Library")
                }
                Button(onClick = onStart) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Now")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
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
