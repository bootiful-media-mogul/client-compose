package com.joshlong.mogul.client.app

import com.joshlong.mogul.client.platform.AudioHandle
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.set
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.IosCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsStore
import org.publicvalue.multiplatform.oidc.tokenstore.SettingsTokenStore
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrService
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.currentTraitCollection
import platform.darwin.NSObjectProtocol

@OptIn(ExperimentalOpenIdConnect::class)
internal actual object NativePlatform {

    // the simulator shares this machine's network, so 127.0.0.1 is the gateway running here
    actual fun server(configured: String): String = configured

    actual val redirectUri: String = appConfig.redirectUri

    actual val authFlows: CodeAuthFlowFactory by lazy { IosCodeAuthFlowFactory() }

    actual val tokenStore: TokenStore by lazy { SettingsTokenStore(Keychain()) }

    actual fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    actual fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    // an iPad's keyboard spells its shortcuts with ⌘
    actual val isMac: Boolean = true

    actual fun prefersDark(): Boolean =
        UITraitCollection.currentTraitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark

    actual fun createAudio(url: String, headers: Map<String, String>): AudioHandle = IosAudio(url, headers)

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun saveDownload(bytes: ByteArray, filename: String) = withContext(Dispatchers.IO) {
        val documents = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as NSURL
        val target = documents.URLByAppendingPathComponent(filename) ?: error("couldn't save $filename")
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToURL(target, atomically = true)
        Unit
    }

    actual fun setTitle(title: String) = Unit
}

/** the keychain, under a name of the studio's own, for the tokens */
@OptIn(ExperimentalForeignApi::class, ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
private class Keychain : SettingsStore {

    private val keychain by lazy {
        KeychainSettings(
            kSecAttrService to CFBridgingRetain("com.joshlong.mogul.studio.auth"),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
        )
    }

    override suspend fun get(key: String): String? = this.keychain.getStringOrNull(key)

    override suspend fun put(key: String, value: String) {
        this.keychain[key] = value
    }

    override suspend fun remove(key: String) = this.keychain.remove(key)

    override suspend fun clear() = this.keychain.clear()
}

/** an audio element, on AVPlayer, which streams the URL itself and sends the token as a header */
@OptIn(ExperimentalForeignApi::class)
private class IosAudio(url: String, headers: Map<String, String>) : AudioHandle {

    private val item = AVPlayerItem(
        asset = AVURLAsset(
            uRL = NSURL.URLWithString(url)!!,
            // not in the public headers, but how everyone sends AVPlayer's requests headers
            options = mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to headers),
        ),
    )

    private val player = AVPlayer(playerItem = this.item)

    private var timeUpdate: (() -> Unit)? = null

    private var ended: (() -> Unit)? = null

    // the web's timeupdate, about four times a second
    private val timeObserver: Any? = this.player.addPeriodicTimeObserverForInterval(
        CMTimeMakeWithSeconds(0.25, 600),
        queue = null,
    ) { _ -> this.timeUpdate?.invoke() }

    private val endObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
        AVPlayerItemDidPlayToEndTimeNotification,
        `object` = this.item,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        this.player.pause()
        this.ended?.invoke()
    }

    private fun seconds(time: kotlinx.cinterop.CValue<platform.CoreMedia.CMTime>): Double = CMTimeGetSeconds(time)

    override val currentTime: Double get() = this.seconds(this.player.currentTime()).takeIf { !it.isNaN() } ?: 0.0

    override val duration: Double
        get() = this.player.currentItem?.let { this.seconds(it.duration) }?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0 } ?: Double.NaN

    override val paused: Boolean get() = this.player.rate == 0.0f

    override fun play() = this.player.play()

    override fun pause() {
        this.player.pause()
        this.timeUpdate?.invoke()
    }

    override fun seek(seconds: Double) {
        this.player.seekToTime(CMTimeMakeWithSeconds(seconds, 600))
        this.timeUpdate?.invoke()
    }

    override fun onTimeUpdate(callback: () -> Unit) {
        this.timeUpdate = callback
    }

    override fun onEnded(callback: () -> Unit) {
        this.ended = callback
    }

    override fun release() {
        this.player.pause()
        this.timeObserver?.let { this.player.removeTimeObserver(it) }
        NSNotificationCenter.defaultCenter.removeObserver(this.endObserver)
    }
}
