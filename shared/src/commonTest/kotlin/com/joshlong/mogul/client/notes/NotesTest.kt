package com.joshlong.mogul.client.notes

import com.joshlong.mogul.client.api.Note
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
