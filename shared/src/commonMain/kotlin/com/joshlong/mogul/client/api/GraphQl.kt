package com.joshlong.mogul.client.api

import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** one entry of a GraphQL response's errors */
data class GraphQlError(val message: String, val classification: String?)

class GraphQlException(val status: Int, val errors: List<GraphQlError>) :
    RuntimeException(errors.joinToString("; ") { it.message }.ifBlank { "GraphQL request failed with HTTP $status" }) {

    /**
     * only an expired or rejected session should send you back to the login flow. every other
     * GraphQL error -- a failed validation, a resolver blowing up -- is the caller's to deal with.
     */
    val authenticationFailure: Boolean
        get() = status == 401 || status == 403 ||
            errors.any { it.classification?.uppercase() in setOf("UNAUTHORIZED", "FORBIDDEN") }
}

/**
 * posts queries to the api's GraphQL endpoint through the gateway, which carries the session
 * cookie, so a relative URL is all it takes.
 */
class GraphQlClient(private val http: HttpClient, private val url: String = "/api/graphql") {

    /** runs the query and answers its `data`; throws [GraphQlException] on any error */
    suspend fun execute(query: String, variables: Map<String, Any?> = emptyMap()): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("variables", toJson(variables))
        }
        val response: HttpResponse = this.http.post(this.url) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(body.toString())
        }
        val status = response.status.value
        val text = response.bodyAsText()
        val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val errors = (parsed?.get("errors") as? JsonArray)?.map { error ->
            val o = error.jsonObject
            GraphQlError(
                (o["message"] as? JsonPrimitive)?.content ?: "",
                ((o["extensions"] as? JsonObject)?.get("classification") as? JsonPrimitive)?.content,
            )
        } ?: emptyList()
        val data = parsed?.get("data") as? JsonObject
        if (status !in 200..299 || errors.isNotEmpty() || data == null) {
            val exception = GraphQlException(status, errors)
            println("graphql error: ${exception.message}")
            if (exception.authenticationFailure) {
                Events.emit(AppEvent.Unauthorized)
            }
            throw exception
        }
        return data
    }

    /** runs the query and decodes one top-level field of its result */
    suspend inline fun <reified T> field(query: String, variables: Map<String, Any?>, field: String): T {
        val data = execute(query, variables)
        return json.decodeFromJsonElement<T>(data[field] ?: JsonNull)
    }
}

/** variables for a query, as JSON. instants go as ISO-8601 strings, the DateTime scalar */
fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Instant -> JsonPrimitive(value.toString())
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
    is Iterable<*> -> JsonArray(value.map { toJson(it) })
    is Array<*> -> JsonArray(value.map { toJson(it) })
    else -> error("can't send a ${value::class.simpleName} as a GraphQL variable")
}

/** a JSON object string (as the api sends contexts) as a map of numbers, e.g. `{"podcastId": 3}` */
fun parseNumberContext(context: String?): Map<String, Long> {
    if (context.isNullOrBlank()) return emptyMap()
    val element = runCatching { json.parseToJsonElement(context) }.getOrNull() as? JsonObject ?: return emptyMap()
    return element.mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()?.let { k to it }
    }.toMap()
}

/** the fields of a JSON object string, as strings, for interpolating into messages */
fun parseStringContext(context: String?): Map<String, String> {
    if (context.isNullOrBlank()) return emptyMap()
    val element = runCatching { json.parseToJsonElement(context) }.getOrNull() as? JsonObject ?: return emptyMap()
    return element.mapValues { (_, v) -> if (v is JsonPrimitive) v.content else v.toString() }
}

internal fun JsonElement?.asListOrEmpty(): List<JsonElement> = (this as? JsonArray)?.jsonArray ?: emptyList()

internal fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
