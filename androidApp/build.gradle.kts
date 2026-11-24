import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// auth0 sends the browser back to the app on this scheme: the scheme of mogul.redirectUri,
// which shared/build.gradle.kts reads too
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val redirectUri = providers.gradleProperty("mogul.redirectUri").orNull
    ?: localProperties.getProperty("mogul.redirectUri")
    ?: "com.joshlong.mogul.studio://callback"

android {
    namespace = "com.joshlong.mogul.client.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.joshlong.mogul.studio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // bin/android.sh sets both for a release: play takes each version code only once
        versionCode = providers.gradleProperty("mogul.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("mogul.versionName").orNull ?: "1.0"
        manifestPlaceholders["oidcRedirectScheme"] = redirectUri.substringBefore(":")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}
