package com.joshlong.mogul.client.platform

import io.ktor.client.HttpClient

/**
 * everything the studio needs from the page it runs in. the Vue client reached for
 * `window` wherever it needed it; here it all goes through this one seam, so the rest of
 * the code is plain common Kotlin.
 */
interface Browser {

    /** the path and query of the current location, e.g. `/podcasts/3/episodes?x=1` */
    val path: String

    /** where the page was served from, e.g. `http://127.0.0.1:1010`, for libraries that won't take a relative URL */
    val origin: String

    fun pushPath(path: String)

    fun replacePath(path: String)

    /** the back and forward buttons. the callback gets the new path */
    fun onPopState(callback: (String) -> Unit)

    fun setTitle(title: String)

    fun setDocumentLanguage(language: String)

    // storage. every access can throw (private mode, blocked site data), so these swallow
    fun localGet(key: String): String?

    fun localSet(key: String, value: String)

    fun sessionGet(key: String): String?

    fun sessionSet(key: String, value: String)

    fun sessionRemove(key: String)

    /** another tab changed a localStorage key */
    fun onStorage(callback: (key: String, value: String?) -> Unit)

    /** the browser's own blocking confirmation, as the Vue client used for deletions */
    suspend fun confirm(message: String): Boolean

    fun open(url: String, target: String = "_blank")

    /** the browser's IANA zone, e.g. `America/Los_Angeles` */
    fun timeZone(): String

    val isMac: Boolean

    fun prefersDark(): Boolean

    fun onPrefersDarkChange(callback: (Boolean) -> Unit)

    /** the tab (or laptop) came back */
    fun onVisible(callback: () -> Unit)

    /** the api's public base URL, from /config.js; empty means same origin */
    fun apiUrl(): String

    fun copyToClipboard(text: String)

    /** a download of the given URL, saved as filename */
    fun download(url: String, filename: String)

    /** a download of text made here, saved as filename */
    fun downloadText(text: String, filename: String, contentType: String)

    /** asks for a file; the callback gets null if the dialog was dismissed */
    fun pickFile(accept: String, callback: (PickedFile?) -> Unit)

    /**
     * posts the file as multipart field `file` to url, reporting progress in [0, 1]. done
     * gets the HTTP status (0 for a network failure).
     */
    fun upload(url: String, file: PickedFile, onProgress: (Double) -> Unit, onDone: (Int) -> Unit)

    fun createAudio(url: String): AudioHandle

    /**
     * the whole file at url, downloaded, as a URL to give createAudio. an editor seeks
     * constantly, and a local copy seeks anywhere without the server answering range
     * requests, which the api's don't: without them Chrome can't seek and Safari won't
     * play. give it back with releaseAudio when done
     */
    suspend fun loadAudio(url: String): String

    fun releaseAudio(localUrl: String)

    /**
     * subscribes to an Ably channel, authenticating with a token request the api made.
     * every message's data (a JSON string) goes to onMessage
     */
    fun ablySubscribe(tokenRequestJson: String, channel: String, onMessage: (String) -> Unit)

    fun reload()
}

/** a file the user picked, still in the browser */
interface PickedFile {
    val name: String
    val size: Long
    val type: String
}

/** an HTML audio element */
interface AudioHandle {
    /** seconds */
    val currentTime: Double

    /** seconds, NaN until known */
    val duration: Double

    val paused: Boolean

    fun play()

    fun pause()

    fun seek(seconds: Double)

    fun onTimeUpdate(callback: () -> Unit)

    fun onEnded(callback: () -> Unit)

    fun release()
}

expect val browser: Browser

/**
 * the one HTTP client: on the web a plain one, which the gateway's session cookie authenticates;
 * in the apps one that resolves `/api/...` against the gateway and carries the bearer token
 */
expect fun createHttpClient(): HttpClient

/** signing in, for the apps, which are oauth clients of their own. the web build is signed in by the gateway */
interface SignIn {

    /** whether this build signs in itself (and so shows a sign-in screen when it isn't) */
    val available: Boolean

    suspend fun signIn()

    suspend fun signOut()
}

expect val signIn: SignIn
