package com.joshlong.mogul.client.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Instant

/**
 * the DateTime scalar. the api sends ISO-8601 with an offset, but some older fields arrive
 * as epoch milliseconds, and the Vue client happily took either, so this does too.
 */
object FlexibleInstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: error("expected a DateTime, got $element")
        if (!primitive.isString) {
            primitive.doubleOrNull?.let { return Instant.fromEpochMilliseconds(it.toLong()) }
        }
        val text = primitive.content
        text.toDoubleOrNull()?.let { return Instant.fromEpochMilliseconds(it.toLong()) }
        return parseInstant(text)
    }
}

/** ISO-8601, tolerating a missing zone (read as UTC) as Java's LocalDateTime prints it */
fun parseInstant(text: String): Instant =
    runCatching { Instant.parse(text) }.getOrElse { Instant.parse(text.trimEnd('Z') + "Z") }

typealias DateTime = @Serializable(with = FlexibleInstantSerializer::class) Instant

@Serializable
data class User(
    val id: Long = 0,
    val name: String? = null,
    val email: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    /** IANA zone id, or null until the mogul has told us where they are */
    val timeZone: String? = null,
) {
    val fullName: String get() = listOfNotNull(this.givenName, this.familyName).filter { it.isNotBlank() }.joinToString(" ")

    val displayName: String get() = if (this.email != null) "$fullName (${this.email})" else fullName

    val initials: String
        get() {
            val fromName = "${this.givenName?.firstOrNull() ?: ""}${this.familyName?.firstOrNull() ?: ""}"
            return fromName.ifEmpty { this.email?.firstOrNull()?.toString() ?: "" }.uppercase()
        }
}

/**
 * a single day in the life of a mogul. this -- and not the mogul -- is what publications
 * attach to, so that they stay bounded to a day instead of piling up against an identity
 * that never ends.
 */
@Serializable
data class MogulStatus(val id: Long = 0, val mogulId: Long = 0, val date: String = "")

@Serializable
data class Job(val name: String = "", val requiredContextAttributes: List<String> = emptyList())

@Serializable
data class Podcast(val id: Long = 0, val title: String = "", val created: DateTime? = null)

@Serializable
data class Setting(
    val name: String = "",
    val valid: Boolean = false,
    val value: String? = null,
    val required: Boolean = false,
    /** SECRET, TEXT or BOOLEAN: how the settings page shows it */
    val type: String = "SECRET",
    /** what an unset value means, if anything */
    val defaultValue: String? = null,
)

@Serializable
data class SettingsPage(val valid: Boolean = false, val category: String = "", val settings: List<Setting> = emptyList())

@Serializable
data class ManagedFile(
    val id: Long = 0,
    val bucket: String? = null,
    val folder: String? = null,
    val filename: String? = null,
    val size: Double = 0.0,
    val written: Boolean = false,
    val visible: Boolean = false,
    val contentType: String? = null,
    val visibleUrl: String? = null,
    val downloadableUrl: String? = null,
    val url: String? = null,
)

@Serializable
data class Transcript(val id: Long = 0, val transcript: String? = null)

/** one stretch of a segment's uploaded audio to keep, in milliseconds */
@Serializable
data class KeptRange(val start: Long, val end: Long)

/** the automatic cuts in effect for a segment */
@Serializable
data class SegmentFilters(
    val fillers: Boolean = false,
    val stutters: Boolean = false,
    val silences: Boolean = false,
    val silenceLongerThan: Long = 0,
    val silenceLeave: Long = 0,
)

@Serializable
data class PodcastEpisodeSegment(
    val id: Long = 0,
    val name: String? = null,
    val audio: ManagedFile? = null,
    val order: Int = 0,
    val transcript: Transcript? = null,
    /** milliseconds */
    val duration: Double = 0.0,
    val crossFadeDuration: Long = 0,
    /** the upload with the edits applied: what actually goes into the episode */
    val producedAudio: ManagedFile? = null,
    /** null for older segments until one is requested */
    val waveform: ManagedFile? = null,
    /** null when the segment hasn't been edited */
    val edits: List<KeptRange>? = null,
    /** cleanvoice's cleaned copy, once there is one for the audio as it is now */
    val cleanedAudio: ManagedFile? = null,
    val cleaned: Boolean = false,
    val cleaning: Boolean = false,
    val cleaningError: String? = null,
    val cleanable: Boolean = false,
    /** json: {"cleanvoice": [{start, end, kind}], "silences": [{start, end}]} */
    val detections: String? = null,
    val filters: SegmentFilters? = null,
    val restored: List<String> = emptyList(),
)

