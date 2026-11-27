package com.joshlong.mogul.client.podcasts.segments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// TrackEditor.vue's geometry, in dp: the timeline is drawn `zoom` dp per second
private val RULER_HEIGHT = 36.dp
private val LANE_HEIGHT = 153.dp
private val VOID_HEIGHT = 39.dp
private val CONTENT_HEIGHT = RULER_HEIGHT + LANE_HEIGHT + VOID_HEIGHT
private const val SEGMENT_TOP = 10f
private const val SEGMENT_HEIGHT = 132f
private const val SEGMENT_BAR = 25f
private const val MARK_BOTTOM = 18f
private const val TRIM_WIDTH = 10f
private const val GRIP_WIDTH = 20f

/** the editor's colors, from the palette, as the Vue editor's --te-* tokens were */
private class TrackColors(
    val ink: Color,
    val onInk: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val surface: Color,
    val hover: Color,
    val accent: Color,
    val accentLight: Color,
    val disabled: Color,
    val grid: Color,
    val shade: Color,
    val chromeMuted: Color,
    val danger: Color,
)

@Composable
private fun trackColors(): TrackColors {
    val p = palette
    return TrackColors(
        ink = p.chrome, onInk = p.chromeInk, muted = p.muted, faint = p.muted.copy(alpha = 0.75f), line = p.rule,
        surface = p.fieldBg, hover = p.hoverTint, accent = p.accent, accentLight = lerp(p.fieldBg, p.accent, 0.16f),
        disabled = p.disabled, grid = p.ink.copy(alpha = 0.06f), shade = p.ink.copy(alpha = 0.18f),
        chromeMuted = p.chromeMuted, danger = p.danger,
    )
}

/** what a press on the lane landed on */
private sealed interface LaneHit {
    data class Grip(val segment: TrackSegment) : LaneHit
    data class Trim(val segment: TrackSegment) : LaneHit
    data class Mark(val mark: TrackMark) : LaneHit
    data class Body(val segment: TrackSegment) : LaneHit
    data object Empty : LaneHit
}

/** the marks that fall inside one clip, placed on it in dp from the clip's left edge */
private class PlacedMark(val mark: TrackMark, val left: Float, val width: Float)

private fun marksIn(segment: TrackSegment, marks: List<TrackMark>, zoom: Int): List<PlacedMark> =
    marks.filter { it.end > segment.sourceStart && it.start < segment.sourceEnd }.map { mark ->
        val from = max(mark.start, segment.sourceStart)
        val to = min(mark.end, segment.sourceEnd)
        PlacedMark(mark, ((from - segment.sourceStart) * zoom).toFloat(), max(2.0, (to - from) * zoom).toFloat())
    }

private fun hitTest(state: TrackEditorState, marks: List<TrackMark>, x: Float, y: Float): LaneHit {
    val zoom = state.zoom
    // later clips are drawn over earlier ones, so look from the top of the stack down
    val segment = state.segments.lastOrNull { s ->
        x >= s.timelineStart * zoom && x < s.end * zoom && y >= SEGMENT_TOP && y < SEGMENT_TOP + SEGMENT_HEIGHT
    } ?: return LaneHit.Empty
    val left = (segment.timelineStart * zoom).toFloat()
    val right = (segment.end * zoom).toFloat()
    if (x >= right - TRIM_WIDTH) return LaneHit.Trim(segment)
    if (y >= SEGMENT_TOP + SEGMENT_BAR && y < SEGMENT_TOP + SEGMENT_HEIGHT - MARK_BOTTOM) {
        marksIn(segment, marks, zoom).lastOrNull { x >= left + it.left && x < left + it.left + it.width }?.let { return LaneHit.Mark(it.mark) }
    }
    if (y < SEGMENT_TOP + SEGMENT_BAR && x < left + GRIP_WIDTH) return LaneHit.Grip(segment)
    return LaneHit.Body(segment)
}

@Composable
fun rememberTrackEditorState(clip: TrackClip, onEdit: (TrackEdit) -> Unit): TrackEditorState {
    val currentOnEdit = rememberUpdatedState(onEdit)
    val state = remember(clip.id) { TrackEditorState(clip) { currentOnEdit.value(it) } }
    state.clip = clip
    DisposableEffect(state) { onDispose { state.release() } }
    return state
}

