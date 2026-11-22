package com.joshlong.mogul.client.transcripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.parseStringContext
import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.events.SidebarPanel
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.writing.writingTools
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * the transcript being edited. it lives outside the panel, whose content is only drawn while
 * it's expanded: asking to edit a transcript is what expands it.
 */
internal object TranscriptsStore {

    private val scope = MainScope()

    private var started = false

    var title by mutableStateOf("")
    var transcriptId by mutableStateOf(0L)

    /** what the transcript was when it was loaded (or saved), to know when it's been edited */
    var saved by mutableStateOf("")
    var transcript by mutableStateOf("")

    /** a re-transcription is running */
    var busy by mutableStateOf(false)

    val dirty: Boolean get() = this.transcript != this.saved

    fun start() {
        if (this.started) return
        this.started = true
        this.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            Events.events.collect { event ->
                if (event is AppEvent.TranscriptEdit) {
                    this@TranscriptsStore.transcript = event.transcript
                    this@TranscriptsStore.saved = event.transcript
                    this@TranscriptsStore.title = event.title
                    this@TranscriptsStore.transcriptId = event.id
                    Events.emit(AppEvent.SidebarPanelOpened(SidebarPanel.Transcripts))
                }
            }
        }
        // the store lives as long as the app, so these are never unregistered
        Mogul.notifications.listenForCategory("transcript-processed-event") { notification ->
            if (notification.key.toLongOrNull() == this.transcriptId) {
                this.transcript = notification.context ?: ""
                this.saved = this.transcript
            }
            this.busy = false
        }
        Mogul.notifications.listenForCategory("transcript-completed-event") { notification ->
            val context = parseStringContext(notification.context)
            if (context["transcriptId"]?.toDoubleOrNull()?.toLong() != this.transcriptId) return@listenForCategory
            this.transcript = context["transcript"] ?: ""
            this.saved = this.transcript
            this.busy = false
        }
    }

    fun cancel() {
        this.title = ""
        this.transcriptId = 0
        this.transcript = ""
        this.saved = ""
    }

    suspend fun refresh() {
        Mogul.transcripts.refreshTranscript(this.transcriptId)
        this.busy = true
        this.saved = this.transcript
    }

    suspend fun save() {
        Mogul.transcripts.writeTranscript(this.transcriptId, this.transcript)
        this.saved = this.transcript
    }
}

/** the transcripts sidebar panel (TranscriptComponent.vue) */
@Composable
fun TranscriptsPanel(modifier: Modifier = Modifier) {
    val store = TranscriptsStore
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.start() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        MogulTextField(
            store.transcript,
            { store.transcript = it },
            label = store.title.takeIf { it.isNotEmpty() }?.let { t("transcripts.text", "title" to it) },
            required = true,
            singleLine = false,
            minLines = 10,
            enabled = !store.busy,
            showCount = true,
            tools = writingTools(),
        )
        ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            MogulButton(t("transcripts.buttons.refresh"), onClick = { scope.launch { store.refresh() } }, enabled = store.transcriptId > 0)
            MogulButton(t("transcripts.buttons.save"), onClick = { scope.launch { store.save() } }, enabled = store.dirty)
            MogulButton(t("transcripts.buttons.cancel"), onClick = { store.cancel() }, enabled = store.dirty)
        }
    }
}
