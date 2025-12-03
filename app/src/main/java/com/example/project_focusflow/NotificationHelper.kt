package com.example.project_focusflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// ID used to group all app notifications into one channel
const val FOCUS_CHANNEL_ID = "focus_channel"

// Creates the notification channel
// Must be called once when the app starts
fun createFocusNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        // Visible channel name shown in phone notification settings
        val name = "Focus notifications"

        // Description shown in system settings
        val descriptionText = "Notifications for focus sessions"

        // Default importance level sound and status bar
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        // Build the channel
        val channel = NotificationChannel(FOCUS_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        // Register the channel with Android system
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}


// notification for system is finished

fun showSessionFinishedNotification(context: Context, minutes: Int) {

    // Intent to open the Summary screen when notification is tapped
    val intent = Intent(context, SummaryActivity::class.java).apply {

        // Pass the number of focused minutes to SummaryActivity
        putExtra("SESSION_MINUTES", minutes)

        // Start fresh activity if already running
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Wrap intent inside PendingIntent which is required for notifications
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        // Update the intent if notification is reused
        PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE
                else 0)
    )

    // Build the actual notification
    val builder = NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)  // Icon shown in status bar
        .setContentTitle(context.getString(R.string.notif_title_session)) // Title text
        .setContentText(context.getString(R.string.notif_body_session, minutes)) // Body text
        .setAutoCancel(true) // Remove when user taps it
        .setContentIntent(pendingIntent) // Open SummaryActivity on tap
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    // Android 13+ requires permission for notifications
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        // Do not show notification if permission not granted
        if (!granted) return
    }

    // Show the notification with ID = 1
    with(NotificationManagerCompat.from(context)) {
        notify(1, builder.build())
    }
}


// notification of bluetooth is paused

fun showBluetoothPausedNotification(context: Context) {

    // Build notification for Bluetooth pause warning
    val builder = NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher) // Status bar icon
        .setContentTitle(context.getString(R.string.notif_title_bt)) // Title text
        .setContentText(context.getString(R.string.notif_body_bt)) // Body message
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    // Android 13+ permission check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        // Exit if permission missing
        if (!granted) return
    }

    // Show the Bluetooth warning notification with ID = 2
    with(NotificationManagerCompat.from(context)) {
        notify(2, builder.build())
    }
}
