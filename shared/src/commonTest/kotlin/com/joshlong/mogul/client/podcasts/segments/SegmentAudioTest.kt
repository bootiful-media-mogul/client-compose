package com.joshlong.mogul.client.podcasts.segments

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SegmentAudioTest {

    private fun waveform(magic: String, peaksPerSecond: Int, durationMs: Long, peaks: List<Int>): ByteArray {
        val bytes = ByteArray(14 + peaks.size)
        magic.encodeToByteArray().copyInto(bytes, 0, 0, 4)
        bytes[4] = (peaksPerSecond shr 8).toByte()
        bytes[5] = peaksPerSecond.toByte()
        for (i in 0 until 4) bytes[6 + i] = (durationMs shr (24 - 8 * i)).toByte()
        for (i in 0 until 4) bytes[10 + i] = (peaks.size shr (24 - 8 * i)).toByte()
        peaks.forEachIndexed { i, peak -> bytes[14 + i] = peak.toByte() }
        return bytes
    }

    @Test
    fun readsTheFormatTheProcessorsWrite() {
        val parsed = parseWaveform(waveform("MWF1", 20, 2050, listOf(0, 255, 64)))
        assertEquals(20, parsed.peaksPerSecond)
        assertEquals(2.05, parsed.durationSeconds)
        // within float precision: on js a Float constant stays a double, while a FloatArray holds float32
        listOf(0f, 1f, 64 / 255f).zip(parsed.peaks.toList()).forEach { (expected, actual) -> assertEquals(expected, actual, 1e-6f) }
        assertEquals(3, parsed.peaks.size)
    }

    @Test
    fun readsADurationTooLongForASignedInt16BigEndian() {
        assertEquals(3600.0, parseWaveform(waveform("MWF1", 20, 3_600_000, emptyList())).durationSeconds)
    }

    @Test
    fun refusesAnythingElse() {
        val e = assertFailsWith<IllegalArgumentException> { parseWaveform(waveform("<htm", 20, 0, emptyList())) }
        assertContains(e.message ?: "", "not a waveform")
    }
}
