package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Series
import com.example.domain.model.Sermon
import com.example.presentation.components.bounceClickable
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.viewmodel.ShepherdViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonOrganizerScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val sermons by viewModel.sermons.collectAsState()
    val seriesList by viewModel.seriesList.collectAsState()

    var showCreateSermonDialog by remember { mutableStateOf(false) }
    var showCreateSeriesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Missions & Sermons", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Preaching Calendar")
                    }
                }
            )
        },
        bottomBar = { },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { showCreateSeriesDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.Black,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = "Create Series")
                }
                FloatingActionButton(
                    onClick = { showCreateSermonDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Create, contentDescription = "Schedule Sermon")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Upcoming items
            item {
                Text(
                    "Upcoming preaching Timeline",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (sermons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No sermons scheduled yet. Tap + to draft one.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sermons.take(3).forEach { sermon ->
                            SermonScheduleCard(sermon = sermon, series = seriesList.find { it.id == sermon.seriesId })
                        }
                    }
                }
            }

            // Series row
            item {
                Text(
                    "Active Series Groups",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (seriesList.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Compile sermon series (e.g. Genesis, Sermon on the Mount)",
                            modifier = Modifier.padding(20.dp),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(seriesList) { series ->
                            SeriesDisplayCard(series = series)
                        }
                    }
                }
            }
        }
    }

    if (showCreateSermonDialog) {
        CreateSermonWizardDialog(
            seriesOptions = seriesList,
            onDismiss = { showCreateSermonDialog = false },
            onConfirm = { title, scripture, seriesId, notes ->
                viewModel.createSermon(title, scripture, seriesId, System.currentTimeMillis(), notes, null)
                showCreateSermonDialog = false
            }
        )
    }

    if (showCreateSeriesDialog) {
        CreateSeriesDialog(
            onDismiss = { showCreateSeriesDialog = false },
            onConfirm = { name, desc ->
                viewModel.createSeries(name, desc, "#C9A84C")
                showCreateSeriesDialog = false
            }
        )
    }
}

@Composable
fun SermonScheduleCard(
    sermon: Sermon,
    series: Series?
) {
    val formatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val dateStr = if (sermon.datePreached != null) formatter.format(Date(sermon.datePreached)) else "No Date set"

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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sermon.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Text: ${sermon.scriptureRef}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (series != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Series: ${series.name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }

            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun SeriesDisplayCard(series: Series) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(110.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Column {
                Text(
                    text = series.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = series.description,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSermonWizardDialog(
    seriesOptions: List<Series>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var scripture by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule sermon outline", fontFamily = FontFamily.Serif) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Sermon Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = scripture,
                    onValueChange = { scripture = it },
                    label = { Text("Scripture Verse Reference") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Personal details details") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (seriesOptions.isNotEmpty()) {
                    Text("Select Series Parent Link", fontSize = 11.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seriesOptions.forEach { series ->
                            FilterChip(
                                selected = selectedSeriesId == series.id,
                                onClick = { selectedSeriesId = if (selectedSeriesId == series.id) null else series.id },
                                label = { Text(series.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title, scripture, selectedSeriesId, notes) },
                enabled = title.isNotBlank()
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateSeriesDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sermon Series", fontFamily = FontFamily.Serif) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Series Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Brief description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, desc) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
