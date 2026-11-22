package com.joshlong.mogul.client.app

import com.joshlong.mogul.client.api.json
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * server-to-app notifications, over Ably's server-sent events: the Ably library the web build
 * loads is a browser script, but its SSE endpoint is plain HTTP. the api's token request is
 * exchanged for a token at Ably's REST API, as the library would, and the channel streamed with it
 */
internal object AblySse {

    /** a client of its own: the gateway's bearer token must never go to Ably */
    private val http = HttpClient {
        install(HttpTimeout) {
            // a quiet channel is still an open one; Ably's heartbeats keep the socket alive
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = 60_000L
        }
    }

    suspend fun subscribe(tokenRequestJson: String, channel: String, onMessage: (String) -> Unit) {
        val tokenRequest = json.parseToJsonElement(tokenRequestJson).jsonObject
        val keyName = tokenRequest["keyName"]?.jsonPrimitive?.content ?: error("the token request names no key")
        val token = runCatching {
            val details = this.http.post("https://rest.ably.io/keys/$keyName/requestToken") {
                contentType(ContentType.Application.Json)
                setBody(tokenRequestJson)
            }.bodyAsText()
            json.parseToJsonElement(details).jsonObject["token"]?.jsonPrimitive?.content
        }.getOrNull()
        if (token == null) {
            println("could not get an Ably token for notifications")
            return
        }
        // the token outlives a dropped connection (an hour, as the api asks for), so a stream
        // that ends is simply opened again, a little later each time it keeps failing
        var backoff = 1_000L
        while (true) {
            val outcome = runCatching { this.stream(token, channel, onMessage) }
            if (outcome.getOrNull() == Stop) return
            outcome.exceptionOrNull()?.let { println("notifications dropped: ${it.message}") }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private object Stop

    private suspend fun stream(token: String, channel: String, onMessage: (String) -> Unit): Any? =
        this.http.prepareGet("https://realtime.ably.io/sse") {
            parameter("v", "1.2")
            parameter("channels", channel)
            parameter("accessToken", token)
            parameter("heartbeats", "true")
        }.execute { response ->
            // an expired or rejected token won't get better by asking again
            if (response.status.value in 400..499) {
                println("notifications refused: HTTP ${response.status.value}")
                return@execute Stop
            }
            val body = response.bodyAsChannel()
            while (!body.isClosedForRead) {
                val line = body.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                // each event is a message, enveloped: what the api published is its data
                val message = runCatching { json.parseToJsonElement(line.removePrefix("data:").trim()) }.getOrNull() as? JsonObject ?: continue
                when (val data = message["data"]) {
                    null -> Unit
                    is JsonPrimitive -> onMessage(data.content)
                    else -> onMessage(data.toString())
                }
            }
            null
        }
}
