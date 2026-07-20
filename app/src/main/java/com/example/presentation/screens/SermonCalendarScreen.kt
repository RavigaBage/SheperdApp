package com.example.presentation.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SermonCalendarEntity
import com.example.domain.model.ShepherdFile
import com.example.presentation.components.keyboardAware
import com.example.presentation.viewmodel.ShepherdViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonCalendarScreen(
    viewModel: ShepherdViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val upcomingEvents by viewModel.upcomingEvents.collectAsState()
    val selectedDayEvents by viewModel.selectedDayEvents.collectAsState()
    val allFiles by viewModel.files.collectAsState()

    var showAddEventSheet by remember { mutableStateOf(false) }
    var selectedEventForDetail by remember { mutableStateOf<SermonCalendarEntity?>(null) }

    var currentMonthCal by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateMs by remember { mutableStateOf(Calendar.getInstance().timeInMillis) }

    val monthYearFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    LaunchedEffect(selectedDateMs) {
        viewModel.onDaySelected(selectedDateMs)
    }

    val daysInGrid = remember(currentMonthCal) {
        val grid = mutableListOf<Calendar>()
        val cal = currentMonthCal.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val leadingEmptyCount = firstDayOfWeek - Calendar.SUNDAY
        val prevMonthCal = cal.clone() as Calendar
        prevMonthCal.add(Calendar.MONTH, -1)
        val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 0 until leadingEmptyCount) {
            val padCal = prevMonthCal.clone() as Calendar
            padCal.set(Calendar.DAY_OF_MONTH, daysInPrevMonth - leadingEmptyCount + i + 1)
            grid.add(padCal)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, i)
            grid.add(dayCal)
        }
        while (grid.size % 7 != 0) {
            val nextMonthCal = cal.clone() as Calendar
            nextMonthCal.add(Calendar.MONTH, 1)
            val nextMonthDay = grid.size - leadingEmptyCount - daysInMonth + 1
            nextMonthCal.set(Calendar.DAY_OF_MONTH, nextMonthDay)
            grid.add(nextMonthCal)
        }
        grid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sermon Pipeline", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B2B4B)) } },
                actions = { IconButton(onClick = { showAddEventSheet = true }) { Icon(Icons.Default.AddCircleOutline, contentDescription = "Schedule Event", tint = Color.Black) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = monthYearFormatter.format(currentMonthCal.time), fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Serif, color = Color(0xFF1B2B4B))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { val next = currentMonthCal.clone() as Calendar; next.add(Calendar.MONTH, -1); currentMonthCal = next }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month", tint = Color(0xFF1B2B4B)) }
                        IconButton(onClick = { val next = currentMonthCal.clone() as Calendar; next.add(Calendar.MONTH, 1); currentMonthCal = next }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month", tint = Color(0xFF1B2B4B)) }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                    weekdays.forEach { day -> Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray) }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rows = daysInGrid.chunked(7)
                        rows.forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { dayCal ->
                                    val dayMs = dayCal.timeInMillis
                                    val isSelected = isSameDay(dayMs, selectedDateMs)
                                    val isCurrentMonth = dayCal.get(Calendar.MONTH) == currentMonthCal.get(Calendar.MONTH)
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1.1f).clip(RoundedCornerShape(12.dp)).clickable { selectedDateMs = dayMs }.background(if (isSelected) Color(0xFF1B2B4B).copy(alpha = 0.08f) else Color.Transparent), contentAlignment = Alignment.Center) {
                                        val dayEvents = getEventsForDay(dayCal, upcomingEvents)
                                        if (dayEvents.isNotEmpty()) {
                                            val (event, pillType) = dayEvents.first()
                                            val color = getColorForEventType(event.eventType)
                                            when (pillType) {
                                                "single" -> Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color))
                                                "start" -> Box(modifier = Modifier.fillMaxWidth().height(26.dp).padding(start = 2.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)).background(color))
                                                "middle" -> Box(modifier = Modifier.fillMaxWidth().height(26.dp).background(color))
                                                "end" -> Box(modifier = Modifier.fillMaxWidth().height(26.dp).padding(end = 2.dp).clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)).background(color))
                                            }
                                        }
                                        Text(text = "${dayCal.get(Calendar.DAY_OF_MONTH)}", fontSize = 13.sp, fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal, color = when { dayEvents.isNotEmpty() -> Color.White; isCurrentMonth -> Color(0xFF1B2B4B); else -> Color.LightGray })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    LegendItem(color = Color(0xFF1B2B4B), label = "Sunday Service")
                    LegendItem(color = Color(0xFFE07A5F), label = "Special/Guest")
                    LegendItem(color = Color(0xFF81B29A), label = "Conference")
                }
            }
            item { Text(text = "Pipeline for ${dateFormatter.format(Date(selectedDateMs))}", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B2B4B)) }
            if (selectedDayEvents.isEmpty()) {
                item { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(text = "No preaching engagements on this date.", fontSize = 13.sp, color = Color.Gray, fontFamily = FontFamily.Serif) } } }
            } else {
                items(selectedDayEvents) { event -> SermonEngagementCard(event = event, onClick = { selectedEventForDetail = event }, onDelete = { viewModel.deleteEvent(context, event) }) }
            }
            item { Text(text = "Upcoming Placements", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B2B4B)) }
            if (upcomingEvents.isEmpty()) {
                item { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Text(text = "No upcoming engagements. Tap + to schedule.", modifier = Modifier.padding(24.dp), fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center) } }
            } else {
                items(upcomingEvents) { event -> SermonEngagementCard(event = event, onClick = { selectedEventForDetail = event }, onDelete = { viewModel.deleteEvent(context, event) }) }
            }
        }
    }

    selectedEventForDetail?.let { activeEvent ->
        val listAll = upcomingEvents.ifEmpty { listOf(activeEvent) }
        val startIndex = listAll.indexOfFirst { it.id == activeEvent.id }.coerceAtLeast(0)
        EngagementDetailDialog(
            eventsList = listAll,
            initialIndex = startIndex,
            allFiles = allFiles,
            onDismiss = { selectedEventForDetail = null }
        )
    }

    if (showAddEventSheet) {
        ModalBottomSheet(onDismissRequest = { showAddEventSheet = false }, containerColor = Color.White) {
            var eventName by remember { mutableStateOf("") }
            var sermonTitle by remember { mutableStateOf("") }
            var eventType by remember { mutableStateOf("Sunday Service") }
            var venueName by remember { mutableStateOf("Main Sanctuary") }
            var description by remember { mutableStateOf("") }
            var coSpeakers by remember { mutableStateOf("") }
            var travelMins by remember { mutableStateOf(30) }
            var multiDayDays by remember { mutableStateOf(1) }
            var attachmentUris = remember { mutableStateListOf<String>() }

            Column(modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Schedule Sermon Event", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1B2B4B))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                OutlinedTextField(value = eventName, onValueChange = { eventName = it }, label = { Text("Event Title") }, modifier = Modifier.fillMaxWidth().keyboardAware(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = sermonTitle, onValueChange = { sermonTitle = it }, label = { Text("Sermon Topic") }, modifier = Modifier.fillMaxWidth().keyboardAware(), shape = RoundedCornerShape(12.dp))
                
                Text("Attachments", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                attachmentUris.forEach { uri -> Text(uri, fontSize = 11.sp, color = Color.Gray) }
                Button(onClick = { /* In real app, launch file picker */ attachmentUris.add("sample_note_id") }) { Text("Add Attachment") }

                Button(
                    onClick = {
                        val durationMs = (multiDayDays - 1) * 24 * 60 * 60 * 1000L
                        val newEvent = SermonCalendarEntity(
                            sermonId = UUID.randomUUID().toString(),
                            eventName = eventName.ifBlank { "Sunday Service" },
                            sermonTitle = sermonTitle.ifBlank { "Sunday Sermon" },
                            scheduledDateMs = selectedDateMs,
                            endDateMs = selectedDateMs + durationMs,
                            eventType = eventType,
                            venueName = venueName,
                            coSpeakersCount = if (coSpeakers.isBlank()) 0 else coSpeakers.split(",").size,
                            coSpeakersNamesJson = coSpeakers.ifBlank { null },
                            description = description,
                            notes = "",
                            travelMinutes = travelMins,
                            attachmentUrisJson = attachmentUris.joinToString("|")
                        )
                        viewModel.scheduleEvent(context, newEvent)
                        showAddEventSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                ) { Text("Schedule Event", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngagementDetailDialog(
    eventsList: List<SermonCalendarEntity>,
    initialIndex: Int,
    allFiles: List<ShepherdFile>,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { eventsList.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(20.dp).clip(RoundedCornerShape(24.dp)),
        content = {
            Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1B2B4B)) } }
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                        val event = eventsList[index]
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = event.sermonTitle, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = Color(0xFF1B2B4B))
                            Text(text = event.description ?: "Ready outline.", fontSize = 14.sp, color = Color(0xFF1B2B4B), lineHeight = 20.sp)
                            
                            val attachmentCount = event.attachmentUrisJson?.split("|")?.filter { it.isNotBlank() }?.size ?: 0
                            Text("Attachments: $attachmentCount", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1B2B4B))
    }
}

@Composable
fun SermonEngagementCard(event: SermonCalendarEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(getColorForEventType(event.eventType).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = getColorForEventType(event.eventType)) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.sermonTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B2B4B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = formatter.format(Date(event.scheduledDateMs)), fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.LightGray) }
        }
    }
}

private fun isSameDay(timeMs1: Long, timeMs2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timeMs1 }; val cal2 = Calendar.getInstance().apply { timeInMillis = timeMs2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun getEventsForDay(dayCal: Calendar, events: List<SermonCalendarEntity>): List<Pair<SermonCalendarEntity, String>> {
    val cal = dayCal.clone() as Calendar
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startMs = cal.timeInMillis
    val endMs = startMs + 86400000
    return events.filter { it.scheduledDateMs < endMs && it.endDateMs >= startMs }.map { Pair(it, if (it.endDateMs - it.scheduledDateMs < 86400000) "single" else "start") }
}

private fun getColorForEventType(type: String): Color = when (type) { "Sunday Service" -> Color(0xFF1B2B4B); "Special Engagement" -> Color(0xFFE07A5F); "Conference" -> Color(0xFF81B29A); else -> Color(0xFF1B2B4B) }
