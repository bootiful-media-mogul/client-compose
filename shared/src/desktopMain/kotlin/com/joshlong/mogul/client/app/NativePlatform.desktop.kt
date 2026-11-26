package com.joshlong.mogul.client.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import com.joshlong.mogul.client.platform.AudioHandle
import com.joshlong.mogul.client.resources.Res
import com.joshlong.mogul.client.resources.mogul_logo
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Url
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.JvmCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.appsupport.webserver.SimpleKtorWebserver
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsStore
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsTokenStore
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.util.prefs.Preferences
import javax.swing.SwingUtilities

/** the window's title, which the pages set as the web build sets the tab's, and its icon */
object DesktopWindow {
    var title by mutableStateOf("Mogul Studio")
        internal set

    /** the logo, the same one the studio draws its wordmark with */
    val icon: Painter
        @Composable get() = painterResource(Res.drawable.mogul_logo)
}

@OptIn(ExperimentalOpenIdConnect::class)
internal actual object NativePlatform {

    actual fun server(configured: String): String = configured

    // a desktop app can't register a scheme while it runs from gradle: auth0 sends the browser
    // back to a port this app listens on instead
    actual val redirectUri: String = appConfig.desktopRedirectUri

    actual val authFlows: CodeAuthFlowFactory by lazy {
        val port = Url(this.redirectUri).port
        JvmCodeAuthFlowFactory(webserverProvider = { SimpleKtorWebserver(port = port) })
    }

    actual val tokenStore: TokenStore by lazy { SettingsTokenStore(PreferencesStore("com/joshlong/mogul/studio/auth")) }

    actual fun openUrl(url: String) = Desktop.getDesktop().browse(URI(url))

    actual fun copyToClipboard(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

    actual val isMac: Boolean = System.getProperty("os.name").startsWith("Mac")

    actual fun prefersDark(): Boolean = currentSystemTheme == SystemTheme.DARK

    actual fun createAudio(url: String, headers: Map<String, String>): AudioHandle = JavaFxAudio(url, headers)

    actual suspend fun saveDownload(bytes: ByteArray, filename: String) = withContext(Dispatchers.IO) {
        val downloads = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
        File(downloads, filename).writeBytes(bytes)
    }

    actual fun setTitle(title: String) {
        DesktopWindow.title = title
    }
}

/** the user's preferences, for the tokens: per user, and kept with the rest of their settings */
private class PreferencesStore(node: String) : SettingsStore {

    private val settings = PreferencesSettings(Preferences.userRoot().node(node))

    override suspend fun get(key: String): String? = this.settings.getStringOrNull(key)

    override suspend fun put(key: String, value: String) {
        this.settings[key] = value
    }

    override suspend fun remove(key: String) = this.settings.remove(key)

    override suspend fun clear() = this.settings.clear()
}

/**
 * an audio element, on JavaFX's media player: java has no mp3 decoder of its own. the player
 * can't send headers, so the file is fetched (with the bearer token, if it's the api's) to a
 * temporary file and played from there
 */
private class JavaFxAudio(private val url: String, private val headers: Map<String, String>) : AudioHandle {

    private var player: MediaPlayer? = null

    private var file: File? = null

    private var playing = false

    private var playWhenReady = false

    private var startAt = 0.0

    private var timeUpdate: (() -> Unit)? = null

    private var ended: (() -> Unit)? = null

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                // here, not on the UI thread the editor makes this on: see startJavaFx
                startJavaFx()
                val bytes = http.get(this@JavaFxAudio.url) { headers { this@JavaFxAudio.headers.forEach { (k, v) -> append(k, v) } } }.readRawBytes()
                val suffix = "." + this@JavaFxAudio.url.substringAfterLast('.', "mp3").take(4).filter { it.isLetterOrDigit() }.ifEmpty { "mp3" }
                val temp = File.createTempFile("mogul-audio", suffix).apply { deleteOnExit(); writeBytes(bytes) }
                this@JavaFxAudio.file = temp
                Platform.runLater { this@JavaFxAudio.load(temp) }
            }.onFailure { println("couldn't load audio ${this@JavaFxAudio.url}: ${it.message}") }
        }
    }

    private fun load(file: File) {
        val player = MediaPlayer(Media(file.toURI().toString()))
        player.currentTimeProperty().addListener { _, _, _ -> this.notifyTimeUpdate() }
        player.setOnReady {
            if (this.startAt > 0) player.seek(Duration.seconds(this.startAt))
            if (this.playWhenReady) player.play()
            this.notifyTimeUpdate()
        }
        player.setOnEndOfMedia {
            this.playing = false
            player.stop()
            SwingUtilities.invokeLater { this.ended?.invoke() }
        }
        this.player = player
    }

    private fun notifyTimeUpdate() = SwingUtilities.invokeLater { this.timeUpdate?.invoke() }

    override val currentTime: Double get() = this.player?.currentTime?.toSeconds() ?: this.startAt

    override val duration: Double
        get() = this.player?.media?.duration?.toSeconds()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0 } ?: Double.NaN

    override val paused: Boolean get() = !this.playing

    override fun play() {
        this.playing = true
        val player = this.player
        if (player == null) this.playWhenReady = true else Platform.runLater { player.play() }
    }

    override fun pause() {
        this.playing = false
        this.playWhenReady = false
        this.player?.let { Platform.runLater { it.pause() } }
    }

    override fun seek(seconds: Double) {
        val player = this.player
        if (player == null) this.startAt = seconds else Platform.runLater { player.seek(Duration.seconds(seconds)) }
    }

    override fun onTimeUpdate(callback: () -> Unit) {
        this.timeUpdate = callback
    }

    override fun onEnded(callback: () -> Unit) {
        this.ended = callback
    }

    override fun release() {
        this.playing = false
        this.player?.let { Platform.runLater { it.dispose() } }
        this.player = null
        this.file?.delete()
    }

    companion object {

        /** a client of its own: the audio's headers say whether it carries a token */
        private val http by lazy { HttpClient() }

        @Volatile
        private var javaFxStarted = false

        /**
         * JavaFX's media player needs its toolkit running, which a Compose app never starts.
         * never start it on the UI thread: on a mac, JavaFX starts by handing its run loop to the
         * main thread and waiting until the main thread has taken it, and AWT's main thread waits
         * on the UI thread whenever something asks the window about itself (an accessibility
         * client, the input method). when the two happen at once each waits on the other for
         * good: the window freezes, and the app has to be force quit
         */
        private fun startJavaFx() {
            check(!SwingUtilities.isEventDispatchThread()) { "JavaFX must be started off the UI thread" }
            if (javaFxStarted) return
            synchronized(this) {
                if (javaFxStarted) return
                Platform.setImplicitExit(false)
                runCatching { Platform.startup {} }
                javaFxStarted = true
            }
        }
    }
}
