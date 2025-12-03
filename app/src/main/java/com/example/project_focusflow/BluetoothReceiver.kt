package com.example.project_focusflow

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Log.d("BluetoothReceiver", "Bluetooth device connected")
                // Tell the timer to start/resume
                SensorEvents.notifyBluetoothConnected()
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> {
                Log.d("BluetoothReceiver", "Bluetooth device disconnected")
                // Tell the timer to pause
                SensorEvents.notifyBluetoothDisconnected()
                // Show your pause notification
                showBluetoothPausedNotification(context)
            }
        }
    }
}
