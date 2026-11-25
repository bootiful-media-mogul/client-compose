package com.joshlong.mogul.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import kotlin.math.roundToInt

/**
 * puts [text] into [value] at [at], with a space before it wherever it would otherwise run into a
 * word, and a newline after it, which takes the place of any spaces there, so the next line
 * doesn't start with them. a [singleLine] field can't hold a newline, so it gets a space there
 * instead, and only where a word follows
 */
internal fun insertDroppedText(value: String, at: Int, text: String, singleLine: Boolean = false): String {
    val i = at.coerceIn(0, value.length)
    val before = if (i > 0 && !value[i - 1].isWhitespace()) " " else ""
    val after = when {
        !singleLine -> "\n"
        i < value.length && !value[i].isWhitespace() -> " "
        else -> ""
    }
    val rest = value.substring(i)
    return value.substring(0, i) + before + text + after + if (singleLine) rest else rest.trimStart(' ', '\t')
}

/**
 * text dragged across the studio and dropped into a text field, such as a note. it's drawn by
 * the studio itself rather than handed to the platform, so it works the same in a browser, on
 * the desktop and on a tablet: every [MogulTextField] says where it is, and the one under the
 * pointer when it's let go takes the text where the pointer is.
 */
internal object TextDrag {

    class Target(val bounds: () -> Rect?, val drop: (Offset, String) -> Unit)

    private val targets = mutableStateMapOf<Any, Target>()

    /** the text being dragged, and what the ghost under the pointer says */
    var text by mutableStateOf<String?>(null)
        private set
    var label by mutableStateOf("")
        private set

    /** where the pointer is, in the window */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    fun register(key: Any, target: Target) {
        this.targets[key] = target
    }

    fun unregister(key: Any) {
        this.targets.remove(key)
    }

    /** the key of the field the text would land in if it were let go now */
    fun hovered(): Any? {
        if (this.text == null) return null
        return this.targets.entries.firstOrNull { it.value.bounds()?.contains(this.pointer) == true }?.key
    }

    fun start(text: String, label: String, at: Offset) {
        this.text = text
        this.label = label
        this.pointer = at
    }

    fun move(to: Offset) {
        this.pointer = to
    }

    fun drop() {
        val text = this.text ?: return
        val key = hovered()
        this.text = null
        if (key != null) this.targets[key]?.drop(this.pointer, text)
    }

    fun cancel() {
        this.text = null
    }
}

/** makes a text field a place [TextDrag] text can land */
@Composable
internal fun rememberTextDropTarget(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean,
): TextDropTarget {
    val target = remember { TextDropTarget() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentSingleLine by rememberUpdatedState(singleLine)
    DisposableEffect(target, enabled) {
        if (enabled) {
            TextDrag.register(
                target,
                TextDrag.Target(
                    bounds = { target.well?.takeIf { it.isAttached }?.boundsInWindow() },
                    drop = { at, text ->
                        val field = target.field
                        val layout = target.layout
                        val offset = if (field != null && field.isAttached && layout != null) {
                            layout.getOffsetForPosition(at - field.positionInWindow())
                        } else {
                            currentValue.length
                        }
                        currentOnValueChange(insertDroppedText(currentValue, offset, text, currentSingleLine))
                    },
                ),
            )
        }
        onDispose { TextDrag.unregister(target) }
    }
    return target
}

/**
 * where a drop can land: [well] is the field's box, which takes the drop anywhere in it, and
 * [field] and [layout] are its text's, so the text lands on the character under the pointer
 */
internal class TextDropTarget {
    var well: LayoutCoordinates? = null
    var field: LayoutCoordinates? = null
    var layout: TextLayoutResult? = null
    val hovered: Boolean get() = TextDrag.hovered() === this
}

/**
 * a grip to drag [text] by (six dots, drawn, as the carousel's arrows are); [label] is what the
 * ghost under the pointer says while it's dragged
 */
@Composable
fun DragHandle(text: String, label: String, description: String, modifier: Modifier = Modifier) {
    // where the pointer is is read from the grip's own coordinates, not summed from the drag's
    // steps, which drift away from the pointer on the web
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun inWindow(local: Offset) = coordinates?.takeIf { it.isAttached }?.localToWindow(local) ?: local
    val currentText by rememberUpdatedState(text)
    val currentLabel by rememberUpdatedState(label)
    val color = palette.muted
    Canvas(
        modifier
            .onGloballyPositioned { coordinates = it }
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { TextDrag.start(currentText, currentLabel, inWindow(it)) },
                    onDrag = { change, _ ->
                        change.consume()
                        TextDrag.move(inWindow(change.position))
                    },
                    onDragEnd = { TextDrag.drop() },
                    onDragCancel = { TextDrag.cancel() },
                )
            }
            .padding(4.dp)
            .size(width = 10.dp, height = 16.dp),
    ) {
        val r = size.width / 6
        for (column in 0..1) for (row in 0..2) {
            drawCircle(color, r, Offset(r + column * (size.width - 2 * r), r + row * (size.height - 2 * r) / 2))
        }
    }
}

/** what's being dragged, drawn at the pointer, over everything; it goes in the window's root */
@Composable
fun TextDragGhost() {
    TextDrag.text ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val at = TextDrag.pointer - origin
        Text(
            TextDrag.label,
            Modifier
                .offset { IntOffset(at.x.roundToInt() + 12, at.y.roundToInt() + 12) }
                .widthIn(max = 240.dp)
                .clip(RoundedCornerShape(Gutter.radius))
                .background(palette.chrome)
                .padding(horizontal = Gutter.half, vertical = 4.dp),
            color = palette.chromeInk,
            fontSize = TextSize.sm,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
