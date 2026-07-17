package com.example.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.file.FileExtensions.toFileColor
import com.example.data.file.FileExtensions.toFileIcon
import com.example.data.file.FileExtensions.toReadableSize
import com.example.domain.model.Category
import com.example.domain.model.ShepherdFile
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.components.OpenFileOptionsBottomSheet
import com.example.presentation.viewmodel.ShepherdViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ShepherdViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToFileBrowser: () -> Unit,
    onNavigateToAiEditor: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToScripture: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val rootFolder by viewModel.rootFolderUri.collectAsState()
    val files by viewModel.files.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val pastorName by viewModel.pastorName.collectAsState()
    val seriesList by viewModel.seriesList.collectAsState()
    val upcomingEvents by viewModel.upcomingEvents.collectAsState()

    var showCreateCategorySheet by remember { mutableStateOf(false) }
    var activeOptionsFile by remember { mutableStateOf<ShepherdFile?>(null) }

    // Playback Simulation State (Suggested Mini Player)
    var isSimulatingPlayback by remember { mutableStateOf(false) }
    var simulatedPlaybackPosition by remember { mutableStateOf(0.35f) }

    // Folder picker launcher for directory CTA
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectRootFolder(uri)
        }
    }

    Scaffold(
        topBar = {
            // Header bar: Solid dark navy bar, full width
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Menu (hamburger) button + App Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer",
                                tint = Color.Black
                            )
                        }
                        Text(
                            text = "Shepherd",
                            color = Color.Black,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    // Right: Search icon, white, tappable
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.Black
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Bottom navigation removed as per request
        },
        containerColor = Color.White // Soft Light Page Background
    ) { paddingValues ->
        if (rootFolder == null) {
            // Connect directory onboarding CTA
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B2B4B).copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Color(0xFF1B2B4B)
                            )
                        }

                        Text(
                            "Connect Sermon Directory",
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2B4B)
                        )

                        Text(
                            "Link your pastoral study folder to explore, prepare outlines, and manage sermon slide delivery.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray,
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                        ) {
                            Text("Select Study Folder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Dashboard Layout
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Search bar sitting just below header, light gray, rounded, no border
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAF9F6))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFEFEFF1))
                                    .clickable { onNavigateToSearch() }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search icon",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                               )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Search documents, sermons…",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Featured Hero Card representing latest series
                    item {
                        val latestSeries = seriesList.lastOrNull()
                        val currentSeriesName = latestSeries?.name ?: "Walking In Unending Grace"
                        val docCount = files.size
                        val audioCount = files.count { it.extension == "mp3" || it.extension == "wav" }

                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2B4B)) // Navy matches header
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Top-left: Small pill badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF4D35E)) // Warm Yellow badge
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "LATEST SERIES",
                                            color = Color(0xFF1B2B4B),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    // Bold White Title
                                    Text(
                                        text = currentSeriesName,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Serif,
                                        lineHeight = 28.sp
                                    )

                                    // White Metadata separated by middle dot
                                    Text(
                                        text = "$docCount Outlines  •  $audioCount Audio Recs",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Stats Row directly under the hero card (No card background)
                    item {
                        val totalFiles = files.size
                        val totalCategories = categories.size
                        val totalBookmarked = bookmarks.size

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (totalFiles == 0 && totalCategories == 0 && totalBookmarked == 0) {
                                // Empty state nudge
                                Text(
                                    text = " Add your first sermon outline to populate dashboard statistics",
                                    color = Color(0xFF1B2B4B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF4D35E).copy(alpha = 0.12f))
                                        .padding(14.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Column 1: Files
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onNavigateToFileBrowser() },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$totalFiles",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1B2B4B),
                                            fontFamily = FontFamily.Serif
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Files",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Divider
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

                                    // Column 2: Categories
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onNavigateToFileBrowser() },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$totalCategories",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1B2B4B),
                                            fontFamily = FontFamily.Serif
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Categories",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Divider
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

                                    // Column 3: Bookmarked
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onNavigateToFileBrowser() },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$totalBookmarked",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1B2B4B),
                                            fontFamily = FontFamily.Serif
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Bookmarked",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Next Up engagement strip pulling from calendar
                    item {
                        val nextEvent = upcomingEvents.firstOrNull()
                        if (nextEvent != null) {
                            val sdf = remember { SimpleDateFormat("E, d MMM", Locale.getDefault()) }
                            val dateStr = sdf.format(Date(nextEvent.scheduledDateMs))

                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)),
                                    border = BorderStroke(1.dp, Color(0xFFF4D35E).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF1B2B4B).copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dateStr.take(3),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1B2B4B)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "NEXT PLACEMENT • $dateStr",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE07A5F)
                                                )
                                                Text(
                                                    text = nextEvent.sermonTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1B2B4B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Quick access chip to preach mode
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1B2B4B))
                                                .clickable {
                                                    viewModel.activeViewerSermonId = nextEvent.sermonId
                                                    viewModel.activeViewerFilePath = ""
                                                    viewModel.activeViewerTitle = nextEvent.sermonTitle
                                                    viewModel.livePreachDurationMinutes = 30
                                                    onNavigate("preach_mode")
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text("PREACH", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Categories Section (2-Column Grid with floating overlap button)
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = "Study Categories",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1B2B4B)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (categories.isEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showCreateCategorySheet = true },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Text(
                                            text = "No study folders yet. Tap + to setup your first category.",
                                            modifier = Modifier.padding(20.dp),
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        categories.chunked(2).forEach { chunk ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                chunk.forEach { category ->
                                                    val count = files.count { it.categoryId == category.id }
                                                    Card(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(76.dp)
                                                            .clickable {
                                                                viewModel.selectCategoryFilter(category.id)
                                                                onNavigateToFileBrowser()
                                                            },
                                                        shape = RoundedCornerShape(14.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = Color(0xFFFFFEEB) // soft pastel yellow fill preset
                                                        )
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(12.dp),
                                                            verticalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = category.iconEmoji,
                                                                fontSize = 16.sp
                                                            )
                                                            Column {
                                                                Text(
                                                                    text = category.name,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF1B2B4B),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "$count items",
                                                                    fontSize = 9.sp,
                                                                    color = Color.Gray
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (chunk.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Overlapping FAB anchored to bottom-right corner of categories section
                                FloatingActionButton(
                                    onClick = { showCreateCategorySheet = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 4.dp, end = 4.dp)
                                        .size(32.dp),
                                    containerColor = Color(0xFF1B2B4B),
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add category", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Recent Files Section
                    if (files.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Recent Outlines",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1B2B4B)
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 16.dp)
                                ) {
                                    items(files.take(6)) { file ->
                                        // Circular icon thumbnail layout
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(72.dp)
                                                .clickable {
                                                viewModel.incrementFileUsage(file.id)
                                                viewModel.activeViewerSermonId = file.id
                                                viewModel.activeViewerFilePath = file.uriString
                                                viewModel.activeViewerTitle = file.name
                                                viewModel.loadDocumentFromUri(file.id, file.uriString, file.name)
                                                onNavigate("sermon_viewer")
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEFEFF1)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        file.extension.lowercase() == "mp3" -> Icons.Default.Audiotrack
                                                        else -> Icons.Default.Description
                                                    },
                                                    contentDescription = null,
                                                    tint = Color(0xFF1B2B4B),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = file.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF1B2B4B),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    // Modal sheet overrides for Dynamic category setup
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

// Bottom sheet modal for creating category presets
