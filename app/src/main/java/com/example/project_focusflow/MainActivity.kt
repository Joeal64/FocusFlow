package com.example.project_focusflow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_focusflow.ui.theme.ProjectFocusFlowTheme
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    // Sensor variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // values used to detect a shake
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var shake = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {

            var showTimer by remember { mutableStateOf(false) }
            var minutes by remember { mutableStateOf(25) }
            var darkTheme by remember { mutableStateOf(true) } // default all dark

            ProjectFocusFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showTimer) {
                        PomodoroTimer(
                            startMinutes = minutes,
                            onBack = { showTimer = false }
                        )
                    } else {
                        FocusFlowScreen(
                            onStart = { userMinutes ->
                                minutes = userMinutes
                                showTimer = true
                            },
                            darkTheme = darkTheme,
                            onDarkThemeChange = { darkTheme = it }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Called whenever the accelerometer value changes
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // overall acceleration strength
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            shake = shake * 0.9f + delta  // simple smoothing

            if (shake > 12) {
                // Call whatever the timer registered
                SensorEvents.onShake?.invoke()
            }

            lastAcceleration = currentAcceleration
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // not needed for this case
    }
}

@Composable
fun FocusFlowScreen(
    onStart: (Int) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    var studyMinutes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "FocusFlow",
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dark mode",
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = darkTheme,
                onCheckedChange = onDarkThemeChange
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = studyMinutes,
            onValueChange = { studyMinutes = it },
            placeholder = { Text("Enter minutes you want to study for") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val mins = studyMinutes.toIntOrNull() ?: 25
                onStart(mins)
            }
        ) {
            Text("Start Timer")
        }
    }
}
