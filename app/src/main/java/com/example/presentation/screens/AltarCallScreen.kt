package com.example.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.bounceClickable

@Composable
fun AltarCallScreen(
    onBack: () -> Unit
) {
    val altarCallVerses = remember {
        listOf(
            Pair("John 3:16", "For God so loved the world that he gave his one and only Son, that whoever believes in him shall not perish but have eternal life."),
            Pair("Romans 10:13", "Everyone who calls on the name of the Lord will be saved."),
            Pair("Matthew 11:28", "Come to me, all you who are weary and burdened, and I will give you rest."),
            Pair("Revelation 3:20", "Here I am! I stand at the door and knock. If anyone hears my voice and opens the door, I will come in."),
            Pair("Acts 2:38", "Repent and be baptized, every one of you, in the name of Jesus Christ for the forgiveness of your sins. And you will receive the gift of the Holy Spirit."),
            Pair("Isaiah 1:18", "Come now, let us settle the matter. Though your sins are like scarlet, they shall be as white as snow; though they are red as crimson, they shall be like wool."),
            Pair("Luke 15:7", "I tell you that in the same way there will be more rejoicing in heaven over one sinner who repents than over ninety-nine righteous persons who do not need to repent."),
            Pair("John 6:37", "All those the Father gives me will come to me, and whoever comes to me I will never drive away.")
        )
    }

    var currentIndex by remember { mutableStateOf(0) }
    val (verseRef, verseText) = altarCallVerses[currentIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFDF7)) // Warm organic white
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // High fidelity vector Cross drawing to center
            Canvas(
                modifier = Modifier
                    .size(width = 80.dp, height = 140.dp)
                    .padding(bottom = 16.dp)
            ) {
                // Gold color palette selection
                val goldColor = Color(0xFFB8960C)
                
                // Draw vertical beam
                drawRect(
                    color = goldColor,
                    topLeft = Offset(x = size.width * 0.4f, y = 0f),
                    size = Size(width = size.width * 0.2f, height = size.height)
                )

                // Draw crossbar
                drawRect(
                    color = goldColor,
                    topLeft = Offset(x = 0f, y = size.height * 0.3f),
                    size = Size(width = size.width, height = size.height * 0.12f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Come just as you are",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light,
                fontSize = 28.sp,
                color = Color(0xFF3E2723), // Deep brown
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Salvation Verse box
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFDFBF7))
                    .border(1.dp, Color(0xFFEFEBE9), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "\"$verseText\"",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5D4037), // Mahogany
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(1.dp)
                            .background(Color(0xFFD7CCC8))
                    )

                    Text(
                        text = verseRef,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFFB8960C),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // FOOTER ACTIONS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    currentIndex = (currentIndex + 1) % altarCallVerses.size
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF5D4037)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy()
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change Verse", fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF5D4037))
                    .bounceClickable { onBack() }
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Return to Presentation",
                    tint = Color.White
                )
            }
        }
    }
}
