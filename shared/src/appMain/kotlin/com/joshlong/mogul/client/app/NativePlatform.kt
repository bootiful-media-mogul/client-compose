package com.joshlong.mogul.client.app

import com.joshlong.mogul.client.platform.AudioHandle
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore

/** the little each app does its own way: the rest of the browser seam is common to them all */
internal expect object NativePlatform {

    /** the gateway, as this device reaches it (the android emulator knows this machine by another address) */
    fun server(configured: String): String

    /** where auth0 sends the app back to once you've signed in */
    val redirectUri: String

    val authFlows: CodeAuthFlowFactory

    /** where the tokens are kept: the keystore, the keychain, or the user's preferences */
    val tokenStore: TokenStore

    fun openUrl(url: String)

    fun copyToClipboard(text: String)

    /** whether shortcuts are spelled with ⌘ */
    val isMac: Boolean

    fun prefersDark(): Boolean

    /** a player for the audio at url, sent with headers (the bearer token, for the api's own files) */
    fun createAudio(url: String, headers: Map<String, String>): AudioHandle

    /** keeps a download where the device keeps downloads */
    suspend fun saveDownload(bytes: ByteArray, filename: String)

    fun setTitle(title: String)
}
