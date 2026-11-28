package com.joshlong.mogul.client.api

import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.platform.browser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

// the api, one class per area, as services.ts had them. the queries are the Vue client's,
// field for field, so the two clients ask the server the same things.

class MogulService(private val client: GraphQlClient) {

    suspend fun user(): User = this.client.field(
        "query { me { id, name, email, givenName, familyName, timeZone } }",
        emptyMap(), "me",
    )

    /**
     * the account's language as a tag like `es`, or null until a client has said. asked for
     * apart from [user] so that an api which doesn't know the field yet fails this and not sign-in
     */
    suspend fun language(): String? =
        this.client.execute("query { me { language } }", emptyMap())["me"]?.jsonObject?.get("language")
            ?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    suspend fun setTimeZone(timeZone: String): Boolean = this.client.field(
        "mutation ( \$timeZone: String ) { setMogulTimeZone( timeZone: \$timeZone ) }",
        mapOf("timeZone" to timeZone), "setMogulTimeZone",
    )

    /**
     * the language of the mogul's whole system, as a tag like `es`: what the api has the
     * model write in, and what every device shows the studio in once it has signed in
     */
    suspend fun setLanguage(language: String): Boolean = this.client.field(
        "mutation ( \$language: String ) { setMogulLanguage( language: \$language ) }",
        mapOf("language" to language), "setMogulLanguage",
    )

    /**
     * a mogul_status is a calendar day, and which day a thing falls on depends on where you
     * are. only offered when the server has none stored: picking up the browser's zone on
     * every load would silently re-file your days the moment you opened the app from another
     * timezone, which is a decision to make deliberately rather than by travelling.
     */
    suspend fun adoptBrowserTimeZoneIfUnset(user: User) {
        if (!user.timeZone.isNullOrBlank()) return
        val zone = browser.timeZone()
        if (zone.isNotBlank()) setTimeZone(zone)
    }

    suspend fun todayStatus(): MogulStatus = this.client.field(
        "query { mogulStatusToday { id, mogulId, date } }", emptyMap(), "mogulStatusToday",
    )

    /** the status for a day (yyyy-mm-dd), created if there isn't one yet */
    suspend fun statusForDate(date: String): MogulStatus = this.client.field(
        "query ( \$date: String ) { mogulStatusForDate( date: \$date ) { id, mogulId, date } }",
        mapOf("date" to date), "mogulStatusForDate",
    )
}

class JobsService(private val client: GraphQlClient) {

    suspend fun launch(jobName: String, context: Map<String, Any?>): Boolean {
        val data = this.client.execute(
            """
            mutation JobLaunch(${'$'}jobName: String, ${'$'}contextAsJson: String) {
              launchJob(jobName: ${'$'}jobName, contextAsJson: ${'$'}contextAsJson)
            }
            """,
            mapOf("jobName" to jobName, "contextAsJson" to toJson(context).toString()),
        )
        return data["launchJob"]?.jsonPrimitive?.content == "true"
    }

    /** jobs that need a file to work on get one explicitly */
    suspend fun createManagedFile(jobName: String): Long {
        val data = this.client.execute(
            "mutation CreateJobManagedFile(\$jobName: String) { createJobManagedFile(jobName: \$jobName) { id } }",
            mapOf("jobName" to jobName),
        )
        return data["createJobManagedFile"]!!.jsonObject["id"]!!.jsonPrimitive.content.toDouble().toLong()
    }

    suspend fun jobs(): List<Job> = this.client.field("query { jobs { name, requiredContextAttributes } }", emptyMap(), "jobs")
}

class PodcastsService(private val client: GraphQlClient) {

    /** what the segment audio editor needs to open: the upload, its waveform, and the edits */
    suspend fun podcastEpisodeSegmentById(podcastEpisodeSegmentId: Long): PodcastEpisodeSegment = this.client.field(
        """
        query (${'$'}podcastEpisodeSegmentId: Int) {
          podcastEpisodeSegmentById(podcastEpisodeSegmentId: ${'$'}podcastEpisodeSegmentId) {
            id, name, order, duration,
            audio { id, filename, contentType, written, url },
            producedAudio { id, written },
            waveform { id, written, url },
            cleanedAudio { id, written, url },
            cleaned, cleaning, cleaningError, cleanable,
            detections,
            filters { fillers, stutters, silences, silenceLongerThan, silenceLeave },
            restored,
            edits { start, end }
          }
        }
        """,
        mapOf("podcastEpisodeSegmentId" to podcastEpisodeSegmentId), "podcastEpisodeSegmentById",
    )

