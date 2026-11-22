package com.joshlong.mogul.client.podcasts.segments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joshlong.mogul.client.platform.AudioHandle
import com.joshlong.mogul.client.platform.browser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.time.TimeSource

/** what the editor edits: one source file, in seconds */
class TrackClip(
    val id: Long,
    val name: String,
    /** seconds */
    val duration: Double,
    val waveform: FloatArray,
    val audioUrl: String?,
    /** the parts of the source to start with, in seconds, in the order they play. null means all of it */
    val kept: List<Range>?,
)

/** a segment maps sourceStart..sourceEnd in the original file to timelineStart */
data class TrackSegment(val id: String, val timelineStart: Double, val sourceStart: Double, val sourceEnd: Double) {
    val end: Double get() = this.timelineStart + this.sourceEnd - this.sourceStart
}

data class TrackSelection(val start: Double, val end: Double)

/** an edit: what happened, and the segments as they are now */
data class TrackEdit(
    val operation: String,
    val clipId: Long,
    val segments: List<TrackSegment>,
    val timelineDuration: Double,
    val details: Map<String, Any?> = emptyMap(),
)

/**
 * a stretch of the source an automatic cut takes out (active) or would have but was put
 * back (not active), in seconds. tapping one asks for it to be flipped.
 */
data class TrackMark(val id: String, val start: Double, val end: Double, val kind: String, val active: Boolean)

enum class TrackTool { Range, Move }

internal const val EPSILON = 0.02

internal fun round3(value: Double): Double = round(value * 1000) / 1000

/**
 * the track editor's state, TrackEditor.vue's script: the clips on the timeline, the
 * selection, the playhead and playback, and undo. one per clip; the composable draws it.
 */
class TrackEditorState(clip: TrackClip, private val onEdit: (TrackEdit) -> Unit) {

    var clip: TrackClip = clip
        internal set

    var segments by mutableStateOf<List<TrackSegment>>(emptyList())
        private set

    var selection by mutableStateOf<TrackSelection?>(null)
        private set

    var playhead by mutableStateOf(0.0)
        internal set

    /** pixels (dp) per second */
    var zoom by mutableStateOf(4)

    var tool by mutableStateOf(TrackTool.Range)

    var isPlaying by mutableStateOf(false)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private val history = ArrayDeque<List<TrackSegment>>()

    private val future = ArrayDeque<List<TrackSegment>>()

    private var audio: AudioHandle? = null

    /** the clip the audio is playing right now, if any */
    private var playing: TrackSegment? = null

    private var playbackAt = 0.0

    private var playbackStart = TimeSource.Monotonic.markNow()

    private var idSequence = 0

    init {
        reset()
    }

    val timelineDuration: Double get() = max(0.0, this.segments.maxOfOrNull { it.end } ?: 0.0)

    val timelineWidth: Double get() = max(1100.0, (max(this.clip.duration, this.timelineDuration) + 15) * this.zoom)

    val selectedDuration: Double get() = this.selection?.let { max(0.0, it.end - it.start) } ?: 0.0

    private fun sorted(list: List<TrackSegment>) = list.sortedBy { it.timelineStart }

    private fun publish(operation: String, details: Map<String, Any?> = emptyMap()) =
        this.onEdit(TrackEdit(operation, this.clip.id, this.segments, round3(this.timelineDuration), details))

    private fun remember() {
        this.history.addLast(this.segments)
        if (this.history.size > 50) this.history.removeFirst()
        this.future.clear()
        this.canUndo = true
        this.canRedo = false
    }

    fun undo() {
        if (this.history.isEmpty()) return
        pause()
        this.future.addLast(this.segments)
        this.segments = this.history.removeLast()
        this.canUndo = this.history.isNotEmpty()
        this.canRedo = true
        this.selection = null
        publish("undo")
    }

    fun redo() {
        if (this.future.isEmpty()) return
        pause()
        this.history.addLast(this.segments)
        this.segments = this.future.removeLast()
        this.canUndo = true
        this.canRedo = this.future.isNotEmpty()
        publish("redo")
    }

    fun setSelection(start: Double, end: Double) {
        this.selection = TrackSelection(round3(max(0.0, min(start, end))), round3(min(this.timelineDuration, max(start, end))))
    }

