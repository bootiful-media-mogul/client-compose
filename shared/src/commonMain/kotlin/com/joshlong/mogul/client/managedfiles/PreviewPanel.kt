package com.joshlong.mogul.client.managedfiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.joshlong.mogul.client.api.ManagedFile
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.events.SidebarPanel
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.prettyPrintInBytes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** what a managed file is, as far as previewing it goes */
internal class PreviewableFile(val file: ManagedFile) {

    /** a visible file is served from its public URL; otherwise the api serves it to its owner */
    val url: String = if (this.file.visible && !this.file.visibleUrl.isNullOrBlank()) this.file.visibleUrl else "/api" + (this.file.url ?: "")

    /**
     * where an image's bytes are fetched from. unlike an <img>, Coil fetches with fetch(), which
     * needs CORS headers the public CDN doesn't send, so it always goes through the api, same-origin.
     * absolute, since Coil only gives http(s) URLs to its network fetcher: a bare path is never fetched
     */
    val imageUrl: String = browser.origin + "/api" + (this.file.url ?: "")

    val contentType: String = (this.file.contentType ?: "").lowercase()

    val isImage: Boolean = listOf("jpg", "jpeg", "png", "gif").any { this.contentType.endsWith(it) }

    val isAudio: Boolean = listOf("mp3", "wav", "mpeg").any { this.contentType.endsWith(it) }
}

/**
 * the file the preview panel shows. it lives outside the panel, whose content is only drawn
 * while it's expanded: asking for a preview is what expands it.
 */
internal object PreviewStore {

    private val scope = MainScope()

    private var started = false

    var previewing by mutableStateOf<PreviewableFile?>(null)
        private set

    fun start() {
        if (this.started) return
        this.started = true
        this.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            Events.events.collect { event ->
                if (event is AppEvent.PreviewManagedFile) {
                    val file = runCatching { Mogul.managedFiles.managedFileById(event.managedFileId) }.getOrNull()
                    if (file != null) {
                        this@PreviewStore.previewing = PreviewableFile(file)
                        Events.emit(AppEvent.SidebarPanelOpened(SidebarPanel.Preview))
                    }
                }
            }
        }
    }
}

/** the media preview sidebar panel (PreviewComponent.vue) */
@Composable
fun PreviewPanel(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { PreviewStore.start() }
    val previewing = PreviewStore.previewing ?: return
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory(httpClient = { Mogul.http })) }.build()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        if (previewing.isImage) {
            AsyncImage(
                model = previewing.imageUrl,
                contentDescription = t("managedfiles.alt.preview", "id" to previewing.file.id),
                imageLoader = imageLoader,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (previewing.isAudio) {
            AudioPlayer(previewing.url)
        }
        Text(previewing.file.filename ?: "", color = palette.ink, fontWeight = FontWeight.Bold)
        Text(previewing.contentType, color = palette.ink, fontFamily = fonts.mono, fontSize = TextSize.sm)
        Text(prettyPrintInBytes(previewing.file.size), color = palette.ink, fontFamily = fonts.mono, fontSize = TextSize.sm)
        if (previewing.url.isNotBlank()) {
            IconButton(MogulIcon.Link, t("icons.open-link"), onClick = { browser.open(previewing.url, "managedFilePublicUrl") })
        }
    }
}

/** a chrome button with a drawn play triangle or pause bars: as ▶ text, the browser's emoji font drew it */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val ink = palette.chromeInk
    Canvas(
        Modifier
            .clip(RoundedCornerShape(Gutter.radius))
            .background(palette.chrome)
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .size(14.dp),
    ) {
        if (playing) {
            val bar = size.width / 3
            drawRect(ink, size = Size(bar, size.height))
            drawRect(ink, topLeft = Offset(size.width - bar, 0f), size = Size(bar, size.height))
        } else {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(0f, size.height)
                    close()
                },
                ink,
            )
        }
    }
}

private fun clock(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "0:00"
    val total = seconds.toLong()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** an audio element's controls: play or pause, where you are, and a scrubber */
@Composable
internal fun AudioPlayer(url: String, modifier: Modifier = Modifier) {
    var position by remember(url) { mutableStateOf(0.0) }
    var duration by remember(url) { mutableStateOf(Double.NaN) }
    var playing by remember(url) { mutableStateOf(false) }
    val audio = remember(url) { browser.createAudio(url) }
    DisposableEffect(audio) {
        audio.onTimeUpdate {
            position = audio.currentTime
            duration = audio.duration
            playing = !audio.paused
        }
        audio.onEnded { playing = false }
        onDispose { audio.release() }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
        PlayPauseButton(playing) {
            if (audio.paused) audio.play() else audio.pause()
            playing = !audio.paused
        }
        Slider(
            value = if (duration.isNaN() || duration <= 0) 0f else (position / duration).toFloat().coerceIn(0f, 1f),
            onValueChange = { fraction ->
                if (!duration.isNaN() && duration > 0) {
                    position = fraction * duration
                    audio.seek(position)
                }
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent, inactiveTrackColor = palette.rule),
        )
        Text("${clock(position)} / ${clock(duration)}", color = palette.muted, fontFamily = fonts.mono, fontSize = TextSize.xs)
    }
}
