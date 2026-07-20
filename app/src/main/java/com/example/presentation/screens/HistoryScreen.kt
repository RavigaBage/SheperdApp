package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ActionType
import com.example.domain.model.HistoryEntry
import com.example.presentation.components.SkeletonItem
import com.example.presentation.components.bounceClickable
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.viewmodel.ShepherdViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val history by viewModel.history.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Activity History", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { },
        containerColor = Color.White
    ) { paddingValues ->
        if (isInitialLoading && history.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(8) {
                    SkeletonItem(height = 72.dp, shape = RoundedCornerShape(14.dp))
                }
            }
        } else if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No actions cataloged yet",
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(history) { entry ->
                    HistoryLogCard(entry = entry)
                }
            }
        }
    }
}

@Composable
fun HistoryLogCard(entry: HistoryEntry) {
    val formatter = remember { SimpleDateFormat("h:mm a, d MMM yyyy", Locale.getDefault()) }
    val timeStr = formatter.format(Date(entry.timestamp))

    val colorAccent = when (entry.actionType) {
        ActionType.MOVED -> Color(0xFF3498DB)
        ActionType.DELETED -> Color(0xFFC0392B)
        ActionType.CREATED -> Color(0xFF2ECC71)
        ActionType.RENAMED -> Color(0xFFF1C40F)
        ActionType.AI_GENERATED -> Color(0xFF9B59B6)
        ActionType.BOOKMARKED -> Color(0xFFE67E22)
        ActionType.TAGGED -> Color(0xFF1ABC9C)
        ActionType.RESTORED -> Color(0xFF34495E)
        ActionType.PURGED -> Color(0xFF95A5A6)
        ActionType.PREACH -> Color(0xFFFF4081) // Pink accent for preaching
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorAccent)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.action,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode: ${entry.actionType.name}",
                        fontSize = 11.sp,
                        color = colorAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