    fun clearSelection() {
        this.selection = null
    }

    fun deleteSelection() {
        val selection = this.selection ?: return
        if (this.selectedDuration < EPSILON) return
        pause()
        val (start, end) = selection
        val removed = end - start
        remember()
        val next = mutableListOf<TrackSegment>()
        for (segment in this.segments) {
            val from = segment.timelineStart
            val to = segment.end
            if (to <= start + EPSILON) next.add(segment)
            else if (from >= end - EPSILON) next.add(segment.copy(timelineStart = round3(from - removed)))
            else {
                if (from < start - EPSILON) next.add(segment.copy(sourceEnd = round3(segment.sourceStart + start - from)))
                if (to > end + EPSILON) {
                    next.add(
                        segment.copy(
                            id = "segment-${++this.idSequence}",
                            sourceStart = round3(segment.sourceStart + end - from),
                            timelineStart = round3(start),
                        ),
                    )
                }
            }
        }
        this.segments = sorted(next)
        this.playhead = min(start, this.timelineDuration)
        clearSelection()
        publish("ripple-delete", mapOf("range" to selection, "removedDuration" to round3(removed)))
    }

    // the drags. the visual edit happens while dragging; the end of the drag records it

    internal fun beginRange(time: Double) {
        pause()
        val start = min(this.timelineDuration, time)
        setSelection(start, start)
    }

    /** answers whether the drag has moved far enough to count as a selection */
    internal fun dragRange(start: Double, time: Double): Boolean {
        val end = min(this.timelineDuration, time)
        setSelection(start, end)
        return abs(end - start) > 0.05
    }

    internal fun endRange(start: Double, moved: Boolean) {
        if (!moved) {
            this.playhead = start
            clearSelection()
        } else if (this.selectedDuration < EPSILON) clearSelection()
    }

    internal fun beginSegmentDrag() {
        pause()
        clearSelection()
    }

    internal fun dragMove(id: String, original: Double, delta: Double) {
        val order = sorted(this.segments)
        val index = order.indexOfFirst { it.id == id }
        if (index < 0) return
        val segment = order[index]
        val previous = order.getOrNull(index - 1)
        val next = order.getOrNull(index + 1)
        val minimum = previous?.end ?: 0.0
        val maximum = next?.let { it.timelineStart - (segment.sourceEnd - segment.sourceStart) } ?: Double.POSITIVE_INFINITY
        val moved = segment.copy(timelineStart = round3(min(maximum, max(minimum, original + delta))))
        this.segments = this.segments.map { if (it.id == id) moved else it }
    }

    internal fun dragTrim(id: String, original: Double, delta: Double) {
        val order = sorted(this.segments)
        val index = order.indexOfFirst { it.id == id }
        if (index < 0) return
        val segment = order[index]
        val next = order.getOrNull(index + 1)
        val maximum = min(
            this.clip.duration,
            next?.let { segment.sourceStart + it.timelineStart - segment.timelineStart } ?: Double.POSITIVE_INFINITY,
        )
        val trimmed = segment.copy(sourceEnd = round3(min(maximum, max(segment.sourceStart + 0.1, original + delta))))
        this.segments = this.segments.map { if (it.id == id) trimmed else it }
    }

    internal fun endSegmentDrag(id: String, move: Boolean, original: Double) {
        val segment = this.segments.firstOrNull { it.id == id } ?: return
        val now = if (move) segment.timelineStart else segment.sourceEnd
        if (abs(now - original) <= EPSILON) return
        // store the state before the gesture, since the visual edit happened while dragging
        val previous = this.segments.map {
            if (it.id != id) it else if (move) it.copy(timelineStart = original) else it.copy(sourceEnd = original)
        }
        this.history.addLast(previous)
        if (this.history.size > 50) this.history.removeFirst()
        this.future.clear()
        this.canUndo = true
        this.canRedo = false
        this.segments = sorted(this.segments)
        publish(
            if (move) "move-segment" else "trim-segment",
            mapOf("segmentId" to id, "before" to round3(original), "after" to round3(now)),
        )
    }

    // playback