    /**
     * keeps only these ranges (milliseconds) of the segment's audio and re-renders it; the
     * render finishes later, with a media-normalized-event
     */
    suspend fun editPodcastEpisodeSegment(
        podcastEpisodeSegmentId: Long,
        kept: List<KeptRange>?,
        filters: SegmentFilters? = null,
        restored: List<String> = emptyList(),
    ): Boolean = this.client.field(
        """
        mutation (${'$'}podcastEpisodeSegmentId: Int, ${'$'}kept: [KeptRangeInput!], ${'$'}filters: SegmentFiltersInput, ${'$'}restored: [String!]) {
          editPodcastEpisodeSegment(podcastEpisodeSegmentId: ${'$'}podcastEpisodeSegmentId, kept: ${'$'}kept, filters: ${'$'}filters, restored: ${'$'}restored)
        }
        """,
        mapOf(
            "podcastEpisodeSegmentId" to podcastEpisodeSegmentId,
            "kept" to kept?.map { mapOf("start" to it.start, "end" to it.end) },
            "filters" to filters?.let {
                mapOf(
                    "fillers" to it.fillers, "stutters" to it.stutters, "silences" to it.silences,
                    "silenceLongerThan" to it.silenceLongerThan, "silenceLeave" to it.silenceLeave,
                )
            },
            "restored" to restored,
        ),
        "editPodcastEpisodeSegment",
    )

    /**
     * cleans the segment's audio with the mogul's cleanvoice account; the outcome arrives as
     * podcast-episode-segment-cleaned-event or podcast-episode-segment-cleaning-failed-event
     */
    suspend fun cleanPodcastEpisodeSegment(podcastEpisodeSegmentId: Long): Boolean = this.client.field(
        "mutation (\$podcastEpisodeSegmentId: Int) { cleanPodcastEpisodeSegment(podcastEpisodeSegmentId: \$podcastEpisodeSegmentId) }",
        mapOf("podcastEpisodeSegmentId" to podcastEpisodeSegmentId), "cleanPodcastEpisodeSegment",
    )

    /** segments from before waveforms were drawn on upload get one on demand */
    suspend fun requestPodcastEpisodeSegmentWaveform(podcastEpisodeSegmentId: Long): Boolean = this.client.field(
        "mutation (\$podcastEpisodeSegmentId: Int) { requestPodcastEpisodeSegmentWaveform(podcastEpisodeSegmentId: \$podcastEpisodeSegmentId) }",
        mapOf("podcastEpisodeSegmentId" to podcastEpisodeSegmentId), "requestPodcastEpisodeSegmentWaveform",
    )

    suspend fun createPodcastEpisodeSegment(podcastEpisodeId: Long) {
        this.client.execute(
            "mutation (\$podcastEpisodeId: Int) { createPodcastEpisodeSegment(podcastEpisodeId: \$podcastEpisodeId) }",
            mapOf("podcastEpisodeId" to podcastEpisodeId),
        )
    }

    /** a null created leaves the existing creation date alone, server-side */
    suspend fun updatePodcastEpisode(
        podcastEpisodeId: Long,
        title: String,
        description: String,
        created: Instant? = null,
    ): PodcastEpisode {
        this.client.execute(
            """
            mutation (${'$'}podcastEpisodeId: Int, ${'$'}title: String, ${'$'}description: String, ${'$'}created: DateTime) {
              updatePodcastEpisode(podcastEpisodeId: ${'$'}podcastEpisodeId, title: ${'$'}title, description: ${'$'}description, created: ${'$'}created)
            }
            """,
            mapOf("podcastEpisodeId" to podcastEpisodeId, "title" to title, "description" to description, "created" to created),
        )
        return podcastEpisodeById(podcastEpisodeId)
    }

