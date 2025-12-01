package com.example.project_focusflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.project_focusflow.ui.theme.ProjectFocusFlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

private const val MAIN_LOG_TAG = "FocusFlowTimer"

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Timer : Screen("timer", "Timer", Icons.Default.Timer)
    object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
}

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var shake = 0f

    private lateinit var db: AppDatabase

    // --- FIX: Add a flag to control the interruption logic ---
    private var isFlowInterrupted = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createFocusNotificationChannel(this)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "focus_db")
            .fallbackToDestructiveMigration()
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 6001)
            }
        }

        setContent {
            var showMainApp by remember { mutableStateOf(false) }
            var minutes by remember { mutableStateOf(25) }
            var darkTheme by remember { mutableStateOf(true) }
            var focusLock by remember { mutableStateOf(true) }
            var streakCount by remember { mutableStateOf(0) }

            val transparentColor = Color.Transparent
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                        lightScrim = transparentColor.hashCode(),
                        darkScrim = transparentColor.hashCode()
                    ) { darkTheme },
                    navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                        lightScrim = transparentColor.hashCode(),
                        darkScrim = transparentColor.hashCode()
                    ) { darkTheme }
                )
                onDispose {}
            }

            ProjectFocusFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showMainApp) {
                        MainAppScaffold(
                            darkTheme = darkTheme,
                            onDarkThemeChange = { darkTheme = it },
                            streakCount = streakCount,
                            startMinutes = minutes,
                            onTimerFinish = { sessionMinutes ->
                                handleTimerFinish(sessionMinutes) { newStreakAchieved ->
                                    if (newStreakAchieved) {
                                        streakCount++
                                    }
                                }
                            },
                            onGoHome = {
                                // When going home, re-enable the interruption check
                                isFlowInterrupted = true
                                showMainApp = false
                            }
                        )
                    } else {
                        FocusFlowScreen(
                            onStart = { userMinutes ->
                                minutes = userMinutes
                                // When starting the timer, enable the interruption check
                                isFlowInterrupted = true
                                showMainApp = true
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

    private fun handleTimerFinish(sessionMinutes: Int, onStreakResult: (Boolean) -> Unit) {
        // --- FIX: Before navigating away, disable the interruption check ---
        isFlowInterrupted = false

        Log.d(MAIN_LOG_TAG, "handleTimerFinish called with $sessionMinutes minutes.")
        lifecycleScope.launch(Dispatchers.IO) {
            // ... (database logic is correct and remains unchanged)
            val dao = db.focusSessionDao()
            var newStreakAchieved = false
            dao.insert(FocusSession(durationMinutes = sessionMinutes, completedAt = System.currentTimeMillis()))
            val minutesToday = dao.getMinutesToday() ?: 0
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val existingStreak = dao.getStreak(today)
            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val requiredMinutes = if (isDebuggable) 1 else 25
            if (existingStreak == null && minutesToday >= requiredMinutes) {
                dao.setStreak(DailyStreak(date = today, count = 1))
                newStreakAchieved = true
            }
            launch(Dispatchers.Main) {
                onStreakResult(newStreakAchieved)
            }
        }

        // ... (Vibration and Notification logic is correct)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(400)
        }
        showSessionFinishedNotification(this, sessionMinutes)

        // Launch Summary Activity
        val intent = Intent(this, SummaryActivity::class.java).apply {
            putExtra("SESSION_MINUTES", sessionMinutes)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // When the user comes back to the app, re-enable the interruption check
        isFlowInterrupted = true
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            shake = shake * 0.9f + delta
            if (shake > 12) {
                SensorEvents.onShake?.invoke()
            }
            lastAcceleration = currentAcceleration
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onStart() {
        super.onStart()
        AppLifecycleEvents.onAppForegrounded?.invoke()
    }

    override fun onStop() {
        super.onStop()
        // --- FIX: Only trigger the background event if the flag is true ---
        if (isFlowInterrupted) {
            AppLifecycleEvents.onAppBackgrounded?.invoke()
        }
    }
}

// MainAppScaffold composable is correct and does not need changes
@Composable
fun MainAppScaffold(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    streakCount: Int,
    startMinutes: Int,
    onTimerFinish: (Int) -> Unit,
    onGoHome: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Timer) }
    val screens = listOf(Screen.Timer, Screen.Stats)

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentScreen.route == screen.route,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Timer -> {
                    PomodoroTimer(
                        startMinutes = startMinutes,
                        onBack = onGoHome,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        streakCount = streakCount,
                        onTimerFinish = onTimerFinish
                    )
                }
                Screen.Stats -> {
                    WeeklyStatsScreen(onBack = { currentScreen = Screen.Timer })
                }
            }
        }
    }
}

// FocusFlowScreen composable is correct and does not need changes
@Composable
fun FocusFlowScreen(
    onStart: (Int) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    focusLockEnabled: Boolean,
    onFocusLockChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Button(
                onClick = { onStart(25) }
            ) {
                Text(stringResource(R.string.start_timer))
            }
        }
    }
}
