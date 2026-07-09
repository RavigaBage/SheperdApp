package com.example.preachmode.ui

import android.content.Context
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit

@Composable
fun TimerBar(
    elapsedMillis: Long,
    remainingMillis: Long,
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Trigger standard haptic vibration once when pulse begins
    LaunchedEffect(isPulsing) {
        if (isPulsing) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(120)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Set up pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Format elapsed time (e.g., 18:42)
    val elapsedMins = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
    val elapsedSecs = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
    val elapsedStr = String.format("%02d:%02d", elapsedMins, elapsedSecs)

    // Format remaining time (e.g., 11:18)
    val remainingMins = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
    val remainingSecs = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
    val remainingStr = String.format("%02d:%02d", remainingMins, remainingSecs)

    val backgroundColor = if (isPulsing) {
        // Soft amber/gold pulse background for visual alerts
        Color(0xFFFFD54F).copy(alpha = pulseAlpha)
    } else {
        Color(0xFFF5F5F5)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$elapsedStr elapsed  ·  $remainingStr left",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isPulsing) Color(0xFF5D4037) else Color.Gray
        )
    }
}
