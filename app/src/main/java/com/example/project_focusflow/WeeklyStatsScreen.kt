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

// This screen shows a weekly bar chart for study minutes
@Composable
fun WeeklyStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Create the database
    val db = remember {
        Room.databaseBuilder(context, AppDatabase::class.java, "focus_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    // Get DAO from the database
    val dao = db.focusSessionDao()

    // List of 7 days, each with its total minutes
    var weeklyData by remember { mutableStateOf<List<DailyMinutes>>(emptyList()) }

    // How many weeks back we are
    var weekOffset by remember { mutableStateOf(0) }

    var weekDateRange by remember { mutableStateOf("") }

    val textMeasurer = rememberTextMeasurer()

    // Use theme's text color
    val textColor = MaterialTheme.colorScheme.onBackground

    // Use theme's primary color for the bar chart
    val barColor = MaterialTheme.colorScheme.primary

    // When weekOffset changes, we load new data from the database
    LaunchedEffect(weekOffset) {
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance()

            // Move calendar to the correct week
            cal.add(Calendar.WEEK_OF_YEAR, -weekOffset)

            // Go to first day of that week
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            val weekStartDate = cal.time

            // Go to last day of that week
            cal.add(Calendar.DAY_OF_YEAR, 6)
            val weekEndDate = cal.time

            // Format for showing dates on the screen
            val weekFormat = SimpleDateFormat("MMM d", Locale.getDefault())

            // Format for querying DB
            val querySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endDateForQuery = querySdf.format(weekEndDate)

            weekDateRange = "${weekFormat.format(weekStartDate)} - ${weekFormat.format(weekEndDate)}"

            // Get last 7 days of minutes from DB
            val dbData = dao.getLast7DaysMinutes(endDate = endDateForQuery)

            // Prepare a map for 7 days in this week, all starting at 0 minutes
            val dayDataMap = mutableMapOf<String, Int>()
            cal.time = weekStartDate
            for (i in 0..6) {
                // Add the date (yyyy-MM-dd) with 0 minutes
                dayDataMap[querySdf.format(cal.time)] = 0
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Fill map with real minutes from DB if available for that day
            dbData.forEach { dailyMinutes ->
                if (dayDataMap.containsKey(dailyMinutes.day)) {
                    dayDataMap[dailyMinutes.day] = dailyMinutes.minutes
                }
            }

            // Convert map back to a sorted list by day (so bars are in correct order)
            weeklyData = dayDataMap.entries
                .map { DailyMinutes(it.key, it.value) }
                .sortedBy { it.day }
        }
    }

    // Main container for the screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Column for title, week selector, and chart
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen title
            Text("Weekly Study Minutes", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Row with back/forward week buttons and date range text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Go to previous week
                IconButton(onClick = { weekOffset++ }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Week"
                    )
                }

                // Show current week range
                Text(weekDateRange, fontSize = 16.sp)

                // Go to next week
                IconButton(
                    onClick = { if (weekOffset > 0) weekOffset-- },
                    enabled = weekOffset > 0
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Week"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Only draw the chart if we have data
            if (weeklyData.isNotEmpty()) {
                // Find the maximum minutes in the week to scale the bar height
                val maxMinutes = (weeklyData.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(10)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    // Width of each day's slot
                    val slotWidth = size.width / weeklyData.size

                    // Width of the bar within that slot
                    val barWidth = slotWidth / 2f

                    // Formatters for day name and parsing DB date
                    val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
                    val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    // Loop over each day and draw its bar and labels
                    weeklyData.forEachIndexed { index, entry ->
                        // Bar height relative to maxMinutes
                        val barHeight = (entry.minutes.toFloat() / maxMinutes) * size.height

                        // Start x position of the slot
                        val slotStart = index * slotWidth

                        // Center the bar inside its slot
                        val x = slotStart + (slotWidth - barWidth) / 2

                        // Draw the bar rectangle
                        drawRect(
                            color = barColor, // use theme primary color
                            topLeft = Offset(x, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            style = Fill
                        )

                        // Draw minutes value above bar
                        val minutesLabel = entry.minutes.toString()
                        val minutesLayoutResult = textMeasurer.measure(
                            text = minutesLabel,
                            style = TextStyle(
                                color = textColor,
                                fontSize = 12.sp
                            )
                        )

                        // Center text horizontally above the bar,
                        // and keep it at least within the Canvas top
                        drawText(
                            textLayoutResult = minutesLayoutResult,
                            topLeft = Offset(
                                x = x + (barWidth - minutesLayoutResult.size.width) / 2,
                                y = (size.height - barHeight - minutesLayoutResult.size.height - 5.dp.toPx())
                                    .coerceAtLeast(0f)
                            )
                        )

                        //  Draw day name under the bar
                        val date = dateParser.parse(entry.day)
                        val dayName = date?.let { dayNameFormat.format(it) } ?: ""
                        val dayLayoutResult = textMeasurer.measure(
                            text = dayName,
                            style = TextStyle(
                                color = textColor,
                                fontSize = 12.sp
                            )
                        )

                        // Draw day name under the chart
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

        // Back button pinned at bottom center of the screen
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text("Back")
        }
    }
}
