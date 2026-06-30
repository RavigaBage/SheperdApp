package com.example.presentation.screens

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.file.FileExtensions.toReadableSize
import com.example.domain.model.Category
import com.example.domain.model.ShepherdFile
import com.example.presentation.components.OpenFileOptionsBottomSheet
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.components.bounceClickable
import com.example.presentation.viewmodel.ShepherdViewModel
import com.example.ui.theme.ShepherdGold
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigateToSermonViewer: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val files by viewModel.files.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var activeOptionsFile by remember { mutableStateOf<ShepherdFile?>(null) }
    var showCategorizeDialog by remember { mutableStateOf<ShepherdFile?>(null) }
    var showCreateCategorySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Study Library",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = ShepherdGold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go Back", tint = ShepherdGold)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateCategorySheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Category", tint = ShepherdGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            MinistryBottomBar(
                currentRoute = "library",
                onNavigate = onNavigate
            )
        },
        containerColor = Color(0xFFFAFAFA)
    ) { paddingValues ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = ShepherdGold.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Library is Empty",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Connect your sermon folder or create categories on the Home Screen to populate your study cabin.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Section for each category
                categories.forEach { category ->
                    val categoryFiles = files.filter { it.categoryId == category.id }
                    if (categoryFiles.isNotEmpty()) {
                        item {
                            CategoryLibrarySection(
                                category = category,
                                files = categoryFiles,
                                onFileClick = { activeOptionsFile = it }
                            )
                        }
                    }
                }

                // Uncategorized section
                val uncategorizedFiles = files.filter { it.categoryId == null || categories.none { cat -> cat.id == it.categoryId } }
                if (uncategorizedFiles.isNotEmpty()) {
                    item {
                        CategoryLibrarySection(
                            category = Category(
                                id = "uncategorized",
                                name = "General Study",
                                colorHex = "#7F8C8D",
                                iconEmoji = "📂",
                                parentFolderId = null,
                                createdAt = 0L
                            ),
                            files = uncategorizedFiles,
                            onFileClick = { activeOptionsFile = it }
                        )
                    }
                }
            }
        }
    }

    activeOptionsFile?.let { file ->
        OpenFileOptionsBottomSheet(
            file = file,
            onDismiss = { activeOptionsFile = null },
            onOpenDefault = {
                viewModel.incrementFileUsage(file.id)
                if (listOf("txt", "docx", "pdf").contains(file.extension.lowercase())) {
                    viewModel.activeViewerSermonId = file.id
                    viewModel.activeViewerFilePath = file.uriString
                    viewModel.activeViewerTitle = file.name
                    onNavigateToSermonViewer()
                } else {
                    val context = viewModel.getApplication<com.example.ShepherdApplication>()
                    viewModel.getApplication<com.example.ShepherdApplication>().safFileManager.openFileWithNativeApp(
                        uri = Uri.parse(file.uriString),
                        extension = file.extension,
                        customContext = context
                    )
                }
            },
            onOpenPreachMode = {
                viewModel.incrementFileUsage(file.id)
                viewModel.activeViewerSermonId = file.id
                viewModel.activeViewerFilePath = file.uriString
                viewModel.activeViewerTitle = file.name
                viewModel.addNotification(
                    title = "Preach Mode Loaded",
                    message = "Your file \"${file.name}\" was successfully optimized. Go deliver a wonderful sermon!"
                )
                onNavigate("ai_preach_mode")
            },
            extraContent = {
                Surface(
                    onClick = { 
                        showCategorizeDialog = file
                        activeOptionsFile = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Gray.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Categorize File",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Move to a study folder for organization",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        )
    }

    showCategorizeDialog?.let { file ->
        CategorizeFileDialog(
            categories = categories,
            currentCategoryId = file.categoryId,
            onDismiss = { showCategorizeDialog = null },
            onSelectCategory = { categoryId ->
                viewModel.updateFileCategory(file.id, categoryId)
                showCategorizeDialog = null
            }
        )
    }

    if (showCreateCategorySheet) {
        LibraryCreateCategoryBottomSheet(
            onDismiss = { showCreateCategorySheet = false },
            onCreate = { name: String, emoji: String, colorHex: String ->
                viewModel.createCategory(name, colorHex, emoji)
                showCreateCategorySheet = false
            }
        )
    }
}

@Composable
fun CategorizeFileDialog(
    categories: List<Category>,
    currentCategoryId: String?,
    onDismiss: () -> Unit,
    onSelectCategory: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Category", fontFamily = FontFamily.Serif) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CategoryItem(
                        name = "None (General Study)",
                        icon = "📂",
                        isSelected = currentCategoryId == null,
                        onClick = { onSelectCategory(null) }
                    )
                }
                items(categories) { category ->
                    CategoryItem(
                        name = category.name,
                        icon = category.iconEmoji,
                        isSelected = currentCategoryId == category.id,
                        onClick = { onSelectCategory(category.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CategoryItem(
    name: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) ShepherdGold.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) ShepherdGold else Color.Black
            )
            if (isSelected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.Check, contentDescription = null, tint = ShepherdGold)
            }
        }
    }
}

