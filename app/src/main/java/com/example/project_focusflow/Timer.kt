package com.example.project_focusflow

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ConfirmAction {
    PAUSE, RESET
}

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

    // Safety: never allow zero-length timer
    if (baseSeconds <= 0) {
        baseSeconds = 60
        remaining = baseSeconds
        knobAngle = (1f / 60f) * 360f
    }

    // Dialog state
    var showConfirm by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // Block system back while timer is running (focus mode)
    BackHandler(enabled = running) {
        // consume back press
    }

    val context = LocalContext.current
    val db = remember {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "focus_db"
        ).fallbackToDestructiveMigration().build()
    }
    val dao = db.focusSessionDao()

    var streakToday by remember { mutableStateOf(false) }

    // Load streak for today
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val s = dao.getStreak(today)
            streakToday = (s?.count ?: 0) > 0
        }
    }

    // Sensor events
    LaunchedEffect(Unit) {
        SensorEvents.onShake = { running = false }
        SensorEvents.onBluetoothConnected = { running = true }
        SensorEvents.onBluetoothDisconnected = { running = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            SensorEvents.onShake = null
            SensorEvents.onBluetoothConnected = null
            SensorEvents.onBluetoothDisconnected = null
        }
    }

    // Timer loop
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect

        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }

        if (running && remaining <= 0) {
            running = false

            val sessionMinutes = baseSeconds / 60

            // Save session + streak
            withContext(Dispatchers.IO) {
                dao.insert(
                    FocusSession(
                        durationMinutes = sessionMinutes,
                        completedAt = System.currentTimeMillis()
                    )
                )

                val minutesToday = dao.getMinutesToday() ?: 0
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                if (minutesToday >= 25) {
                    dao.setStreak(
                        DailyStreak(
                            date = today,
                            count = 1
                        )
                    )
                    streakToday = true
                }
            }

            // Vibrate
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        400,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(400)
            }

            // Notification
            showSessionFinishedNotification(context, sessionMinutes)

            // Navigate to summary
            val intent = android.content.Intent(context, SummaryActivity::class.java).apply {
                putExtra("SESSION_MINUTES", sessionMinutes)
            }
            context.startActivity(intent)
        }
    }

    val formatted = "%02d:%02d".format(remaining / 60, remaining % 60)

    Box(modifier = Modifier.fillMaxSize()) {

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: title + streak
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (streakToday) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔥", fontSize = 22.sp)
                }
            }

            // Right: dark mode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dark),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = darkTheme,
                    onCheckedChange = onDarkThemeChange
                )
            }
        }

        // Center content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Dial(
                knobAngle = knobAngle,
                onKnobAngleChange = { angle ->
                    if (!running) {
                        knobAngle = angle
                        val mins = ((angle / 360f) * 60).roundToInt().coerceIn(1, 60)
                        baseSeconds = mins * 60
                        remaining = baseSeconds
                    }
                },
                remainingTimeFormatted = formatted,
                running = running
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row {
                Button(onClick = { running = true }, enabled = !running && remaining > 0) {
                    Text(stringResource(R.string.start))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        confirmAction = ConfirmAction.PAUSE
                        showConfirm = true
                    },
                    enabled = running
                ) {
                    Text(stringResource(R.string.pause))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        confirmAction = ConfirmAction.RESET
                        showConfirm = true
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBack,
                enabled = !running
            ) {
                Text(stringResource(R.string.back))
            }

            if (running) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Focus mode is ON. Pause or reset to exit.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Confirmation dialog
        if (showConfirm && confirmAction != null) {
            val message = when (confirmAction) {
                ConfirmAction.PAUSE ->
                    "Are you sure you want to pause your focus session?"
                ConfirmAction.RESET ->
                    "Are you sure you want to reset the timer?"
                null -> ""
            }

            AlertDialog(
                onDismissRequest = {
                    showConfirm = false
                    confirmAction = null
                },
                title = { Text("Confirm") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (confirmAction) {
                                ConfirmAction.PAUSE -> running = false
                                ConfirmAction.RESET -> {
                                    running = false
                                    baseSeconds = startMinutes * 60
                                    remaining = baseSeconds
                                    knobAngle = (startMinutes / 60f) * 360f
                                }
                                null -> {}
                            }
                            showConfirm = false
                            confirmAction = null
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showConfirm = false
                            confirmAction = null
                        }
                    ) {
                        Text("No")
                    }
                }
            )
        }
    }
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
                detectDragGestures(
                    onDragStart = { offset ->
                        if (running) return@detectDragGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        val normalized = (deg + 450f) % 360f
                        onKnobAngleChange(normalized)
                    },
                    onDrag = { change, _ ->
                        if (running) return@detectDragGestures
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
