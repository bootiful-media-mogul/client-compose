package com.joshlong.mogul.client.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.joshlong.mogul.client.App
import com.joshlong.mogul.client.app.DesktopWindow
import com.joshlong.mogul.client.app.UpdateNotice
import kotlinx.coroutines.delay
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/**
 * the build runs the packaged app once to train its AOT cache (desktopApp's build.gradle.kts),
 * with this set to how many seconds to run for: long enough to draw the first screen and settle
 */
private val trainingSeconds = System.getProperty("mogul.aot.training")?.toIntOrNull()

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
        trainingSeconds?.let { seconds ->
            LaunchedEffect(Unit) {
                delay(seconds.seconds)
                // the JVM writes what the run taught it as it exits. exitProcess rather than
                // exitApplication: a client's thread left running would keep it from exiting
                exitProcess(0)
            }
        }
    }
}
