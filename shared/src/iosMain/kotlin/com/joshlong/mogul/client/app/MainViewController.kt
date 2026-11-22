package com.joshlong.mogul.client.app

import androidx.compose.ui.window.ComposeUIViewController
import com.joshlong.mogul.client.App
import platform.UIKit.UIViewController

/** Mogul Studio on an iPad: the same studio as the web build, laid out as the desktop is on a wide screen */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