    suspend fun podcastEpisodeById(podcastEpisodeId: Long): PodcastEpisode = this.client.field(
        """
        query (${'$'}podcastEpisodeId: Int) {
          podcastEpisodeById(podcastEpisodeId: ${'$'}podcastEpisodeId) {
            created, id, title, podcastId, description, complete, duration,
            producedAudio { id },
            graphic { id },
            segments {
              id, name, audio { id }, order, crossFadeDuration, duration,
              cleaned, cleaning, cleanable, cleaningError,
              transcript { id, transcript }
            }
            descriptionComposition { id, field, attachments { id, caption, managedFile { id } } }
            titleComposition { id, field, attachments { id, caption, managedFile { id } } }
          }
        }
        """,
        mapOf("podcastEpisodeId" to podcastEpisodeId), "podcastEpisodeById",
    )

    suspend fun update(podcastId: Long, title: String): Podcast {
        this.client.execute(
            "mutation (\$podcastId: Int, \$title: String) { updatePodcast(podcastId: \$podcastId, title: \$title) }",
            mapOf("podcastId" to podcastId, "title" to title),
        )
        return podcastById(podcastId)
    }

    suspend fun create(title: String): Podcast = this.client.field(
        "mutation (\$title: String) { createPodcast(title: \$title) { id, title } }",
        mapOf("title" to title), "createPodcast",
    )

    /** the episodes, newest first: all of them, or one page when an offset and/or limit is given */
    suspend fun podcastEpisodesPreviews(podcastId: Long, offset: Int? = null, limit: Int? = null): List<PodcastEpisode> = this.client.field(
        """
        query GetPodcastEpisodesByPodcast(${'$'}podcastId: Int, ${'$'}offset: Int, ${'$'}limit: Int) {
          podcastEpisodesByPodcast(podcastId: ${'$'}podcastId, offset: ${'$'}offset, limit: ${'$'}limit) { created, id, title, description, complete }
        }
        """,
        mapOf("podcastId" to podcastId, "offset" to offset, "limit" to limit), "podcastEpisodesByPodcast",
    )

    suspend fun transcribePodcastEpisodeSegment(podcastEpisodeSegmentId: Long): Boolean = this.client.field(
        "mutation (\$podcastEpisodeSegmentId: Int) { transcribePodcastEpisodeSegment(podcastEpisodeSegmentId: \$podcastEpisodeSegmentId) }",
        mapOf("podcastEpisodeSegmentId" to podcastEpisodeSegmentId), "transcribePodcastEpisodeSegment",
    )

    suspend fun deletePodcastEpisodeSegment(podcastEpisodeSegmentId: Long) {
        this.client.execute(
            "mutation (\$podcastEpisodeSegmentId: Int) { deletePodcastEpisodeSegment(podcastEpisodeSegmentId: \$podcastEpisodeSegmentId) }",
            mapOf("podcastEpisodeSegmentId" to podcastEpisodeSegmentId),
        )
    }

    suspend fun deletePodcastEpisode(podcastEpisodeId: Long) {
        this.client.execute(
            "mutation (\$podcastEpisodeId: Int) { deletePodcastEpisode(podcastEpisodeId: \$podcastEpisodeId) }",
            mapOf("podcastEpisodeId" to podcastEpisodeId),
        )
    }

    suspend fun deletePodcast(podcastId: Long) {
        this.client.execute(
            "mutation (\$podcastId: Int) { deletePodcast(podcastId: \$podcastId) }",
            mapOf("podcastId" to podcastId),
        )
    }

    suspend fun podcasts(): List<Podcast> = this.client.field("query { podcasts { id, title, created } }", emptyMap(), "podcasts")

    suspend fun createPodcastEpisodeDraft(podcastId: Long, title: String, description: String): PodcastEpisode {
        val data = this.client.execute(
            """
            mutation (${'$'}podcastId: Int, ${'$'}title: String, ${'$'}description: String) {
              createPodcastEpisodeDraft(podcastId: ${'$'}podcastId, title: ${'$'}title, description: ${'$'}description) { id }
            }
            """,
            mapOf("podcastId" to podcastId, "title" to title, "description" to description),
        )
        val id = data["createPodcastEpisodeDraft"]!!.jsonObject["id"]!!.jsonPrimitive.content.toDouble().toLong()
        return podcastEpisodeById(id)
    }

