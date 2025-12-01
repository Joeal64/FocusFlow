package com.example.project_focusflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WeeklyStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember {
        Room.databaseBuilder(context, AppDatabase::class.java, "focus_db")
            .fallbackToDestructiveMigration().build()
    }
    val dao = db.focusSessionDao()

    var weeklyData by remember { mutableStateOf<List<DailyMinutes>>(emptyList()) }
    var weekOffset by remember { mutableStateOf(0) }
    var weekDateRange by remember { mutableStateOf("") }
    val textMeasurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onBackground

    // This effect re-fetches data whenever the weekOffset changes
    LaunchedEffect(weekOffset) {
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            // Move calendar to the correct week based on the offset
            cal.add(Calendar.WEEK_OF_YEAR, -weekOffset)

            // --- FIX: Simplified and Corrected Date Logic ---

            // 1. Find the start and end dates of the target week.
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek) // Go to the first day of the week (e.g., Monday)
            val weekStartDate = cal.time
            cal.add(Calendar.DAY_OF_YEAR, 6) // Go to the last day of the week
            val weekEndDate = cal.time

            // 2. Format the dates for UI display and the database query.
            val weekFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val querySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endDateForQuery = querySdf.format(weekEndDate)

            // Set the date range string for the UI
            weekDateRange = "${weekFormat.format(weekStartDate)} - ${weekFormat.format(weekEndDate)}"

            // 3. Fetch data for the 7-day period ending on our calculated weekEndDate.
            val dbData = dao.getLast7DaysMinutes(endDate = endDateForQuery)

            // 4. Create a complete map for the week to handle days with no data.
            val dayDataMap = mutableMapOf<String, Int>()
            cal.time = weekStartDate // Reset calendar to the start of the week
            for (i in 0..6) {
                dayDataMap[querySdf.format(cal.time)] = 0
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Populate the map with real data from the database
            dbData.forEach { dailyMinutes ->
                if (dayDataMap.containsKey(dailyMinutes.day)) {
                    dayDataMap[dailyMinutes.day] = dailyMinutes.minutes
                }
            }

            // Convert the map to a sorted list for predictable chart drawing
            weeklyData = dayDataMap.entries.map { DailyMinutes(it.key, it.value) }.sortedBy { it.day }
        }
    }

    // --- The rest of the UI is correct and remains unchanged ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Weekly Study Minutes", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Week Navigation UI
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { weekOffset++ }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
                }
                Text(weekDateRange, fontSize = 16.sp)
                IconButton(onClick = { if (weekOffset > 0) weekOffset-- }, enabled = weekOffset > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (weeklyData.isNotEmpty()) {
                val maxMinutes = (weeklyData.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(10)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    val slotWidth = size.width / weeklyData.size
                    val barWidth = slotWidth / 2f
                    val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
                    val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    weeklyData.forEachIndexed { index, entry ->
                        val barHeight = (entry.minutes.toFloat() / maxMinutes) * size.height
                        val slotStart = index * slotWidth
                        val x = slotStart + (slotWidth - barWidth) / 2

                        drawRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(x, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            style = Fill
                        )

                        val minutesLabel = entry.minutes.toString()
                        val minutesLayoutResult = textMeasurer.measure(
                            text = minutesLabel,
                            style = TextStyle(color = textColor, fontSize = 12.sp)
                        )
                        drawText(
                            textLayoutResult = minutesLayoutResult,
                            topLeft = Offset(
                                x = x + (barWidth - minutesLayoutResult.size.width) / 2,
                                y = (size.height - barHeight - minutesLayoutResult.size.height - 5.dp.toPx()).coerceAtLeast(0f)
                            )
                        )

                        val date = dateParser.parse(entry.day)
                        val dayName = date?.let { dayNameFormat.format(it) } ?: ""
                        val dayLayoutResult = textMeasurer.measure(
                            text = dayName,
                            style = TextStyle(color = textColor, fontSize = 12.sp)
                        )
                        drawText(
                            textLayoutResult = dayLayoutResult,
                            topLeft = Offset(
                                x = x + (barWidth - dayLayoutResult.size.width) / 2,
                                y = size.height + 5.dp.toPx()
                            )
                        )
                    }
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text("Back")
        }
    }
}
