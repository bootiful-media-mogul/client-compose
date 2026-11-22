package com.joshlong.mogul.client.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Note
import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulCheckbox
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.dateTimeToString
import com.joshlong.mogul.client.writing.writingTools
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** the api only takes absolute http(s) urls, since a note's url is shown as a link */
internal fun isWebUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    val rest = when {
        lower.startsWith("https://") -> lower.removePrefix("https://")
        lower.startsWith("http://") -> lower.removePrefix("http://")
        else -> return false
    }
    return rest.isNotEmpty() && !rest.startsWith("/") && rest.none { it.isWhitespace() }
}

internal fun blankToNull(value: String): String? = value.trim().ifEmpty { null }

/**
 * what the notes panel shows. it lives outside the panel: the panel's content is only drawn
 * while it's expanded, but a page asks for its notes when it loads, collapsed or not.
 */
internal object NotesStore {

    private val scope = MainScope()

    private var started = false

    var mogulId by mutableStateOf(-1L)
        private set
    var mogulName by mutableStateOf("")
        private set
    var mogulNotes by mutableStateOf<List<Note>>(emptyList())
        private set
    var entityNotes by mutableStateOf<List<Note>>(emptyList())
        private set
    var notableId by mutableStateOf(-1L)
        private set
    var type by mutableStateOf("mogul")
        private set
    var entityLoaded by mutableStateOf(false)
        private set
    var entityName by mutableStateOf("")
        private set
    var showDoneNotes by mutableStateOf(false)

    // the composer
    var showComposer by mutableStateOf(false)
    var noteTarget by mutableStateOf("mogul")
    var noteId by mutableStateOf(-1L)
    var noteText by mutableStateOf("")
    var noteTitle by mutableStateOf("")
    var noteUrl by mutableStateOf("")
    var urlError by mutableStateOf(false)

    fun start() {
        if (this.started) return
        this.started = true
        // undispatched, so it's listening before anything a page does on load can tell it
        this.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            Events.events.collect { event ->
                when (event) {
                    is AppEvent.ResetNotesForNotable -> {
                        this@NotesStore.notableId = -1
                        this@NotesStore.type = "mogul"
                        this@NotesStore.entityNotes = emptyList()
                        this@NotesStore.entityLoaded = false
                        this@NotesStore.showComposer = false
                        // keep the mogul's own notes fresh
                        reloadMogulNotes()
                    }
                    is AppEvent.NotesForNotable -> {
                        this@NotesStore.notableId = event.notableId
                        this@NotesStore.entityName = event.entityName
                        this@NotesStore.type = event.type
                        this@NotesStore.entityLoaded = true
                        reload()
                    }
                    else -> {}
                }
            }
        }
        this.scope.launch {
            val user = runCatching { Mogul.mogul.user() }.getOrNull() ?: return@launch
            this@NotesStore.mogulName = user.givenName ?: ""
            this@NotesStore.mogulId = user.id
            reload()
        }
    }

    private suspend fun reloadMogulNotes() {
        if (this.mogulId < 0) return
        this.mogulNotes = runCatching { Mogul.notes.notesForNotable(this.mogulId, "mogul", this.showDoneNotes) }.getOrElse { emptyList() }
    }

    suspend fun reload() {
        reloadMogulNotes()
        if (this.notableId > 0 && this.type.isNotBlank()) {
            this.entityNotes = runCatching { Mogul.notes.notesForNotable(this.notableId, this.type, this.showDoneNotes) }.getOrElse { emptyList() }
        }
    }

    /** a note can be just a title or just a url, as the clipper makes them */
    fun hasContent(): Boolean = listOf(this.noteText, this.noteTitle, this.noteUrl).any { it.isNotBlank() }

    fun loadIntoEditor(note: Note) {
        this.noteText = note.note
        this.noteTitle = note.title ?: ""
        this.noteUrl = note.url ?: ""
        this.urlError = false
        this.noteId = note.id
        this.noteTarget = if (note.type == "mogul") "mogul" else "entity"
        this.showComposer = true
    }

    fun showComposerFor(target: String) {
        this.noteTarget = target
        this.showComposer = true
    }

    fun clear() {
        this.noteText = ""
        this.noteTitle = ""
        this.noteUrl = ""
        this.urlError = false
        this.noteId = -1
        this.showComposer = false
    }

    suspend fun save() {
        val (id, type) = if (this.noteTarget == "entity") this.notableId to this.type else this.mogulId to "mogul"
        val title = blankToNull(this.noteTitle)
        val url = blankToNull(this.noteUrl)
        this.urlError = url != null && !isWebUrl(url)
        if (this.urlError) return
        if (this.noteId > 0) Mogul.notes.updateNote(this.noteId, this.noteText, title, url)
        else Mogul.notes.createNote(type, id, this.noteText, title, url)
        reload()
        clear()
    }
}

