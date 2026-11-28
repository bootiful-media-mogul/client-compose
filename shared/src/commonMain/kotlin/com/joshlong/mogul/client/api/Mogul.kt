package com.joshlong.mogul.client.api

import com.joshlong.mogul.client.platform.createHttpClient
import io.ktor.client.HttpClient

/** every service, wired once, as services.ts exported them */
object Mogul {

    /**
     * the one client every request goes through. on the web it rides the gateway's session
     * cookie with relative URLs; in the apps it carries the bearer token to the gateway's address
     */
    val http: HttpClient = createHttpClient()

    val graphql = GraphQlClient(this.http)

    val resources = ApiResources(this.http)

    val mogul = MogulService(this.graphql)
    val wordpress = WordPressService(this.graphql)
    val blogs = BlogsService(this.graphql)
    val search = SearchService(this.graphql)
    val jobs = JobsService(this.graphql)
    val publications = PublicationsService(this.graphql)
    val ai = AiService(this.graphql)
    val notifications = NotificationsService(this.graphql)
    val diagnostics = DiagnosticsService(this.graphql)
    val podcasts = PodcastsService(this.graphql)
    val managedFiles = ManagedFilesService(this.graphql)
    val settings = SettingsService(this.graphql)
    val compositions = CompositionsService(this.graphql)
    val ayrshare = AyrshareService(this.graphql)
    val transcripts = TranscriptsService(this.graphql)
    val notes = NotesService(this.graphql)
    val analysis = AnalysisService(this.graphql)
    val entityContexts = EntityContextsService(this.graphql)
}
