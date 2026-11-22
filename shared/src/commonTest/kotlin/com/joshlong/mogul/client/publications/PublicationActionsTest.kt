package com.joshlong.mogul.client.publications

import com.joshlong.mogul.client.api.Publication
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
        assertEquals("Publish", mainButtonLabel(null, null))
        assertEquals("Create blog post", mainButtonLabel(null, "Create blog post"))
    }

    // the home page's calendar has already said when, so the main button schedules for then
    @Test
    fun scheduleForTheMomentTheyAreGiven() {
        val whenever = LocalDateTime(2099, 6, 15, 9, 0).toInstant(TimeZone.currentSystemDefault())
        assertTrue(mainButtonLabel(whenever, "Create blog post").startsWith("Schedule for 06/15/99"))
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
