import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.joshlong.mogul.client.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mogul Studio"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.joshlong.mogul.studio"
            }
            // ktor's okhttp engine and javafx's media player reach for these at runtime
            modules("java.net.http", "jdk.unsupported", "java.naming")
        }
    }
}
