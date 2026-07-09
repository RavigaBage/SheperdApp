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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign

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
    val trashedCount by viewModel.trashedFiles.collectAsState()

    val selectedCategoryFilter by viewModel.selectedCategoryIdForFilter.collectAsState()
    val selectedExtensionFilter by viewModel.selectedExtensionForFilter.collectAsState()
    var showCreateCategorySheet by remember { mutableStateOf(false) }
    var showCreateFileSheet by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    // Playback Simulation State (Suggested Mini Player)
    var isSimulatingPlayback by remember { mutableStateOf(false) }
    var simulatedPlaybackPosition by remember { mutableStateOf(0.35f) }
    var showMoveCategoryDialog by remember { mutableStateOf(false) }
    var activeOptionsFile by remember { mutableStateOf<ShepherdFile?>(null) }
    var fileToRename by remember { mutableStateOf<ShepherdFile?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }

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
                        IconButton(onClick = { onNavigate("trash") }) {
                            BadgedBox(
                                badge = {
                                    if (trashedCount.isNotEmpty()) {
                                        Badge { Text(trashedCount.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Trash")
                            }
                        }
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

                            // Delete (soft-delete -> Trash)
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
                // Bottom bar removed
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
                                }
                            },
                            onLongClick = {
                                viewModel.toggleFileSelection(file.id)
                            },
                            onToggleBookmark = {
                                viewModel.toggleBookmark(file, "Sunday Sermon Reference")
                            },
                            onRenameRequest = {
                                renameError = null
                                fileToRename = file
                            },
                            onDeleteRequest = {
                                viewModel.toggleFileSelection(file.id)
                                viewModel.deleteSelectedFiles()
                            }
                        )
                    }
                }
            }
        }
    }

    // FAB — tap opens a small menu: New Folder (existing Category flow) / New File
    Box {
        FloatingActionButton(
            onClick = { showFabMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(56.dp),
            containerColor = Color(0xFF1B2B4B),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Create new",
                modifier = Modifier.size(24.dp)
            )
        }
        DropdownMenu(
            expanded = showFabMenu,
            onDismissRequest = { showFabMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("New Folder") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                onClick = {
                    showFabMenu = false
                    showCreateCategorySheet = true
                }
            )
            DropdownMenuItem(
                text = { Text("New File") },
                leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                onClick = {
                    showFabMenu = false
                    showCreateFileSheet = true
                }
            )
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

    if (showCreateFileSheet) {
        CreateFileBottomSheet(
            categories = categories,
            onDismiss = { showCreateFileSheet = false },
            onCreate = { name, extension, categoryId ->
                viewModel.createFile(name, extension, categoryId)
                showCreateFileSheet = false
            }
        )
    }

    if (showMoveCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showMoveCategoryDialog = false },
            title = { Text("Move files to category", fontFamily = FontFamily.Serif) },
            text = {
                Column {
                    Text("Select target category:")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        items(categories) { cat ->
                            CategoryChip(
                                category = cat,
                                onClick = {
                                    viewModel.moveSelectedFilesToCategory(cat)
                                    showMoveCategoryDialog = false
                                }
                            )
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

    fileToRename?.let { file ->
        RenameFileDialog(
            currentName = file.name,
            errorMessage = renameError,
            onDismiss = {
                fileToRename = null
                renameError = null
            },
            onConfirm = { newName ->
                viewModel.renameFile(file, newName) { success ->
                    if (success) {
                        fileToRename = null
                        renameError = null
                    } else {
                        renameError = "Couldn't rename this file. It may not support renaming, or the name is already taken."
                    }
                }
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCategoryBottomSheet(
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
private fun RenameFileDialog(
    currentName: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename file") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text != currentName
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFileBottomSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCreate: (name: String, extension: String, categoryId: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedExtension by remember { mutableStateOf("txt") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val extensions = listOf("txt", "docx")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("New File", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Type", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                extensions.forEach { ext ->
                    FilterChip(
                        selected = selectedExtension == ext,
                        onClick = { selectedExtension = ext },
                        label = { Text(ext.uppercase()) }
                    )
                }
            }

            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Save into", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Root") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text("${cat.iconEmoji} ${cat.name}") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onCreate(name, selectedExtension, selectedCategoryId) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// --- List Items / Skeletons ---
@Composable
private fun CategoryChip(
    category: Category,
    onClick: () -> Unit
) {
    val bgColor = remember(category.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(category.colorHex))
        } catch (e: Exception) {
            Color(0xFF64748B) // fallback slate if colorHex is malformed/blank
        }
    }
    val textColor = if (bgColor.luminance() > 0.5f) Color.Black else Color.White

    val initials = remember(category.name) {
        category.name.trim()
            .take(2)
            .uppercase()
            .ifEmpty { "?" }
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (category.iconEmoji.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = category.iconEmoji, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: ShepherdFile,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRenameRequest: () -> Unit = {},
    onDeleteRequest: () -> Unit = {}
) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }
    val dateString = formatter.format(Date(file.lastModified))
    var showOverflowMenu by remember { mutableStateOf(false) }

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

            // Per-item overflow menu (rename / delete) — only relevant outside multi-select
            if (!isMultiSelectActive) {
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.LightGray)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onRenameRequest()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onDeleteRequest()
                            }
                        )
                    }
                }
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