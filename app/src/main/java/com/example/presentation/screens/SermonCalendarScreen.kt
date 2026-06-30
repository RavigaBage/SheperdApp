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
import com.example.presentation.components.MinistryBottomBar
import com.example.presentation.viewmodel.ShepherdViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    var showAddEventSheet by remember { mutableStateOf(false) }
    var selectedEventForDetail by remember { mutableStateOf<SermonCalendarEntity?>(null) }

    // Calendar month pagination state
    var currentMonthCal by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateMs by remember { mutableStateOf(Calendar.getInstance().timeInMillis) }

    val monthYearFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    LaunchedEffect(selectedDateMs) {
        viewModel.onDaySelected(selectedDateMs)
    }

    // Days in Month Grid calculation
    val daysInGrid = remember(currentMonthCal) {
        val grid = mutableListOf<Calendar>()
        val cal = currentMonthCal.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val leadingEmptyCount = firstDayOfWeek - Calendar.SUNDAY

        // Pad previous month days
        val prevMonthCal = cal.clone() as Calendar
        prevMonthCal.add(Calendar.MONTH, -1)
        val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 0 until leadingEmptyCount) {
            val padCal = prevMonthCal.clone() as Calendar
            padCal.set(Calendar.DAY_OF_MONTH, daysInPrevMonth - leadingEmptyCount + i + 1)
            grid.add(padCal)
        }

        // Current month days
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, i)
            grid.add(dayCal)
        }

        // Pad trailing days to complete full row of weeks
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
                title = { 
                    Text(
                        "Preaching Pipeline", 
                        fontFamily = FontFamily.Serif, 
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B2B4B)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B2B4B))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddEventSheet = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Schedule Event", tint = Color(0xFF1B2B4B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFAF9F6))
            )
        },
        bottomBar = {
            MinistryBottomBar(
                currentRoute = "sermon_calendar",
                onNavigate = onNavigate
            )
        },
        containerColor = Color(0xFFFAF9F6) // Warm light background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Month Header with Prev/Next buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = monthYearFormatter.format(currentMonthCal.time),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFF1B2B4B)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            val next = currentMonthCal.clone() as Calendar
                            next.add(Calendar.MONTH, -1)
                            currentMonthCal = next
                        }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month", tint = Color(0xFF1B2B4B))
                        }
                        IconButton(onClick = {
                            val next = currentMonthCal.clone() as Calendar
                            next.add(Calendar.MONTH, 1)
                            currentMonthCal = next
                        }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month", tint = Color(0xFF1B2B4B))
                        }
                    }
                }
            }

            // Weekday Headers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                    weekdays.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Month Grid Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rows = daysInGrid.chunked(7)
                        rows.forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { dayCal ->
                                    val dayMs = dayCal.timeInMillis
                                    val isSelected = isSameDay(dayMs, selectedDateMs)
                                    val isCurrentMonth = dayCal.get(Calendar.MONTH) == currentMonthCal.get(Calendar.MONTH)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedDateMs = dayMs
                                                // Trigger the Add Event sheet if clicked
                                                showAddEventSheet = true
                                            }
                                            .background(
                                                if (isSelected) Color(0xFF1B2B4B).copy(alpha = 0.08f)
                                                else Color.Transparent
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Draw connected range pills if event matches
                                        val dayEvents = getEventsForDay(dayCal, upcomingEvents)
                                        if (dayEvents.isNotEmpty()) {
                                            val (event, pillType) = dayEvents.first()
                                            val color = getColorForEventType(event.eventType)

                                            when (pillType) {
                                                "single" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                }
                                                "start" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(26.dp)
                                                            .padding(start = 2.dp)
                                                            .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                                                            .background(color)
                                                    )
                                                }
                                                "middle" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(26.dp)
                                                            .background(color)
                                                    )
                                                }
                                                "end" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(26.dp)
                                                            .padding(end = 2.dp)
                                                            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                                                            .background(color)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "${dayCal.get(Calendar.DAY_OF_MONTH)}",
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                dayEvents.isNotEmpty() -> Color.White
                                                isCurrentMonth -> Color(0xFF1B2B4B)
                                                else -> Color.LightGray
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Legend Indicator
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = Color(0xFF1B2B4B), label = "Sunday Service")
                    LegendItem(color = Color(0xFFE07A5F), label = "Special/Guest")
                    LegendItem(color = Color(0xFF81B29A), label = "Conference")
                }
            }

            // Scheduled for day header
            item {
                Text(
                    text = "Pipeline for ${dateFormatter.format(Date(selectedDateMs))}",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B2B4B)
                )
            }

            // Selected Day Events List
            if (selectedDayEvents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No preaching engagements on this date.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            } else {
                items(selectedDayEvents) { event ->
                    SermonEngagementCard(
                        event = event,
                        onClick = { selectedEventForDetail = event },
                        onDelete = { viewModel.deleteEvent(context, event) }
                    )
                }
            }

            // Upcoming Pipeline header
            item {
                Text(
                    text = "Upcoming Placements",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B2B4B)
                )
            }

            if (upcomingEvents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Text(
                            text = "No upcoming engagements. Tap + to schedule.",
                            modifier = Modifier.padding(24.dp),
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(upcomingEvents) { event ->
                    SermonEngagementCard(
                        event = event,
                        onClick = { selectedEventForDetail = event },
                        onDelete = { viewModel.deleteEvent(context, event) }
                    )
                }
            }
        }
    }

    // DETAIL VIEW PAGED OVERLAY
    selectedEventForDetail?.let { activeEvent ->
        val listAll = upcomingEvents.ifEmpty { listOf(activeEvent) }
        val startIndex = listAll.indexOfFirst { it.id == activeEvent.id }.coerceAtLeast(0)
        
        EngagementDetailDialog(
            eventsList = listAll,
            initialIndex = startIndex,
            onDismiss = { selectedEventForDetail = null },
            onOpenPreachMode = { sermonId, title ->
                viewModel.activeViewerSermonId = sermonId
                viewModel.activeViewerFilePath = ""
                viewModel.activeViewerTitle = title
                viewModel.livePreachDurationMinutes = 30
                onNavigate("ai_preach_mode")
                selectedEventForDetail = null
            }
        )
    }

    // SCHEDULE NEW EVENT SHEET
    if (showAddEventSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddEventSheet = false },
            containerColor = Color.White
        ) {
            var eventName by remember { mutableStateOf("") }
            var sermonTitle by remember { mutableStateOf("") }
            var eventType by remember { mutableStateOf("Sunday Service") }
            var venueName by remember { mutableStateOf("Main Sanctuary") }
            var description by remember { mutableStateOf("") }
            var coSpeakers by remember { mutableStateOf("") }
            var notes by remember { mutableStateOf("") }
            var travelMins by remember { mutableStateOf(30) }
            var multiDayDays by remember { mutableStateOf(1) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Schedule Sermon Event",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1B2B4B)
                )

                HorizontalDivider(color = Color(0xFFF0F0F0))

                OutlinedTextField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("Event Title (e.g. Sunday Morning, Easter Sunday)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = sermonTitle,
                    onValueChange = { sermonTitle = it },
                    label = { Text("Sermon Topic / Draft Outline") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Event Type Legend presets
                Text("Event Color Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Sunday Service", "Special Engagement", "Conference").forEach { type ->
                        val isSel = eventType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) getColorForEventType(type) else Color(0xFFFAF9F6))
                                .clickable { eventType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Color(0xFF1B2B4B)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = venueName,
                        onValueChange = { venueName = it },
                        label = { Text("Venue / Location") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = coSpeakers,
                        onValueChange = { coSpeakers = it },
                        label = { Text("Co-Speakers (comma separated)") },
                        placeholder = { Text("e.g. Pastor Caleb, Dr. Adams") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duration Days", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { multiDayDays = (multiDayDays - 1).coerceAtLeast(1) }) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                            }
                            Text("$multiDayDays day(s)", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { multiDayDays++ }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Transit Minutes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { travelMins = (travelMins - 5).coerceAtLeast(5) }) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                            }
                            Text("$travelMins mins", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { travelMins += 5 }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Brief Focus Description") },
                    placeholder = { Text("Scripture text, audience theme focus") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        val durationMs = (multiDayDays - 1) * 24 * 60 * 60 * 1000L
                        val newEvent = SermonCalendarEntity(
                            eventName = eventName.ifBlank { "Sunday Morning Service" },
                            sermonTitle = sermonTitle.ifBlank { "Unfolding Grace" },
                            scheduledDateMs = selectedDateMs,
                            endDateMs = selectedDateMs + durationMs,
                            eventType = eventType,
                            venueName = venueName,
                            coSpeakersCount = if (coSpeakers.isBlank()) 0 else coSpeakers.split(",").size,
                            coSpeakersNamesJson = coSpeakers.ifBlank { null },
                            description = description.ifBlank { "Study sermon of scripture focus and outreach guidance." },
                            notes = notes.ifBlank { "Study sermon notes" },
                            scriptureFocus = "Psalm 23 / Romans 8",
                            intendedAudience = "General Congregation",
                            travelMinutes = travelMins,
                            sermonId = UUID.randomUUID().toString()
                        )
                        viewModel.scheduleEvent(context, newEvent)
                        showAddEventSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Schedule and Auto-Set 3 Reminders", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1B2B4B))
    }
}

@Composable
fun SermonEngagementCard(
    event: SermonCalendarEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = formatter.format(Date(event.scheduledDateMs))
    val isMultiDay = event.endDateMs > event.scheduledDateMs
    val dateText = if (isMultiDay) "$formattedDate - ${formatter.format(Date(event.endDateMs))}" else formattedDate

    // Urgency check (within 4 hours and 30 minutes)
    val now = System.currentTimeMillis()
    val timeDiffMs = event.scheduledDateMs - now
    val isWithin4Hours = timeDiffMs in 0L..(4 * 60 * 60 * 1000L)
    val isWithin30Mins = timeDiffMs in 0L..(30 * 60 * 1000L)

    val indicatorColor = when {
        isWithin30Mins -> Color(0xFFE07A5F) // Urgency Orange
        isWithin4Hours -> Color(0xFF1B2B4B) // Alert Navy
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (indicatorColor != Color.Transparent) {
                        // Draw subtle left border
                        drawRect(
                            color = indicatorColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(12f, size.height)
                        )
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getColorForEventType(event.eventType).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (event.eventType) {
                        "Conference" -> Icons.Default.Groups
                        "Special Engagement" -> Icons.Default.Star
                        else -> Icons.Default.MenuBook
                    },
                    contentDescription = null,
                    tint = getColorForEventType(event.eventType)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.sermonTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1B2B4B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Event: ${event.eventName ?: "Standing Service"} • ${event.venueName ?: "Main Sanctuary"}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dateText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = getColorForEventType(event.eventType)
                    )
                    if (event.coSpeakersCount > 0) {
                        Text(
                            text = "• ${event.coSpeakersCount} Co-speaker(s)",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Prep status indicator ring inside the card
            PrepStatusMiniBadge(notes = event.notesReady, scriptures = event.scripturePulled, slides = event.slidesBuilt)

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete event", tint = Color.LightGray)
            }
        }
    }
}

@Composable
fun PrepStatusMiniBadge(notes: Boolean, scriptures: Boolean, slides: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        PrepStatusDot(active = notes, label = "Notes")
        PrepStatusDot(active = scriptures, label = "Scriptures")
        PrepStatusDot(active = slides, label = "Slides")
    }
}

@Composable
fun PrepStatusDot(active: Boolean, label: String) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF81B29A) else Color.LightGray)
    )
}

