package com.example.preachmode

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.preachmode.model.PreachDocument
import com.example.preachmode.model.PreachSection
import com.example.preachmode.model.SectionType
import com.example.preachmode.ui.*
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreachModeScreen(
    viewModel: ShepherdViewModel,
    filePath: String,
    sermonTitle: String,
    durationMinutes: Int,
    isNote: Boolean = false,
    isNotebookScope: Boolean = false,
    sermonId: String = "",
    onBack: () -> Unit,
    preachViewModel: PreachModeViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.ShepherdApplication
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    val uiState by preachViewModel.uiState.collectAsState()
    val timerState by preachViewModel.timerState.collectAsState()

    var showSlideSorter by remember { mutableStateOf(false) }
    var showTimerPanel by remember { mutableStateOf(false) }
    var isMirrorMode by remember { mutableStateOf(false) }
    var showSpeakerNotes by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(true) }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val totalDurationMillis = durationMinutes * 60 * 1000L

    LaunchedEffect(timerState.elapsed) {
        if (timerState.remaining in (totalDurationMillis / 2 - 5000)..(totalDurationMillis / 2 + 5000)) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val attachmentUris = viewModel.activeViewerAttachmentUris

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val source = if (attachmentUris.isNotEmpty()) {
            MultiContentSource(attachmentUris.map { uri ->
                if (uri.startsWith("notebook://")) {
                    val id = uri.removePrefix("notebook://")
                    NotesContentSource(context, app.notesRepository, NotesContentSource.NotesContentScope.Notebook(id))
                } else if (uri.startsWith("page://")) {
                    val id = uri.removePrefix("page://")
                    NotesContentSource(context, app.notesRepository, NotesContentSource.NotesContentScope.Page(id))
                } else {
                    FileContentSource(context, uri)
                }
            })
        } else if (isNote) {
            NotesContentSource(
                context = context,
                repository = app.notesRepository,
                scope = if (isNotebookScope) NotesContentSource.NotesContentScope.Notebook(sermonId) else NotesContentSource.NotesContentScope.Page(sermonId)
            )
        } else if (filePath.isNotBlank()) {
            FileContentSource(context, filePath)
        } else {
            null
        }
        
        if (source != null) {
            preachViewModel.startPreaching(source, durationMinutes)
        } else {
            preachViewModel.setEmptyState()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is PreachUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PreachUiState.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("No Preach Content Found", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Please attach a notebook, page, or document to this event to begin preaching.", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onBack) { Text("Go Back") }
                    }
                }
                is PreachUiState.Error -> {
                    Text(state.message, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                }
                is PreachUiState.Success -> {
                    val doc = state.document
                    val pagerState = rememberPagerState(pageCount = { doc.sections.size })
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val section = doc.sections[page]
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                if (section.displayText.endsWith(".png") || section.displayText.endsWith(".jpg")) {
                                    AsyncImage(model = section.displayText, contentDescription = "Page", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                } else {
                                    Text(section.displayText, fontSize = 24.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        
                        // Timer & Navigation overlays...
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
                            Text("${String.format("%02d:%02d", timerState.remaining/60000, (timerState.remaining/1000)%60)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
