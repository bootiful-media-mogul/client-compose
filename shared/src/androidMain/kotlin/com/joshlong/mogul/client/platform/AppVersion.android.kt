package com.joshlong.mogul.client.platform

import com.joshlong.mogul.client.app.AndroidApp

actual val appVersion: String? by lazy {
    val context = AndroidApp.context
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}
