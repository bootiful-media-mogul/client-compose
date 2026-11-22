package com.joshlong.mogul.client.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.joshlong.mogul.client.App
import com.joshlong.mogul.client.app.DesktopWindow

/** Mogul Studio, in a window of its own: the same studio as the web build, signed in on its own */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = DesktopWindow.title,
        state = rememberWindowState(width = 1480.dp, height = 960.dp),
    ) {
        App()
    }
}
