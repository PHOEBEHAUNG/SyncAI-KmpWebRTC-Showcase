package com.codingdrama.vlmwebrtc

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SyncAI WebRTC Test",
        state = rememberWindowState(width = 480.dp, height = 900.dp)
    ) {
        App()
    }
}