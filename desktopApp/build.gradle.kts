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
        // a release knows its version, and asks github for the latest release as it opens
        // (UpdateNotice). run from gradle without a version, it doesn't ask
        providers.gradleProperty("mogul.version").orNull?.let { version ->
            jvmArgs += listOf(
                "-Dmogul.version=$version",
                "-Dmogul.releases=https://api.github.com/repos/bootiful-media-mogul/studio-compose/releases/latest",
            )
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mogul Studio"
            // bin/desktop.sh sets it for a release. a dmg wants its first number above 0, and an
            // msi wants each of the three no higher than 255.255.65535
            packageVersion = providers.gradleProperty("mogul.version").orNull ?: "1.0.0"
            // the logo, as bin/brand.sh makes it for each system
            macOS {
                bundleID = "com.joshlong.mogul.studio"
                iconFile.set(project.file("icons/mogul.icns"))
            }
            windows {
                iconFile.set(project.file("icons/mogul.ico"))
            }
            linux {
                iconFile.set(project.file("icons/mogul.png"))
            }
            // ktor's okhttp engine and javafx's media player reach for these at runtime
            modules("java.net.http", "jdk.unsupported", "java.naming")
        }
    }
}
