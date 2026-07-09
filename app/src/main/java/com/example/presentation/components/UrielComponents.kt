package com.example.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.viewmodel.ShepherdNotification
import com.example.ui.theme.ShepherdGold
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UrielMascot(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "uriel_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (unreadCount > 0) 1.12f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(ShepherdGold.copy(alpha = if (unreadCount > 0) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (unreadCount > 0) "👼✨" else "👼",
                fontSize = 20.sp
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Red, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unreadCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrielNotificationsBottomSheet(
    notifications: List<ShepherdNotification>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = ShepherdGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Uriel's Messages",
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(onClick = onClearAll) {
                    Text("Mark Read", color = ShepherdGold, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No messages from Uriel yet.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxHeight(0.6f)
                ) {
                    items(notifications) { notif ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                } else {
                                    ShepherdGold.copy(alpha = 0.08f)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = notif.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notif.message,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dateFormatter.format(Date(notif.timestamp)),
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