    suspend fun podcastById(podcastId: Long): Podcast = this.client.field(
        "query (\$podcastId: Int) { podcastById(podcastId: \$podcastId) { id, title } }",
        mapOf("podcastId" to podcastId), "podcastById",
    )

    suspend fun movePodcastEpisodeSegmentDown(episodeId: Long, episodeSegmentId: Long) {
        this.client.execute(
            """
            mutation (${'$'}podcastEpisodeId: Int, ${'$'}podcastEpisodeSegmentId: Int) {
              movePodcastEpisodeSegmentDown(podcastEpisodeId: ${'$'}podcastEpisodeId, podcastEpisodeSegmentId: ${'$'}podcastEpisodeSegmentId)
            }
            """,
            mapOf("podcastEpisodeId" to episodeId, "podcastEpisodeSegmentId" to episodeSegmentId),
        )
    }

    suspend fun movePodcastEpisodeSegmentUp(episodeId: Long, episodeSegmentId: Long) {
        this.client.execute(
            """
            mutation (${'$'}podcastEpisodeId: Int, ${'$'}podcastEpisodeSegmentId: Int) {
              movePodcastEpisodeSegmentUp(podcastEpisodeId: ${'$'}podcastEpisodeId, podcastEpisodeSegmentId: ${'$'}podcastEpisodeSegmentId)
            }
            """,
            mapOf("podcastEpisodeId" to episodeId, "podcastEpisodeSegmentId" to episodeSegmentId),
        )
    }
}

/** handles updating and inspecting all the configuration values */
class SettingsService(private val client: GraphQlClient) {

    suspend fun updateSetting(category: String, name: String, value: String): Boolean = this.client.field(
        """
        mutation UpdateSetting(${'$'}category: String, ${'$'}name: String, ${'$'}value: String) {
          updateSetting(category: ${'$'}category, name: ${'$'}name, value: ${'$'}value)
        }
        """,
        mapOf("category" to category, "name" to name, "value" to value), "updateSetting",
    )

    suspend fun settings(): List<SettingsPage> = this.client.field(
        "query { settings { valid, category, settings { name, valid, value, required, type, defaultValue } } }",
        emptyMap(), "settings",
    )
}

class ManagedFilesService(private val client: GraphQlClient) {

    suspend fun setManagedFileVisibility(managedFileId: Long, visible: Boolean): Boolean = this.client.field(
        """
        mutation SetManagedFileVisibility(${'$'}managedFileId: Int, ${'$'}visible: Boolean) {
          setManagedFileVisibility(managedFileId: ${'$'}managedFileId, visible: ${'$'}visible)
        }
        """,
        mapOf("managedFileId" to managedFileId, "visible" to visible), "setManagedFileVisibility",
    )

    suspend fun managedFileById(managedFileId: Long): ManagedFile = this.client.field(
        """
        query (${'$'}managedFileId: Int) {
          managedFileById(managedFileId: ${'$'}managedFileId) {
            id, bucket, folder, filename, size, written, contentType, visible, url, visibleUrl, downloadableUrl
          }
        }
        """,
        mapOf("managedFileId" to managedFileId), "managedFileById",
    )

    /** where a managed file's bytes are posted (multipart, field `file`) */
    fun uploadPath(managedFileId: Long): String = "/api/managedfiles/$managedFileId"
}

class AiService(private val client: GraphQlClient) {

    suspend fun chat(prompt: String): String = this.client.field(
        "query AiChatQuery(\$prompt: String) { aiChat(prompt: \$prompt) }",
        mapOf("prompt" to prompt), "aiChat",
    )
}

class CompositionsService(private val client: GraphQlClient) {

    suspend fun deleteCompositionAttachment(compositionAttachmentId: Long): Boolean {
        this.client.execute(
            "mutation (\$compositionAttachmentId: Int) { deleteCompositionAttachment(compositionAttachmentId: \$compositionAttachmentId) }",
            mapOf("compositionAttachmentId" to compositionAttachmentId),
        )
        return true
    }

