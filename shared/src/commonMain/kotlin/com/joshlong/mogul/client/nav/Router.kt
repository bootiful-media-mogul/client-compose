package com.joshlong.mogul.client.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joshlong.mogul.client.api.EntityContext
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.events.resetNotesForNotable
import com.joshlong.mogul.client.platform.browser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** the pages, with the same paths as the Vue router's, so links and bookmarks carry over */
sealed interface Route {

    /** an i18n key naming the page, for the window title */
    val titleKey: String?

    val path: String

    data object Home : Route {
        override val titleKey = "app.menu.home"
        override val path = "/"
    }

    data object Jobs : Route {
        override val titleKey = "app.menu.jobs"
        override val path = "/jobs"
    }

    /** `when` is a cache buster: following the podcasts link again reloads the list */
    data class Podcasts(val `when`: Long? = null) : Route {
        override val titleKey = "app.menu.podcasts"
        override val path = if (`when` != null) "/podcasts?when=$`when`" else "/podcasts"
    }

    data object Blogs : Route {
        override val titleKey = "app.menu.blogs"
        override val path = "/blogs"
    }

    data class Search(val query: String? = null) : Route {
        override val titleKey = "app.menu.search"
        override val path = if (query.isNullOrBlank()) "/search" else "/search?query=${encode(query)}"
    }

    data object Settings : Route {
        override val titleKey = "app.menu.settings"
        override val path = "/settings"
    }

    data object About : Route {
        override val titleKey = "app.menu.about"
        override val path = "/about"
    }

    /** resolved, via the api, to the page for the entity and then replaced by it */
    data class Entity(val type: String, val id: Long) : Route {
        override val titleKey: String? = null
        override val path = "/entities/$type/$id"
    }

    data class BlogPosts(val blogId: Long) : Route {
        override val titleKey = "app.pages.posts"
        override val path = "/blogs/$blogId/posts"
    }

    data class BlogPost(val blogId: Long, val postId: Long) : Route {
        override val titleKey = "app.pages.post"
        override val path = "/blogs/$blogId/posts/$postId"
    }

    data class PodcastEpisodes(val podcastId: Long) : Route {
        override val titleKey = "app.pages.episodes"
        override val path = "/podcasts/$podcastId/episodes"
    }

    data class PodcastEpisode(val podcastId: Long, val episodeId: Long) : Route {
        override val titleKey = "app.pages.episode"
        override val path = "/podcasts/$podcastId/episodes/$episodeId"
    }

    data class NotFound(override val path: String) : Route {
        override val titleKey: String? = null
    }

    companion object {

        fun parse(pathAndQuery: String): Route {
            val path = pathAndQuery.substringBefore('?').trimEnd('/').ifEmpty { "/" }
            val query = pathAndQuery.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
                .associate { it.substringBefore('=') to decode(it.substringAfter('=', "")) }
            val parts = path.split('/').filter { it.isNotEmpty() }
            fun id(i: Int) = parts.getOrNull(i)?.toLongOrNull()
            return when {
                parts.isEmpty() -> Home
                parts == listOf("jobs") -> Jobs
                parts == listOf("podcasts") -> Podcasts(query["when"]?.toLongOrNull())
                parts == listOf("blogs") -> Blogs
                parts == listOf("search") -> Search(query["query"])
                parts == listOf("settings") -> Settings
                parts == listOf("about") -> About
                parts.size == 3 && parts[0] == "entities" && id(2) != null -> Entity(parts[1], id(2)!!)
                parts.size == 3 && parts[0] == "blogs" && parts[2] == "posts" && id(1) != null -> BlogPosts(id(1)!!)
                parts.size == 4 && parts[0] == "blogs" && parts[2] == "posts" && id(1) != null && id(3) != null ->
                    BlogPost(id(1)!!, id(3)!!)
                parts.size == 3 && parts[0] == "podcasts" && parts[2] == "episodes" && id(1) != null -> PodcastEpisodes(id(1)!!)
                parts.size == 4 && parts[0] == "podcasts" && parts[2] == "episodes" && id(1) != null && id(3) != null ->
                    PodcastEpisode(id(1)!!, id(3)!!)
                else -> NotFound(path)
            }
        }

        private fun encode(s: String) = s.encodeToByteArray().joinToString("") { b ->
            val c = (b.toInt() and 0xff).toChar()
            // only unreserved ascii goes as-is: a byte of a multibyte character reads as a latin-1 letter
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.~") c.toString() else "%" + (b.toInt() and 0xff).toString(16).uppercase().padStart(2, '0')
        }

        private fun decode(s: String): String {
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    s.substring(i + 1, i + 3).toIntOrNull(16)?.let { bytes.add(it.toByte()); i += 3; continue }
                }
                if (c == '+') bytes.add(' '.code.toByte()) else bytes.addAll(c.toString().encodeToByteArray().toList())
                i++
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}

/** where a search result (or an entity link) takes you, given its context */
fun navigationFor(resolvedType: String, context: Map<String, Long>): Route? = when (resolvedType) {
    "post" -> context["blogId"]?.let { blogId -> context["postId"]?.let { Route.BlogPost(blogId, it) } }
    "episode", "segment" -> context["podcastId"]?.let { podcastId -> context["episodeId"]?.let { Route.PodcastEpisode(podcastId, it) } }
    "mogul" -> Route.Home
    else -> null
}

/**
 * the current page, kept in step with the address bar. a view keys its state on the route,
 * so following a link to the page you're on (the podcasts link's cache buster) reloads it.
 */
object Router {

    var route: Route by mutableStateOf(Route.parse(browser.path))
        private set

    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        browser.onPopState { path -> show(Route.parse(path)) }
        show(this.route)
    }

    fun push(route: Route) {
        if (route.path != browser.path) browser.pushPath(route.path)
        show(route)
    }

    fun replace(route: Route) {
        browser.replacePath(route.path)
        show(route)
    }

    /** a fresh podcasts list, even from the podcasts page */
    fun podcasts() = push(Route.Podcasts(Clock.System.now().toEpochMilliseconds()))

    private fun show(route: Route) {
        // the notes panel follows the page: each page that has notes asks for them again
        resetNotesForNotable()
        if (route is Route.Entity) {
            this.scope?.launch {
                val context = runCatching { Mogul.entityContexts.buildEntityContextFor(route.type, route.id) }.getOrNull()
                val target = context?.let { navigationFor(it.resolvedType, it.context) } ?: Route.Home
                replace(target)
            }
            this.route = route
            return
        }
        this.route = route
    }

    // a navigation that was waiting on a sign-in, resumed once the app is back
    private const val PENDING_ACTION = "pending_action"

    fun resumePendingAction() {
        val raw = browser.sessionGet(PENDING_ACTION) ?: return
        browser.sessionRemove(PENDING_ACTION)
        val context = runCatching { json.decodeFromString<EntityContext>(raw) }.getOrNull() ?: return
        navigationFor(context.resolvedType, context.context)?.let { push(it) }
    }
}
