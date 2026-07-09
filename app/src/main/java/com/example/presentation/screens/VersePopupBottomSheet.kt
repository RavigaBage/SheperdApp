package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersePopupBottomSheet(
    viewModel: ShepherdViewModel,
    verseReference: String,
    onDismiss: () -> Unit
) {
    var selectedRef by remember { mutableStateOf(verseReference) }
    var selectedTranslation by remember { mutableStateOf("NIV") } // NIV, KJV, ESV, AMP
    var verseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val translations = listOf("NIV", "KJV", "ESV", "AMP")

    // Retrieve translated verses from open source API or solid offline local library
    LaunchedEffect(selectedRef, selectedTranslation) {
        isLoading = true
        try {
            verseText = viewModel.getBibleVerse(selectedRef, selectedTranslation)
        } catch (e: Exception) {
            verseText = "Could not load scripture."
        } finally {
            isLoading = false
        }
    }

    // Related Scriptures (highly relevant thematic pairings)
    val relatedVerses = remember(selectedRef) {
        getRelatedScripts(selectedRef)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag indicators and top header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .width(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.LightGray)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedRef,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            // Translation Chips Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                translations.forEach { trans ->
                    val isSelected = selectedTranslation == trans
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTranslation = trans },
                        label = { Text(trans, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Central verse description panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "\"$verseText\"",
                        fontSize = 17.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Related Scriptures Header
            Text(
                "RELATED SCRIPTURES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Related Scriptures Rows List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                relatedVerses.forEach { refPair ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRef = refPair.first },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                refPair.first,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                refPair.second,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getOfflineFallback(ref: String, trans: String): String {
    val clean = ref.lowercase()
    return when {
         clean.contains("john 3:16") -> {
             if (trans == "KJV") "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."
             else "For God so loved the world that he gave his one and only Son, that whoever believes in him shall not perish but have eternal life."
         }
         clean.contains("psalm 23:1") || clean.contains("ps. 23:1") -> {
             if (trans == "KJV") "The LORD is my shepherd; I shall not want."
             else "The LORD is my shepherd, I lack nothing."
         }
         clean.contains("romans 8:28") || clean.contains("rom 8:28") -> {
             "And we know that in all things God works for the good of those who love him, who have been called according to his purpose."
         }
         clean.contains("romans 10:13") || clean.contains("rom 10:13") -> {
             "Everyone who calls on the name of the Lord will be saved."
         }
         clean.contains("matthew 11:28") || clean.contains("matt 11:28") -> {
             "Come to me, all you who are weary and burdened, and I will give you rest."
         }
         clean.contains("revelation 3:20") || clean.contains("rev 3:20") -> {
             "Here I am! I stand at the door and knock. If anyone hears my voice and opens the door, I will come in..."
         }
         else -> "Scripture cached locally. For full verse retrieval, make sure your mobile device has active internet permission."
    }
}

private fun getRelatedScripts(ref: String): List<Pair<String, String>> {
    val clean = ref.lowercase()
    return when {
        clean.contains("john 3:16") -> listOf(
            "Romans 5:8" to "But God demonstrates his own love for us in this...",
            "1 John 4:9" to "This is how God showed his love among us..."
        )
        clean.contains("ps") -> listOf(
            "John 10:11" to "I am the good shepherd. The good shepherd lays down...",
            "Isaiah 40:11" to "He tends his flock like a shepherd: He gathers the lambs..."
        )
        else -> listOf(
            "John 3:16" to "For God so loved the world...",
            "Romans 8:28" to "And we know that in all things God works..."
        )
    }
}