/**
 * the track editor (TrackEditor.vue): one clip on a timeline, cut with a range selection
 * and a ripple delete, moved and trimmed by its handles, played back skipping the cuts.
 * the keyboard shortcuts go to the editor last pressed on, as the Vue editor's did.
 */
@Composable
fun TrackEditor(
    state: TrackEditorState,
    marks: List<TrackMark>,
    onToggleMark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = trackColors()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current.density
    var jumpFocused by remember { mutableStateOf(false) }
    val currentMarks = rememberUpdatedState(marks)
    val currentToggle = rememberUpdatedState(onToggleMark)

    // the playhead follows the audio, a frame at a time, while playing
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            withFrameNanos { }
            state.playbackFrame()
        }
    }
    LaunchedEffect(state) {
        scroll.scrollTo(0)
        runCatching { focus.requestFocus() }
    }

    fun jumpTo(text: String): Boolean {
        val seconds = parseTimecode(text) ?: return false
        state.jumpTo(seconds)
        scope.launch {
            // the playhead lands mid-view
            val target = (state.playhead * state.zoom).toFloat() * density
            scroll.scrollTo(max(0, (target - scroll.viewportSize / 2f).toInt()))
        }
        return true
    }

    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(state) {
                // pressing anywhere on the editor makes it the one the shortcuts go to
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    runCatching { focus.requestFocus() }
                }
            }
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || jumpFocused) return@onKeyEvent false
                when {
                    (event.isMetaPressed || event.isCtrlPressed) && event.key == Key.Z -> {
                        if (event.isShiftPressed) state.redo() else state.undo()
                        true
                    }
                    event.key == Key.Delete || event.key == Key.Backspace -> {
                        if (state.selection != null) {
                            state.deleteSelection()
                            true
                        } else false
                    }
                    event.key == Key.Spacebar -> {
                        state.togglePlayback()
                        true
                    }
                    event.key == Key.R -> { state.tool = TrackTool.Range; true }
                    event.key == Key.V -> { state.tool = TrackTool.Move; true }
                    event.key == Key.Escape -> { state.clearSelection(); true }
                    else -> false
                }
            }
            .semantics { contentDescription = t("track-editor.label") },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 760.dp
            val toolLabels = maxWidth >= 700.dp
            Column {
                Toolbar(state, colors, compact, toolLabels, onJump = ::jumpTo, onJumpFocus = { jumpFocused = it })
                Row(Modifier.fillMaxWidth().height(CONTENT_HEIGHT + 22.dp)) {
                    TrackSidebar(colors, if (compact) 112.dp else 154.dp)
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        TimelineContent(state, colors, currentMarks.value) { currentToggle.value(it) }
                    }
                }
                Footer(state, colors)
            }
        }
    }
}

