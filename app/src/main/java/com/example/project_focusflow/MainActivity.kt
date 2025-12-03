package com.example.project_focusflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

// Tag used for log messages
private const val MAIN_LOG_TAG = "FocusFlowTimer"

// Simple sealed class to define screens for bottom navigation
sealed class Screen(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Timer : Screen("timer", "Timer", Icons.Default.Timer)
    object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
}

// Main activity of the app
class MainActivity : ComponentActivity(), SensorEventListener {

    // Accelerometer sensor variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var shake = 0f

    // Room database instance
    private lateinit var db: AppDatabase

    // Used to know if the focus flow was interrupted
    private var isFlowInterrupted = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind system bars
        enableEdgeToEdge()

        // Set up accelerometer for shake detection
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Create notification channel for this app for notifications
        createFocusNotificationChannel(this)

        // Build Room database
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "focus_db")
            .fallbackToDestructiveMigration()
            .build()

        // Request Bluetooth permission for Android 12+ if needed
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
            // Whether to show the main app or the landing screen
            var showMainApp by remember { mutableStateOf(false) }

            // Default timer length
            var minutes by remember { mutableStateOf(25) }

            // Theme state: true = dark, false = light
            var darkTheme by remember { mutableStateOf(true) }

            // Total streak count (number of days user reached the goal)
            var streakCount by remember { mutableStateOf(0) }

            // Is the timer currently running?
            var isTimerRunning by remember { mutableStateOf(false) }

            // Was the timer interrupted (e.g., app went to background)?
            var wasInterrupted by remember { mutableStateOf(false) }

            // Listen to app going to background while timer is running
            LaunchedEffect(isTimerRunning) {
                AppLifecycleEvents.onAppBackgrounded = {
                    if (isTimerRunning) {
                        // Stop the timer and mark that it was interrupted
                        isTimerRunning = false
                        wasInterrupted = true
                    }
                }
            }

            // Clear the listener when this composable leaves composition
            DisposableEffect(Unit) {
                onDispose {
                    AppLifecycleEvents.onAppBackgrounded = null
                }
            }

            // Load initial streak count from database when UI starts
            LaunchedEffect(Unit) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val totalStreaks = db.focusSessionDao().getTotalStreaks()
                    withContext(Dispatchers.Main) {
                        streakCount = totalStreaks
                    }
                }
            }

            // Make system bars transparent but still adapt to dark/light
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

            // Wrap the whole UI in the app theme
            ProjectFocusFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showMainApp) {
                        // Show main app with bottom navigation
                        MainAppScaffold(
                            darkTheme = darkTheme,
                            onDarkThemeChange = { darkTheme = it },
                            streakCount = streakCount,
                            startMinutes = minutes,
                            onTimerFinish = { sessionMinutes ->
                                // When timer finishes, save session + update streaks
                                handleTimerFinish(sessionMinutes) { newStreakAchieved ->
                                    if (newStreakAchieved) {
                                        // Reload streak count from database
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val totalStreaks =
                                                db.focusSessionDao().getTotalStreaks()
                                            withContext(Dispatchers.Main) {
                                                streakCount = totalStreaks
                                            }
                                        }
                                    }
                                }
                            },
                            onGoHome = {
                                // Go back to landing screen
                                isFlowInterrupted = true
                                showMainApp = false
                            },
                            isTimerRunning = isTimerRunning,
                            onTimerRunningChange = { isTimerRunning = it },
                            wasInterrupted = wasInterrupted,
                            onInterruptionHandled = { wasInterrupted = false }
                        )
                    } else {
                        // Show the first screen asking user to start the timer
                        FocusFlowScreen(
                            onStart = { userMinutes ->
                                minutes = userMinutes
                                isFlowInterrupted = true
                                showMainApp = true
                            },
                            darkTheme = darkTheme,
                            onDarkThemeChange = { darkTheme = it }
                        )
                    }
                }
            }
        }
    }

    // Called when the Pomodoro timer finishes
    private fun handleTimerFinish(
        sessionMinutes: Int,
        onStreakResult: (Boolean) -> Unit
    ) {
        isFlowInterrupted = false
        Log.d(MAIN_LOG_TAG, "handleTimerFinish called with $sessionMinutes minutes.")

        // Run all DB operations in background
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.focusSessionDao()
            var newStreakAchieved = false

            // Save this session to the database
            dao.insert(
                FocusSession(
                    durationMinutes = sessionMinutes,
                    completedAt = System.currentTimeMillis()
                )
            )

            // Get today's total minutes including this new session
            val minutesToday = dao.getMinutesToday() ?: 0

            // Format today's date as string yyyy-MM-dd
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // Check if app is in debug mode
            val isDebuggable =
                (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            // If debug: 1 min is enough to count as a streak, otherwise 25
            val requiredMinutes = if (isDebuggable) 1 else 25
            Log.d(
                MAIN_LOG_TAG,
                "App is debuggable: $isDebuggable. Required minutes for streak: $requiredMinutes"
            )

            // If user studied enough minutes today, record a streak
            if (minutesToday >= requiredMinutes) {
                Log.d(
                    MAIN_LOG_TAG,
                    "Streak condition MET. Inserting a new streak entry."
                )
                // Add a new streak record for today
                dao.addStreak(DailyStreak(date = today, count = 1))
                newStreakAchieved = true
            } else {
                Log.d(MAIN_LOG_TAG, "Streak condition NOT MET.")
            }

            // Notify UI about the streak result on the main thread
            launch(Dispatchers.Main) {
                onStreakResult(newStreakAchieved)
            }
        }

        // Vibrate to notify user that the session has finished
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
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

        // Show a system notification about the finished session
        showSessionFinishedNotification(this, sessionMinutes)

        // Open the SummaryActivity screen showing how many minutes were completed
        val intent = Intent(this, SummaryActivity::class.java).apply {
            putExtra("SESSION_MINUTES", sessionMinutes)
        }
        startActivity(intent)
    }

    // When activity comes to foreground: register accelerometer listener
    override fun onResume() {
        super.onResume()
        isFlowInterrupted = true
        accelerometer?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    // When activity goes to background: stop listening to sensor
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Called when accelerometer values change used for shake detection
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate current acceleration magnitude
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            // Difference from last acceleration
            val delta = currentAcceleration - lastAcceleration

            // Simple low-pass filter for shake detection
            shake = shake * 0.9f + delta

            // If shake is strong enough, trigger shake event
            if (shake > 12) {
                SensorEvents.onShake?.invoke()
            }

            lastAcceleration = currentAcceleration
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // When activity starts: notify app that it’s in foreground
    override fun onStart() {
        super.onStart()
        AppLifecycleEvents.onAppForegrounded?.invoke()
    }

    // When activity stops: if flow was interrupted, notify app that it’s in background
    override fun onStop() {
        super.onStop()
        if (isFlowInterrupted) {
            AppLifecycleEvents.onAppBackgrounded?.invoke()
        }
    }
}