    suspend fun createCompositionAttachment(compositionId: Long): Boolean {
        this.client.execute(
            "mutation (\$compositionId: Int) { createCompositionAttachment(compositionId: \$compositionId) { id } }",
            mapOf("compositionId" to compositionId),
        )
        return true
    }

    suspend fun getCompositionById(id: Long): Composition = this.client.field(
        """
        query (${'$'}id: Int) {
          compositionById(compositionId: ${'$'}id) {
            id, field, attachments { id, caption, markdown, managedFile { id } }
          }
        }
        """,
        mapOf("id" to id), "compositionById",
    )
}

class TranscriptsService(private val client: GraphQlClient) {

    suspend fun refreshTranscript(id: Long): Boolean = this.client.field(
        "mutation (\$transcriptId: Int) { refreshTranscript(transcriptId: \$transcriptId) }",
        mapOf("transcriptId" to id), "refreshTranscript",
    )

    suspend fun writeTranscript(id: Long, transcript: String): Boolean = this.client.field(
        "mutation (\$transcriptId: Int, \$transcript: String) { writeTranscript(transcriptId: \$transcriptId, transcript: \$transcript) }",
        mapOf("transcriptId" to id, "transcript" to transcript), "writeTranscript",
    )

    /** opens the transcript in the transcripts panel */
    fun editTranscript(id: Long, text: String, title: String) =
        Events.emit(AppEvent.TranscriptEdit(title, id, text))

    suspend fun transcriptById(transcriptId: Long): Transcript = this.client.field(
        "query (\$transcriptId: Int) { transcriptById(transcriptId: \$transcriptId) { id, transcript } }",
        mapOf("transcriptId" to transcriptId), "transcriptById",
    )
}

class AyrshareService(private val client: GraphQlClient) {

    suspend fun publicationCompositions(): List<AyrsharePublicationComposition> = this.client.field(
        "query { ayrsharePublicationCompositions { platform, composition { id, attachments { id } } } }",
        emptyMap(), "ayrsharePublicationCompositions",
    )

    suspend fun platforms(): List<String> = this.client.field("query { ayrsharePlatforms }", emptyMap(), "ayrsharePlatforms")
}

/**
 * the publication fields we ask for, in the one place that defines them. asked for against a
 * single publishable, and again against each of the last N days.
 */
private const val PUBLICATION_FIELDS = """
  id, plugin, created, published, scheduled, url, state,
  outcomes { id, created, success, url, key, serverErrorMessage, preview }
"""

class DiagnosticsService(private val client: GraphQlClient) {

    suspend fun serverTime(): ServerTime = this.client.field(
        "query { serverTime { instant, javaTimeZone, javaOffset, javaLocalTime, databaseTimeZone, databaseLocalTime } }",
        emptyMap(), "serverTime",
    )
}

class PublicationsService(private val client: GraphQlClient) {

    suspend fun publications(id: Long, type: String): List<Publication> = this.client.field(
        "query (\$type: String, \$id: Int) { publicationsForPublishable(id: \$id, type: \$type) { $PUBLICATION_FIELDS } }",
        mapOf("id" to id, "type" to type), "publicationsForPublishable",
    )

    /**
     * every publication, about anything, that went out or is due to go out in [from, to),
     * each with what it was about
     */
    suspend fun between(from: Instant, to: Instant): List<Publication> = this.client.field<List<Publication>?>(
        """
        query (${'$'}from: DateTime, ${'$'}to: DateTime) {
          publicationsBetween(from: ${'$'}from, to: ${'$'}to) { $PUBLICATION_FIELDS publishable { type, id, title } }
        }
        """,
        mapOf("from" to from, "to" to to), "publicationsBetween",
    ) ?: emptyList()

    suspend fun unpublish(publicationId: Long): Boolean = this.client.field(
        "mutation (\$publicationId: Int) { unpublish(publicationId: \$publicationId) }",
        mapOf("publicationId" to publicationId), "unpublish",
    )

