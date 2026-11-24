package com.joshlong.mogul.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.MogulTheme
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.updates.Version
import com.joshlong.mogul.client.updates.installerFor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// what a release is packaged as, and where it asks for the newest release (desktopApp's
// build.gradle.kts sets both). run from gradle, it has neither, and doesn't ask
private val packagedVersion: String? = System.getProperty("mogul.version")

private val latestReleaseUrl: String? = System.getProperty("mogul.releases")

/** what github says of a release, of what this needs */
@Serializable
private class Release(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val page: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<Asset> = emptyList(),
)

@Serializable
private class Asset(val name: String, @SerialName("browser_download_url") val url: String)

/** a newer release, and where to get it: its installer for this system, or its page if it has none */
private class Update(val version: Version, val url: String)

private val json = Json { ignoreUnknownKeys = true }

// as bin/desktop.sh names them
private val system = System.getProperty("os.name").let {
    when {
        it.startsWith("Mac") -> "macos"
        it.startsWith("Windows") -> "windows"
        else -> "linux"
    }
}

private val cpu = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"

/** the latest release, if it's newer than this one */
private suspend fun newerRelease(url: String, current: Version): Update? = withContext(Dispatchers.IO) {
    HttpClient(OkHttp).use { client ->
        val response = client.get(url) {
            header("Accept", "application/vnd.github+json")
            // github turns away a request that doesn't say what's asking
            header("User-Agent", "Mogul-Studio/$current")
        }
        if (!response.status.isSuccess()) {
            println("couldn't check for a newer release: $url answered ${response.status}")
            return@withContext null
        }
        val release = json.decodeFromString<Release>(response.bodyAsText())
        val version = Version.parse(release.tag)
        if (release.draft || release.prerelease || version == null || version <= current) return@withContext null
        val installer = installerFor(release.assets.map { it.name }, system, cpu)
        Update(version, release.assets.firstOrNull { it.name == installer }?.url ?: release.page)
    }
}

/**
 * says so when there's a newer release than this one, once, as the app opens: the desktop app
 * can't update itself, so it offers the download. nothing shows if it can't find out
 */
@Composable
fun UpdateNotice() {
    val current = packagedVersion?.let(Version::parse) ?: return
    val url = latestReleaseUrl ?: return
    var update by remember { mutableStateOf<Update?>(null) }

    LaunchedEffect(Unit) {
        update = runCatching { newerRelease(url, current) }
            .onFailure { println("couldn't check for a newer release: ${it.message}") }
            .getOrNull()
    }

    val found = update ?: return
    MogulTheme {
        Box(Modifier.fillMaxSize().padding(Gutter.full), contentAlignment = Alignment.BottomEnd) {
            Column(
                Modifier
                    .widthIn(max = 360.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.raised)
                    .padding(Gutter.full),
                verticalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                Text(t("updates.available", "latest" to found.version, "current" to current), color = palette.ink)
                ButtonRow {
                    MogulButton(t("updates.later"), onClick = { update = null })
                    MogulButton(t("updates.download"), onClick = {
                        NativePlatform.openUrl(found.url)
                        update = null
                    })
                }
            }
        }
    }
}
