package com.example.preachmode.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.preachmode.model.ScriptureBlock

@Composable
fun ScriptureReveal(
    scriptureBlock: ScriptureBlock,
    sectionId: String,
    modifier: Modifier = Modifier
) {
    var isRevealed by rememberSaveable(key = "reveal_scripture_$sectionId") {
        mutableStateOf(false)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isRevealed) Color(0xFFE8F5E9) else Color(0xFFF1F8E9))
                .clickable { isRevealed = !isRevealed }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "📖 ${scriptureBlock.reference} — " + (if (isRevealed) "tap to hide" else "tap to reveal verse"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRevealed) Color(0xFF2E7D32) else Color(0xFF558B2F)
            )
        }

        AnimatedVisibility(
            visible = isRevealed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFAFAFA))
                    .padding(16.dp)
            ) {
                Text(
                    text = scriptureBlock.text.trim(),
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    color = Color.DarkGray
                )
            }
        }
    }
}
