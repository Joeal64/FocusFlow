package com.example.project_focusflow

import android.Manifest
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.bluetooth.BluetoothDevice
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_focusflow.ui.theme.ProjectFocusFlowTheme
import kotlin.math.sqrt
import com.example.project_focusflow.R


class MainActivity : ComponentActivity(), SensorEventListener {

    // shake detector variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var shake = 0f

    // bluetooth receiver
    private lateinit var bluetoothReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setup shake sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createFocusNotificationChannel(this)

        // setup bluetooth receiver
        setupBluetoothReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    6001
                )
            }
        }
        setContent {

            var showTimer by remember { mutableStateOf(false) }
            var minutes by remember { mutableStateOf(25) }
            var darkTheme by remember { mutableStateOf(true) }
            var focusLock by remember { mutableStateOf(true) }

            ProjectFocusFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showTimer) {
                        PomodoroTimer(
                            startMinutes = minutes,
                            onBack = { showTimer = false },
                            darkTheme = darkTheme,
                            onDarkThemeChange = { darkTheme = it },
                        )
                    } else {
                        FocusFlowScreen(
                            onStart = { userMinutes ->
                                minutes = userMinutes
                                showTimer = true
                            },
                            darkTheme = darkTheme,
                            onDarkThemeChange = { darkTheme = it },
                            focusLockEnabled = focusLock,
                            onFocusLockChange = { focusLock = it }
                        )
                    }
                }
            }
        }
    }

    private fun setupBluetoothReceiver() {

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return

                when (intent.action) {

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Toast.makeText(
                            ctx,
                            "${device.name} connected, starting focus",
                            Toast.LENGTH_SHORT
                        ).show()

                        SensorEvents.onBluetoothConnected?.invoke()
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        Toast.makeText(
                            ctx,
                            "${device.name} disconnected, pausing",
                            Toast.LENGTH_SHORT
                        ).show()

                        SensorEvents.onBluetoothDisconnected?.invoke()

                        // show notification when bluetooth disconnected
                        showBluetoothPausedNotification(ctx)
                    }

                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Supports Android 13+
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            currentAcceleration = sqrt((x*x + y*y + z*z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            shake = shake * 0.9f + delta

            if (shake > 12) {
                SensorEvents.onShake?.invoke()
            }

            lastAcceleration = currentAcceleration
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // nothing needed
    }

    override fun onStart() {
        super.onStart()
        AppLifecycleEvents.onAppForegrounded?.invoke()
    }

    override fun onStop() {
        super.onStop()
        AppLifecycleEvents.onAppBackgrounded?.invoke()
    }
}

@Composable
fun FocusFlowScreen(
    onStart: (Int) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    focusLockEnabled: Boolean,
    onFocusLockChange: (Boolean) -> Unit
) {
    var studyMinutes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        // Top-right dark mode toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (darkTheme)
                    stringResource(R.string.dark_mode)
                else
                    stringResource(R.string.light_mode),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = darkTheme,
                onCheckedChange = onDarkThemeChange

            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground
            )


            Spacer(modifier = Modifier.height(32.dp))

            TextField(
                value = studyMinutes,
                onValueChange = {
                    studyMinutes = it
                    error = null
                },
                placeholder = { Text(stringResource(R.string.enter_minutes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val mins = studyMinutes.toIntOrNull()

                    if (mins == null || mins <= 0) {
                        error = context.getString(R.string.minutes_error)
                    } else {
                        onStart(mins)
                    }
                }
            ) {
                Text(stringResource(R.string.start_timer))
            }

        }
    }
}