/**
 * what a publication is about, named well enough to list beside publications about other
 * kinds of things. title is null when the thing has since been deleted.
 */
@Serializable
data class PublishableReference(val type: String = "", val id: Long = 0, val title: String? = null)

@Serializable
data class PublicationOutcome(
    val id: Long = 0,
    val success: Boolean = false,
    val url: String? = null,
    val key: String? = null,
    val serverErrorMessage: String? = null,
    val created: DateTime? = null,
    val preview: String? = null,
)

@Serializable
data class Publication(
    val id: Long = 0,
    val plugin: String = "",
    val created: DateTime? = null,
    val published: DateTime? = null,
    /** when it was due; null for "as soon as the job queue got to it" */
    val scheduled: DateTime? = null,
    val url: String? = null,
    val state: String? = null,
    val outcomes: List<PublicationOutcome> = emptyList(),
    /** only asked for where publications of different things are shown together */
    val publishable: PublishableReference? = null,
) {
    /**
     * every publication waits its turn on the server's job queue, and comes back from it
     * 'pending' until it's done
     */
    val publishing: Boolean get() = this.state == "pending"
}

@Serializable
data class Attachment(
    val id: Long = 0,
    val caption: String? = null,
    val managedFile: ManagedFile = ManagedFile(),
    val markdown: String? = null,
)

@Serializable
data class Composition(val id: Long = 0, val field: String? = null, val attachments: List<Attachment> = emptyList())

@Serializable
data class AyrsharePublicationComposition(val composition: Composition = Composition(), val platform: String = "")

@Serializable
data class PodcastEpisode(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val podcastId: Long = 0,
    val graphic: ManagedFile? = null,
    val producedAudio: ManagedFile? = null,
    val complete: Boolean = false,
    val created: DateTime? = null,
    /** milliseconds */
    val duration: Double = 0.0,
    val segments: List<PodcastEpisodeSegment> = emptyList(),
    val descriptionComposition: Composition? = null,
    val titleComposition: Composition? = null,
)

@Serializable
data class Notification(
    val mogulId: Long = 0,
    val key: String = "",
    val category: String = "",
    val context: String? = null,
    @Serializable(with = FlexibleInstantSerializer::class) val `when`: Instant? = null,
    val modal: Boolean = false,
    val visible: Boolean = false,
)

@Serializable
data class SearchableResult(
    val searchableId: Long = 0,
    val aggregateId: Long = 0,
    val title: String? = null,
    /** json from the server */
    val context: String? = null,
    val description: String? = null,
    val type: String = "",
    val rank: Double = 0.0,
    val created: DateTime? = null,
) {
    val contextMap: Map<String, Long> get() = parseNumberContext(this.context)
}

@Serializable
data class Blog(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val rssUrl: String? = null,
    val created: DateTime? = null,
)

@Serializable
data class Post(
    val id: Long = 0,
    val blogId: Long = 0,
    val title: String = "",
    val content: String = "",
    val summary: String = "",
    val rssSlug: String? = null,
    val complete: Boolean = false,
    val visible: Boolean = false,
    val created: DateTime? = null,
    val descriptionComposition: Composition? = null,
)

@Serializable
data class Note(
    val id: Long = 0,
    val note: String = "",
    val type: String = "",
    val created: DateTime? = null,
    val done: DateTime? = null,
    val url: String? = null,
    val title: String? = null,
)

@Serializable
data class EntityContext(val resolvedType: String, val context: Map<String, Long>)

@Serializable
data class WordPressStatus(val displayName: String? = null, val connected: Boolean = false, val id: Long = 0)

@Serializable
data class ServerTime(
    val instant: String? = null,
    val javaTimeZone: String? = null,
    val javaOffset: String? = null,
    val javaLocalTime: String? = null,
    val databaseTimeZone: String? = null,
    val databaseLocalTime: String? = null,
)
