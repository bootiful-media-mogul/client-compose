package com.joshlong.mogul.client.ui

import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsTest {

    @Test
    fun onlyFieldsForWordsGetTheWritingTools() {
        assertTrue(takesProse(KeyboardType.Text))
        assertTrue(takesProse(KeyboardType.Ascii))
        assertFalse(takesProse(KeyboardType.Decimal))
        assertFalse(takesProse(KeyboardType.Number))
        assertFalse(takesProse(KeyboardType.Phone))
        assertFalse(takesProse(KeyboardType.Uri))
        assertFalse(takesProse(KeyboardType.Email))
        assertFalse(takesProse(KeyboardType.Password))
        assertFalse(takesProse(KeyboardType.NumberPassword))
    }
}
