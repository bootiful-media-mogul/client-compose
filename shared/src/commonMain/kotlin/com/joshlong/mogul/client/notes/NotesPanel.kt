package com.joshlong.mogul.client.notes

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
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
import com.joshlong.mogul.client.ui.DragHandle
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.LayoutWidth
import com.joshlong.mogul.client.ui.LocalLayoutWidth
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulCheckbox
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulReadOnlyField
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.SectionHeading
import com.joshlong.mogul.client.ui.dateTimeToString
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
 * a note as it's dropped into a field: its title as a markdown link to its url, then a dash and
 * its text. whatever it lacks is left out, and a note with nothing in it is null.
 */
internal fun noteAsText(note: Note): String? {
    val title = note.title?.trim().orEmpty()
    val url = note.url?.trim().orEmpty()
    val head = when {
        title.isNotEmpty() && url.isNotEmpty() -> "[${title.replace("[", "\\[").replace("]", "\\]")}]($url)"
        else -> title.ifEmpty { url }
    }
    return listOf(head, note.note.trim()).filter { it.isNotEmpty() }.joinToString(" - ").ifEmpty { null }
}

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
        this.mogulNotes = runCatching {
            Mogul.notes.notesForNotable(
                this.mogulId,
                "mogul",
                this.showDoneNotes
            )
        }.getOrElse { emptyList() }
    }

    suspend fun reload() {
        reloadMogulNotes()
        if (this.notableId > 0 && this.type.isNotBlank()) {
            this.entityNotes = runCatching {
                Mogul.notes.notesForNotable(
                    this.notableId,
                    this.type,
                    this.showDoneNotes
                )
            }.getOrElse { emptyList() }
        }
    }

    /** a note can be just a title or just a url, as the clipper makes them */
    fun hasContent(): Boolean =
        listOf(this.noteText, this.noteTitle, this.noteUrl).any { it.isNotBlank() }

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
        // a new note doesn't start from the one being edited
        if (this.noteId > 0) clear()
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
        // a new note is written up here; an existing one is edited where it's shown
        if (store.showComposer && store.noteId < 0) {
            NoteComposer()
            Box(
                Modifier.fillMaxWidth().padding(vertical = Gutter.half).height(2.dp)
                    .background(palette.rule)
            )
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
            NotesSection(
                t("notes.entity.title", "entityName" to store.entityName),
                store.entityNotes,
                onNew = { store.showComposerFor("entity") })
        }
        NotesSection(
            t("notes.system-wide.title"),
            store.mogulNotes,
            onNew = { store.showComposerFor("mogul") })
    }
}

/** the label over a note's text, the same in the composer as in the note it edits */
@Composable
private fun noteLabel(): String = t("notes.new.description")

/** the note being written or edited: its title, url and text, then save and cancel */
@Composable
private fun NoteComposer() {
    val store = NotesStore
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        MogulTextField(store.noteTitle, { store.noteTitle = it }, label = t("notes.fields.title"))
        MogulTextField(store.noteUrl, { store.noteUrl = it }, label = t("notes.fields.url"))
        if (store.urlError) Text(
            t("notes.errors.url"),
            color = palette.danger,
            fontSize = TextSize.sm
        )
        MogulTextField(
            store.noteText,
            { store.noteText = it },
            label = noteLabel(),
            singleLine = false,
            minLines = 10,
            showCount = true,
        )
        ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            MogulButton(
                t("notes.buttons.save"),
                onClick = { scope.launch { store.save() } },
                enabled = store.hasContent()
            )
            MogulButton(
                t("transcripts.buttons.cancel"),
                onClick = { store.clear() },
                enabled = store.hasContent() || store.noteId > 0
            )
        }
    }
}

@Composable
private fun NotesSection(title: String, notes: List<Note>, onNew: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Gutter.half),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeading(title, Modifier.weight(1f))
            IconButton(MogulIcon.Plus, t("icons.new-note"), onClick = onNew)
        }
        notes.forEach { note ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            NoteItem(
                note,
                onDelete = { scope.launch { Mogul.notes.deleteNote(note.id); NotesStore.reload() } },
                onDone = { done ->
                    scope.launch {
                        Mogul.notes.setNoteDone(
                            note.id,
                            done
                        ); NotesStore.reload()
                    }
                },
                onEdit = { NotesStore.loadIntoEditor(note) },
            )
        }
    }
}

/**
 * one note (NoteEditor.vue), laid out as the composer lays it out but read-only: its title, url
 * and text, then when it was written and the done, edit and delete controls. editing it swaps
 * the read-only fields for the composer, in place.
 */
@Composable
private fun NoteItem(
    note: Note,
    onDelete: () -> Unit,
    onDone: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val editing = NotesStore.showComposer && NotesStore.noteId == note.id
    Column(
        Modifier.fillMaxWidth().padding(vertical = Gutter.half),
        verticalArrangement = Arrangement.spacedBy(Gutter.half)
    ) {
        if (editing) NoteComposer() else NoteFields(note, onDelete, onDone, onEdit)
    }
}

/** a note's fields, read-only, where the composer would put them */
@Composable
private fun NoteFields(
    note: Note,
    onDelete: () -> Unit,
    onDone: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val done = note.done != null
    // on a phone, or any window too thin for the panel to sit beside the page, the notes are a
    // tab of their own with no field beside them to drop into
    val dragged = noteAsText(note).takeIf { LocalLayoutWidth.current != LayoutWidth.Mobile }
    note.title?.takeIf { it.isNotEmpty() }?.let {
        MogulReadOnlyField(it, label = t("notes.fields.title"))
    }
    note.url?.takeIf { it.isNotEmpty() }?.let { url ->
        MogulReadOnlyField(url, label = t("notes.fields.url"), onClick = { browser.open(url) })
    }
    if (note.note.isNotEmpty()) {
        MogulReadOnlyField(
            note.note,
            label = noteLabel(),
            textStyle =
                if (done)
                    TextStyle(
                        color = palette.ink.copy(alpha = 0.65f),
                        textDecoration = TextDecoration.LineThrough
                    )
                else
                    null,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gutter.half),
    ) {
        if (dragged != null) {
            DragHandle(
                dragged,
                label = note.title?.takeIf { it.isNotBlank() } ?: note.url?.takeIf { it.isNotBlank() } ?: note.note,
                description = t("icons.drag-note"),
            )
        }
        Checkbox(
            checked = done,
            onCheckedChange = onDone,
            colors = CheckboxDefaults.colors(
                checkedColor = palette.accent,
                uncheckedColor = palette.fieldBorder,
                checkmarkColor = palette.accentInk
            ),
        )
        IconButton(MogulIcon.Edit, t("icons.edit-note"), onClick = onEdit)
        IconButton(MogulIcon.Delete, t("icons.delete-note"), onClick = onDelete)
        Text(
            dateTimeToString(note.created) ?: "",
            Modifier.weight(1f),
            color = palette.muted,
            fontSize = TextSize.sm,
            textAlign = TextAlign.End,
        )
    }
}
