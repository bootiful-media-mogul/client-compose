@file:OptIn(ExperimentalWasmJsInterop::class)

package com.joshlong.mogul.client.platform

import io.ktor.client.HttpClient
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

// every js() call is the whole body of a top-level function: that is the only form
// Kotlin/Wasm accepts, and Kotlin/JS is happy with it too, so one file serves both

private fun jsPath(): String = js("window.location.pathname + window.location.search")

private fun jsOrigin(): String = js("window.location.origin")

private fun jsPushPath(path: String): Unit = js("window.history.pushState({}, '', path)")

private fun jsReplacePath(path: String): Unit = js("window.history.replaceState({}, '', path)")

private fun jsOnPopState(callback: (String) -> Unit): Unit =
    js("window.addEventListener('popstate', function () { callback(window.location.pathname + window.location.search) })")

private fun jsSetTitle(title: String): Unit = js("document.title = title")

private fun jsSetLanguage(language: String): Unit = js("document.documentElement.lang = language")

private fun jsLocalGet(key: String): String? = js("(function () { try { return window.localStorage.getItem(key) } catch (e) { return null } })()")

private fun jsLocalSet(key: String, value: String): Unit = js("(function () { try { window.localStorage.setItem(key, value) } catch (e) {} })()")

private fun jsSessionGet(key: String): String? = js("(function () { try { return window.sessionStorage.getItem(key) } catch (e) { return null } })()")

private fun jsSessionSet(key: String, value: String): Unit = js("(function () { try { window.sessionStorage.setItem(key, value) } catch (e) {} })()")

private fun jsSessionRemove(key: String): Unit = js("(function () { try { window.sessionStorage.removeItem(key) } catch (e) {} })()")

private fun jsOnStorage(callback: (String, String?) -> Unit): Unit =
    js("window.addEventListener('storage', function (e) { if (e.key) callback(e.key, e.newValue) })")

private fun jsConfirm(message: String): Boolean = js("window.confirm(message)")

private fun jsOpen(url: String, target: String): Unit = js("{ window.open(url, target) }")

private fun jsTimeZone(): String = js("Intl.DateTimeFormat().resolvedOptions().timeZone || ''")

private fun jsIsMac(): Boolean = js("/Mac|iPhone|iPad/.test(navigator.userAgent)")

private fun jsPrefersDark(): Boolean = js("!!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)")

private fun jsOnPrefersDarkChange(callback: (Boolean) -> Unit): Unit =
    js("window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) { callback(e.matches) })")

private fun jsOnVisible(callback: () -> Unit): Unit =
    js("document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') callback() })")

private fun jsApiUrl(): String = js("window.MOGUL_API_URL || ''")

private fun jsCopy(text: String): Unit = js("{ navigator.clipboard && navigator.clipboard.writeText(text) }")

private fun jsDownload(url: String, filename: String): Unit =
    js("(function () { var a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove() })()")

private fun jsDownloadText(text: String, filename: String, contentType: String): Unit =
    js("(function () { var url = URL.createObjectURL(new Blob([text], { type: contentType })); var a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove(); setTimeout(function () { URL.revokeObjectURL(url) }, 1000) })()")

private fun jsPickFile(accept: String, callback: (JsAny?) -> Unit): Unit =
    js("""(function () {
        var input = document.createElement('input')
        input.type = 'file'
        if (accept) input.accept = accept
        input.style.display = 'none'
        var settled = false
        input.addEventListener('change', function () {
          settled = true
          callback(input.files && input.files.length ? input.files[0] : null)
          input.remove()
        })
        input.addEventListener('cancel', function () { if (!settled) callback(null); input.remove() })
        document.body.appendChild(input)
        input.click()
    })()""")

private fun jsFileName(file: JsAny): String = js("file.name")

private fun jsFileSize(file: JsAny): Double = js("file.size")

private fun jsFileType(file: JsAny): String = js("file.type || ''")

private fun jsUpload(url: String, file: JsAny, onProgress: (Double) -> Unit, onDone: (Int) -> Unit): Unit =
    js("""(function () {
        var data = new FormData()
        data.append('file', file)
        var xhr = new XMLHttpRequest()
        xhr.open('POST', url)
        xhr.withCredentials = true
        xhr.upload.onprogress = function (e) { if (e.lengthComputable) onProgress(e.loaded / e.total) }
        xhr.onload = function () { onDone(xhr.status) }
        xhr.onerror = function () { onDone(0) }
        xhr.send(data)
    })()""")

private fun jsCreateAudio(url: String): JsAny = js("(function () { var a = new Audio(url); a.preload = 'metadata'; return a })()")

private fun jsAudioCurrentTime(audio: JsAny): Double = js("audio.currentTime")

private fun jsAudioDuration(audio: JsAny): Double = js("audio.duration")

private fun jsAudioPaused(audio: JsAny): Boolean = js("audio.paused")

private fun jsAudioPlay(audio: JsAny): Unit = js("{ var p = audio.play(); if (p && p.catch) p.catch(function () {}) }")

