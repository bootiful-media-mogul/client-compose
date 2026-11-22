package com.joshlong.mogul.client.podcasts

import com.joshlong.mogul.client.ui.LocalWindowWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.managedfiles.ManagedFileField
import com.joshlong.mogul.client.podcasts.segments.SegmentAudioEditor
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.durationToString

/**
 * one file of an episode: the graphic, or a segment's audio (PodcastEpisodeSegmentEditor.vue).
 * a segment row can move, be deleted, open its transcript, and open the audio editor, which
 * then takes the row's place.
 */
@Composable
internal fun PodcastEpisodeSegmentRow(
    label: String,
    managedFileId: Long,
    accept: String,
    type: String,
    segmentId: Long? = null,
    showReorder: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    showDelete: Boolean = false,
    showTranscript: Boolean = false,
    showAudioEditor: Boolean = false,
    cleaning: Boolean = false,
    cleaned: Boolean = false,
    cleanable: Boolean = false,
    cleaningError: String? = null,
    duration: Double? = null,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEditTranscript: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // when true, the audio editor replaces this row entirely
    var editingAudio by remember { mutableStateOf(false) }

    // a segment that's never been cleaned, for a mogul with a cleanvoice key, gets cleaned
    // first; the editor opens on the cleaned audio once that's done. otherwise -- no key, or a
    // cleaning that failed -- it opens on the original.
    fun openAudioEditor() {
        if (cleaning) return
        if (cleanable && !cleaned && cleaningError == null && segmentId != null) {
            scope.launchSafely { Mogul.podcasts.cleanPodcastEpisodeSegment(segmentId) }
            return
        }
        editingAudio = true
    }

    if (editingAudio && segmentId != null) {
        SegmentAudioEditor(label, segmentId, onClose = { editingAudio = false })
        return
    }

    val formattedDuration = durationToString(duration)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Gutter.full)
            .clip(RoundedCornerShape(Gutter.radius))
            .background(palette.panelBg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Row(
                Modifier
                    // minmax(12vw, 130px), as the Vue grid sized the type column
                    .widthIn(min = maxOf(130.dp, LocalWindowWidth.current * 0.12f))
                    .clip(RoundedCornerShape(bottomEnd = Gutter.radius))
                    .background(palette.chrome)
                    .padding(Gutter.half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                Icon(
                    if (type == "graphic") MogulIcon.ImageFile else MogulIcon.MusicFile,
                    t("icons.segment-type.$type"),
                    tint = palette.chromeInk,
                )
                Text(label, color = palette.chromeInk, fontSize = TextSize.sm)
            }
            Box(Modifier.weight(1f))
            if (showReorder) {
                Row(Modifier.padding(Gutter.half), horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                    IconButton(MogulIcon.Up, t("icons.move-up"), onClick = onMoveUp, enabled = canMoveUp)
                    IconButton(MogulIcon.Down, t("icons.move-down"), onClick = onMoveDown, enabled = canMoveDown)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        ManagedFileField(managedFileId, accept, Modifier.padding(Gutter.half))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Row(
            Modifier.fillMaxWidth().padding(Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.half),
        ) {
            if (showTranscript) {
                IconButton(MogulIcon.Transcript, t("icons.transcript"), onClick = onEditTranscript)
            }
            if (showAudioEditor) {
                IconButton(MogulIcon.AudioEditor, t("icons.audio-editor"), onClick = { openAudioEditor() }, enabled = !cleaning)
            }
            Text(
                formattedDuration ?: "",
                modifier = Modifier.weight(1f),
                color = palette.muted,
                fontFamily = fonts.mono,
                fontSize = TextSize.sm,
                textAlign = TextAlign.Center,
            )
            IconButton(MogulIcon.Delete, t("icons.delete-segment"), onClick = onDelete, enabled = showDelete)
        }
    }
}
