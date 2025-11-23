package com.example.project_focusflow

import androidx.compose.material3.Switch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun PomodoroTimer(startMinutes: Int, onBack: () -> Unit, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {

    var baseSeconds by remember { mutableStateOf(startMinutes * 60) }
    var remaining by remember { mutableStateOf(baseSeconds) }
    var running by remember { mutableStateOf(false) }

    // knob angle 0..360
    var knobAngle by remember {
        mutableStateOf((startMinutes / 60f) * 360f)
    }
    val context = LocalContext.current

    val db = remember {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "focus_db"
        ).build()
    }

    val dao = db.focusSessionDao()

// listen to shake + bluetooth events
    LaunchedEffect(Unit) {

        SensorEvents.onShake = {
            running = false
        }

        SensorEvents.onBluetoothConnected = {
            running = true
        }

        SensorEvents.onBluetoothDisconnected = {
            running = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SensorEvents.onShake = null
            SensorEvents.onBluetoothConnected = null
            SensorEvents.onBluetoothDisconnected = null
        }
    }


    // Timer countdown loop
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
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

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FocusFlow",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Switch(
                checked = darkTheme,
                onCheckedChange = onDarkThemeChange
            )
        }

        Dial(
            knobAngle = knobAngle,
            onKnobAngleChange = { angle ->
                knobAngle = angle

                val mins = ((angle / 360f) * 60)
                    .roundToInt()
                    .coerceIn(1, 60)

                baseSeconds = mins * 60

                if (!running) {
                    remaining = baseSeconds
                }
            },
            remainingTimeFormatted = formatted
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row {
            Button(
                onClick = { running = true },
                enabled = !running
            ) { Text("Start") }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { running = false },
                enabled = running
            ) { Text("Pause") }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    running = false
                    baseSeconds = startMinutes * 60
                    remaining = baseSeconds
                    knobAngle = (startMinutes / 60f) * 360f
                }
            ) { Text("Reset") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { onBack() }) {
            Text("Back")
        }
    }
}

@Composable
fun Dial(
    knobAngle: Float,
    onKnobAngleChange: (Float) -> Unit,
    remainingTimeFormatted: String
) {
    var dragging by remember { mutableStateOf(false) }
    val textColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(true) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f

                        val r = min(w, h) / 2f
                        val rad = Math.toRadians((knobAngle - 90).toDouble())
                        val kx = cx + r * cos(rad)
                        val ky = cy + r * sin(rad)

                        val dist = hypot(offset.x - kx, offset.y - ky)
                        dragging = dist < 60f
                    },
                    onDrag = { change, _ ->
                        if (!dragging) return@detectDragGestures

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy

                        val rad = atan2(dy, dx)
                        val deg = Math.toDegrees(rad.toDouble()).toFloat()
                        val ang = (deg + 360f) % 360f

                        onKnobAngleChange(ang)
                    },
                    onDragEnd = { dragging = false }
                )
            }
    ) {
        val stroke = 20.dp.toPx()
        val radius = size.minDimension / 2f

        drawArc(
            color = Color.LightGray,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke)
        )

        drawArc(
            color = Color(0xFF4CAF50),
            startAngle = -90f,
            sweepAngle = knobAngle,
            useCenter = false,
            style = Stroke(width = stroke)
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
