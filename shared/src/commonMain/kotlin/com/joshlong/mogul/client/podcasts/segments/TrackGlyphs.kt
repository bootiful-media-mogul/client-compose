package com.joshlong.mogul.client.podcasts.segments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * the editor's toolbar glyphs. the Vue editor used lucide's icons; these are the same
 * shapes, drawn on a 24-unit grid as lucide draws them
 */
internal enum class TrackGlyph { SkipBack, Play, Pause, Undo, Redo, Minus, Plus, Pointer, MoveHorizontal, Grip, Scissors, Trash, Volume }

@Composable
internal fun Glyph(glyph: TrackGlyph, color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
        fun path(filled: Boolean = false, build: Path.() -> Unit) {
            val path = Path().apply(build)
            if (filled) drawPath(path, color) else drawPath(path, color, style = stroke)
        }
        fun Path.at(x: Float, y: Float) = moveTo(x * u, y * u)
        fun Path.to(x: Float, y: Float) = lineTo(x * u, y * u)
        when (glyph) {
            TrackGlyph.SkipBack -> {
                path { at(19f, 20f); to(9f, 12f); to(19f, 4f); close() }
                line(5f, 19f, 5f, 5f)
            }
            TrackGlyph.Play -> path(filled = true) { at(6f, 3f); to(20f, 12f); to(6f, 21f); close() }
            TrackGlyph.Pause -> {
                drawRect(color, p(6f, 4f), androidx.compose.ui.geometry.Size(4f * u, 16f * u))
                drawRect(color, p(14f, 4f), androidx.compose.ui.geometry.Size(4f * u, 16f * u))
            }
            TrackGlyph.Undo -> {
                path { at(9f, 14f); to(4f, 9f); to(9f, 4f) }
                path { at(4f, 9f); to(14.5f, 9f); cubicTo(18f * u, 9f * u, 20f * u, 11.5f * u, 20f * u, 14.5f * u); cubicTo(20f * u, 17.5f * u, 18f * u, 20f * u, 14.5f * u, 20f * u); to(11f, 20f) }
            }
            TrackGlyph.Redo -> {
                path { at(15f, 14f); to(20f, 9f); to(15f, 4f) }
                path { at(20f, 9f); to(9.5f, 9f); cubicTo(6f * u, 9f * u, 4f * u, 11.5f * u, 4f * u, 14.5f * u); cubicTo(4f * u, 17.5f * u, 6f * u, 20f * u, 9.5f * u, 20f * u); to(13f, 20f) }
            }
            TrackGlyph.Minus -> line(5f, 12f, 19f, 12f)
            TrackGlyph.Plus -> {
                line(5f, 12f, 19f, 12f)
                line(12f, 5f, 12f, 19f)
            }
            TrackGlyph.Pointer -> path { at(4f, 4f); to(11f, 21f); to(13.5f, 13.5f); to(21f, 11f); close() }
            TrackGlyph.MoveHorizontal -> {
                line(2f, 12f, 22f, 12f)
                path { at(18f, 8f); to(22f, 12f); to(18f, 16f) }
                path { at(6f, 8f); to(2f, 12f); to(6f, 16f) }
            }
            TrackGlyph.Grip -> listOf(5f, 12f, 19f).forEach { y ->
                drawCircle(color, 1.5f * u, p(9f, y))
                drawCircle(color, 1.5f * u, p(15f, y))
            }
            TrackGlyph.Scissors -> {
                drawCircle(color, 3f * u, p(6f, 6f), style = stroke)
                drawCircle(color, 3f * u, p(6f, 18f), style = stroke)
                line(20f, 4f, 8.12f, 15.88f)
                line(14.47f, 14.48f, 20f, 20f)
                line(8.12f, 8.12f, 12f, 12f)
            }
            TrackGlyph.Trash -> {
                line(3f, 6f, 21f, 6f)
                path { at(19f, 6f); to(19f, 20f); to(5f, 20f); to(5f, 6f) }
                path { at(8f, 6f); to(8f, 4f); to(16f, 4f); to(16f, 6f) }
            }
            TrackGlyph.Volume -> {
                path { at(11f, 5f); to(6f, 9f); to(2f, 9f); to(2f, 15f); to(6f, 15f); to(11f, 19f); close() }
                path { at(15.5f, 8.5f); quadraticTo(18f * u, 12f * u, 15.5f * u, 15.5f * u) }
                path { at(19f, 5f); quadraticTo(24f * u, 12f * u, 19f * u, 19f * u) }
            }
        }
    }
}

/** the playhead's head: a pentagon pointing down */
internal fun DrawScope.playheadHead(color: Color, x: Float, width: Float, height: Float) {
    val path = Path().apply {
        moveTo(x - width / 2, 0f)
        lineTo(x + width / 2, 0f)
        lineTo(x + width / 2, height * 0.7f)
        lineTo(x, height)
        lineTo(x - width / 2, height * 0.7f)
        close()
    }
    drawPath(path, color)
}
