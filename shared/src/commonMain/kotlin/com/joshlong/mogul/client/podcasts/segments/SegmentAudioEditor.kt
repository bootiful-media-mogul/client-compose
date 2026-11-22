package com.joshlong.mogul.client.podcasts.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.KeptRange
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Notification
import com.joshlong.mogul.client.api.SegmentFilters
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.api.parseStringContext
import com.joshlong.mogul.client.events.previewManagedFile
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.ListenForNotification
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulCheckbox
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong

private enum class Phase { Loading, NoAudio, Drawing, Ready, Error }

private const val KEY = "podcasts.episodes.episode.segments.audio-editor"

/** what the editor opens on: the audio as it plays, its waveform, and the saved edits */
private class Opened(
    val waveform: Waveform,
    val audioUrl: String,
    /** seconds */
    val kept: List<Range>?,
)

/** a number as it reads in a seconds field: `2`, not `2.0` */
private fun secondsText(seconds: Double): String = jsNumber(seconds)

/**
 * the audio editor for one segment (PodcastEpisodeSegmentAudioEditor.vue): it loads the
 * segment's upload and waveform, opens the track editor on them, offers the automatic cuts
 * once the audio's been cleaned, and saves the edits, which the processors then render.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentAudioEditor(label: String, segmentId: Long, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.Loading) }
    var error by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Opened?>(null) }
    // edited since the last save
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // the save went through and the processors are rendering the produced audio
    var rendering by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    // the rendered segment, what the episode will actually contain. the editor itself plays
    // the upload and skips the cuts; this is the real thing
    var producedAudioId by remember { mutableStateOf<Long?>(null) }
    var producedAudioWritten by remember { mutableStateOf(false) }
    var waveformRequested by remember { mutableStateOf(false) }
    // the automatic cuts: only once the audio has been cleaned and analysed
    var cleaned by remember { mutableStateOf(false) }
    var cleaningError by remember { mutableStateOf<String?>(null) }
    var detections by remember { mutableStateOf<Detections?>(null) }
    var filters by remember { mutableStateOf<SegmentFilters?>(null) }
    var restored by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editor by remember { mutableStateOf<TrackEditorState?>(null) }

    fun changed() {
        dirty = true
        status = ""
    }

    suspend fun load() {
        try {
            val segment = Mogul.podcasts.podcastEpisodeSegmentById(segmentId)
            filename = segment.audio?.filename ?: ""
            producedAudioId = segment.producedAudio?.id
            producedAudioWritten = segment.producedAudio?.written ?: false
            if (segment.audio?.written != true) {
                phase = Phase.NoAudio
                return
            }
            if (segment.waveform?.written != true) {
                // uploads draw their own; this is for segments from before they did, or an
                // upload whose waveform is still being drawn. either way the notification reloads us
                phase = Phase.Drawing
                if (!waveformRequested) {
                    waveformRequested = true
                    Mogul.podcasts.requestPodcastEpisodeSegmentWaveform(segmentId)
                }
                return
            }
            // once it's been cleaned, the cleaned audio is what gets rendered, so it's what plays
            val audio = segment.audio!!
            val playing = segment.cleanedAudio?.takeIf { segment.cleaned } ?: audio
            val drawn = loadWaveform(segment.waveform!!.url!!)
            opened = Opened(
                drawn,
                "/api" + playing.url,
                segment.edits?.map { Range(it.start / 1000.0, it.end / 1000.0) },
            )
            cleaned = segment.cleaned
            cleaningError = segment.cleaningError
            detections = if (segment.cleaned && segment.detections != null) json.decodeFromString<Detections>(segment.detections) else null
            filters = segment.filters
            restored = segment.restored.toSet()
            phase = Phase.Ready
        } catch (e: Exception) {
            println("couldn't open the audio editor for podcast episode segment $segmentId: ${e.message}")
            error = e.message ?: e.toString()
            phase = Phase.Error
        }
    }

    LaunchedEffect(segmentId) { load() }

    fun isThisSegment(notification: Notification) = parseStringContext(notification.context)["segmentId"] == segmentId.toString()

    ListenForNotification("audio-waveform-computed-event") { notification ->
        if (!isThisSegment(notification) || phase != Phase.Drawing) return@ListenForNotification
        val outcome = parseStringContext(notification.context)
        if (outcome["success"] == "false") {
            error = outcome["error"] ?: ""
            phase = Phase.Error
            return@ListenForNotification
        }
        scope.launch { load() }
    }
    ListenForNotification("media-normalized-event") { notification ->
        if (!isThisSegment(notification) || !rendering) return@ListenForNotification
        rendering = false
        producedAudioWritten = true
        status = t("$KEY.saved")
    }
    ListenForNotification("media-normalization-failed-event") { notification ->
        if (!isThisSegment(notification) || !rendering) return@ListenForNotification
        rendering = false
        status = t("$KEY.render-failed", "error" to parseStringContext(notification.context)["error"])
    }

    /**
     * the editor's ranges in whole milliseconds, or null when they amount to the whole file:
     * an edit that keeps everything is no edit, and the segment should say so
     */
    fun keptForSaving(state: TrackEditorState, durationSeconds: Double): List<KeptRange>? {
        val kept = state.keptRanges().map { KeptRange((it.start * 1000).roundToLong(), (it.end * 1000).roundToLong()) }
        val everything = kept.size == 1 && kept[0].start <= 10 && kept[0].end >= (durationSeconds * 1000).roundToLong() - 10
        return if (everything) null else kept
    }

    fun save() {
        val state = editor ?: return
        val current = opened ?: return
        val kept = keptForSaving(state, current.waveform.durationSeconds)
        saving = true
        status = t("$KEY.saving")
        scope.launch {
            try {
                Mogul.podcasts.editPodcastEpisodeSegment(segmentId, kept, if (cleaned) filters else null, restored.toList())
                dirty = false
                rendering = true
                status = t("$KEY.rendering")
            } catch (e: Exception) {
                println("couldn't save the edits to podcast episode segment $segmentId: ${e.message}")
                status = t("$KEY.save-failed", "error" to (e.message ?: e.toString()))
            } finally {
                saving = false
            }
        }
    }

    val canListen = producedAudioId != null && producedAudioWritten && !rendering

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Gutter.radius)).background(palette.panelBg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // the editor's name, on a chrome tab hanging from the top-left corner
            Row(
                Modifier
                    .widthIn(min = 130.dp)
                    .clip(RoundedCornerShape(bottomEnd = Gutter.radius))
                    .background(palette.chrome)
                    .padding(Gutter.half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                Icon(MogulIcon.AudioEditor, null, size = 24.dp, tint = palette.chromeInk)
                Text(label, color = palette.chromeInk, fontSize = TextSize.sm)
            }
            Row(
                Modifier.weight(1f).padding(Gutter.half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gutter.half, Alignment.End),
            ) {
                Text(status, color = palette.ink, fontSize = TextSize.sm)
                if (phase == Phase.Ready) {
                    IconButton(
                        MogulIcon.Preview,
                        t("$KEY.listen"),
                        onClick = { producedAudioId?.takeIf { canListen }?.let { previewManagedFile(it) } },
                        enabled = canListen,
                    )
                    MogulButton(t("$KEY.save"), onClick = ::save, enabled = dirty && !saving && !rendering)
                }
                IconButton(MogulIcon.Close, t("$KEY.close"), onClick = onClose)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Column(Modifier.fillMaxWidth().background(palette.panelBg)) {
            when (phase) {
                Phase.Ready -> {
                    cleaningError?.let {
                        StatusLine(t("$KEY.not-cleaned", "reason" to it))
                    }
                    val current = filters
                    if (cleaned && current != null) {
                        Filters(current) {
                            filters = it
                            changed()
                        }
                    }
                    opened?.let { open ->
                        val clip = remember(open) {
                            TrackClip(segmentId, filename.ifBlank { label }, open.waveform.durationSeconds, open.waveform.peaks, open.audioUrl, open.kept)
                        }
                        val state = rememberTrackEditorState(clip) { edit ->
                            println("audio editor edit for podcast episode segment $segmentId: ${edit.operation}")
                            changed()
                        }
                        editor = state
                        // what the automatic cuts would take out, drawn on the clips; restored
                        // ones too, so they can be cut again
                        val marks = removals(detections, filters, emptySet()).map { removal ->
                            TrackMark(removal.id, removal.start / 1000, removal.end / 1000, t("$KEY.marks.${removal.kind}"), removal.id !in restored)
                        }
                        TrackEditor(state, marks, onToggleMark = { id ->
                            restored = if (id in restored) restored - id else restored + id
                            changed()
                        })
                    }
                }
                Phase.Error -> StatusLine(t("$KEY.error", "error" to error), color = palette.danger)
                Phase.NoAudio -> StatusLine(t("$KEY.no-audio"))
                Phase.Drawing -> StatusLine(t("$KEY.drawing"))
                Phase.Loading -> StatusLine(t("$KEY.loading"))
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color = palette.ink) {
    Text(text, Modifier.padding(Gutter.full), color = color, fontSize = TextSize.sm)
}

/** the automatic cuts in effect, and the silence thresholds, in seconds as people think of pauses */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(filters: SegmentFilters, onChange: (SegmentFilters) -> Unit) {
    var longerThan by remember { mutableStateOf(secondsText(filters.silenceLongerThan / 1000.0)) }
    var leave by remember { mutableStateOf(secondsText(filters.silenceLeave / 1000.0)) }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Gutter.full, vertical = Gutter.half),
        horizontalArrangement = Arrangement.spacedBy(Gutter.full),
        verticalArrangement = Arrangement.spacedBy(Gutter.half),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        MogulCheckbox(filters.fillers, { onChange(filters.copy(fillers = it)) }, t("$KEY.filters.fillers"))
        MogulCheckbox(filters.stutters, { onChange(filters.copy(stutters = it)) }, t("$KEY.filters.stutters"))
        MogulCheckbox(filters.silences, { onChange(filters.copy(silences = it)) }, t("$KEY.filters.silences"))
        Row(
            Modifier.alpha(if (filters.silences) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.third),
        ) {
            Text(t("$KEY.filters.longer-than"), color = palette.ink, fontSize = TextSize.sm)
            MogulTextField(
                longerThan,
                { text ->
                    longerThan = text
                    text.toDoubleOrNull()?.let { onChange(filters.copy(silenceLongerThan = (max(0.5, it) * 1000).roundToLong())) }
                },
                Modifier.width(72.dp),
                enabled = filters.silences,
                keyboardType = KeyboardType.Decimal,
            )
        }
        Row(
            Modifier.alpha(if (filters.silences) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.third),
        ) {
            Text(t("$KEY.filters.leave"), color = palette.ink, fontSize = TextSize.sm)
            MogulTextField(
                leave,
                { text ->
                    leave = text
                    text.toDoubleOrNull()?.let { onChange(filters.copy(silenceLeave = (max(0.0, it) * 1000).roundToLong())) }
                },
                Modifier.width(72.dp),
                enabled = filters.silences,
                keyboardType = KeyboardType.Decimal,
            )
        }
        Text(t("$KEY.filters.hint"), color = palette.muted, fontSize = TextSize.sm)
    }
}