// Re-using CreateCategoryBottomSheet from HomeScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCreateCategoryBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("📖") }
    var selectedColorHex by remember { mutableStateOf("#1B2B4B") }

    val emojis = listOf("📖", "✉️", "📊", "⛪", "✝️", "📚", "🕊️", "🏡")
    val colors = listOf("#1B2B4B", "#C9A84C", "#2D6A4F", "#C0392B", "#6C5CE7", "#00CEC9", "#E17055", "#636E72")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                "Create Study Folder",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = Color(0xFF1B2B4B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Select Visual Symbol", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                emojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (selectedEmoji == emoji) Color(0xFF1B2B4B).copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedEmoji = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Folder Theme Presets", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { colorStr ->
                    val color = Color(android.graphics.Color.parseColor(colorStr))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColorHex = colorStr },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColorHex == colorStr) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, selectedEmoji, selectedColorHex)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
            ) {
                Text("Create Dynamic Folder", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CategoryLibrarySection(
    category: Category,
    files: List<ShepherdFile>,
    onFileClick: (ShepherdFile) -> Unit
) {
    val categoryColor = try {
        Color(android.graphics.Color.parseColor(category.colorHex))
    } catch (e: Exception) {
        ShepherdGold
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.iconEmoji, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = category.name,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${files.size} outline files",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal media listing
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
        ) {
            items(files, key = { it.id }) { file ->
                MediaFileCard(
                    file = file,
                    categoryColor = categoryColor,
                    onClick = { onFileClick(file) }
                )
            }
        }
    }
}

@Composable
fun MediaFileCard(
    file: ShepherdFile,
    categoryColor: Color,
    onClick: () -> Unit
) {
    val initials = remember(file.name) {
        val cleanName = file.name.replace(Regex("[._-]"), " ").trim()
        val parts = cleanName.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) "FD"
        else if (parts.size == 1) {
            parts[0].take(2).uppercase()
        } else {
            (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateString = dateFormatter.format(Date(file.lastModified))

    Card(
        modifier = Modifier
            .width(136.dp)
            .height(184.dp)
            .bounceClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F0))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Light tinted visual glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                categoryColor.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Centered Avatar Initials
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = categoryColor,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Top-right Usage Count Badge (Gold)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShepherdGold.copy(alpha = 0.15f))
                    .border(0.5.dp, ShepherdGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = "Opened Count",
                        tint = ShepherdGold,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = file.usageCount.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShepherdGold
                    )
                }
            }

            // Bottom Anchored File details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(0.35f)
                    .background(Color(0xFFFCFCFC))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = file.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.extension.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor
                        )
                        Text(
                            text = file.sizeBytes.toReadableSize(),
                            fontSize = 8.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