@Composable
private fun Toolbar(
    state: TrackEditorState,
    colors: TrackColors,
    compact: Boolean,
    toolLabels: Boolean,
    onJump: (String) -> Boolean,
    onJumpFocus: (Boolean) -> Unit,
) {
    var jumpText by remember { mutableStateOf("") }
    var jumpInvalid by remember { mutableStateOf(false) }
    val jumpInteraction = remember { MutableInteractionSource() }
    val jumpFocused by jumpInteraction.collectIsFocusedAsState()
    LaunchedEffect(jumpFocused) { onJumpFocus(jumpFocused) }
    Row(
        Modifier.fillMaxWidth().height(58.dp).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height)) }
            .padding(horizontal = if (compact) 8.dp else 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolbarIconButton(TrackGlyph.SkipBack, "Go to start", colors, onClick = state::goToStart)
            PlayButton(state, colors)
            Row(Modifier.padding(start = 9.dp)) {
                Text(formatTime(state.playhead, true), color = colors.ink, fontFamily = fonts.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (!compact) Text(" / ${formatTime(state.timelineDuration, true)}", color = colors.faint, fontFamily = fonts.mono, fontSize = 11.sp)
            }
            BasicTextField(
                value = jumpText,
                onValueChange = {
                    jumpText = it
                    jumpInvalid = false
                },
                singleLine = true,
                interactionSource = jumpInteraction,
                cursorBrush = SolidColor(colors.ink),
                textStyle = TextStyle(fontFamily = fonts.mono, fontSize = 11.sp, color = colors.ink),
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surface)
                    .border(1.dp, if (jumpInvalid) colors.danger else colors.line, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            jumpInvalid = !onJump(jumpText)
                            true
                        } else false
                    }
                    .semantics { contentDescription = t("track-editor.go-to.label") },
                decorationBox = { inner ->
                    if (jumpText.isEmpty()) Text(t("track-editor.go-to.placeholder"), color = colors.faint, fontFamily = fonts.mono, fontSize = 11.sp)
                    inner()
                },
            )
        }
        if (!compact) Divider(colors)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolButton("Range", "Range tool (R)", TrackGlyph.Pointer, state.tool == TrackTool.Range, toolLabels, colors) { state.tool = TrackTool.Range }
            ToolButton("Move", "Move tool (V)", TrackGlyph.MoveHorizontal, state.tool == TrackTool.Move, toolLabels, colors) { state.tool = TrackTool.Move }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolbarIconButton(TrackGlyph.Undo, "Undo", colors, enabled = state.canUndo, onClick = state::undo)
            ToolbarIconButton(TrackGlyph.Redo, "Redo", colors, enabled = state.canRedo, onClick = state::redo)
        }
        if (!compact) Divider(colors)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 7.dp)) {
            ToolbarIconButton(TrackGlyph.Minus, "Zoom out", colors, enabled = state.zoom > 2) { state.zoom = max(2, state.zoom - 1) }
            Text(
                "${(state.zoom * 100 + 2) / 4}%",
                Modifier.width(35.dp),
                color = colors.muted,
                fontFamily = fonts.mono,
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ToolbarIconButton(TrackGlyph.Plus, "Zoom in", colors, enabled = state.zoom < 16) { state.zoom = min(16, state.zoom + 1) }
        }
    }
}

@Composable
private fun Divider(colors: TrackColors) {
    Box(Modifier.width(1.dp).height(24.dp).background(colors.line))
}

@Composable
private fun ToolbarIconButton(glyph: TrackGlyph, label: String, colors: TrackColors, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered && enabled) colors.hover else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, if (enabled) colors.ink else colors.ink.copy(alpha = 0.35f), 17.dp)
    }
}

@Composable
private fun PlayButton(state: TrackEditorState, colors: TrackColors) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val label = if (state.isPlaying) "Pause" else "Play"
    Box(
        Modifier
            .size(31.dp)
            .clip(RoundedCornerShape(7.dp))
            // buttons are never blue: the hover is the chrome's own, not the accent
            .background(if (hovered) palette.chromeHover else colors.ink)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { state.togglePlayback() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(if (state.isPlaying) TrackGlyph.Pause else TrackGlyph.Play, colors.onInk, 16.dp)
    }
}

