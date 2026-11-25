package com.joshlong.mogul.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TextDragTest {

    @Test
    fun droppedTextIsFollowedByANewline() {
        assertEquals("x\n", insertDroppedText("", 0, "x"))
        assertEquals("see x\nnow", insertDroppedText("see now", 4, "x"))
        assertEquals("see x\nnow", insertDroppedText("seenow", 3, "x"))
        assertEquals("see x\n", insertDroppedText("see", 3, "x"))
        assertEquals("x\nsee", insertDroppedText("see", 0, "x"))
        assertEquals("see x\n", insertDroppedText("see", 99, "x"))
        assertEquals("see x\nnow", insertDroppedText("see  now", 3, "x"))
        assertEquals("see x\n\nnow", insertDroppedText("see\nnow", 3, "x"))
    }

    @Test
    fun droppedTextIsSpacedFromTheWordsAroundItInASingleLineField() {
        assertEquals("x", insertDroppedText("", 0, "x", singleLine = true))
        assertEquals("see x now", insertDroppedText("see now", 4, "x", singleLine = true))
        assertEquals("see x now", insertDroppedText("seenow", 3, "x", singleLine = true))
        assertEquals("see x", insertDroppedText("see", 3, "x", singleLine = true))
        assertEquals("x see", insertDroppedText("see", 0, "x", singleLine = true))
    }
}
