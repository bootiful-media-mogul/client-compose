package com.joshlong.mogul.client.notifications

import com.joshlong.mogul.client.api.Notification
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationMessageTest {

    @Test
    fun aCategoryReadsAsItsMessageWithTheKey() {
        val notification = Notification(category = "job-started-event", key = "reindex", context = "reindex")
        assertEquals("the job reindex has started", notificationMessage(notification))
    }

    @Test
    fun anUnknownCategoryFallsBackToItsKey() {
        val notification = Notification(category = "no-such-category", key = "k", context = """{"reason": "quota"}""")
        assertEquals("notifications.no-such-category", notificationMessage(notification))
    }

    @Test
    fun aJsonContextsFieldsAreThereByName() {
        val notification = Notification(category = "podcast-episode-segment-cleaning-failed-event", key = "7", context = """{"reason": "quota"}""")
        assertEquals(
            "The audio for segment #7 couldn't be cleaned (quota), so it will use the original audio.",
            notificationMessage(notification),
        )
    }
}