// PREMIUM SWIPEABLE DETAIL PAGE VIEW DIALOG
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngagementDetailDialog(
    eventsList: List<SermonCalendarEntity>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onOpenPreachMode: (String, String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { eventsList.size }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(20.dp)
            .clip(RoundedCornerShape(24.dp)),
        content = {
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1B2B4B))
                        }
                    }

                    // Swipeable horizontal view block
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { index ->
                        val event = eventsList[index]
                        
                        // Parse co speakers initials
                        val namesList = remember(event.coSpeakersNamesJson) {
                            event.coSpeakersNamesJson?.split(",")?.map { it.trim() } ?: emptyList()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Hero Banner Visual at Top
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(getColorForEventType(event.eventType), Color(0xFF1B2B4B))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.LocalLibrary,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = event.eventType.uppercase(),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            // Sermon Topic Title
                            Text(
                                text = event.sermonTitle,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Serif,
                                color = Color(0xFF1B2B4B)
                            )

                            // Date range badge
                            val sdf = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
                            val dateStr = if (event.endDateMs > event.scheduledDateMs) {
                                "${sdf.format(Date(event.scheduledDateMs))} - ${sdf.format(Date(event.endDateMs))}"
                            } else {
                                sdf.format(Date(event.scheduledDateMs))
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(getColorForEventType(event.eventType).copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = dateStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = getColorForEventType(event.eventType)
                                )
                            }

                            // Preparation checklist panel
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF9F6))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Pastoral Readiness Checklist", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2B4B))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        ReadinessItem(active = event.notesReady, label = "Draft notes")
                                        ReadinessItem(active = event.scripturePulled, label = "Scriptures")
                                        ReadinessItem(active = event.slidesBuilt, label = "Slides")
                                    }
                                }
                            }

                            // Description block
                            Text(
                                text = "Topic Focus Outline & Audience",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(
                                text = event.description ?: "Ready outline addressing faith foundations, community growth, and devotional study.",
                                fontSize = 14.sp,
                                color = Color(0xFF1B2B4B),
                                lineHeight = 20.sp
                            )

                            // Co Preachers Avatars
                            if (namesList.isNotEmpty()) {
                                Text(
                                    text = "Co-preachers rotation list",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy((-8).dp) // overlapping
                                ) {
                                    namesList.take(3).forEach { name ->
                                        val initial = name.firstOrNull()?.uppercase() ?: "P"
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE07A5F))
                                                .border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(initial, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (namesList.size > 3) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray)
                                                .border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+${namesList.size - 3}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Smart Leave by indicator
                            val travelMins = event.travelMinutes
                            val leaveByMs = event.scheduledDateMs - (travelMins * 60 * 1000L)
                            val leaveByStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(leaveByMs))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFFC9A84C))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Smart Leave-by Nudge: Leave by $leaveByStr (includes a $travelMins-min drive to ${event.venueName ?: "Sanctuary"})",
                                        fontSize = 12.sp,
                                        color = Color(0xFF1B2B4B),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // One tap open in Preach Mode Button
                            Button(
                                onClick = { onOpenPreachMode(event.sermonId, event.sermonTitle) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B4B))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("One-Tap Launch Preach Mode", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Swipeable Pager Indicator at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${eventsList.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2B4B),
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun ReadinessItem(active: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (active) Color(0xFF81B29A) else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Text(text = label, fontSize = 11.sp, color = Color(0xFF1B2B4B))
    }
}

