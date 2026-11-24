package com.joshlong.mogul.client.podcasts.segments

import com.joshlong.mogul.client.api.SegmentFilters
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// the editor's preview of what the server renders: the ranges kept by hand, minus the
// automatic cuts. this mirrors SegmentCuts in the api, and the two are held to the same
// cases (the Vue client's test/unit/segment-cuts-cases.json, the api's
// src/test/resources/segment-cuts/cases.json, and SegmentCutsTest here). everything is in
// milliseconds.

data class Range(val start: Double, val end: Double)

@Serializable
data class Detection(
    val start: Double,
    val end: Double,
    /** cleanvoice's kind, like FILLER_SOUND; absent for a silence */
    val kind: String? = null,
)

@Serializable
data class Detections(val cleanvoice: List<Detection> = emptyList(), val silences: List<Detection> = emptyList())

/** a stretch an automatic cut takes out, with the detection responsible */
data class Removal(val id: String, val kind: String, val start: Double, val end: Double)

const val FILLER = "FILLER_SOUND"
const val STUTTER = "STUTTERING"
private const val SHORTEST_PIECE = 10.0

/**
 * a number as javascript prints it: `1000`, not `1000.0`. detection ids are stored by the
 * server as the Vue client made them, so they have to come out the same
 */
internal fun jsNumber(value: Double): String =
    if (value == floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

/** javascript's Math.round: halves go up, toward positive infinity */
internal fun jsRound(value: Double): Double = floor(value + 0.5)

fun detectionId(detection: Detection): String = (detection.kind ?: "silence") + "@" + jsNumber(detection.start)

/** about 45% of a filler, between a tenth and a third of a second, never longer than it */
fun pauseFor(fillerLength: Double): Double {
    val pause = max(100.0, min(350.0, jsRound(fillerLength * 0.45)))
    return min(fillerLength, pause)
}

/**
 * every stretch the automatic cuts would take out, with the detection responsible, so the
 * editor can draw them and put any one of them back
 */
fun removals(detections: Detections?, filters: SegmentFilters?, restored: Set<String>): List<Removal> {
    if (detections == null || filters == null) return emptyList()
    val out = mutableListOf<Removal>()
    for (d in detections.cleanvoice) {
        val wanted = (filters.fillers && d.kind == FILLER) || (filters.stutters && d.kind == STUTTER)
        if (!wanted || detectionId(d) in restored) continue
        out.add(Removal(detectionId(d), d.kind, d.start + pauseFor(d.end - d.start), d.end))
    }
    if (filters.silences) {
        for (d in detections.silences) {
            if (d.end - d.start <= filters.silenceLongerThan || detectionId(d) in restored) continue
            val half = floor(filters.silenceLeave / 2.0)
            out.add(Removal(detectionId(d), "SILENCE", d.start + half, d.end - half))
        }
    }
    return out.filter { it.end > it.start }
}

fun compute(
    duration: Double,
    kept: List<Range>?,
    detections: Detections?,
    filters: SegmentFilters?,
    restored: Set<String>,
): List<Range> {
    val byHand = if (!kept.isNullOrEmpty()) kept else listOf(Range(0.0, duration))
    if (detections == null || filters == null) return byHand
    val sorted = removals(detections, filters, restored).map { doubleArrayOf(it.start, it.end) }.sortedBy { it[0] }
    val merged = mutableListOf<DoubleArray>()
    for (r in sorted) {
        val last = merged.lastOrNull()
        if (last != null && r[0] <= last[1]) last[1] = max(last[1], r[1]) else merged.add(doubleArrayOf(r[0], r[1]))
    }
    val result = mutableListOf<Range>()
    for (range in byHand) {
        var from = range.start
        for (r in merged) {
            if (r[1] <= from || r[0] >= range.end) continue
            if (r[0] - from >= SHORTEST_PIECE) result.add(Range(from, r[0]))
            from = max(from, r[1])
        }
        if (range.end - from >= SHORTEST_PIECE) result.add(Range(from, range.end))
    }
    // nothing left would render as "keep everything", which is the opposite
    return result.ifEmpty { byHand }
}