@Composable
private fun ToolButton(
    label: String,
    title: String,
    glyph: TrackGlyph,
    active: Boolean,
    showLabel: Boolean,
    colors: TrackColors,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = when {
        active -> colors.onInk
        hovered -> colors.ink
        else -> colors.muted
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) colors.ink else if (hovered) colors.hover else Color.Transparent)
            .border(1.dp, if (active) colors.ink else Color.Transparent, RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = title }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Glyph(glyph, ink, 16.dp)
        if (showLabel) Text(label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrackSidebar(colors: TrackColors, width: Dp) {
    val label = TextStyle(color = colors.faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
    Column(Modifier.width(width).fillMaxHeight().drawBehind { drawLine(colors.line, Offset(size.width, 0f), Offset(size.width, size.height)) }) {
        Box(
            Modifier.fillMaxWidth().height(RULER_HEIGHT).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height)) }
                .padding(start = if (width < 150.dp) 7.dp else 17.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(t("track-editor.timeline"), style = label) }
        Row(
            Modifier.fillMaxWidth().height(LANE_HEIGHT).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height)) }
                .padding(horizontal = if (width < 150.dp) 6.dp else 12.dp, vertical = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(27.dp, 25.dp).clip(RoundedCornerShape(4.dp)).background(colors.ink), contentAlignment = Alignment.Center) {
                Text("01", color = colors.onInk, fontFamily = fonts.mono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(t("track-editor.track"), color = colors.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Glyph(TrackGlyph.Volume, colors.muted, 12.dp)
                    Text(t("track-editor.format"), color = colors.muted, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        Text(t("track-editor.source"), Modifier.padding(horizontal = if (width < 150.dp) 7.dp else 17.dp, vertical = 15.dp), style = label.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Normal))
    }
}

@Composable
private fun TimelineContent(state: TrackEditorState, colors: TrackColors, marks: List<TrackMark>, onToggleMark: (String) -> Unit) {
    val zoom = state.zoom
    val width = state.timelineWidth.dp
    Box(Modifier.width(width).height(CONTENT_HEIGHT)) {
        Ruler(state, colors, width)
        Lane(state, colors, marks, onToggleMark, width)
        // the void below the lane
        Box(
            Modifier.offset(y = RULER_HEIGHT + LANE_HEIGHT).width(width).height(VOID_HEIGHT)
                .drawBehind { drawLine(colors.line, Offset(0f, 12.dp.toPx()), Offset(size.width, 12.dp.toPx())) },
        )
        // the selection, over the lane, with its length on a tag above it
        state.selection?.takeIf { state.selectedDuration > 0 }?.let { selection ->
            Box(
                Modifier.offset(x = (selection.start * zoom).dp, y = RULER_HEIGHT)
                    .width((state.selectedDuration * zoom).dp)
                    .height(LANE_HEIGHT)
                    .background(colors.accent.copy(alpha = 0.14f))
                    .drawBehind {
                        val w = 2.dp.toPx()
                        drawRect(colors.ink, size = androidx.compose.ui.geometry.Size(w, size.height))
                        drawRect(colors.ink, topLeft = Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
                    },
            )
            Text(
                t("track-editor.selected", "duration" to formatTime(state.selectedDuration, true)),
                Modifier.offset(x = (selection.start * zoom).dp, y = RULER_HEIGHT - 31.dp)
                    .clip(RoundedCornerShape(4.dp)).background(colors.ink).padding(horizontal = 7.dp, vertical = 4.dp),
                color = colors.onInk,
                fontFamily = fonts.mono,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        // the playhead moves every frame, so its position is read at layout, not composition
        Canvas(
            Modifier
                .offset { IntOffset((state.playhead * state.zoom).dp.roundToPx(), 0) }
                .width(1.dp)
                .height(CONTENT_HEIGHT),
        ) {
            playheadHead(colors.ink, 0.5f, 9.dp.toPx(), 11.dp.toPx())
            drawLine(colors.ink, Offset(0.5f, 10.dp.toPx()), Offset(0.5f, size.height), 1.dp.toPx())
        }
    }
}

@Composable
private fun Ruler(state: TrackEditorState, colors: TrackColors, width: Dp) {
    val zoom = state.zoom
    val interval = if (zoom < 4) 30 else if (zoom < 8) 15 else 10
    val count = ceil(state.timelineWidth / zoom / interval).toInt()
    Box(
        Modifier.width(width).height(RULER_HEIGHT)
            .drawBehind {
                drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height))
                for (index in 0..count) {
                    val x = (index * interval * zoom).dp.toPx()
                    drawLine(colors.line, Offset(x, 0f), Offset(x, size.height))
                    drawLine(colors.line, Offset(x + 29.dp.toPx(), size.height - 6.dp.toPx()), Offset(x + 29.dp.toPx(), size.height))
                }
            }
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(state) {
                // the ruler selects at once: nothing there needs to scroll
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                    down.consume()
                    rangeGesture(state, down.id, down.position.x / density / state.zoom.toDouble())
                }
            },
    ) {
        for (index in 0..count) {
            Text(
                formatTime((index * interval).toDouble()),
                Modifier.offset(x = (index * interval * zoom).dp + 6.dp, y = 8.dp),
                color = colors.muted,
                fontFamily = fonts.mono,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/** a drag across the timeline that selects a range; a press without a drag moves the playhead */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.rangeGesture(
    state: TrackEditorState,
    pointer: androidx.compose.ui.input.pointer.PointerId,
    startTime: Double,
) {
    val start = min(state.timelineDuration, startTime)
    state.beginRange(start)
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointer } ?: break
        if (!change.pressed) break
        change.consume()
        val time = max(0.0, min(state.timelineWidth / state.zoom, (change.position.x / density / state.zoom).toDouble()))
        if (state.dragRange(start, time)) moved = true
    }
    state.endRange(start, moved)
}

@Composable
private fun Lane(state: TrackEditorState, colors: TrackColors, marks: List<TrackMark>, onToggleMark: (String) -> Unit, width: Dp) {
    val currentMarks = rememberUpdatedState(marks)
    Box(
        Modifier
            .offset(y = RULER_HEIGHT)
            .width(width)
            .height(LANE_HEIGHT)
            .background(colors.surface)
            .drawBehind {
                // a faint grid every 75dp
                val step = 75.dp.toPx()
                var x = step - 1
                while (x < size.width) {
                    drawLine(colors.grid, Offset(x, 0f), Offset(x, size.height))
                    x += step
                }
                drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height))
            }
            .pointerHoverIcon(if (state.tool == TrackTool.Range) PointerIcon.Crosshair else PointerIcon.Default)
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                    val x = down.position.x / density
                    val y = down.position.y / density
                    when (val hit = hitTest(state, currentMarks.value, x, y)) {
                        is LaneHit.Mark -> {
                            down.consume()
                            // a tap flips it; the up is what counts, as a click
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (change.changedToUp()) {
                                    onToggleMark(hit.mark.id)
                                    break
                                }
                            }
                        }
                        is LaneHit.Grip -> segmentGesture(state, down.id, down.position.x, hit.segment, move = true).also { down.consume() }
                        is LaneHit.Trim -> segmentGesture(state, down.id, down.position.x, hit.segment, move = false).also { down.consume() }
                        is LaneHit.Body -> if (state.tool == TrackTool.Move) {
                            down.consume()
                            segmentGesture(state, down.id, down.position.x, hit.segment, move = true)
                        } else laneRange(state, down)
                        LaneHit.Empty -> if (state.tool == TrackTool.Range) laneRange(state, down)
                    }
                }
            },
    ) {
        for (segment in state.segments) {
            SegmentView(state, segment, colors, marksIn(segment, marks, state.zoom))
        }
    }
}

