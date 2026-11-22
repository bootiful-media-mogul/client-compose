package com.joshlong.mogul.client.podcasts.segments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimecodeTest {

    @Test
    fun readsPlainSeconds() {
        assertEquals(90.0, parseTimecode("90"))
        assertEquals(30.5, parseTimecode("30.5"))
        assertEquals(30.0, parseTimecode("30s"))
    }

    @Test
    fun readsMinutesAndSecondsToTheMillisecond() {
        assertEquals(90.0, parseTimecode("1:30"))
        assertEquals(30.125, parseTimecode("0:30.125"))
        assertEquals(125.0, parseTimecode(" 2:05 "))
    }

    @Test
    fun readsHoursMinutesAndSeconds() {
        assertEquals(3723.5, parseTimecode("1:02:03.5"))
    }

    @Test
    fun refusesWhatIsNotATime() {
        for (text in listOf("", "abc", "1:75", "1::2", "1:2:3:4", "-5", "1.5:30", "1:30.")) {
            assertNull(parseTimecode(text), text)
        }
    }
}
