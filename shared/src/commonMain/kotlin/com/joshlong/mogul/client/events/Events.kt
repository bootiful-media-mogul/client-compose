package com.joshlong.mogul.client.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** what one part of the studio tells another, the Vue client's `mitt` events */
sealed interface AppEvent {

    /** a request was refused for want of a session */
    data object Unauthorized : AppEvent

    /** the notes panel should show the notes for this notable */
    data class NotesForNotable(val type: String, val notableId: Long, val entityName: String) : AppEvent

    /** the notes panel should forget the notable it was showing */
    data object ResetNotesForNotable : AppEvent

    /** the preview panel should show this managed file */
    data class PreviewManagedFile(val managedFileId: Long) : AppEvent

    /** the transcripts panel should open this transcript for editing */
    data class TranscriptEdit(val title: String, val id: Long, val transcript: String) : AppEvent

    /** one of the sidebar panels asked to be opened (and, on mobile, its tab shown) */
    data class SidebarPanelOpened(val panel: SidebarPanel) : AppEvent
}

enum class SidebarPanel { Notes, Preview, Transcripts }

object Events {

    private val flow = MutableSharedFlow<AppEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AppEvent> = this.flow

    fun emit(event: AppEvent) {
        this.flow.tryEmit(event)
    }
}

fun loadNotesForNotable(type: String, notableId: Long, entityName: String) =
    Events.emit(AppEvent.NotesForNotable(type, notableId, entityName))

fun resetNotesForNotable() = Events.emit(AppEvent.ResetNotesForNotable)

fun previewManagedFile(managedFileId: Long) = Events.emit(AppEvent.PreviewManagedFile(managedFileId))
