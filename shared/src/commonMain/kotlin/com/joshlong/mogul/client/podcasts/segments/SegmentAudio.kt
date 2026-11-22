package com.joshlong.mogul.client.podcasts.segments

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

// reads what the segment audio editor draws: the waveform the processors drew on upload.
// it comes through the gateway at /api, same origin as the app, so the file can stay private.

class Waveform(
    /** the waveform's own idea of the audio's length, which is exact; the mp3's isn't */
    val durationSeconds: Double,
    val peaksPerSecond: Int,
    /** 0 (silence) to 1 (the loudest moment in the file) */
    val peaks: FloatArray,
)

private const val MAGIC = "MWF1"

private fun ByteArray.unsigned(at: Int): Int = this[at].toInt() and 0xff

/**
 * the format the processors write (see AudioWaveform there): big-endian, the ascii bytes
 * MWF1, a uint16 of peaks per second, a uint32 duration in milliseconds, a uint32 count of
 * peaks, and then that many bytes, one per peak, 0 to 255.
 */
fun parseWaveform(bytes: ByteArray): Waveform {
    val magic = bytes.copyOfRange(0, minOf(4, bytes.size)).decodeToString()
    if (magic != MAGIC) throw IllegalArgumentException("not a waveform: it starts with \"$magic\", not \"$MAGIC\"")
    fun uint32(at: Int): Long =
        (bytes.unsigned(at).toLong() shl 24) or (bytes.unsigned(at + 1).toLong() shl 16) or
            (bytes.unsigned(at + 2).toLong() shl 8) or bytes.unsigned(at + 3).toLong()
    val peaksPerSecond = (bytes.unsigned(4) shl 8) or bytes.unsigned(5)
    val durationMilliseconds = uint32(6)
    val count = uint32(10).toInt()
    val peaks = FloatArray(count) { bytes.unsigned(14 + it) / 255f }
    return Waveform(durationMilliseconds / 1000.0, peaksPerSecond, peaks)
}

private val http by lazy { HttpClient() }

suspend fun loadWaveform(url: String): Waveform {
    val response = http.get("/api$url")
    if (response.status.value !in 200..299) error("couldn't download $url: HTTP ${response.status.value}")
    return parseWaveform(response.readRawBytes())
}
