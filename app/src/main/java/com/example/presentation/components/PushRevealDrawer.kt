package com.example.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ShepherdFile
import com.example.presentation.viewmodel.ShepherdViewModel
import com.example.ui.theme.ShepherdGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DrawerMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun PushRevealDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    activeRoute: String,
    viewModel: ShepherdViewModel,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    val pastorName by viewModel.pastorName.collectAsState()
    val files by viewModel.files.collectAsState()
    val scope = rememberCoroutineScope()

    // Interactive screens overlay state
    var activeOverlayItem by remember { mutableStateOf<String?>(null) }

    // Custom animation spec (280-320ms, cubic-bezier(0.25, 0.8, 0.25, 1))
    val animationFraction by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)
        ),
        label = "drawer_animation"
    )

    val menuItems = remember {
        listOf(
            DrawerMenuItem("home", "Home", Icons.Outlined.Home),
            DrawerMenuItem("sermons", "Bible", Icons.Outlined.MenuBook),
            DrawerMenuItem("preach_mode", "Preach Mode", Icons.Outlined.PlayArrow),
            DrawerMenuItem("notes", "Notes", Icons.Outlined.Description),
            DrawerMenuItem("file_list", "Library", Icons.Outlined.FolderOpen),
            DrawerMenuItem("ai_editor", "AI Tools", Icons.Outlined.AutoAwesome),
            DrawerMenuItem("settings", "Settings", Icons.Outlined.Settings),
            DrawerMenuItem("profile", "Profile", Icons.Outlined.Person)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16213E)) // Solid Navy background
    ) {
        // --- 1. DRAWER PANEL (Left layer) ---
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                .background(Color(0xFF16213E)) // Solid Navy (#16213E)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Header (Top Section)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    // 56px circular avatar
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFFFD700), ShepherdGold))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pastorName.take(1).uppercase(Locale.getDefault()).ifEmpty { "P" },
                            color = Color(0xFF16213E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        // name in bold white 16px
                        Text(
                            text = pastorName.ifEmpty { "Pastor" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        // role/subtitle in white at 60% opacity, 14px
                        Text(
                            text = "Lead Shepherd",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }

                // Scrollable rows to fit any screen safely
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(menuItems) { item ->
                        val isItemActive = activeRoute == item.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (isItemActive) Color.White.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onClose()
                                    val topLevelRoutes = listOf("home", "settings", "sermons", "preach_mode", "file_list", "ai_editor", "notes")
                                    if (item.id in topLevelRoutes) {
                                        onNavigate(item.id)
                                    } else {
                                        activeOverlayItem = item.id
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isItemActive) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = item.label,
                                    color = if (isItemActive) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = if (isItemActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    item {
                        // Divider
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        // Logout row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .clickable {
                                    onClose()
                                    activeOverlayItem = "logout"
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ExitToApp,
                                    contentDescription = "Logout",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Logout",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. MAIN CONTENT SCREEN (Pushed to the right) ---
        val mainScale = 1f - (0.06f * animationFraction) // scales down to 0.94
        val leftRadius = 24.dp * animationFraction // Left edge gets matching 24px radius

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * 0.78f * animationFraction
                    scaleX = mainScale
                    scaleY = mainScale
                    shadowElevation = if (animationFraction > 0f) 16.dp.toPx() else 0f
                    shape = RoundedCornerShape(topStart = leftRadius, bottomStart = leftRadius)
                    clip = animationFraction > 0f
                }
        ) {
            content()

            // Click/Swipe Overlay on main content when open
            if (animationFraction > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                // Drag left (negative) or right (positive) can close
                                if (dragAmount < -10f || dragAmount > 10f) {
                                    onClose()
                                }
                            }
                        }
                        .clickable(enabled = true, onClick = onClose)
                )
            }
        }
    }

    activeOverlayItem?.let { itemId ->
        when (itemId) {
            "profile" -> {
                ProfileDialog(
                    pastorName = pastorName,
                    onSave = { newName ->
                        viewModel.updatePastorName(newName)
                        activeOverlayItem = null
                    },
                    onDismiss = { activeOverlayItem = null }
                )
            }
            "nearby" -> {
                NearbyShareDialog(
                    onDismiss = { activeOverlayItem = null }
                )
            }
            "favorites" -> {
                val favoriteFiles = files.filter { it.isFavorite }
                FavoritesDialog(
                    favorites = favoriteFiles,
                    onOpenFile = { file ->
                        viewModel.incrementFileUsage(file.id)
                        viewModel.activeViewerSermonId = file.id
                        viewModel.activeViewerFilePath = file.uriString
                        viewModel.activeViewerTitle = file.name
                        viewModel.loadDocumentFromUri(file.id, file.uriString, file.name)
                        onNavigate("sermon_viewer")
                        activeOverlayItem = null
                    },
                    onDismiss = { activeOverlayItem = null }
                )
            }
            "notifications" -> {
                val notifications by viewModel.notifications.collectAsState()
                NotificationsDialog(
                    notifications = notifications,
                    onDismiss = { activeOverlayItem = null }
                )
            }
            "promotions" -> {
                PromotionsDialog(
                    onDismiss = { activeOverlayItem = null }
                )
            }
            "help" -> {
                HelpDialog(
                    onDismiss = { activeOverlayItem = null }
                )
            }
            "logout" -> {
                LogoutConfirmationDialog(
                    onConfirm = {
                        activeOverlayItem = null
                        onLogout()
                    },
                    onDismiss = { activeOverlayItem = null }
                )
            }
        }
    }
}