private fun jsAudioPause(audio: JsAny): Unit = js("audio.pause()")

private fun jsAudioSeek(audio: JsAny, seconds: Double): Unit = js("audio.currentTime = seconds")

private fun jsAudioOn(audio: JsAny, event: String, callback: () -> Unit): Unit = js("audio.addEventListener(event, function () { callback() })")

private fun jsAudioRelease(audio: JsAny): Unit = js("{ audio.pause(); audio.removeAttribute('src'); audio.load() }")

private fun jsAblySubscribe(tokenRequestJson: String, channel: String, onMessage: (String) -> Unit): Unit =
    js("""(function () {
        if (!window.Ably) { console.error('Ably is not loaded; notifications are disabled'); return }
        var tokenRequest = JSON.parse(tokenRequestJson)
        var ably = new window.Ably.Realtime({ authCallback: function (params, cb) { cb(null, tokenRequest) } })
        ably.channels.get(channel).subscribe(function (message) {
          onMessage(typeof message.data === 'string' ? message.data : JSON.stringify(message.data))
        })
    })()""")

private fun jsReload(): Unit = js("window.location.reload()")

private class WebPickedFile(val handle: JsAny) : PickedFile {
    override val name: String = jsFileName(this.handle)
    override val size: Long = jsFileSize(this.handle).toLong()
    override val type: String = jsFileType(this.handle)
}

private class WebAudio(url: String) : AudioHandle {

    private val element = jsCreateAudio(url)

    override val currentTime: Double get() = jsAudioCurrentTime(this.element)
    override val duration: Double get() = jsAudioDuration(this.element)
    override val paused: Boolean get() = jsAudioPaused(this.element)

    override fun play() = jsAudioPlay(this.element)

    override fun pause() = jsAudioPause(this.element)

    override fun seek(seconds: Double) = jsAudioSeek(this.element, seconds)

    override fun onTimeUpdate(callback: () -> Unit) = jsAudioOn(this.element, "timeupdate", callback)

    override fun onEnded(callback: () -> Unit) = jsAudioOn(this.element, "ended", callback)

    override fun release() = jsAudioRelease(this.element)
}

private object WebBrowser : Browser {

    override val path: String get() = jsPath()

    override val origin: String get() = jsOrigin()

    override fun pushPath(path: String) = jsPushPath(path)

    override fun replacePath(path: String) = jsReplacePath(path)

    override fun onPopState(callback: (String) -> Unit) = jsOnPopState(callback)

    override fun setTitle(title: String) = jsSetTitle(title)

    override fun setDocumentLanguage(language: String) = jsSetLanguage(language)

    override fun localGet(key: String): String? = jsLocalGet(key)

    override fun localSet(key: String, value: String) = jsLocalSet(key, value)

    override fun sessionGet(key: String): String? = jsSessionGet(key)

    override fun sessionSet(key: String, value: String) = jsSessionSet(key, value)

    override fun sessionRemove(key: String) = jsSessionRemove(key)

    override fun onStorage(callback: (key: String, value: String?) -> Unit) = jsOnStorage(callback)

    override suspend fun confirm(message: String): Boolean = jsConfirm(message)

    override fun open(url: String, target: String) = jsOpen(url, target)

    override fun timeZone(): String = jsTimeZone()

    override val isMac: Boolean by lazy { jsIsMac() }

    override fun prefersDark(): Boolean = jsPrefersDark()

    override fun onPrefersDarkChange(callback: (Boolean) -> Unit) = jsOnPrefersDarkChange(callback)

    override fun onVisible(callback: () -> Unit) = jsOnVisible(callback)

    override fun apiUrl(): String = jsApiUrl()

    override fun copyToClipboard(text: String) = jsCopy(text)

    override fun download(url: String, filename: String) = jsDownload(url, filename)

    override fun downloadText(text: String, filename: String, contentType: String) =
        jsDownloadText(text, filename, contentType)

    override fun pickFile(accept: String, callback: (PickedFile?) -> Unit) =
        jsPickFile(accept) { handle -> callback(handle?.let { WebPickedFile(it) }) }

    override fun upload(url: String, file: PickedFile, onProgress: (Double) -> Unit, onDone: (Int) -> Unit) =
        jsUpload(url, (file as WebPickedFile).handle, onProgress, onDone)

    override fun createAudio(url: String): AudioHandle = WebAudio(url)

    override fun ablySubscribe(tokenRequestJson: String, channel: String, onMessage: (String) -> Unit) =
        jsAblySubscribe(tokenRequestJson, channel, onMessage)

    override fun reload() = jsReload()
}

actual val browser: Browser = WebBrowser

/** relative URLs, sent with the gateway's session cookie by the browser */
actual fun createHttpClient(): HttpClient = HttpClient()

/** the gateway signs the browser in, before the page ever loads */
actual val signIn: SignIn = object : SignIn {
    override val available: Boolean = false

    override suspend fun signIn() = Unit

    override suspend fun signOut() = Unit
}