    /**
     * answers the id of the publication that was started, not whether it succeeded: the
     * server runs the plugin after answering. wait for the publication-completed-event
     * carrying this id before reading anything the publication produced. a null scheduled
     * publishes as soon as the server's job queue gets to it.
     */
    suspend fun publish(
        publishableType: String,
        id: Long,
        contextJson: String,
        plugin: String,
        scheduled: Instant? = null,
    ): Long = this.client.field(
        """
        mutation (${'$'}publishableId: Int, ${'$'}publishableType: String, ${'$'}contextJson: String, ${'$'}plugin: String, ${'$'}scheduled: DateTime) {
          publish(publishableId: ${'$'}publishableId, publishableType: ${'$'}publishableType, contextJson: ${'$'}contextJson, plugin: ${'$'}plugin, scheduled: ${'$'}scheduled)
        }
        """,
        mapOf(
            "publishableType" to publishableType, "publishableId" to id, "contextJson" to contextJson,
            "plugin" to plugin, "scheduled" to scheduled,
        ),
        "publish",
    )

    /** the plugins the server has for a kind of publishable, and whether each can be scheduled */
    suspend fun publisherPlugins(publishableType: String): List<PublisherPluginDescriptor> =
        this.client.field<List<PublisherPluginDescriptor>?>(
            """
            query (${'$'}publishableType: String) {
              publisherPlugins(publishableType: ${'$'}publishableType) { name, publishableType, schedulable }
            }
            """,
            mapOf("publishableType" to publishableType), "publisherPlugins",
        ) ?: emptyList()

    suspend fun canPublish(publishableId: Long, publishableType: String, contextJson: String, plugin: String): Boolean =
        this.client.field(
            """
            query (${'$'}publishableId: Int, ${'$'}publishableType: String, ${'$'}contextJson: String, ${'$'}plugin: String) {
              canPublish(publishableId: ${'$'}publishableId, publishableType: ${'$'}publishableType, contextJson: ${'$'}contextJson, plugin: ${'$'}plugin)
            }
            """,
            mapOf("publishableType" to publishableType, "publishableId" to publishableId, "contextJson" to contextJson, "plugin" to plugin),
            "canPublish",
        )
}

class SearchService(private val client: GraphQlClient) {

    suspend fun search(query: String): List<SearchableResult> = this.client.field<List<SearchableResult>?>(
        """
        query (${'$'}query: String, ${'$'}metadata: JSON) {
          search(query: ${'$'}query, metadata: ${'$'}metadata) { created, searchableId, title, aggregateId, context, type }
        }
        """,
        mapOf("query" to query, "metadata" to JsonObject(emptyMap())), "search",
    ) ?: emptyList()
}

private const val COMPOSITION_FIELDS = "descriptionComposition { id, field, attachments { id, caption, managedFile { id } } }"

class BlogsService(private val client: GraphQlClient) {

    suspend fun deleteBlog(blogId: Long): Boolean = this.client.field(
        "mutation deleteBlog(\$blogId: Int) { deleteBlog(blogId: \$blogId) }", mapOf("blogId" to blogId), "deleteBlog",
    )

    suspend fun deletePost(postId: Long): Boolean = this.client.field(
        "mutation deletePost(\$postId: Int) { deletePost(postId: \$postId) }", mapOf("postId" to postId), "deletePost",
    )

    suspend fun blogById(blogId: Long): Blog = this.client.field(
        "query blogById(\$blogId: Int) { blogById(blogId: \$blogId) { id, title, description, rssUrl, created } }",
        mapOf("blogId" to blogId), "blogById",
    )

    suspend fun createBlog(title: String, description: String): Blog {
        val blog: Blog = this.client.field(
            """
            mutation (${'$'}title: String, ${'$'}description: String) {
              createBlog(title: ${'$'}title, description: ${'$'}description) { id, title, description, rssUrl, created }
            }
            """,
            mapOf("title" to title, "description" to description), "createBlog",
        )
        return blogById(blog.id)
    }

    suspend fun update(blogId: Long, title: String, description: String, rssUrl: String?): Boolean = this.client.field(
        """
        mutation (${'$'}blogId: Int, ${'$'}title: String, ${'$'}description: String, ${'$'}rssUrl: String) {
          updateBlog(blogId: ${'$'}blogId, title: ${'$'}title, description: ${'$'}description, rssUrl: ${'$'}rssUrl)
        }
        """,
        mapOf("blogId" to blogId, "title" to title, "description" to description, "rssUrl" to rssUrl), "updateBlog",
    )