/**
 * on touch screens a one-finger swipe on the track scrolls the timeline, so a range
 * selection starts from touch-and-hold instead; a quick tap moves the playhead
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.laneRange(
    state: TrackEditorState,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
) {
    if (down.type != PointerType.Touch) {
        down.consume()
        rangeGesture(state, down.id, down.position.x / density / state.zoom.toDouble())
        return
    }
    val held = awaitLongPressOrCancellation(down.id)
    if (held == null) {
        if (currentEvent.changes.any { it.id == down.id && it.changedToUp() }) {
            state.playhead = min(state.timelineDuration, down.position.x / density / state.zoom.toDouble())
            state.clearSelection()
        }
        return
    }
    held.consume()
    rangeGesture(state, held.id, held.position.x / density / state.zoom.toDouble())
}

/** dragging a clip's grip (or its body, with the move tool) moves it; its right edge trims it */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.segmentGesture(
    state: TrackEditorState,
    pointer: androidx.compose.ui.input.pointer.PointerId,
    startX: Float,
    segment: TrackSegment,
    move: Boolean,
) {
    state.beginSegmentDrag()
    val original = if (move) segment.timelineStart else segment.sourceEnd
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: break
        if (!change.pressed) break
        change.consume()
        // the lane doesn't move under the pointer, so its coordinates are a steady ruler
        val delta = ((change.position.x - startX) / density / state.zoom).toDouble()
        if (move) state.dragMove(segment.id, original, delta) else state.dragTrim(segment.id, original, delta)
    }
    state.endSegmentDrag(segment.id, move, original)
}

