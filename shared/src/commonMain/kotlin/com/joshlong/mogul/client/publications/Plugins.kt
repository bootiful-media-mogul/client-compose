package com.joshlong.mogul.client.publications

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Blog
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.api.toJson
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.i18n.te
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.ListenForNotification
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulSelect
import com.joshlong.mogul.client.resources.Res
import com.joshlong.mogul.client.resources.pub_download_blog_post_as_markdown
import com.joshlong.mogul.client.resources.pub_mock
import com.joshlong.mogul.client.resources.pub_publish_to_podbean
import com.joshlong.mogul.client.resources.pub_wordpress_og
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** a plugin's artwork: a png logo, or one of the studio's svg icons drawn light */
sealed interface PluginIcon {
    data class Png(val resource: DrawableResource) : PluginIcon
    data class Svg(val icon: MogulIcon) : PluginIcon
}

/**
 * every plugin's artwork, by plugin name, for the places that list publications without
 * having the plugins' panels on the page: the home page's calendar shows publications of
 * every kind. a plugin missing from here still renders, just without its artwork.
 */
val PLUGIN_ICONS: Map<String, PluginIcon> = mapOf(
    "mogulAyrshare" to PluginIcon.Svg(MogulIcon.Ayrshare),
    "podcastEpisodeAyrshare" to PluginIcon.Svg(MogulIcon.Ayrshare),
    "blogPostAyrshare" to PluginIcon.Svg(MogulIcon.Ayrshare),
    "podbean" to PluginIcon.Png(Res.drawable.pub_publish_to_podbean),
    "mockPodcastEpisode" to PluginIcon.Png(Res.drawable.pub_mock),
    "wordpress" to PluginIcon.Png(Res.drawable.pub_wordpress_og),
    "blogPostMarkdownFile" to PluginIcon.Png(Res.drawable.pub_download_blog_post_as_markdown),
    "audioFile" to PluginIcon.Svg(MogulIcon.AudioFile),
    "podcastEpisodeToBlogPost" to PluginIcon.Svg(MogulIcon.Rss),
)

/** a plugin's own name where it has one, else its id */
fun pluginTitle(plugin: String): String {
    val key = "publications.plugins.$plugin.title"
    return if (te(key)) t(key) else plugin
}

