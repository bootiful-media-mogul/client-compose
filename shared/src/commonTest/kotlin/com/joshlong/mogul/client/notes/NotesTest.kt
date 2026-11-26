package com.joshlong.mogul.client.notes

import com.joshlong.mogul.client.api.Note
import com.joshlong.mogul.client.i18n.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesTest {

    @Test
    fun onlyAbsoluteWebUrlsAreLinks() {
        assertTrue(isWebUrl("https://spring.io"))
        assertTrue(isWebUrl("http://example.com/a?b=c"))
        assertFalse(isWebUrl("spring.io"))
        assertFalse(isWebUrl("ftp://example.com"))
        assertFalse(isWebUrl("https://"))
        assertFalse(isWebUrl("javascript:alert(1)"))
    }

    @Test
    fun aSummaryFillsInANoteOrGoesUnderItsText() {
        assertEquals("The summary.", withSummary("", "The summary."))
        assertEquals("The summary.", withSummary(" \n ", "The summary."))
        assertEquals("Mine.\n\nThe summary.", withSummary("Mine.\n\n\n", "The summary."))
    }

    @Test
    fun aSummaryThatDidNotComeSaysWhy() {
        assertNull(summaryProblemFor("summarized", null))
        assertEquals("notes.summary.errors.blocked", summaryProblemFor("rejected", "blocked"))
        assertEquals("notes.summary.errors.not-found", summaryProblemFor("rejected", "not-found"))
        assertEquals("notes.summary.errors.server-error", summaryProblemFor("rejected", "server-error"))
        assertEquals("notes.summary.errors.not-html", summaryProblemFor("rejected", "not-html"))
        assertEquals("notes.summary.errors.unreachable", summaryProblemFor("failed", "unreachable"))
        // a reason this client has no words for
        assertEquals("notes.summary.errors.failed", summaryProblemFor("rejected", "something-new"))
        assertEquals("notes.summary.errors.failed", summaryProblemFor(null, null))
    }

    @Test
    fun everyReasonHasAMessage() {
        for (reason in listOf(
            "address", "blocked", "not-found", "server-error", "status", "redirects", "content-type", "encoding",
            "not-html", "empty", "unreachable", "model",
        )) {
            val key = summaryProblemFor("rejected", reason)!!
            assertTrue(t(key) != key, "no message for $key")
        }
        assertTrue(t("notes.summary.errors.timeout") != "notes.summary.errors.timeout")
        assertTrue(t("notes.summary.errors.failed") != "notes.summary.errors.failed")
    }

    @Test
    fun blankFieldsAreNull() {
        assertNull(blankToNull("   "))
        assertEquals("x", blankToNull(" x "))
    }

    @Test
    fun aNoteDropsAsALinkThenADashThenItsText() {
        assertEquals(
            "[Spring](https://spring.io) - read this",
            noteAsText(Note(title = "Spring", url = "https://spring.io", note = "read this"))
        )
        assertEquals("[Spring](https://spring.io)", noteAsText(Note(title = "Spring", url = "https://spring.io")))
        assertEquals("Spring", noteAsText(Note(title = "Spring")))
        assertEquals("https://spring.io", noteAsText(Note(url = "https://spring.io")))
        assertEquals("read this", noteAsText(Note(note = "read this")))
        assertEquals("Spring - read this", noteAsText(Note(title = "Spring", note = "read this")))
        assertEquals("https://spring.io - read this", noteAsText(Note(url = "https://spring.io", note = "read this")))
        assertEquals("[a \\[b\\]](https://x.io)", noteAsText(Note(title = "a [b]", url = "https://x.io")))
        assertNull(noteAsText(Note(title = " ", url = "", note = "  ")))
    }
}
