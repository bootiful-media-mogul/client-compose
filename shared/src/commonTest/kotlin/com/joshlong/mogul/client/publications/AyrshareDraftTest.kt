package com.joshlong.mogul.client.publications

import com.joshlong.mogul.client.api.Attachment
import com.joshlong.mogul.client.api.AyrsharePublicationComposition
import com.joshlong.mogul.client.api.Composition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AyrshareDraftTest {

    private val compositions = listOf("bluesky", "linkedin", "twitter").mapIndexed { i, platform ->
        AyrsharePublicationComposition(
            Composition(100L + i, platform, if (platform == "twitter") listOf(Attachment(1)) else emptyList()),
            platform,
        )
    }

    private fun draft(
        shared: String = "hello world",
        selected: List<String> = listOf("bluesky", "twitter"),
        overrides: Map<String, String> = emptyMap(),
    ) = AyrshareDraft(shared, selected, overrides)

    @Test
    fun givesEveryPlatformTheSharedTextUnlessItHasTextOfItsOwn() {
        val d = draft(overrides = mapOf("twitter" to "short"))
        assertEquals("hello world", effectiveText(d, "bluesky"))
        assertEquals("short", effectiveText(d, "twitter"))
    }

    @Test
    fun treatsAnOverrideThatMatchesTheSharedTextAsTextOfItsOwn() {
        val d = draft(shared = "changed", overrides = mapOf("twitter" to "hello world"))
        assertEquals("hello world", effectiveText(d, "twitter"))
    }

    @Test
    fun sendsTheSameShapeThePanelAlwaysHasForTheSelectedPlatformsOnly() {
        val d = draft(overrides = mapOf("twitter" to "short", "linkedin" to "unsent"))
        assertEquals(
            mapOf<String, Any?>(
                "bluesky" to "hello world",
                "blueskyCompositionId" to 100L,
                "twitter" to "short",
                "twitterCompositionId" to 102L,
            ),
            buildClientContext(d, this.compositions),
        )
    }

    @Test
    fun countsHowFarPastItsLimitAPlatformIs() {
        val d = draft(shared = "x".repeat(290))
        assertEquals(10, charactersOver(d, "twitter"))
        assertEquals(0, charactersOver(d, "bluesky"))
    }

    @Test
    fun isPublishableOnceAPlatformIsPickedAndNoneOfThemIsEmpty() {
        assertFalse(isPublishable(draft(selected = emptyList())))
        assertFalse(isPublishable(draft(shared = "  ")))
        assertTrue(isPublishable(draft(shared = "", overrides = mapOf("bluesky" to "a", "twitter" to "b"))))
        assertFalse(isPublishable(draft(overrides = mapOf("twitter" to ""))))
    }

    @Test
    fun formatsLimitsWithThousandsSeparators() {
        assertEquals("63,206", formatNumber(63206))
        assertEquals("280", formatNumber(280))
        assertEquals("40,000", formatNumber(40000))
    }

    @Test
    fun hasABadgeForAnUnknownPlatformToo() {
        assertEquals("X", badgeFor("twitter").initials)
        assertEquals("?", badgeFor("myspace").initials)
    }
}
