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

    var showCreateCategorySheet by remember { mutableStateOf(false) }
    var activeOptionsFile by remember { mutableStateOf<ShepherdFile?>(null) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = { },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateCategorySheet = true },
                containerColor = Color(0xFF1B2B4B),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New category folder",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = Color(0xFFFAFAFA)
    ) { paddingValues ->
        val isSyncing by viewModel.isSyncing.collectAsState()

        if (isSyncing && files.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(6) {
                    SkeletonFileRow()
                }
            }
        } else if (files.isEmpty()) {
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
                                onFileClick = { file ->
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
                                }
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
                            onFileClick = { file ->
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
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateCategorySheet) {
        CreateCategoryBottomSheet(
            onDismiss = { showCreateCategorySheet = false },
            onCreate = { name, emoji, colorHex ->
                viewModel.createCategory(name, colorHex, emoji)
                showCreateCategorySheet = false
            }
        )
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
