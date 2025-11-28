package com.example.project_focusflow

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// The number of minutes required to achieve one streak point.
private const val STREAK_INTERVAL_MINUTES = 25

@Composable
fun PomodoroTimer(
    startMinutes: Int,
    onBack: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    var baseSeconds by remember { mutableStateOf(startMinutes * 60) }
    var remaining by remember { mutableStateOf(baseSeconds) }
    var running by remember { mutableStateOf(false) }
    var knobAngle by remember { mutableStateOf((startMinutes / 60f) * 360f) }

    val context = LocalContext.current
    val db = remember {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "focus_db"
        ).fallbackToDestructiveMigration().build()
    }
    val dao = db.focusSessionDao()

    var streakCount by remember { mutableStateOf(0) }
    var timerCompleted by remember { mutableStateOf(false) }
    var completionCount by remember { mutableStateOf(0) } // Key for re-triggering navigation effect

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // This function fetches the latest streak count from the database.
    val refreshStreakCount = {
        coroutineScope.launch(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            // FIX: This now correctly reads the 'count' property from the DailyStreak object.
            val currentCount = dao.getStreak(today)?.count ?: 0
            withContext(Dispatchers.Main) {
                streakCount = currentCount
            }
        }
    }

    // This effect runs when the screen is first created AND when it becomes visible again.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                refreshStreakCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Set up sensor listeners
        SensorEvents.onShake = { running = false }
        SensorEvents.onBluetoothConnected = { running = true }
        SensorEvents.onBluetoothDisconnected = { running = false }

        // Cleanup function for when the composable leaves the screen
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SensorEvents.onShake = null
            SensorEvents.onBluetoothConnected = null
            SensorEvents.onBluetoothDisconnected = null
        }
    }

    // Main timer countdown logic.
    LaunchedEffect(running) {
        while (true) {
            if (running && remaining > 0) {
                delay(1000)
                remaining--
            } else if (running && remaining == 0) {
                // Timer finished, handle completion logic.
                val sessionMinutes = (baseSeconds / 60).coerceAtLeast(1)

                // The notification function from your teammate
                showSessionFinishedNotification(context, sessionMinutes)

                // Perform database operations on a background thread
                withContext(Dispatchers.IO) {
                    val minutesBeforeSession = dao.getMinutesToday() ?: 0
                    val streaksBeforeSession = minutesBeforeSession / STREAK_INTERVAL_MINUTES

                    dao.insert(FocusSession(durationMinutes = sessionMinutes, completedAt = System.currentTimeMillis()))

                    val minutesAfterSession = dao.getMinutesToday() ?: 0
                    val streaksAfterSession = minutesAfterSession / STREAK_INTERVAL_MINUTES

                    // **Corrected cumulative streak logic**
                    if (streaksAfterSession > streaksBeforeSession) {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        dao.setStreak(DailyStreak(date = today, count = streaksAfterSession))
                    }
                }
                // Stop the timer and trigger the UI update to show the completion screen.
                running = false
                timerCompleted = true
                completionCount++ // This is crucial to re-run the navigation effect
            } else {
                // If not running, idle to prevent a busy-wait loop.
                delay(100)
            }
        }
    }

    val formattedTime = "%02d:%02d".format(remaining / 60, remaining % 60)

    Box(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(modifier = Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                // Use the corrected state variable 'streakCount'
                if (streakCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔥 $streakCount", fontSize = 22.sp)
                }
            }
            Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dark),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
        }

        // This Column switches between the timer UI and the completion screen.
        Column(
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (timerCompleted) {
                // Show this UI after the timer finishes.
                SessionCompleteScreen()

                // This effect handles navigation AFTER a delay.
                LaunchedEffect(completionCount) {
                    if (completionCount > 0) {
                        delay(2000) // Wait for 2 seconds to show the completion screen.

                        // Navigate to Summary.
                        val intent = android.content.Intent(context, SummaryActivity::class.java).apply {
                            putExtra("SESSION_MINUTES", (baseSeconds / 60).coerceAtLeast(1))
                        }
                        context.startActivity(intent)

                        // Reset state for when the user returns.
                        delay(500)
                        timerCompleted = false
                        remaining = baseSeconds
                    }
                }
            } else {
                // Show the standard timer UI.
                TimerDialAndControls(
                    knobAngle = knobAngle,
                    onKnobAngleChange = { angle ->
                        knobAngle = angle
                        val mins = ((angle / 360f) * 60).roundToInt().coerceIn(1, 60)
                        baseSeconds = mins * 60
                        if (!running) remaining = baseSeconds
                    },
                    remainingTimeFormatted = formattedTime,
                    running = running,
                    onStart = { running = true },
                    onPause = { running = false },
                    onReset = {
                        running = false
                        baseSeconds = startMinutes * 60
                        remaining = baseSeconds
                        knobAngle = (startMinutes / 60f) * 360f
                    },
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
fun SessionCompleteScreen() {
    Text("Session Complete!", fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(24.dp))
    CircularProgressIndicator()
    Spacer(modifier = Modifier.height(16.dp))
    Text("Loading summary...")
}

@Composable
fun TimerDialAndControls(
    knobAngle: Float,
    onKnobAngleChange: (Float) -> Unit,
    remainingTimeFormatted: String,
    running: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    Dial(
        knobAngle = knobAngle,
        onKnobAngleChange = onKnobAngleChange,
        remainingTimeFormatted = remainingTimeFormatted,
        running = running
    )
    Spacer(modifier = Modifier.height(40.dp))
    Row {
        Button(onClick = onStart, enabled = !running) { Text(stringResource(R.string.start)) }
        Spacer(modifier = Modifier.width(16.dp))
        Button(onClick = onPause, enabled = running) { Text(stringResource(R.string.pause)) }
        Spacer(modifier = Modifier.width(16.dp))
        Button(onClick = onReset) { Text(stringResource(R.string.reset)) }
    }
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onBack) { Text(stringResource(R.string.back)) }
}

@Composable
fun Dial(
    knobAngle: Float,
    onKnobAngleChange: (Float) -> Unit,
    remainingTimeFormatted: String,
    running: Boolean
) {
    val textColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(running) {
                // Disable dragging while the timer is running.
                if (running) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        val normalized = (deg + 450f) % 360f
                        onKnobAngleChange(normalized)
                    },
                    onDrag = { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        val normalized = (deg + 450f) % 360f
                        onKnobAngleChange(normalized)
                        change.consume()
                    }
                )
            }
    ) {
        val stroke = 20.dp.toPx()
        val radius = min(size.width, size.height) / 2f

        drawArc(
            color = Color.LightGray,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(stroke)
        )

        drawArc(
            color = Color(0xFF4CAF50),
            startAngle = -90f,
            sweepAngle = knobAngle,
            useCenter = false,
            style = Stroke(stroke)
        )

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 90f
            color = textColor.toArgb()
        }

        drawContext.canvas.nativeCanvas.drawText(
            remainingTimeFormatted,
            center.x,
            center.y + paint.textSize / 3,
            paint
        )

        val rad = Math.toRadians((knobAngle - 90).toDouble())
        val kx = center.x + radius * cos(rad)
        val ky = center.y + radius * sin(rad)

        drawCircle(
            color = Color.Black,
            radius = 18.dp.toPx(),
            center = Offset(kx.toFloat(), ky.toFloat())
        )
    }
}