/** the notes sidebar panel (NotesComponent.vue): the page's notes, then the mogul's own */
@Composable
fun NotesPanel(modifier: Modifier = Modifier) {
    val store = NotesStore
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.start() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        if (store.showComposer) {
            Column(verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
                MogulTextField(store.noteTitle, { store.noteTitle = it }, label = t("notes.fields.title"))
                MogulTextField(store.noteUrl, { store.noteUrl = it }, label = t("notes.fields.url"))
                if (store.urlError) Text(t("notes.errors.url"), color = palette.danger, fontSize = TextSize.sm)
                MogulTextField(
                    store.noteText,
                    { store.noteText = it },
                    label = t("notes.new.prompt", "entityName" to if (store.noteTarget == "entity") store.entityName else store.mogulName),
                    singleLine = false,
                    minLines = 10,
                    showCount = true,
                    tools = writingTools(),
                )
                ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                    MogulButton(t("notes.buttons.save"), onClick = { scope.launch { store.save() } }, enabled = store.hasContent())
                    MogulButton(t("transcripts.buttons.cancel"), onClick = { store.clear() }, enabled = store.hasContent())
                }
                Box(Modifier.fillMaxWidth().padding(vertical = Gutter.half).height(2.dp).background(palette.rule))
            }
        }
        MogulCheckbox(
            store.showDoneNotes,
            {
                store.showDoneNotes = it
                scope.launch { store.reload() }
            },
            t("notes.filters.show-done"),
        )
        if (store.entityLoaded) {
            NotesSection(t("notes.entity.title", "entityName" to store.entityName), store.entityNotes, onNew = { store.showComposerFor("entity") })
        }
        NotesSection(t("notes.system-wide.title"), store.mogulNotes, onNew = { store.showComposerFor("mogul") })
    }
}

@Composable
private fun NotesSection(title: String, notes: List<Note>, onNew: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = Gutter.half), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.SemiBold)
            IconButton(MogulIcon.Plus, t("icons.new-note"), onClick = onNew)
        }
        notes.forEachIndexed { index, note ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            NoteItem(
                note,
                onDelete = { scope.launch { Mogul.notes.deleteNote(note.id); NotesStore.reload() } },
                onDone = { done -> scope.launch { Mogul.notes.setNoteDone(note.id, done); NotesStore.reload() } },
                onEdit = { NotesStore.loadIntoEditor(note) },
            )
        }
    }
}

/** one note (NoteEditor.vue): when, what, and the done, edit and delete controls */
@Composable
private fun NoteItem(note: Note, onDelete: () -> Unit, onDone: (Boolean) -> Unit, onEdit: () -> Unit) {
    val done = note.done != null
    Column(Modifier.fillMaxWidth().padding(bottom = Gutter.half)) {
        Text(
            dateTimeToString(note.created) ?: "",
            Modifier.fillMaxWidth().padding(top = Gutter.half, bottom = Gutter.half),
            color = palette.ink,
            fontSize = TextSize.sm,
            textAlign = TextAlign.End,
        )
        note.title?.takeIf { it.isNotEmpty() }?.let {
            Text(it, Modifier.padding(bottom = Gutter.half), color = palette.ink, fontWeight = FontWeight.Bold)
        }
        note.url?.takeIf { it.isNotEmpty() }?.let { url ->
            Text(
                url,
                Modifier.padding(bottom = Gutter.half).pointerHoverIcon(PointerIcon.Hand).clickable { browser.open(url) },
                color = palette.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            note.note,
            color = if (done) palette.ink.copy(alpha = 0.65f) else palette.ink,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        )
        Row(
            Modifier.padding(vertical = Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.half),
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = onDone,
                colors = CheckboxDefaults.colors(checkedColor = palette.accent, uncheckedColor = palette.fieldBorder, checkmarkColor = palette.accentInk),
            )
            IconButton(MogulIcon.Edit, t("icons.edit-note"), onClick = onEdit)
            IconButton(MogulIcon.Delete, t("icons.delete-note"), onClick = onDelete)
        }
    }
}
