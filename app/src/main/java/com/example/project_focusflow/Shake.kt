package com.example.project_focusflow

import android.os.Handler
import android.os.Looper

object SensorEvents {
    var onShake: (() -> Unit)? = null
    var onBluetoothConnected: (() ->Unit)? = null
    var onBluetoothDisconnected: (() ->Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun notifyShake() {
        mainHandler.post { onShake?.invoke() }
    }

    fun notifyBluetoothConnected() {
        mainHandler.post { onBluetoothConnected?.invoke() }
    }

    fun notifyBluetoothDisconnected() {
        mainHandler.post { onBluetoothDisconnected?.invoke() }
    }
}
