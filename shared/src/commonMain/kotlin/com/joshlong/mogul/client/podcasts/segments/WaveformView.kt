package com.joshlong.mogul.client.podcasts.segments

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * a stretch of the source's waveform, sourceStart to sourceEnd (seconds), as bars three
 * pixels apart. the color is passed in, so it follows the palette like the rest of the editor
 */
@Composable
internal fun WaveformView(
    peaks: FloatArray,
    sourceStart: Double,
    sourceEnd: Double,
    sourceDuration: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val width = max(1f, size.width)
        val height = max(1f, size.height)
        val step = 3f * density
        val barWidth = 1.6f * density
        val bars = ceil(width / step).toInt()
        val visibleDuration = max(0.001, sourceEnd - sourceStart)
        for (index in 0 until bars) {
            val sourceTime = sourceStart + (index.toDouble() / bars) * visibleDuration
            val peakIndex = min(peaks.size - 1, floor((sourceTime / sourceDuration) * peaks.size).toInt())
            val amplitude = if (peakIndex >= 0) peaks[peakIndex] else 0.18f
            val barHeight = max(3f * density, amplitude * (height - 10f * density))
            drawRect(
                color.copy(alpha = (0.62f + amplitude * 0.35f).coerceAtMost(1f)),
                topLeft = Offset(index * step, (height - barHeight) / 2),
                size = Size(barWidth, barHeight),
            )
        }
    }
}
