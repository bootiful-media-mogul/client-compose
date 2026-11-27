package com.joshlong.mogul.client.platform

import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.app.AblySse
import com.joshlong.mogul.client.app.AppAuth
import com.joshlong.mogul.client.app.NativePlatform
import com.joshlong.mogul.client.ui.Confirmations
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.ktor.oidcBearer

/**
 * the browser seam, for the apps. there is no page: the history is a stack in memory, storage is
 * the device's preferences, a confirmation is a dialog, and files come and go through the gateway
 * with the bearer token. what differs between the apps is in [NativePlatform]
 */
private object AppBrowser : Browser {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val settings: Settings by lazy { Settings() }

    private val session = mutableMapOf<String, String>()

    /** the pages visited, the current one last: the back button pops it */
    private val history = ArrayDeque(listOf("/"))

    private var popStateListener: ((String) -> Unit)? = null

    private val visibleListeners = mutableListOf<() -> Unit>()

    override val path: String get() = this.history.last()

    override val origin: String get() = AppAuth.server

    override fun pushPath(path: String) {
        if (path != this.history.last()) this.history.addLast(path)
    }

    override fun replacePath(path: String) {
        this.history.removeLast()
        this.history.addLast(path)
    }

    override fun onPopState(callback: (String) -> Unit) {
        this.popStateListener = callback
    }

    /** the platform's back gesture: answers whether there was a page to go back to */
    fun back(): Boolean {
        if (this.history.size < 2) return false
        this.history.removeLast()
        this.popStateListener?.invoke(this.history.last())
        return true
    }

    /** the app came back to the foreground */
    fun becameVisible() = this.visibleListeners.toList().forEach { it() }

    override fun setTitle(title: String) = NativePlatform.setTitle(title)

    override fun setDocumentLanguage(language: String) = Unit

    override fun language(): String = NativePlatform.language()

    override fun localGet(key: String): String? = this.settings.getStringOrNull(key)

    override fun localSet(key: String, value: String) = this.settings.putString(key, value)

    override fun sessionGet(key: String): String? = this.session[key]

    override fun sessionSet(key: String, value: String) {
        this.session[key] = value
    }

    override fun sessionRemove(key: String) {
        this.session.remove(key)
    }

    // one app, one window: nothing else writes these settings while it runs
    override fun onStorage(callback: (key: String, value: String?) -> Unit) = Unit

    override suspend fun confirm(message: String): Boolean = Confirmations.ask(message)

    override fun open(url: String, target: String) = NativePlatform.openUrl(this.absolute(url))

    override fun timeZone(): String = TimeZone.currentSystemDefault().id

    override val isMac: Boolean get() = NativePlatform.isMac

    override fun prefersDark(): Boolean = NativePlatform.prefersDark()

    override fun onPrefersDarkChange(callback: (Boolean) -> Unit) = Unit

    override fun onVisible(callback: () -> Unit) {
        this.visibleListeners += callback
    }

    override fun apiUrl(): String = AppAuth.server + "/api"

    override fun copyToClipboard(text: String) = NativePlatform.copyToClipboard(text)

    override fun download(url: String, filename: String) {
        this.scope.launch {
            runCatching { NativePlatform.saveDownload(Mogul.http.get(url).readRawBytes(), filename) }
                .onFailure { println("couldn't download $url: ${it.message}") }
        }
    }

    override fun downloadText(text: String, filename: String, contentType: String) {
        this.scope.launch { NativePlatform.saveDownload(text.encodeToByteArray(), filename) }
    }

    override fun pickFile(accept: String, callback: (PickedFile?) -> Unit) {
        // the extensions of an <input accept>, e.g. ".jpg,.png,image/jpeg": the media types
        // say the same again, and a picker can't take them everywhere
        val extensions = accept.split(',').map { it.trim() }.filter { it.startsWith('.') }.map { it.drop(1).lowercase() }.toSet()
        this.scope.launch {
            val type = if (extensions.isEmpty()) FileKitType.File() else FileKitType.File(extensions)
            val file = runCatching { FileKit.openFilePicker(type = type) }.getOrNull()
            callback(file?.let { AppPickedFile(it) })
        }
    }

    override fun upload(url: String, file: PickedFile, onProgress: (Double) -> Unit, onDone: (Int) -> Unit) {
        val picked = file as AppPickedFile
        this.scope.launch {
            val status = runCatching {
                val bytes = picked.file.readBytes()
                Mogul.http.submitFormWithBinaryData(
                    url,
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, picked.type)
                            append(HttpHeaders.ContentDisposition, "filename=\"${picked.name.replace("\"", "")}\"")
                        })
                    },
                ) {
                    onUpload { sent, total -> if (total != null && total > 0) onProgress(sent.toDouble() / total) }
                }.status.value
            }.getOrElse {
                println("couldn't upload ${picked.name}: ${it.message}")
                0
            }
            onDone(status)
        }
    }

    override fun createAudio(url: String): AudioHandle {
        val absolute = this.absolute(url)
        // the api's own files need the bearer token; a public one (the CDN) must not be sent it
        val headers = AppAuth.accessToken
            ?.takeIf { absolute.startsWith(AppAuth.server) }
            ?.let { mapOf(HttpHeaders.Authorization to "Bearer $it") }
            ?: emptyMap()
        return NativePlatform.createAudio(absolute, headers)
    }

    // the native players stream and seek on their own; there's nothing to download first
    override suspend fun loadAudio(url: String): String = url

    override fun releaseAudio(localUrl: String) = Unit

    override fun ablySubscribe(tokenRequestJson: String, channel: String, onMessage: (String) -> Unit) {
        this.scope.launch { AblySse.subscribe(tokenRequestJson, channel, onMessage) }
    }

    override fun reload() = Unit

    /** a path on the gateway (as the web build would have asked for it) or a URL already */
    private fun absolute(url: String): String = if (url.startsWith("/")) AppAuth.server + url else url
}

/** a file picked on the device */
private class AppPickedFile(val file: PlatformFile) : PickedFile {

    override val name: String = this.file.name

    override val size: Long = this.file.size()

    override val type: String = contentTypeFor(this.file.extension)
}

/** the media type the api expects for a file, by its extension */
internal fun contentTypeFor(extension: String): String = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    "mp4" -> "video/mp4"
    "pdf" -> "application/pdf"
    "md", "markdown" -> "text/markdown"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}

actual val browser: Browser = AppBrowser

/** the platform's back gesture, for the apps: false when there's no page to go back to */
fun navigateBack(): Boolean = AppBrowser.back()

/** for the apps, when they come back to the foreground */
fun appBecameVisible() = AppBrowser.becameVisible()

/**
 * requests to `/api/...` go to the gateway, with the access token; the gateway checks it and
 * passes it on to the api. an expired token is refreshed once, and a failed refresh signs you out
 */
@OptIn(ExperimentalOpenIdConnect::class)
actual fun createHttpClient(): HttpClient = HttpClient {
    install(Auth) { oidcBearer(AppAuth.tokens, AppAuth.refreshHandler, AppAuth.oidc) }
    install(HttpTimeout) {
        // uploads of an hour's audio take a while; nothing else comes close
        requestTimeoutMillis = 30 * 60 * 1000L
        connectTimeoutMillis = 15_000L
    }
    defaultRequest { url(AppAuth.server + "/") }
}

actual val signIn: SignIn = object : SignIn {

    override val available: Boolean = true

    override suspend fun signIn() = AppAuth.signIn()

    override suspend fun signOut() = AppAuth.signOut()
}
