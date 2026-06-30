package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ShepherdGold
import com.example.presentation.components.OpenFileOptionsBottomSheet
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.file.FileExtensions.toFileColor
import com.example.data.file.FileExtensions.toFileIcon
import com.example.data.file.FileExtensions.toReadableSize
import com.example.domain.model.Category
import com.example.domain.model.ShepherdFile
import com.example.presentation.components.bounceClickable
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.components.shimmerBrush
import com.example.presentation.viewmodel.ShepherdViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigateToSermonViewer: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val isSyncing by viewModel.isSyncing.collectAsState()
    val files by viewModel.filteredFiles.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val isMultiSelectActive by viewModel.isMultiSelectActive.collectAsState()

    val selectedCategoryFilter by viewModel.selectedCategoryIdForFilter.collectAsState()
    val selectedExtensionFilter by viewModel.selectedExtensionForFilter.collectAsState()

    var showMoveCategoryDialog by remember { mutableStateOf(false) }
    var activeOptionsFile by remember { mutableStateOf<ShepherdFile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.syncFiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isMultiSelectActive) "${selectedFileIds.size} Selected" else "Study Library",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isMultiSelectActive) {
                            viewModel.clearSelection()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (isMultiSelectActive) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isMultiSelectActive) {
                        IconButton(onClick = { viewModel.syncFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Files")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isMultiSelectActive) {
                // Slide-up MultiSelect menu bar
                AnimatedVisibility(
                    visible = isMultiSelectActive,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Move to category
                            Button(
                                onClick = { showMoveCategoryDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Move folder")
                            }

                            // Delete
                            Button(
                                onClick = { viewModel.deleteSelectedFiles() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            } else {
                MinistryBottomBar(
                    currentRoute = "file_list",
                    onNavigate = onNavigate
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Horizontal Category Select Filter
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ALL option
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { viewModel.selectCategoryFilter(null) },
                        label = { Text("All Books") }
                    )
                }

                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat.id,
                        onClick = { viewModel.selectCategoryFilter(cat.id) },
                        label = { Text("${cat.iconEmoji} ${cat.name}") }
                    )
                }
            }

            // 2. Horizontal Extension Select Filter
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val extensions = listOf("PDF", "DOCX", "PPTX", "TXT")
                items(extensions) { ext ->
                    FilterChip(
                        selected = selectedExtensionFilter?.lowercase() == ext.lowercase(),
                        onClick = {
                            if (selectedExtensionFilter?.lowercase() == ext.lowercase()) {
                                viewModel.selectExtensionFilter(null)
                            } else {
                                viewModel.selectExtensionFilter(ext)
                            }
                        },
                        label = { Text(ext) }
                    )
                }
            }

            // 3. Main File lazy list
            if (isSyncing && files.isEmpty()) {
                // Beautiful Skeleton listing loads
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(6) {
                        SkeletonFileRow()
                    }
                }
            } else if (files.isEmpty()) {
                // Empty Library State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Your Library is Silent",
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Add outlines via the AI Editor, create new Category folders, or refresh this view to scan connected parent storage.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(files, key = { it.id }) { file ->
                        val isSelected = selectedFileIds.contains(file.id)
                        FileListItem(
                            file = file,
                            isSelected = isSelected,
                            isMultiSelectActive = isMultiSelectActive,
                            onClick = {
                                if (isMultiSelectActive) {
                                    viewModel.toggleFileSelection(file.id)
                                } else {
                                    activeOptionsFile = file
                                }
                            },
                            onLongClick = {
                                viewModel.toggleFileSelection(file.id)
                            },
                            onToggleBookmark = {
                                viewModel.toggleBookmark(file, "Sunday Sermon Reference")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showMoveCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showMoveCategoryDialog = false },
            title = { Text("Move files to category", fontFamily = FontFamily.Serif) },
            text = {
                Column {
                    Text("Select target category:")
                    Spacer(modifier = Modifier.height(12.dp))
                    categories.forEach { cat ->
                        TextButton(
                            onClick = {
                                viewModel.moveSelectedFilesToCategory(cat)
                                showMoveCategoryDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${cat.iconEmoji} ${cat.name}", textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                        uri = android.net.Uri.parse(file.uriString),
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
                    title = "Preach Mode Activated",
                    message = "Your file \"${file.name}\" has been optimized. Welcome to Preach Mode!"
                )
                onNavigate("ai_preach_mode")
            }
        )
    }
}

// --- List Items / Skeletons ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: ShepherdFile,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }
    val dateString = formatter.format(Date(file.lastModified))

    val goldColor = ShepherdGold // Color(0xFFC9A84C)
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) goldColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 180),
        label = "bgColor"
    )

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "borderWidth"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 1.dp,
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(elevation, RoundedCornerShape(14.dp))
            .border(
                width = animatedBorderWidth,
                color = if (isSelected) goldColor else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge Indicator
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(file.extension.toFileColor().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (isMultiSelectActive && isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        file.extension.toFileIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = file.extension.toFileColor()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = file.extension.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = file.extension.toFileColor(),
                        modifier = Modifier
                            .background(file.extension.toFileColor().copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = file.sizeBytes.toReadableSize(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }

            // Bookmark icon
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (file.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (file.isFavorite) MaterialTheme.colorScheme.secondary else Color.LightGray
                )
            }
        }
    }
}

@Composable
fun SkeletonFileRow() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(shimmerBrush())
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}