// Main scaffold for the in-app screens, Timer & Stats with bottom navigation
@Composable
fun MainAppScaffold(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    streakCount: Int,
    startMinutes: Int,
    onTimerFinish: (Int) -> Unit,
    onGoHome: () -> Unit,
    isTimerRunning: Boolean,
    onTimerRunningChange: (Boolean) -> Unit,
    wasInterrupted: Boolean,
    onInterruptionHandled: () -> Unit
) {
    // Which screen is currently selected in the bottom nav
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Timer) }

    // Ordered list of bottom nav items
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
                        onClick = {
                            if (!isTimerRunning) {
                                // Switch screen only if timer is not running
                                currentScreen = screen
                            } else {
                                // If timer is running, treat navigation as going to background
                                AppLifecycleEvents.onAppBackgrounded?.invoke()
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Timer -> {
                    // Main Pomodoro timer screen
                    PomodoroTimer(
                        startMinutes = startMinutes,
                        onBack = onGoHome,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        streakCount = streakCount,
                        onTimerFinish = onTimerFinish,
                        isTimerRunning = isTimerRunning,
                        onTimerRunningChange = onTimerRunningChange,
                        wasInterrupted = wasInterrupted,
                        onInterruptionHandled = onInterruptionHandled
                    )
                }

                Screen.Stats -> {
                    // Weekly statistics screen, bar chart
                    WeeklyStatsScreen(onBack = { currentScreen = Screen.Timer })
                }
            }
        }
    }
}

// First screen user sees before starting the timer
@Composable
fun FocusFlowScreen(
    onStart: (Int) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .systemBarsPadding()
    ) {
        // Theme toggle at the top-right (dark / light)
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
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
            Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
        }

        // Different layout depending on orientation
        if (isLandscape) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // App name
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(32.dp))
                // Button to start a 25-minute timer
                Button(onClick = { onStart(25) }) {
                    Text(stringResource(R.string.start_timer))
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App name
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(32.dp))
                // Button to start a 25 minute timer
                Button(onClick = { onStart(25) }) {
                    Text(stringResource(R.string.start_timer))
                }
            }
        }
    }
}
