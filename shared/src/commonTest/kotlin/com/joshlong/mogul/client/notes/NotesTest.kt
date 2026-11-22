package com.joshlong.mogul.client.notes

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
}
