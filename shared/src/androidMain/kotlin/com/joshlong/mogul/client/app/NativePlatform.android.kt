package com.joshlong.mogul.client.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import com.joshlong.mogul.client.platform.AudioHandle
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.AndroidCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.AndroidSettingsTokenStore
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore
import java.io.File

/**
 * what the shared code needs from the android app: its context, and its activity for the
 * sign-in browser and the file picker. MainActivity hands them over as it starts
 */
@OptIn(ExperimentalOpenIdConnect::class)
object AndroidApp {

    @SuppressLint("StaticFieldLeak") // the application's context, which lives as long as the process
    internal lateinit var context: Context
        private set

    // one for the whole process: a sign-in in the browser outlives the activity that started it
    internal val flows = AndroidCodeAuthFlowFactory(useWebView = false)

    /** has to be called in the activity's onCreate, before the sign-in can show the browser */
    fun attach(activity: ComponentActivity) {
        this.context = activity.applicationContext
        this.flows.registerActivity(activity)
        FileKit.init(activity)
    }
}

@OptIn(ExperimentalOpenIdConnect::class)
internal actual object NativePlatform {

    // the emulator reaches the machine it runs on at 10.0.2.2: there, 127.0.0.1 is the emulator itself
    actual fun server(configured: String): String =
        if (isEmulator()) configured.replace(Regex("//(127\\.0\\.0\\.1|localhost)(?=[:/]|$)"), "//10.0.2.2") else configured

    private fun isEmulator() =
        Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.startsWith("generic") || Build.PRODUCT.startsWith("sdk")

    actual val redirectUri: String = appConfig.redirectUri

    actual val authFlows: CodeAuthFlowFactory get() = AndroidApp.flows

    actual val tokenStore: TokenStore by lazy { AndroidSettingsTokenStore(AndroidApp.context) }

    actual fun openUrl(url: String) {
        AndroidApp.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    actual fun copyToClipboard(text: String) {
        val clipboard = AndroidApp.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Mogul", text))
    }

    actual val isMac: Boolean = false

    actual fun prefersDark(): Boolean =
        (AndroidApp.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    actual fun createAudio(url: String, headers: Map<String, String>): AudioHandle = AndroidAudio(url, headers)

    actual suspend fun saveDownload(bytes: ByteArray, filename: String) = withContext(Dispatchers.IO) {
        val resolver = AndroidApp.context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("couldn't save $filename")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            Unit
        } else {
            val folder = AndroidApp.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: AndroidApp.context.filesDir
            File(folder, filename).writeBytes(bytes)
        }
    }

    actual fun setTitle(title: String) = Unit
}

/** an audio element, on android's MediaPlayer, which streams the URL itself and can send the token */
private class AndroidAudio(url: String, headers: Map<String, String>) : AudioHandle {

    private val player = MediaPlayer()

    private val handler = Handler(Looper.getMainLooper())

    private var prepared = false

    private var playWhenReady = false

    private var startAt = 0.0

    private var timeUpdate: (() -> Unit)? = null

    private var ended: (() -> Unit)? = null

    // the web's timeupdate, which fires about four times a second while playing
    private val ticker = object : Runnable {
        override fun run() {
            this@AndroidAudio.timeUpdate?.invoke()
            if (this@AndroidAudio.player.isPlaying) this@AndroidAudio.handler.postDelayed(this, 250)
        }
    }

    init {
        this.player.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        this.player.setDataSource(AndroidApp.context, Uri.parse(url), headers)
        this.player.setOnPreparedListener {
            this.prepared = true
            if (this.startAt > 0) it.seekTo((this.startAt * 1000).toInt())
            if (this.playWhenReady) this.play()
            this.timeUpdate?.invoke()
        }
        this.player.setOnCompletionListener {
            this.handler.removeCallbacks(this.ticker)
            this.timeUpdate?.invoke()
            this.ended?.invoke()
        }
        this.player.setOnErrorListener { _, what, extra ->
            println("couldn't play $url: $what/$extra")
            true
        }
        this.player.prepareAsync()
    }

    override val currentTime: Double
        get() = if (this.prepared) this.player.currentPosition / 1000.0 else this.startAt

    override val duration: Double
        get() = if (this.prepared && this.player.duration > 0) this.player.duration / 1000.0 else Double.NaN

    override val paused: Boolean get() = !(this.prepared && this.player.isPlaying) && !this.playWhenReady

    override fun play() {
        if (!this.prepared) {
            this.playWhenReady = true
            return
        }
        this.playWhenReady = false
        this.player.start()
        this.handler.post(this.ticker)
    }

    override fun pause() {
        this.playWhenReady = false
        if (this.prepared && this.player.isPlaying) this.player.pause()
        this.handler.removeCallbacks(this.ticker)
        this.timeUpdate?.invoke()
    }

    override fun seek(seconds: Double) {
        if (this.prepared) this.player.seekTo((seconds * 1000).toInt()) else this.startAt = seconds
        this.timeUpdate?.invoke()
    }

    override fun onTimeUpdate(callback: () -> Unit) {
        this.timeUpdate = callback
    }

    override fun onEnded(callback: () -> Unit) {
        this.ended = callback
    }

    override fun release() {
        this.handler.removeCallbacks(this.ticker)
        this.player.release()
    }
}
