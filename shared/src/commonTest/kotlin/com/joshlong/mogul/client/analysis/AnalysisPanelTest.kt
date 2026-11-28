package com.joshlong.mogul.client.analysis

import com.joshlong.mogul.client.api.Highlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalysisPanelTest {

    @Test
    fun anAnalysisThatCameHasNoProblem() = assertNull(analysisProblemFor("analyzed"))

    @Test
    fun nothingToAnalyzeSaysSo() = assertEquals("analysis.errors.empty", analysisProblemFor("empty"))

    @Test
    fun aHighlightInASegmentSaysWhichOne() =
        assertEquals("Useful (8/10), in Interview", highlightLabel(Highlight("useful", "q", "r", 8, "12", "Interview")))

    @Test
    fun aHighlightInAPostSaysNothingMore() =
        assertEquals("Quotable (3/10)", highlightLabel(Highlight("quotable", "q", "r", 3, "7", null)))

    @Test
    fun anythingElseFailed() {
        assertEquals("analysis.errors.failed", analysisProblemFor("failed"))
        assertEquals("analysis.errors.failed", analysisProblemFor(null))
    }
}
