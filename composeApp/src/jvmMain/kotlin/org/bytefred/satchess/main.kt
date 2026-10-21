package org.bytefred.satchess

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bytefred.satchess.ui.App

/** Desktop (JVM) entry point: one window hosting the shared [App]. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SAT chess",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        App()
    }
}