    suspend fun blogs(): List<Blog> = this.client.field("query { blogs { created, id, title, description, rssUrl } }", emptyMap(), "blogs")

    /** a null created leaves the existing creation date alone, server-side */
    suspend fun updatePost(
        postId: Long,
        title: String,
        description: String,
        summary: String,
        rssSlug: String?,
        created: Instant? = null,
    ): Boolean = this.client.field(
        """
        mutation (${'$'}postId: Int, ${'$'}title: String, ${'$'}description: String, ${'$'}summary: String, ${'$'}rssSlug: String, ${'$'}created: DateTime) {
          updatePost(postId: ${'$'}postId, title: ${'$'}title, description: ${'$'}description, summary: ${'$'}summary, rssSlug: ${'$'}rssSlug, created: ${'$'}created)
        }
        """,
        mapOf(
            "postId" to postId, "title" to title, "description" to description, "summary" to summary,
            "rssSlug" to rssSlug, "created" to created,
        ),
        "updatePost",
    )

    suspend fun setBlogPostVisibility(postId: Long, visible: Boolean): Boolean = this.client.field(
        "mutation SetBlogPostVisibility(\$postId: Int, \$visible: Boolean) { setBlogPostVisibility(postId: \$postId, visible: \$visible) }",
        mapOf("postId" to postId, "visible" to visible), "setBlogPostVisibility",
    )

    suspend fun summarize(content: String): String = this.client.field(
        "mutation (\$content: String) { summarize(content: \$content) }", mapOf("content" to content), "summarize",
    )

    /** the posts, newest first: all of them, or one page when an offset and/or limit is given */
    suspend fun blogPostPreviewsByBlog(blogId: Long, offset: Int? = null, limit: Int? = null): List<Post> = this.client.field(
        "query blogPostsByBlog(\$blogId: Int, \$offset: Int, \$limit: Int) { blogPostsByBlog(blogId: \$blogId, offset: \$offset, limit: \$limit) { id, title, content, summary, rssSlug, complete, visible, created } }",
        mapOf("blogId" to blogId, "offset" to offset, "limit" to limit), "blogPostsByBlog",
    )

    suspend fun blogPostsByBlog(blogId: Long): List<Post> = this.client.field(
        "query blogPostsByBlog(\$blogId: Int) { blogPostsByBlog(blogId: \$blogId) { id, title, content, summary, rssSlug, complete, visible, created, $COMPOSITION_FIELDS } }",
        mapOf("blogId" to blogId), "blogPostsByBlog",
    )

    suspend fun postById(postId: Long): Post = this.client.field(
        "query postById(\$postId: Int) { postById(postId: \$postId) { id, title, content, summary, rssSlug, complete, visible, created, $COMPOSITION_FIELDS } }",
        mapOf("postId" to postId), "postById",
    )

    suspend fun createPost(blogId: Long, title: String, content: String, summary: String, rssSlug: String? = null): Post {
        val post: Post = this.client.field(
            """
            mutation (${'$'}blogId: Int, ${'$'}title: String, ${'$'}content: String, ${'$'}summary: String, ${'$'}rssSlug: String) {
              createPost(blogId: ${'$'}blogId, title: ${'$'}title, content: ${'$'}content, summary: ${'$'}summary, rssSlug: ${'$'}rssSlug) { id }
            }
            """,
            mapOf("blogId" to blogId, "title" to title, "content" to content, "summary" to summary, "rssSlug" to rssSlug),
            "createPost",
        )
        return postById(post.id)
    }
}

class NotesService(private val client: GraphQlClient) {

    suspend fun deleteNote(id: Long): Boolean = this.client.field(
        "mutation DeleteNote(\$id: Int) { deleteNote(id: \$id) }", mapOf("id" to id), "deleteNote",
    )

    suspend fun updateNote(id: Long, note: String, title: String?, url: String?): Boolean = this.client.field(
        "mutation UpdateNote(\$id: Int, \$note: String, \$title: String, \$url: String) { updateNote(id: \$id, note: \$note, title: \$title, url: \$url) }",
        mapOf("id" to id, "note" to note, "title" to title, "url" to url), "updateNote",
    )