// ==========================================
// INTERACTIVE COMPONENT DIALOGS
// ==========================================

@Composable
fun ProfileDialog(
    pastorName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(pastorName) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Shepherd Profile",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ShepherdGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👼", fontSize = 40.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Pastor's Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ShepherdGold,
                        focusedLabelColor = ShepherdGold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Button(
                        onClick = { onSave(nameInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ShepherdGold)
                    ) {
                        Text("Save Changes", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyShareDialog(
    onDismiss: () -> Unit
) {
    var isScanning by remember { mutableStateOf(true) }
    var devicesFound by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        delay(1500)
        devicesFound = listOf("Pastor Andrew (Studio Tablet)", "Minister Chloe (Pulpit Mobile)")
        isScanning = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nearby Shepherds",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E)
                )
                Text(
                    text = "Share study outlines instantly via pulpit mesh",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isScanning) {
                    val infiniteTransition = rememberInfiniteTransition(label = "radar")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "radar_pulse"
                    )

                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .scale(pulseScale)
                                .background(ShepherdGold.copy(alpha = 0.12f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(ShepherdGold.copy(alpha = 0.22f), CircleShape)
                        )
                        Icon(
                            Icons.Filled.NearMe,
                            contentDescription = "Scanning",
                            tint = ShepherdGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning pulpit airwaves...", fontSize = 13.sp, color = Color.Gray)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        devicesFound.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Ready to receive", fontSize = 10.sp, color = Color.Gray)
                                }
                                Button(
                                    onClick = { /* Share action */ },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShepherdGold),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Send", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun FavoritesDialog(
    favorites: List<ShepherdFile>,
    onOpenFile: (ShepherdFile) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Starred Outlines",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No starred files yet.", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favorites) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(file) }
                                    .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(ShepherdGold.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(file.extension.uppercase(), fontSize = 10.sp, color = ShepherdGold, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(file.extension.uppercase(), fontSize = 10.sp, color = Color.Gray)
                                }
                                Icon(Icons.Filled.Star, contentDescription = "Starred", tint = ShepherdGold, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun NotificationsDialog(
    notifications: List<com.example.presentation.viewmodel.ShepherdNotification>,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Pulpit Notifications",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications) { notif ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(notif.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(notif.message, fontSize = 11.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(dateFormatter.format(Date(notif.timestamp)), fontSize = 9.sp, color = Color.LightGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun PromotionsDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ministry Offers",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E)
                )
                Text("Special premium resources for the pulpit", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ShepherdGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📚", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hebrew Commentary Pack", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Save 30% on top translation notes", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ShepherdGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎙️", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Acoustic Audio Pack", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Beautiful serene church loops", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ShepherdGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock All Resources", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Shepherd Guide",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("👼 **Push-Reveal Drawer**\nSwipe right from the left edge or tap the menu icon to reveal. Tap the main screen sliver or swipe back to close.", fontSize = 12.sp)
                    Text("📜 **Preach Mode**\nSelect an outline and tap 'Launch in Preach Mode' to open a fluid teleprompter with auto-scroll optimized for the pulpit.", fontSize = 12.sp)
                    Text("🤖 **AI Study Prep**\nFormat sermon drafts, auto-tag scripture scriptures, and structure studies with Uriel, your smart study assistant.", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Got it, Pastor", color = ShepherdGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign Out?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF16213E)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Are you sure you want to log out? This will reset your current pulpit onboarding.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("No, Stay", color = Color.Gray)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Yes, Logout", color = Color.White)
                    }
                }
            }
        }
    }
}
