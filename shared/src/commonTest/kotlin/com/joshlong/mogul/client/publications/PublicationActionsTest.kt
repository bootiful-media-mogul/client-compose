package com.joshlong.mogul.client.publications

import com.joshlong.mogul.client.api.Publication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

// the pure parts of the buttons beneath a publication panel
class PublicationActionsTest {

    private val zone = TimeZone.UTC

    @Test
    fun sayPublishUnlessThePanelNamesThem() {
        assertEquals("Publish", mainButtonLabel(null))
        assertEquals("Create blog post", mainButtonLabel("Create blog post"))
    }

    // the home page's calendar picks a day; the picker starts on it, at nine
    @Test
    fun startTheScheduleOnTheDayPicked() {
        val now = LocalDateTime(2026, 11, 22, 10, 41).toInstant(this.zone)
        val day = LocalDate(2026, 11, 30)
        assertEquals(LocalDateTime(2026, 11, 30, 9, 0), initialScheduleTime(now, this.zone, day).toLocalDateTime(this.zone))
    }

    // no day, today, or a day gone by: the next hour, as anywhere else
    @Test
    fun startTheScheduleAtTheNextHourOtherwise() {
        val now = LocalDateTime(2026, 11, 22, 10, 41).toInstant(this.zone)
        val next = LocalDateTime(2026, 11, 22, 11, 0)
        assertEquals(next, initialScheduleTime(now, this.zone, null).toLocalDateTime(this.zone))
        assertEquals(next, initialScheduleTime(now, this.zone, LocalDate(2026, 11, 22)).toLocalDateTime(this.zone))
        assertEquals(next, initialScheduleTime(now, this.zone, LocalDate(2026, 11, 1)).toLocalDateTime(this.zone))
    }

    // picking a day ahead means scheduling, so the picker is already open; not for a plugin that can't be
    @Test
    fun openTheSchedulerOnlyForADayAheadAndAPluginThatCanBeScheduled() {
        val now = LocalDateTime(2026, 11, 22, 10, 41).toInstant(this.zone)
        assertTrue(opensScheduler(true, now, this.zone, LocalDate(2026, 11, 23)))
        assertFalse(opensScheduler(false, now, this.zone, LocalDate(2026, 11, 23)))
        assertFalse(opensScheduler(true, now, this.zone, LocalDate(2026, 11, 22)))
        assertFalse(opensScheduler(true, now, this.zone, null))
    }

    @Test
    fun startAtTheTopOfTheNextHour() {
        val now = LocalDateTime(2026, 11, 22, 10, 41, 13).toInstant(this.zone)
        val next = topOfNextHour(now, this.zone).toLocalDateTime(this.zone)
        assertEquals(LocalDateTime(2026, 11, 22, 11, 0), next)
    }

    @Test
    fun startAtTheTopOfTheNextHourAcrossMidnight() {
        val now = LocalDateTime(2026, 12, 31, 23, 30).toInstant(this.zone)
        assertEquals(LocalDateTime(2027, 1, 1, 0, 0), topOfNextHour(now, this.zone).toLocalDateTime(this.zone))
    }

    @Test
    fun publishAtThePickedTimeAndOnlyATimeStillToCome() {
        val now = LocalDateTime(2026, 11, 22, 10, 0).toInstant(this.zone)
        assertTrue(canSchedule(true, topOfNextHour(now, this.zone), now))
        assertFalse(canSchedule(true, now - 1.minutes, now))
        assertFalse(canSchedule(true, null, now))
        assertTrue(canSchedule(true, now + 1.days, now))
        // a panel that can't publish can't schedule either
        assertFalse(canSchedule(false, now + 1.days, now))
    }

    @Test
    fun treatPendingDraftAndUnpublishedPublicationsAsWithdrawn() {
        assertTrue(withdrawn(Publication(id = 1, state = "pending", url = "https://x")))
        assertTrue(withdrawn(Publication(id = 1, state = "published", url = "")))
        assertFalse(withdrawn(Publication(id = 1, state = "published", url = "https://x")))
    }
}