// Helper utility functions for Calendar Redesign
private fun isSameDay(timeMs1: Long, timeMs2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timeMs1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timeMs2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun getEventsForDay(dayCal: Calendar, events: List<SermonCalendarEntity>): List<Pair<SermonCalendarEntity, String>> {
    val dayStart = dayCal.clone() as Calendar
    dayStart.set(Calendar.HOUR_OF_DAY, 0)
    dayStart.set(Calendar.MINUTE, 0)
    dayStart.set(Calendar.SECOND, 0)
    dayStart.set(Calendar.MILLISECOND, 0)
    val startMs = dayStart.timeInMillis

    val dayEnd = dayStart.clone() as Calendar
    dayEnd.add(Calendar.DAY_OF_MONTH, 1)
    val endMs = dayEnd.timeInMillis

    return events.filter { event ->
        val s = event.scheduledDateMs
        val e = event.endDateMs
        s < endMs && e >= startMs
    }.map { event ->
        val durationMs = event.endDateMs - event.scheduledDateMs
        val isSingle = durationMs < 24 * 60 * 60 * 1000L
        val typeStr = if (isSingle) {
            "single"
        } else {
            val isStart = event.scheduledDateMs >= startMs && event.scheduledDateMs < endMs
            val isEnd = event.endDateMs >= startMs && event.endDateMs < endMs
            when {
                isStart -> "start"
                isEnd -> "end"
                else -> "middle"
            }
        }
        Pair(event, typeStr)
    }
}

private fun getColorForEventType(type: String): Color {
    return when (type) {
        "Sunday Service" -> Color(0xFF1B2B4B) // Navy Slate
        "Special Engagement" -> Color(0xFFE07A5F) // Soft Terracotta
        "Conference" -> Color(0xFF81B29A) // Soft Sage Green
        else -> Color(0xFF1B2B4B)
    }
}
