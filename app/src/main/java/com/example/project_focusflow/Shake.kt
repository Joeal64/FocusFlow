package com.example.project_focusflow

object SensorEvents {
    var onShake: (() -> Unit)? = null
    var onBluetoothConnected: (() -> Unit)? = null
    var onBluetoothDisconnected: (() -> Unit)? = null
}