    private fun segmentAt(time: Double) = this.segments.firstOrNull { time >= it.timelineStart && time < it.end - EPSILON }

    fun pause() {
        this.isPlaying = false
        this.playing = null
        this.audio?.pause()
    }

    /** the one seek playing a clip needs: to where the playhead is inside it */
    private fun startPlaying(segment: TrackSegment, time: Double) {
        this.playing = segment
        val audio = this.audio ?: return
        audio.seek(segment.sourceStart + time - segment.timelineStart)
        if (audio.paused) audio.play()
    }

    /**
     * while a clip plays, the audio is the clock: the playhead follows the audio, and the
     * audio is only told where to go when playback crosses into another clip. steering the
     * audio to follow the wall clock instead -- seeking whenever the two drifted apart --
     * stalls it on every seek. the wall clock only moves the playhead across gaps.
     */
    internal fun playbackFrame() {
        if (!this.isPlaying) return
        val playing = this.playing
        val audio = this.audio
        val time: Double
        if (playing != null && audio != null && !audio.paused) {
            time = playing.timelineStart + audio.currentTime - playing.sourceStart
            this.playbackAt = time
            this.playbackStart = TimeSource.Monotonic.markNow()
        } else {
            time = this.playbackAt + this.playbackStart.elapsedNow().inWholeMicroseconds / 1_000_000.0
        }
        if (time >= this.timelineDuration) {
            this.playhead = this.timelineDuration
            pause()
            return
        }
        this.playhead = time
        val segment = segmentAt(time)
        if (segment?.id != playing?.id) {
            if (segment != null) startPlaying(segment, time)
            else {
                this.playing = null
                audio?.pause()
            }
        }
    }

    fun togglePlayback() {
        if (this.isPlaying) {
            pause()
            return
        }
        if (this.playhead >= this.timelineDuration - EPSILON) this.playhead = 0.0
        // start the audio here, inside the tap or click: mobile Safari only lets audio begin
        // from the user gesture itself, not from a later frame
        segmentAt(this.playhead)?.let { startPlaying(it, this.playhead) }
        this.playbackAt = this.playhead
        this.playbackStart = TimeSource.Monotonic.markNow()
        this.isPlaying = true
    }

    fun goToStart() {
        this.playhead = 0.0
        pause()
    }

    /** typing a time moves the playhead there exactly, which a drag can't */
    fun jumpTo(seconds: Double) {
        val wasPlaying = this.isPlaying
        pause()
        clearSelection()
        this.playhead = round3(min(seconds, this.timelineDuration))
        if (wasPlaying) togglePlayback()
    }

    private fun reset() {
        pause()
        this.audio?.release()
        this.audio = this.clip.audioUrl?.let { browser.createAudio(it) }
        // saved edits open back to back. a gap left by the move tool isn't kept: the render
        // closes it up, so the editor shows what the render will sound like
        val kept = this.clip.kept?.takeIf { it.isNotEmpty() } ?: listOf(Range(0.0, this.clip.duration))
        var timelineStart = 0.0
        this.segments = kept.map { range ->
            val segment = TrackSegment(
                "segment-${++this.idSequence}",
                round3(timelineStart),
                round3(range.start),
                round3(min(range.end, this.clip.duration)),
            )
            timelineStart += segment.sourceEnd - segment.sourceStart
            segment
        }
        this.history.clear()
        this.future.clear()
        this.canUndo = false
        this.canRedo = false
        this.playhead = 0.0
        clearSelection()
    }

    /** what to render: the source ranges, in the order they play */
    fun keptRanges(): List<Range> = sorted(this.segments).map { Range(it.sourceStart, it.sourceEnd) }

    fun release() {
        pause()
        this.audio?.release()
        this.audio = null
    }
}

/** minutes and seconds, `mm:ss`, or `mm:ss.hh` when precise */
fun formatTime(seconds: Double, precise: Boolean = false): String {
    val safe = max(0.0, seconds)
    val minutes = (safe / 60).toLong()
    val whole = (safe % 60).toLong()
    val base = "${minutes.toString().padStart(2, '0')}:${whole.toString().padStart(2, '0')}"
    return if (precise) "$base.${((safe % 1) * 100).toLong().toString().padStart(2, '0')}" else base
}
