package com.example.project_focusflow

import android.os.Build
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TIMER_LOG_TAG = "FocusFlowTimer"

private enum class ConfirmAction {
    PAUSE, RESET
}

@Composable
fun PomodoroTimer(
    startMinutes: Int,
    onBack: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    streakCount: Int,
    onTimerFinish: (sessionMinutes: Int) -> Unit
) {
    var baseSeconds by remember { mutableStateOf(startMinutes * 60) }
    var remaining by remember { mutableStateOf(baseSeconds) }
    var running by remember { mutableStateOf(false) }
    var knobAngle by remember { mutableStateOf((startMinutes / 60f) * 360f) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (baseSeconds <= 0) {
        baseSeconds = 60
        remaining = baseSeconds
        knobAngle = (1f / 60f) * 360f
    }

    var showConfirm by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var wasInterrupted by remember { mutableStateOf(false) }

    BackHandler(enabled = running) { /* consume back press */ }

    LaunchedEffect(Unit) {
        SensorEvents.onShake = { running = false }
        SensorEvents.onBluetoothConnected = { running = true }
        SensorEvents.onBluetoothDisconnected = { running = false }
        AppLifecycleEvents.onAppBackgrounded = {
            if (running) {
                running = false
                wasInterrupted = true
            }
        }
        AppLifecycleEvents.onAppForegrounded = {}
    }

    DisposableEffect(Unit) {
        onDispose {
            SensorEvents.onShake = null
            SensorEvents.onBluetoothConnected = null
            SensorEvents.onBluetoothDisconnected = null
            AppLifecycleEvents.onAppBackgrounded = null
            AppLifecycleEvents.onAppForegrounded = null
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val wasTimerRunning = running
        Log.d(TIMER_LOG_TAG, "Timer started. Remaining: $remaining seconds.")
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (wasTimerRunning && remaining <= 0) {
            Log.d(TIMER_LOG_TAG, "Timer finished in Composable. Calling onTimerFinish.")
            // --- FIX: Set running to false BEFORE calling the callback ---
            // This immediately disables the BackHandler and exits focus mode.
            running = false
            onTimerFinish(baseSeconds / 60)
        }
    }

    val formatted = "%02d:%02d".format(remaining / 60, remaining % 60)

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (streakCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔥 $streakCount", fontSize = 22.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (darkTheme) stringResource(R.string.dark_mode) else stringResource(R.string.light_mode),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = darkTheme,
                    onCheckedChange = onDarkThemeChange
                )
            }
        }
        if (isLandscape) {
            // landscape
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Drag to put time",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
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
                        running = running,
                        remainingSeconds = remaining,
                        totalSeconds = baseSeconds
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row {
                        Button(onClick = { running = true }, enabled = !running && remaining > 0) {
                            Text(stringResource(R.string.start))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { confirmAction = ConfirmAction.PAUSE; showConfirm = true },
                            enabled = running
                        ) {
                            Text(stringResource(R.string.pause))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { confirmAction = ConfirmAction.RESET; showConfirm = true }
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = onBack, enabled = !running) {
                        Text(stringResource(R.string.back))
                    }

                    if (running) {
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
            // portrait
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
                    running = running,
                    remainingSeconds = remaining,
                    totalSeconds = baseSeconds
                )
                Spacer(modifier = Modifier.height(40.dp))
                Row {
                    Button(onClick = { running = true }, enabled = !running && remaining > 0) {
                        Text(stringResource(R.string.start))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { confirmAction = ConfirmAction.PAUSE; showConfirm = true }, enabled = running) {
                        Text(stringResource(R.string.pause))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { confirmAction = ConfirmAction.RESET; showConfirm = true }) {
                        Text(stringResource(R.string.reset))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, enabled = !running) {
                    Text(stringResource(R.string.back))
                }
                if (running) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.focus_mode_on_message),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }


        if (showConfirm && confirmAction != null) {
            AlertDialog(
                onDismissRequest = { showConfirm = false; confirmAction = null },
                title = { Text(stringResource(R.string.confirm_title)) },
                text = { Text(if (confirmAction == ConfirmAction.PAUSE) stringResource(R.string.confirm_pause_message) else stringResource(R.string.confirm_reset_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (confirmAction) {
                                ConfirmAction.PAUSE -> running = false
                                ConfirmAction.RESET -> {
                                    running = false
                                    remaining = baseSeconds
                                    knobAngle = (baseSeconds / 60f / 60f) * 360f
                                }
                                null -> {}
                            }
                            showConfirm = false
                            confirmAction = null
                        }
                    ) { Text(stringResource(R.string.yes)) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false; confirmAction = null }) { Text(stringResource(R.string.no)) }
                }
            )
        }
        if (wasInterrupted && !running && !showConfirm) {
            AlertDialog(
                onDismissRequest = { /* force choice */ },
                title = { Text(stringResource(R.string.focus_interrupted_title)) },
                text = { Text(stringResource(R.string.focus_interrupted_message)) },
                confirmButton = { TextButton(onClick = { wasInterrupted = false; running = true }) { Text(stringResource(R.string.continue_label)) } },
                dismissButton = {
                    TextButton(
                        onClick = {
                            wasInterrupted = false
                            running = false
                            remaining = baseSeconds
                            knobAngle = (baseSeconds / 60f / 60f) * 360f
                        }
                    ) { Text(stringResource(R.string.reset)) }
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
    running: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val targetAngle = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds.toFloat()) * 360f else 0f
    val displayAngle by animateFloatAsState(
        targetValue = if (running) targetAngle else knobAngle,
        label = "DialAngleAnimation"
    )
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
        val strokeWidth = 20.dp.toPx()
        drawArc(
            color = Color.LightGray,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth)
        )
        drawArc(
            color = Color(0xFF4CAF50),
            startAngle = -90f,
            sweepAngle = displayAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
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
