package com.joshlong.mogul.client.podcasts.segments

import com.joshlong.mogul.client.api.SegmentFilters
import com.joshlong.mogul.client.api.json
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentCutsTest {

    @Serializable
    private data class Case(
        val name: String,
        val duration: Double,
        val kept: List<List<Double>>? = null,
        val detections: Detections? = null,
        val filters: SegmentFilters? = null,
        val restored: List<String> = emptyList(),
        val expected: List<List<Double>>? = null,
    )

    private fun pairs(ranges: List<List<Double>>?) = ranges?.map { (start, end) -> Range(start, end) }

    // the same cases the api's SegmentCutsTest runs
    @Test
    fun sharedWithTheApi() {
        val cases = json.decodeFromString<List<Case>>(segmentCutsCases)
        check(cases.isNotEmpty())
        for (c in cases) {
            assertEquals(
                pairs(c.expected),
                compute(c.duration, pairs(c.kept), c.detections, c.filters, c.restored.toSet()),
                c.name,
            )
        }
    }

    @Test
    fun detectionIdsPrintNumbersAsJavascriptDoes() {
        assertEquals("FILLER_SOUND@1000", detectionId(Detection(1000.0, 1600.0, FILLER)))
        assertEquals("silence@1500.5", detectionId(Detection(1500.5, 4000.0)))
    }
}
