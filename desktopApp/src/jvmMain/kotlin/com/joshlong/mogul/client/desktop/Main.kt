package com.joshlong.mogul.client.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.joshlong.mogul.client.App
import com.joshlong.mogul.client.app.DesktopWindow
import com.joshlong.mogul.client.app.UpdateNotice

/** Mogul Studio, in a window of its own: the same studio as the web build, signed in on its own */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = DesktopWindow.title,
        icon = DesktopWindow.icon,
        state = rememberWindowState(width = 1480.dp, height = 960.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            App()
            UpdateNotice()
        }
    }
}
