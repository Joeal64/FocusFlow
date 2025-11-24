package com.example.project_focusflow

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import androidx.room.Room

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
        ).build()
    }

    val dao = db.focusSessionDao()

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

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }

        if (remaining == 0 && running) {
            running = false
            val sessionMinutes = baseSeconds / 60
            withContext(Dispatchers.IO) {
                dao.insert(
                    FocusSession(
                        durationMinutes = sessionMinutes,
                        completedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    val formatted = "%02d:%02d".format(remaining / 60, remaining % 60)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Dial(
                knobAngle = knobAngle,
                onKnobAngleChange = { angle ->
                    knobAngle = angle
                    val mins = ((angle / 360f) * 60).roundToInt().coerceIn(1, 60)
                    baseSeconds = mins * 60
                    if (!running) remaining = baseSeconds
                },
                remainingTimeFormatted = formatted,
                running = running
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row {
                Button(onClick = { running = true }, enabled = !running) {
                    Text(stringResource(R.string.start))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = { running = false }, enabled = running) {
                    Text(stringResource(R.string.pause))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        running = false
                        baseSeconds = startMinutes * 60
                        remaining = baseSeconds
                        knobAngle = (startMinutes / 60f) * 360f
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
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
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        val normalized = (deg + 450f) % 360f
                        onKnobAngleChange(normalized)
                    },
                    onDrag = { change, _ ->
                        if (running) return@detectDragGestures
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f
                        val pos = change.position
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        val normalized = (deg + 450f) % 360f
                        onKnobAngleChange(normalized)
                        change.consume()
                    }
                )
            }
    ) {
        val stroke = 20.dp.toPx()
        val w = size.width
        val h = size.height
        val radius = min(w, h) / 2f

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