    suspend fun setNoteDone(id: Long, done: Boolean): Boolean = this.client.field(
        "mutation SetNoteDone(\$id: Int, \$done: Boolean) { setNoteDone(id: \$id, done: \$done) }",
        mapOf("id" to id, "done" to done), "setNoteDone",
    )

    /**
     * asks for the page at the url to be read and summarized. the summary comes later, in a
     * page-summarized-event whose key is what this answers
     */
    suspend fun summarizePage(url: String): String? = this.client.field(
        "mutation SummarizePage(\$url: String) { summarizePage(url: \$url) }", mapOf("url" to url), "summarizePage",
    )

    /** a title for a note with this text, written by the model */
    suspend fun noteTitle(description: String): String? = this.client.field(
        "query NoteTitle(\$description: String) { noteTitle(description: \$description) }",
        mapOf("description" to description), "noteTitle",
    )

    /** [summarize]: whether the api should summarize the page into a note with a url and no text */
    suspend fun createNote(
        type: String, id: Long, note: String, title: String?, url: String?, summarize: Boolean = true,
    ): Boolean = this.client.field(
        """
        mutation CreateNote(${'$'}type: String, ${'$'}id: Int, ${'$'}note: String, ${'$'}title: String, ${'$'}url: String, ${'$'}summarize: Boolean) {
          createNote(type: ${'$'}type, id: ${'$'}id, note: ${'$'}note, title: ${'$'}title, url: ${'$'}url, summarize: ${'$'}summarize)
        }
        """,
        mapOf("type" to type, "id" to id, "note" to note, "title" to title, "url" to url, "summarize" to summarize),
        "createNote",
    )

    suspend fun notesForNotable(id: Long, type: String, includeDone: Boolean = true): List<Note> = this.client.field(
        """
        query (${'$'}type: String, ${'$'}id: Int, ${'$'}includeDone: Boolean) {
          notesForNotable(type: ${'$'}type, id: ${'$'}id, includeDone: ${'$'}includeDone) { created, done, id, note, type, url, title }
        }
        """,
        mapOf("type" to type, "id" to id, "includeDone" to includeDone), "notesForNotable",
    )
}

class AnalysisService(private val client: GraphQlClient) {

    /** the analysis of an episode or a post, or null if it's never been analyzed */
    suspend fun analysis(type: String, id: Long): Analysis? = this.client.field(
        """
        query (${'$'}type: String, ${'$'}id: Int) {
          analysis(type: ${'$'}type, id: ${'$'}id) { type, analyzableId, created, highlights { kind, quote, reason, score, section, sectionName } }
        }
        """,
        mapOf("type" to type, "id" to id), "analysis",
    )

    /**
     * asks for an episode or a post to be analyzed. it's done when an analysis-completed-event
     * keyed by `type:id` comes. false if there's nothing to analyze yet, an episode with no
     * transcripts, say
     */
    suspend fun analyze(type: String, id: Long): Boolean = this.client.field(
        "mutation Analyze(\$type: String, \$id: Int) { analyze(type: \$type, id: \$id) }",
        mapOf("type" to type, "id" to id), "analyze",
    )
}

class EntityContextsService(private val client: GraphQlClient) {

    suspend fun buildEntityContextFor(type: String, id: Long): EntityContext {
        val data = this.client.execute(
            "query (\$type: String, \$id: Int) { entityContext(type: \$type, id: \$id) { resolvedType, context } }",
            mapOf("type" to type, "id" to id),
        )
        val entityContext = data["entityContext"]!!.jsonObject
        return EntityContext(
            entityContext["resolvedType"]!!.jsonPrimitive.content,
            parseNumberContext(entityContext["context"]?.jsonPrimitive?.content),
        )
    }
}

class WordPressService(private val client: GraphQlClient) {

    suspend fun wordPressStatus(): WordPressStatus = this.client.field(
        "query { wordPressStatus { connected, id, displayName } }", emptyMap(), "wordPressStatus",
    )
}

/** fetches a text resource from the api, e.g. a waveform's JSON, by its api-relative URL */
class ApiResources(private val http: HttpClient) {

    suspend fun text(apiRelativeUrl: String): String = this.http.get("/api$apiRelativeUrl").bodyAsText()
}
