package com.example.presentation.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.bounceClickable
import com.example.presentation.viewmodel.ShepherdViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    val rootFolder by viewModel.rootFolderUri.collectAsState()
    val pastorName by viewModel.pastorName.collectAsState()
    val bibleVersion by viewModel.bibleVer.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var pastorNameInput by remember { mutableStateOf(pastorName) }
    var showPastorNameDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectRootFolder(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pastor profile configurations
            Text("General Profile Settings", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Person,
                        title = "Pastor Name",
                        subtitle = pastorName,
                        onClick = { showPastorNameDialog = true }
                    )
                    Divider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = Icons.Default.Book,
                        title = "Bible Translation",
                        subtitle = bibleVersion,
                        onClick = {
                            val versions = listOf("NIV", "ESV", "KJV", "NKJV", "NASB")
                            val nextIndex = (versions.indexOf(bibleVersion) + 1) % versions.size
                            viewModel.updateBibleVersion(versions[nextIndex])
                        }
                    )
                }
            }

            // Connection configurations
            Text("Workspace parameters", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Folder,
                        title = "Root Study Folder",
                        subtitle = rootFolder?.path ?: "No folder linked",
                        onClick = { folderPicker.launch(null) }
                    )
                }
            }

            // Action triggers
            Text("Auditing Utilities", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Export Sermon Records to CSV",
                        subtitle = "Generates spreadsheet CSV file with details of preaching timeline logs",
                        onClick = {
                            try {
                                val downloadsDir = File(context.cacheDir, "sermons_export.csv")
                                FileOutputStream(downloadsDir).use { out ->
                                    val header = "ID,Sermon,Scripture,Personal Notes\n"
                                    out.write(header.toByteArray())
                                    // Simulated list write
                                    viewModel.sermons.value.forEach { s ->
                                        out.write("${s.id},\"${s.title}\",\"${s.scriptureRef}\",\"${s.notes}\"\n".toByteArray())
                                    }
                                }
                                Toast.makeText(context, "Spreadsheet exported: ${downloadsDir.absolutePath}", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPastorNameDialog) {
        AlertDialog(
            onDismissRequest = { showPastorNameDialog = false },
            title = { Text("Pastor profile", fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = pastorNameInput,
                    onValueChange = { pastorNameInput = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updatePastorName(pastorNameInput)
                    showPastorNameDialog = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPastorNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}
