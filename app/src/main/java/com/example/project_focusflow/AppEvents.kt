package com.example.project_focusflow

object AppLifecycleEvents {
    var onAppBackgrounded: (() -> Unit)? = null
    var onAppForegrounded: (() -> Unit)? = null
}
