package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.file.FileExtensions.toFileColor
import com.example.data.file.FileExtensions.toFileIcon
import com.example.data.file.FileExtensions.toReadableSize
import com.example.domain.model.ShepherdFile
import com.example.presentation.components.SkeletonItem
import com.example.presentation.viewmodel.ShepherdViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit
) {
    val trashedFiles by viewModel.trashedFiles.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    var pendingPermanentDelete by remember { mutableStateOf<ShepherdFile?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashedFiles.isNotEmpty()) {
                        TextButton(onClick = { showEmptyTrashConfirm = true }) {
                            Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isInitialLoading && trashedFiles.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(6) {
                    SkeletonItem(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        height = 76.dp,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        } else if (trashedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoDelete,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Trash is Empty",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Deleted files stay here for 30 days before being permanently removed.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(trashedFiles, key = { it.id }) { file ->
                    TrashFileRow(
                        file = file,
                        onRestore = { viewModel.restoreFile(file) },
                        onDeleteForever = { pendingPermanentDelete = file }
                    )
                }
            }
        }
    }

    pendingPermanentDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            title = { Text("Delete forever?") },
            text = { Text("\"${file.name}\" will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.permanentlyDeleteFile(file)
                    pendingPermanentDelete = null
                }) {
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Empty Trash?") },
            text = { Text("All ${trashedFiles.size} item(s) in Trash will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashConfirm = false
                }) {
                    Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashFileRow(
    file: ShepherdFile,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val deletedDateString = remember(file.deletedAt) {
        file.deletedAt?.let { formatter.format(Date(it)) } ?: "Unknown date"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(file.extension.toFileColor().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    file.extension.toFileIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = file.extension.toFileColor()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Deleted $deletedDateString · ${file.sizeBytes.toReadableSize()}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}