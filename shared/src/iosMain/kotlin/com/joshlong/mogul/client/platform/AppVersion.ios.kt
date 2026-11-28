package com.joshlong.mogul.client.platform

import platform.Foundation.NSBundle

actual val appVersion: String? = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
