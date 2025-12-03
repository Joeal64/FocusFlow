package com.example.project_focusflow

import android.content.res.Configuration
import android.graphics.Paint
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Log tag just for this timer
private const val TIMER_LOG_TAG = "FocusFlowTimer"

// What kind of action we are confirming in the dialog
private enum class ConfirmAction {
    PAUSE, RESET
}

@Composable
fun PomodoroTimer(
    startMinutes: Int,                         // initial minutes
    onBack: () -> Unit,                        // called when user presses Back
    darkTheme: Boolean,                        // current theme state
    onDarkThemeChange: (Boolean) -> Unit,      // toggle dark/light theme
    streakCount: Int,                          // how many streak days user has
    onTimerFinish: (sessionMinutes: Int) -> Unit, // called when timer reaches 0
    isTimerRunning: Boolean,                   // current running state
    onTimerRunningChange: (Boolean) -> Unit,   // update running state
    wasInterrupted: Boolean,                   // true if app interrupted focus
    onInterruptionHandled: () -> Unit          // tells parent we handled the interruption
) {
    // Total seconds set for this session
    var baseSeconds by remember { mutableStateOf(startMinutes * 60) }

    // How many seconds are left right now
    var remaining by remember { mutableStateOf(baseSeconds) }

    // Angle of the dial knob (0–360) based on minutes
    var knobAngle by remember { mutableStateOf((startMinutes / 60f) * 360f) }

    // Orientation info (portrait or landscape)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Safety: if somehow baseSeconds is 0 or negative, set minimum of 1 minute
    if (baseSeconds <= 0) {
        baseSeconds = 60
        remaining = baseSeconds
        knobAngle = (1f / 60f) * 360f
    }

    // State for confirmation dialog
    var showConfirm by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // Disable system back button while timer is running
    BackHandler(enabled = isTimerRunning) { }

    // Set up sensor events (shake & Bluetooth) when this composable enters
    LaunchedEffect(Unit) {
        // Shaking the phone pauses timer
        SensorEvents.onShake = { onTimerRunningChange(false) }

        // Bluetooth connected: resume timer
        SensorEvents.onBluetoothConnected = { onTimerRunningChange(true) }

        // Bluetooth disconnected: pause timer
        SensorEvents.onBluetoothDisconnected = { onTimerRunningChange(false) }
    }

    // Clean up sensor callbacks when this composable leaves
    DisposableEffect(Unit) {
        onDispose {
            SensorEvents.onShake = null
            SensorEvents.onBluetoothConnected = null
            SensorEvents.onBluetoothDisconnected = null
        }
    }

    // Timer loop: runs only when isTimerRunning is true
    LaunchedEffect(isTimerRunning) {
        if (!isTimerRunning) return@LaunchedEffect

        val wasRunning = isTimerRunning
        while (isTimerRunning && remaining > 0) {
            delay(1000)  // wait 1 second
            remaining--  // decrease remaining time
        }

        // If timer was running and time finished, trigger completion
        if (wasRunning && remaining <= 0) {
            onTimerRunningChange(false)
            // sessionMinutes is based on the original baseSeconds
            onTimerFinish(baseSeconds / 60)
        }
    }

    // Convert seconds to mm:ss format
    val formatted = "%02d:%02d".format(remaining / 60, remaining % 60)

    Box(modifier = Modifier.fillMaxSize()) {

        // Top bar: app name, streak count, theme toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App name + streak
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (streakCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // Small fire icon look using emoji and count
                    Text("🔥 $streakCount", fontSize = 22.sp)
                }
            }

            // Dark / light mode switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (darkTheme)
                        stringResource(R.string.dark_mode)
                    else
                        stringResource(R.string.light_mode),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
        }

        // Layout changes depending on orientation
        if (isLandscape) {
            // LANDSCAPE: dial on the left, buttons on the right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Drag to put time",  // small UX hint
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    // Circular dial for selecting and showing time
                    Dial(
                        knobAngle = knobAngle,
                        onKnobAngleChange = { angle ->
                            // Do not allow changing time while timer is running
                            if (!isTimerRunning) {
                                knobAngle = angle
                                // Convert angle -> minutes (0–360 => 0–60 min)
                                val mins = ((angle / 360f) * 60)
                                    .roundToInt()
                                    .coerceIn(1, 60) // keep between 1 and 60
                                baseSeconds = mins * 60
                                remaining = baseSeconds
                            }
                        },
                        remainingTimeFormatted = formatted,
                        running = isTimerRunning,
                        remainingSeconds = remaining,
                        totalSeconds = baseSeconds
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Buttons and messages
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row {
                        // Start button
                        Button(
                            onClick = { onTimerRunningChange(true) },
                            enabled = !isTimerRunning && remaining > 0
                        ) {
                            Text(stringResource(R.string.start))
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // Pause button opens confirm dialog
                        Button(
                            onClick = {
                                confirmAction = ConfirmAction.PAUSE
                                showConfirm = true
                            },
                            enabled = isTimerRunning
                        ) {
                            Text(stringResource(R.string.pause))
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        // Reset button opens confirm dialog
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

                    // Back button (disabled while running)
                    Button(onClick = onBack, enabled = !isTimerRunning) {
                        Text(stringResource(R.string.back))
                    }

                    // Helper text when focus mode is active
                    if (isTimerRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.focus_mode_on_message),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        } else {
            // PORTRAIT: dial on top, buttons below
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Drag to put time",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Dial that shows and controls the timer
                Dial(
                    knobAngle = knobAngle,
                    onKnobAngleChange = { angle ->
                        if (!isTimerRunning) {
                            knobAngle = angle
                            val mins = ((angle / 360f) * 60)
                                .roundToInt()
                                .coerceIn(1, 60)
                            baseSeconds = mins * 60
                            remaining = baseSeconds
                        }
                    },
                    remainingTimeFormatted = formatted,
                    running = isTimerRunning,
                    remainingSeconds = remaining,
                    totalSeconds = baseSeconds
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Start / pause / reset buttons
                Row {
                    Button(
                        onClick = { onTimerRunningChange(true) },
                        enabled = !isTimerRunning && remaining > 0
                    ) {
                        Text(stringResource(R.string.start))
                    }
                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            confirmAction = ConfirmAction.PAUSE
                            showConfirm = true
                        },
                        enabled = isTimerRunning
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

                Button(onClick = onBack, enabled = !isTimerRunning) {
                    Text(stringResource(R.string.back))
                }

                if (isTimerRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.focus_mode_on_message),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // ======================
        // Confirm dialog (pause / reset)
        // ======================
        if (showConfirm && confirmAction != null) {
            AlertDialog(
                onDismissRequest = {
                    showConfirm = false
                    confirmAction = null
                },
                title = { Text(stringResource(R.string.confirm_title)) },
                text = {
                    Text(
                        if (confirmAction == ConfirmAction.PAUSE)
                            stringResource(R.string.confirm_pause_message)
                        else
                            stringResource(R.string.confirm_reset_message)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (confirmAction) {
                                ConfirmAction.PAUSE -> {
                                    // Just stop the timer
                                    onTimerRunningChange(false)
                                }
                                ConfirmAction.RESET -> {
                                    // Stop and reset time back to baseSeconds
                                    onTimerRunningChange(false)
                                    remaining = baseSeconds
                                    // Convert seconds -> minutes -> angle (each min = 6 degrees)
                                    knobAngle = (baseSeconds / 60f) * 6f
                                }
                                null -> {}
                            }
                            showConfirm = false
                            confirmAction = null
                        }
                    ) { Text(stringResource(R.string.yes)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showConfirm = false
                            confirmAction = null
                        }
                    ) {
                        Text(stringResource(R.string.no))
                    }
                }
            )
        }

        // ======================
        // Interruption dialog (app left while running)
        // ======================
        if (wasInterrupted && !isTimerRunning && !showConfirm) {
            AlertDialog(
                onDismissRequest = { /* do nothing, force user to pick */ },
                title = { Text(stringResource(R.string.focus_interrupted_title)) },
                text = { Text(stringResource(R.string.focus_interrupted_message)) },
                confirmButton = {
                    // Continue from where they left
                    TextButton(onClick = {
                        onInterruptionHandled()
                        onTimerRunningChange(true)
                    }) { Text(stringResource(R.string.continue_label)) }
                },
                dismissButton = {
                    // Reset session after interruption
                    TextButton(
                        onClick = {
                            onInterruptionHandled()
                            onTimerRunningChange(false)
                            remaining = baseSeconds
                            knobAngle = (baseSeconds / 60f) * 6f
                        }
                    ) { Text(stringResource(R.string.reset)) }
                }
            )
        }
    }
}

@Composable
fun Dial(
    knobAngle: Float,                       // angle used when timer is not running
    onKnobAngleChange: (Float) -> Unit,    // callback to update angle
    remainingTimeFormatted: String,        // MM:SS text
    running: Boolean,                      // is timer running
    remainingSeconds: Int,                 // seconds left
    totalSeconds: Int                      // full duration in seconds
) {
    // Get colors from current theme
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    // If timer is running, angle reflects remaining time
    // If not running, angle is the knobAngle (user-selected)
    val targetAngle = if (totalSeconds > 0)
        (remainingSeconds.toFloat() / totalSeconds.toFloat()) * 360f
    else
        0f

    // Smooth animation between angles for nicer visual
    val displayAngle by animateFloatAsState(
        targetValue = if (running) targetAngle else knobAngle,
        label = "DialAngleAnimation"
    )

    Canvas(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(running) {
                // Handle dragging on the canvas to set time
                detectDragGestures(
                    onDragStart = { offset ->
                        // Ignore drags while timer is running
                        if (running) return@detectDragGestures

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy

                        // atan2 gives angle in radians; convert to degrees
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

                        // Normalize so 0 is at top instead of right side
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
                        change.consume() // mark event as handled
                    }
                )
            }
    ) {
        val strokeWidth = 20.dp.toPx()

        // Background circle (full 360°, gray)
        drawArc(
            color = Color.LightGray,
            startAngle = -90f,  // start from top
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth)
        )

        // Progress arc (portion filled, uses primary color)
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = displayAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw time text in the center (MM:SS)
        drawIntoCanvas {
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                // Font size for the timer text
                textSize = 60.sp.toPx()
                color = textColor.toArgb()
            }
            it.nativeCanvas.drawText(
                remainingTimeFormatted,
                center.x,
                center.y + paint.textSize / 3, // small vertical adjustment
                paint
            )
        }

        // Draw small knob circle on the edge of the dial
        val knobRadius = size.width / 2f
        val angleInRadians = Math.toRadians((displayAngle - 90).toDouble())
        val knobX = center.x + knobRadius * cos(angleInRadians)
        val knobY = center.y + knobRadius * sin(angleInRadians)

        drawCircle(
            color = Color.Black,
            radius = 18.dp.toPx(),
            center = Offset(knobX.toFloat(), knobY.toFloat())
        )
    }
}
