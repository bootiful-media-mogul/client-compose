package com.joshlong.mogul.client.api

import com.joshlong.mogul.client.platform.browser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * server-to-browser notifications, over Ably. listeners are plain callbacks; one that throws
 * doesn't cost the others their notification.
 */
class NotificationsService(private val client: GraphQlClient) {

    private val callbacks = mutableListOf<(Notification) -> Unit>()

    private val callbacksByCategory = mutableMapOf<String, MutableList<(Notification) -> Unit>>()

    private var started = false

    suspend fun start() {
        if (this.started) return
        this.started = true
        // nobody awaits this, so report failures here or every notification goes quiet with
        // nothing to explain why
        try {
            val data = this.client.execute(
                """
                query {
                  notificationContext {
                    ablyChannel
                    ablyTokenRequest { keyName, nonce, mac, ttl, capability, timestamp }
                  }
                }
                """,
            )
            val context = data["notificationContext"] as? JsonObject
            if (context == null) {
                println("no notification context was returned; notifications are disabled")
                return
            }
            val channel = context["ablyChannel"].stringOrNull() ?: return
            val tokenRequest = context["ablyTokenRequest"]!!.jsonObject.toString()
            browser.ablySubscribe(tokenRequest, channel) { message -> dispatch(message) }
        } catch (e: Exception) {
            println("could not subscribe to notifications: ${e.message}")
        }
    }

    private fun dispatch(message: String) {
        val notification = try {
            json.decodeFromString<Notification>(message)
        } catch (e: Exception) {
            println("could not parse the notification $message: ${e.message}")
            return
        }
        // snapshot the listeners first: a listener may unregister itself while we dispatch
        val listeners = this.callbacks.toList() + (this.callbacksByCategory[notification.category]?.toList() ?: emptyList())
        for (listener in listeners) {
            try {
                listener(notification)
            } catch (e: Exception) {
                println("a listener for the ${notification.category} notification failed: ${e.message}")
            }
        }
    }

    suspend fun notify(visible: Boolean, modal: Boolean): Boolean = this.client.field(
        "mutation Notify(\$visible: Boolean, \$modal: Boolean) { notify(visible: \$visible, modal: \$modal) }",
        mapOf("visible" to visible, "modal" to modal), "notify",
    )

    /** answers a function that unregisters the callback again */
    fun listenForCategory(category: String, callback: (Notification) -> Unit): () -> Unit {
        val list = this.callbacksByCategory.getOrPut(category) { mutableListOf() }
        list.add(callback)
        return { list.remove(callback) }
    }

    fun listen(callback: (Notification) -> Unit): () -> Unit {
        if (callback !in this.callbacks) this.callbacks.add(callback)
        return { this.callbacks.remove(callback) }
    }
}
