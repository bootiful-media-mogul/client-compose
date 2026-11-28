package com.joshlong.mogul.client.platform

// desktopApp's build sets mogul.version for a release; run from gradle, it isn't set
actual val appVersion: String? = System.getProperty("mogul.version")
