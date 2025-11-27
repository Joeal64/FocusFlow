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

const val FOCUS_CHANNEL_ID = "focus_channel"

fun createFocusNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Focus notifications"
        val descriptionText = "Notifications for focus sessions"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(FOCUS_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

// Timer finished
fun showSessionFinishedNotification(context: Context, minutes: Int) {
    val intent = Intent(context, SummaryActivity::class.java).apply {
        putExtra("SESSION_MINUTES", minutes)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    val builder = NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(context.getString(R.string.notif_title_session))
        .setContentText(context.getString(R.string.notif_body_session, minutes))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) return
    }

    with(NotificationManagerCompat.from(context)) {
        notify(1, builder.build())
    }
}


fun showBluetoothPausedNotification(context: Context) {
    val builder = NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(context.getString(R.string.notif_title_bt))
        .setContentText(context.getString(R.string.notif_body_bt))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) return
    }

    with(NotificationManagerCompat.from(context)) {
        notify(2, builder.build())
    }
}