@Composable
private fun SegmentView(state: TrackEditorState, segment: TrackSegment, colors: TrackColors, marks: List<PlacedMark>) {
    val zoom = state.zoom
    val clip = state.clip
    val segmentWidth = ((segment.sourceEnd - segment.sourceStart) * zoom).dp
    Box(
        Modifier
            .offset(x = (segment.timelineStart * zoom).dp, y = SEGMENT_TOP.dp)
            .width(segmentWidth)
            .height(SEGMENT_HEIGHT.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.accentLight)
            .border(1.dp, colors.accent, RoundedCornerShape(5.dp))
            .pointerHoverIcon(if (state.tool == TrackTool.Move) PointerIcon.Hand else PointerIcon.Crosshair),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(SEGMENT_BAR.dp).background(palette.chrome).padding(start = 3.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.size(17.dp, 21.dp).pointerHoverIcon(PointerIcon.Hand), contentAlignment = Alignment.Center) {
                    Glyph(TrackGlyph.Grip, colors.chromeMuted, 14.dp)
                }
                Text(
                    clip.name,
                    Modifier.weight(1f, fill = false),
                    color = colors.onInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(formatTime(segment.sourceEnd - segment.sourceStart), color = colors.chromeMuted, fontFamily = fonts.mono, fontSize = 9.sp, maxLines = 1)
            }
            WaveformView(
                clip.waveform,
                segment.sourceStart,
                segment.sourceEnd,
                clip.duration,
                colors.accent,
                Modifier.fillMaxWidth().height(70.dp).padding(top = 0.dp).offset(y = 6.dp),
            )
            Text(
                "SOURCE ${formatTime(segment.sourceStart)} — ${formatTime(segment.sourceEnd)}",
                Modifier.padding(start = 10.dp, top = 10.dp),
                color = colors.muted,
                fontFamily = fonts.mono,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        // the automatic cuts: hatched where they take audio out, outlined where put back
        for (placed in marks) {
            Box(
                Modifier
                    .offset(x = placed.left.dp, y = SEGMENT_BAR.dp)
                    .width(placed.width.dp)
                    .height((SEGMENT_HEIGHT - SEGMENT_BAR - MARK_BOTTOM).dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics { contentDescription = placed.mark.kind }
                    .drawBehind {
                        if (placed.mark.active) {
                            val hatch = Color.Black.copy(alpha = 0.45f)
                            val step = 6.dp.toPx()
                            val w = 3.dp.toPx()
                            var x = -size.height
                            while (x < size.width) {
                                drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), w * 0.7f)
                                x += step
                            }
                        } else {
                            drawRect(
                                colors.muted,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                                ),
                            )
                        }
                    },
            )
        }
        // the trim handle on the right edge
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(TRIM_WIDTH.dp)
                .fillMaxHeight()
                .background(colors.hover)
                .pointerHoverIcon(PointerIcon.Hand)
                .drawBehind {
                    drawLine(colors.shade, Offset(0f, 0f), Offset(0f, size.height))
                    drawRect(colors.ink, Offset(3.dp.toPx(), 57.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), 17.dp.toPx()))
                },
        )
    }
}

@Composable
private fun Footer(state: TrackEditorState, colors: TrackColors) {
    Row(
        Modifier.fillMaxWidth().height(53.dp).drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f)) }.padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(TrackGlyph.Scissors, colors.ink, 15.dp)
            val selection = state.selection
            if (selection != null && state.selectedDuration > 0) {
                Text(
                    "${formatTime(selection.start, true)} → ${formatTime(selection.end, true)}",
                    color = colors.ink,
                    fontFamily = fonts.mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(t("track-editor.selected", "duration" to formatTime(state.selectedDuration, true)), color = colors.muted, fontSize = 11.sp)
            } else {
                Text(t("track-editor.select-hint"), color = colors.muted, fontSize = 11.sp)
            }
        }
        DeleteRangeButton(state, colors)
    }
}

@Composable
private fun DeleteRangeButton(state: TrackEditorState, colors: TrackColors) {
    val enabled = state.selectedDuration >= EPSILON
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !enabled -> colors.disabled
        hovered -> colors.danger
        else -> colors.ink
    }
    val ink = if (enabled) colors.onInk else colors.muted
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) { state.deleteSelection() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Glyph(TrackGlyph.Trash, ink, 15.dp)
        Text(t("track-editor.delete-range"), color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