/** a plugin's artwork, filling the tile it's put in. the tiles are dark, so svgs are drawn light */
@Composable
internal fun PluginIconImage(icon: PluginIcon, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = palette.chromeInk) {
    when (icon) {
        is PluginIcon.Png -> Image(painterResource(icon.resource), contentDescription, modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // the ayrshare mark is drawn in its own red, as in the Vue client; the other svgs take the tint
        is PluginIcon.Svg -> Icon(
            icon.icon,
            contentDescription,
            modifier.padding(6.dp),
            size = 38.dp,
            tint = if (icon.icon == MogulIcon.Ayrshare) Color.Unspecified else tint,
        )
    }
}

/**
 * what a plugin's panel needs from the section it sits in, as the Vue section provided it:
 * the publishable, and the ways to publish it. `context` goes to the server as JSON.
 */
internal class PublicationScope(
    val type: String,
    val publishableId: Long,
    private val publishFunction: suspend (Map<String, Any?>, String, Instant?) -> Long,
    private val publishAndAwaitFunction: suspend (Map<String, Any?>, String, Instant?) -> Publication?,
) {

    /** starts a publication and answers its id. it is not finished when this answers */
    suspend fun publish(context: Map<String, Any?>, plugin: String, scheduled: Instant? = null): Long =
        this.publishFunction(context, plugin, scheduled)

    /**
     * starts a publication and answers once it has actually finished, with the publication
     * itself, or null if the wait was abandoned. a scheduled one answers once it's scheduled.
     */
    suspend fun publishAndAwait(context: Map<String, Any?>, plugin: String, scheduled: Instant? = null): Publication? =
        this.publishAndAwaitFunction(context, plugin, scheduled)

    suspend fun isPluginReady(context: Map<String, Any?>, plugin: String): Boolean =
        runCatching { Mogul.publications.canPublish(this.publishableId, this.type, toJson(context).toString(), plugin) }
            .getOrDefault(false)
}

/**
 * a plugin's panel. its state lives here, not in the panel, so closing and reopening the
 * panel keeps what was in it, as the Vue plugin components (always mounted) did. the publish
 * and schedule buttons beneath it come from the section; a plugin says what the main one is
 * called, when it can be pressed and what pressing it does. one without an action gets none.
 * whether it can be scheduled isn't up to the panel: the server says, for every plugin alike.
 */
internal abstract class PublicationPlugin(val scope: PublicationScope, val name: String, val icon: PluginIcon) {

    open val label: String? get() = null

    open val enabled: Boolean get() = true

    open val hasAction: Boolean get() = true

    open suspend fun publish(scheduled: Instant?) {}

    /** what runs for as long as the section is on screen: loading, listening */
    @Composable
    open fun Effects() {}

    @Composable
    open fun Panel() {}
}

/**
 * the plugins that need the plugin to say it's ready (configured, the episode complete)
 * before their button goes live, and ask again whenever an episode completes
 */
internal abstract class ReadinessPlugin(scope: PublicationScope, name: String, icon: PluginIcon) :
    PublicationPlugin(scope, name, icon) {

    protected var disabled by mutableStateOf(false)

    protected suspend fun refreshReadiness() {
        this.disabled = !this.scope.isPluginReady(emptyMap(), this.name)
    }

    @Composable
    override fun Effects() {
        val coroutines = rememberCoroutineScope()
        LaunchedEffect(this) { refreshReadiness() }
        ListenForNotification("podcast-episode-completed-event") { coroutines.launch { refreshReadiness() } }
    }
}

internal class PodbeanPlugin(scope: PublicationScope) :
    ReadinessPlugin(scope, "podbean", PluginIcon.Png(Res.drawable.pub_publish_to_podbean)) {

    override val enabled: Boolean get() = !this.disabled

    override suspend fun publish(scheduled: Instant?) {
        this.scope.publish(emptyMap(), this.name, scheduled)
    }
}

internal class MockPlugin(scope: PublicationScope) :
    ReadinessPlugin(scope, "mockPodcastEpisode", PluginIcon.Png(Res.drawable.pub_mock)) {

    override val enabled: Boolean get() = !this.disabled

    override suspend fun publish(scheduled: Instant?) {
        this.scope.publish(emptyMap(), this.name, scheduled)
    }
}

/**
 * a download: the file the user is asking for doesn't exist until the plugin has run, so it
 * waits for the publication to finish and takes the URL off its outcome. for the episode's
 * audio, that is also what guarantees the render the user just asked for, not the last one.
 */
internal open class DownloadPlugin(scope: PublicationScope, name: String, icon: PluginIcon, private val messages: String) :
    ReadinessPlugin(scope, name, icon) {

    private var producing by mutableStateOf(false)

    private var readyUrl by mutableStateOf<String?>(null)

    override val label: String get() = t(if (this.producing) "$messages.producing" else "$messages.download")

    override val enabled: Boolean get() = !this.disabled && !this.producing

    override suspend fun publish(scheduled: Instant?) {
        this.producing = true
        this.readyUrl = null
        try {
            val publication = this.scope.publishAndAwait(emptyMap(), this.name) ?: return
            val outcome = publication.outcomes.firstOrNull { it.success && !it.url.isNullOrBlank() }
            if (outcome == null) {
                println("the publication finished with nothing to download: $publication")
                return
            }
            this.readyUrl = outcome.url
            browser.open(outcome.url!!, "_blank")
        } finally {
            this.producing = false
        }
    }

    @Composable
    override fun Panel() {
        // opening it is a popup as far as the browser is concerned, and the click that
        // started it no longer counts once we've waited on the server, so it can be
        // blocked. this link is the fallback, and it costs nothing to leave up.
        this.readyUrl?.let { url ->
            Text(
                t("$messages.ready"),
                modifier = Modifier.padding(bottom = Gutter.half).clickable { browser.open(url, "_blank") }.pointerHoverIcon(PointerIcon.Hand),
                color = palette.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal class AudioFilePlugin(scope: PublicationScope) :
    DownloadPlugin(scope, "audioFile", PluginIcon.Svg(MogulIcon.AudioFile), "publications.plugins.audioFile")

internal class BlogPostMarkdownFilePlugin(scope: PublicationScope) :
    DownloadPlugin(
        scope, "blogPostMarkdownFile", PluginIcon.Png(Res.drawable.pub_download_blog_post_as_markdown),
        "publications.plugins.blogPostMarkdownFile",
    ) {

    // unlike the audio, it doesn't wait on an episode completing
    @Composable
    override fun Effects() {
        LaunchedEffect(this) { refreshReadiness() }
    }
}

/** turns an episode into a post on the blog the user picks */
internal class PodcastEpisodeBlogPostPlugin(scope: PublicationScope) :
    PublicationPlugin(scope, "podcastEpisodeToBlogPost", PluginIcon.Svg(MogulIcon.Rss)) {

    private var blogId by mutableStateOf<Long?>(null)

    private var blogs by mutableStateOf<List<Blog>>(emptyList())

    private var publishing by mutableStateOf(false)

    override val label: String
        get() = t(if (this.publishing) "publications.plugins.podcastEpisodeToBlogPost.creating" else "publications.plugins.podcastEpisodeToBlogPost.create")

    override val enabled: Boolean get() = this.blogId != null && !this.publishing

    /**
     * the publish mutation returns as soon as the work is queued, so it waits for the
     * publication itself: otherwise the button goes live again while the post is still being
     * written, and two clicks make two posts
     */
    override suspend fun publish(scheduled: Instant?) {
        val blogId = this.blogId ?: return
        this.publishing = true
        try {
            // the backend plugin reads context.blogId to know which blog to post to
            this.scope.publishAndAwait(mapOf("blogId" to blogId), this.name, scheduled)
        } finally {
            this.publishing = false
        }
    }

    @Composable
    override fun Effects() {
        LaunchedEffect(this) { this@PodcastEpisodeBlogPostPlugin.blogs = runCatching { Mogul.blogs.blogs() }.getOrDefault(emptyList()) }
    }

    @Composable
    override fun Panel() {
        MogulSelect(
            options = this.blogs,
            selected = this.blogs.firstOrNull { it.id == this.blogId },
            onSelect = { this.blogId = it.id },
            optionLabel = { it.title },
            placeholder = t("selections.blogs"),
        )
    }
}

/**
 * publishes to the mogul's WordPress. the publish button is never live unless the plugin is
 * both configured (in settings) and connected (an OAuth grant)
 */
internal class WordpressPlugin(scope: PublicationScope) :
    PublicationPlugin(scope, "wordpress", PluginIcon.Png(Res.drawable.pub_wordpress_og)) {

    private var connected by mutableStateOf(false)

    private var configured by mutableStateOf(false)

    override val label: String get() = t("publications.plugins.wordpress.publish")

    override val enabled: Boolean get() = this.configured && this.connected

    override suspend fun publish(scheduled: Instant?) {
        this.scope.publish(emptyMap(), this.name, scheduled)
    }

    @Composable
    override fun Effects() {
        LaunchedEffect(this) {
            this@WordpressPlugin.configured = this@WordpressPlugin.scope.isPluginReady(emptyMap(), this@WordpressPlugin.name)
            this@WordpressPlugin.connected = runCatching { Mogul.wordpress.wordPressStatus().connected }.getOrDefault(false)
        }
    }

    @Composable
    override fun Panel() {
        val coroutines = rememberCoroutineScope()
        MogulButton(
            t(if (this.connected) "publications.plugins.wordpress.connected" else "publications.plugins.wordpress.connect"),
            onClick = { coroutines.launch { attemptConnection() } },
            enabled = !this.connected,
        )
    }

    // after the OAuth dance the router finds this and takes the user back to the publishable
    private suspend fun attemptConnection() {
        val context = Mogul.entityContexts.buildEntityContextFor(this.scope.type, this.scope.publishableId)
        val json = toJson(mapOf("resolvedType" to context.resolvedType, "context" to context.context)).toString()
        browser.sessionSet("pending_action", json)
        browser.open("/oauth2/authorization/wordpress", "_self")
    }
}

/**
 * the panels the client has for each kind of publishable, in the order the Vue editors listed
 * them. which of them are actually offered, and which can be scheduled, is the server's call
 * (see PublicationsSection): a panel here for a plugin the server doesn't have isn't shown.
 */
internal fun pluginsFor(scope: PublicationScope): List<PublicationPlugin> = when (scope.type) {
    "post" -> listOf(
        BlogPostMarkdownFilePlugin(scope),
        WordpressPlugin(scope),
        AyrsharePlugin(scope, "blogPostAyrshare"),
    )
    "episode" -> listOf(
        PodbeanPlugin(scope),
        AyrsharePlugin(scope, "podcastEpisodeAyrshare", readyEventCategory = "podcast-episode-completed-event"),
        PodcastEpisodeBlogPostPlugin(scope),
        AudioFilePlugin(scope),
        MockPlugin(scope),
    )
    "mogulStatus" -> listOf(AyrsharePlugin(scope, "mogulAyrshare"))
    else -> emptyList()
}
