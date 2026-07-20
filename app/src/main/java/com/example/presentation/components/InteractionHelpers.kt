package com.example.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Duolingo Level Spring Click Feedback & Soft Tactile Haptic ---
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } catch (e: Exception) { e.printStackTrace() }
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } catch (e: Exception) { e.printStackTrace() }
                    onClick()
                }
            )
        }
}

// --- Procedural Shimmer Brush Sweep ---
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@Composable
fun SkeletonItem(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 20.dp,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {
    val shimmerBrush = shimmerBrush()
    Box(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.size(width, height) else Modifier.fillMaxWidth().height(height))
            .clip(shape)
            .background(shimmerBrush)
    )
}

// --- Animated CountUp Statistics ---
@Composable
fun CountUpText(
    targetValue: Int,
    prefix: String = "",
    suffix: String = "",
    style: androidx.compose.ui.text.TextStyle,
    color: Color
) {
    var currentValue by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(targetValue) {
        if (targetValue <= 0) {
            currentValue = 0
            return@LaunchedEffect
        }
        val durationMs = 800
        val steps = targetValue.coerceAtMost(50)
        val delayTime = (durationMs / steps).toLong()
        val stepSize = (targetValue / steps).coerceAtLeast(1)

        for (i in 1..steps) {
            delay(delayTime)
            currentValue = (currentValue + stepSize).coerceAtMost(targetValue)
            if (currentValue == targetValue) break
        }
        currentValue = targetValue
    }

    Text(
        text = "$prefix$currentValue$suffix",
        style = style,
        color = color
    )
}

/**
 * Ensures that a text field (or any focused item) remains visible above the keyboard.
 * Bundles BringIntoViewRequester with focus-event scrolling logic.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.keyboardAware(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { event ->
            if (event.isFocused) {
                coroutineScope.launch {
                    delay(300) // let IME animation start before scrolling
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
}
