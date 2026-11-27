package com.joshlong.mogul.client.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.navOptions
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.joshlong.mogul.client.about.AboutScreen
import com.joshlong.mogul.client.api.EntityContext
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.blogs.BlogPostEditorScreen
import com.joshlong.mogul.client.blogs.BlogPostsScreen
import com.joshlong.mogul.client.blogs.BlogsScreen
import com.joshlong.mogul.client.events.resetNotesForNotable
import com.joshlong.mogul.client.home.HomeScreen
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.jobs.JobsScreen
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.podcasts.PodcastEpisodeEditorScreen
import com.joshlong.mogul.client.podcasts.PodcastEpisodesScreen
import com.joshlong.mogul.client.podcasts.PodcastsScreen
import com.joshlong.mogul.client.search.SearchScreen
import com.joshlong.mogul.client.settings.SettingsScreen
import com.joshlong.mogul.client.theme.palette
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** the pages. navigation keeps the back stack; [Urls] maps each one to its path in the address bar */
@Serializable
sealed interface Route {

    /** an i18n key naming the page, for the window title */
    val titleKey: String?

    @Serializable
    data object Home : Route {
        override val titleKey = "app.menu.home"
    }

    @Serializable
    data object Jobs : Route {

        override val titleKey = "app.menu.jobs"

    }

    /** `when` is a cache buster: following the podcasts link again reloads the list */
    @Serializable
    data class Podcasts(val `when`: Long? = null) : Route {
        override val titleKey = "app.menu.podcasts"
    }

    @Serializable
    data object Blogs : Route {
        override val titleKey = "app.menu.blogs"
    }

    @Serializable
    data class Search(val query: String? = null) : Route {
        override val titleKey = "app.menu.search"
    }

    @Serializable
    data object Settings : Route {
        override val titleKey = "app.menu.settings"
    }

    @Serializable
    data object About : Route {
        override val titleKey = "app.menu.about"
    }

    /** resolved, via the api, to the page for the entity and then replaced by it */
    @Serializable
    data class Entity(val type: String, val id: Long) : Route {
        override val titleKey: String? = null
    }

    @Serializable
    data class BlogPosts(val blogId: Long) : Route {
        override val titleKey = "app.pages.posts"
    }

    @Serializable
    data class BlogPost(val blogId: Long, val postId: Long) : Route {
        override val titleKey = "app.pages.post"
    }

    @Serializable
    data class PodcastEpisodes(val podcastId: Long) : Route {
        override val titleKey = "app.pages.episodes"
    }

    @Serializable
    data class PodcastEpisode(val podcastId: Long, val episodeId: Long) : Route {
        override val titleKey = "app.pages.episode"
    }

    @Serializable
    data class NotFound(val path: String) : Route {
        override val titleKey: String? = null
    }
}

/** where a search result (or an entity link) takes you, given its context */
fun navigationFor(resolvedType: String, context: Map<String, Long>): Route? = when (resolvedType) {
    "post" -> context["blogId"]?.let { blogId ->
        context["postId"]?.let {
            Route.BlogPost(
                blogId,
                it
            )
        }
    }

    "episode", "segment" -> context["podcastId"]?.let { podcastId ->
        context["episodeId"]?.let {
            Route.PodcastEpisode(
                podcastId,
                it
            )
        }
    }

    "mogul" -> Route.Home
    else -> null
}

/** the one place the URLs live: the path each page has, and the page a path names */
internal object Urls {

    fun pathOf(route: Route): String = when (route) {
        Route.Home -> "/"
        Route.Jobs -> "/jobs"
        is Route.Podcasts -> route.`when`?.let { "/podcasts?when=$it" } ?: "/podcasts"
        Route.Blogs -> "/blogs"
        is Route.Search -> if (route.query.isNullOrBlank()) "/search" else "/search?query=${route.query.encodeURLParameter()}"
        Route.Settings -> "/settings"
        Route.About -> "/about"
        is Route.Entity -> "/entities/${route.type}/${route.id}"
        is Route.BlogPosts -> "/blogs/${route.blogId}/posts"
        is Route.BlogPost -> "/blogs/${route.blogId}/posts/${route.postId}"
        is Route.PodcastEpisodes -> "/podcasts/${route.podcastId}/episodes"
        is Route.PodcastEpisode -> "/podcasts/${route.podcastId}/episodes/${route.episodeId}"
        is Route.NotFound -> route.path
    }

    fun routeOf(pathAndQuery: String): Route = runCatching { parse(pathAndQuery) }
        .getOrElse { Route.NotFound(pathAndQuery.substringBefore('?')) }

    private fun parse(pathAndQuery: String): Route {
        val path = pathAndQuery.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        val query = pathAndQuery.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
            .associate {
                it.substringBefore('=') to it.substringAfter('=', "")
                    .decodeURLQueryComponent(plusIsSpace = true)
            }
        val parts = path.split('/').filter { it.isNotEmpty() }
        fun id(i: Int) = parts.getOrNull(i)?.toLongOrNull()
        return when {
            parts.isEmpty() -> Route.Home
            parts == listOf("jobs") -> Route.Jobs
            parts == listOf("podcasts") -> Route.Podcasts(query["when"]?.toLongOrNull())
            parts == listOf("blogs") -> Route.Blogs
            parts == listOf("search") -> Route.Search(query["query"])
            parts == listOf("settings") -> Route.Settings
            parts == listOf("about") -> Route.About
            parts.size == 3 && parts[0] == "entities" && id(2) != null -> Route.Entity(
                parts[1],
                id(2)!!
            )

            parts.size == 3 && parts[0] == "blogs" && parts[2] == "posts" && id(1) != null -> Route.BlogPosts(
                id(1)!!
            )

            parts.size == 4 && parts[0] == "blogs" && parts[2] == "posts" && id(1) != null && id(3) != null ->
                Route.BlogPost(id(1)!!, id(3)!!)

            parts.size == 3 && parts[0] == "podcasts" && parts[2] == "episodes" && id(1) != null -> Route.PodcastEpisodes(
                id(1)!!
            )

            parts.size == 4 && parts[0] == "podcasts" && parts[2] == "episodes" && id(1) != null && id(
                3
            ) != null ->
                Route.PodcastEpisode(id(1)!!, id(3)!!)

            else -> Route.NotFound(path)
        }
    }
}

/** reports the page that is on screen, so the router knows the current route */
@Composable
private fun Track(route: Route) {
    LaunchedEffect(route) { Router.track(route) }
}

/** the pages, each one's screen, with no transitions between them */
@Composable
fun Pages(navController: NavHostController) {
    LaunchedEffect(navController) {
        Router.attach(navController)
        Router.start()
    }
    NavHost(
        navController = navController,
        // the page the address bar names: a deep link opens on its own page
        startDestination = remember { Urls.routeOf(browser.path) },
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<Route.Home> { Track(Route.Home); HomeScreen() }
        composable<Route.Jobs> { Track(Route.Jobs); JobsScreen() }
        composable<Route.Podcasts> { Track(it.toRoute<Route.Podcasts>()); PodcastsScreen() }
        composable<Route.Blogs> { Track(Route.Blogs); BlogsScreen() }
        composable<Route.Search> {
            val search = it.toRoute<Route.Search>();
            Track(search);
            SearchScreen(search.query)
        }
        composable<Route.Settings> {
            Track(Route.Settings);
            SettingsScreen()
        }
        composable<Route.About> {
            Track(Route.About);
            AboutScreen()
        }
        composable<Route.BlogPosts> {
            val posts = it.toRoute<Route.BlogPosts>();
            Track(posts);
            BlogPostsScreen(posts.blogId)
        }
        composable<Route.BlogPost> {
            val post = it.toRoute<Route.BlogPost>()
            Track(post)
            BlogPostEditorScreen(post.blogId, post.postId)
        }
        composable<Route.PodcastEpisodes> {
            val episodes =
                it.toRoute<Route.PodcastEpisodes>();
            Track(episodes);
            PodcastEpisodesScreen(episodes.podcastId)
        }
        composable<Route.PodcastEpisode> {
            val episode = it.toRoute<Route.PodcastEpisode>()
            Track(episode)
            PodcastEpisodeEditorScreen(episode.podcastId, episode.episodeId)
        }
        composable<Route.Entity> {
            val entity = it.toRoute<Route.Entity>()
            Track(entity)
            LaunchedEffect(entity) { Router.replace(Router.resolve(entity)) }
            Text(t("app.redirecting"), color = palette.muted)
        }
        composable<Route.NotFound> {
            val notFound = it.toRoute<Route.NotFound>(); Track(notFound); Text(
            notFound.path,
            color = palette.muted
        )
        }
    }
}

/**
 * the current page, navigated through the nav host's controller. the address bar follows the
 * page, and the browser's back and forward follow the address bar.
 */
object Router {

    var route: Route by mutableStateOf(Urls.routeOf(browser.path))
        private set

    private var controller: NavHostController? = null

    private var started = false

    // a navigation that was waiting on a sign-in, resumed once the app is back
    private const val PENDING_ACTION = "pending_action"

    fun attach(controller: NavHostController) {
        if (this.controller === controller) return
        this.controller = controller
// the notes panel follows the page: each page that has notes asks for them again
        controller.addOnDestinationChangedListener { _, _, _ -> resetNotesForNotable() }
    }

    /** the browser's back and forward, once the pages are on screen */
    fun start() {
        if (started || controller == null) return
        started = true
        browser.onPopState { path -> show(Urls.routeOf(path)) }
        resumePendingAction()
    }

    fun track(route: Route) {
        this.route = route
    }

    fun push(route: Route) {
        val nav = controller ?: return
        if (route == this.route) return
        if (Urls.pathOf(route) != browser.path) browser.pushPath(Urls.pathOf(route))
        nav.navigate(route)
    }

    fun replace(route: Route) {
        browser.replacePath(Urls.pathOf(route))
        show(route)
    }

    /** a fresh podcasts list, even from the podcasts page */
    fun podcasts() = push(Route.Podcasts(Clock.System.now().toEpochMilliseconds()))

    /** the page an entity resolves to, for the entity page to replace itself with */
    suspend fun resolve(entity: Route.Entity): Route {
        val context = runCatching {
            Mogul.entityContexts.buildEntityContextFor(
                entity.type,
                entity.id
            )
        }.getOrNull()
        return context?.let { navigationFor(it.resolvedType, it.context) } ?: Route.Home
    }

    // the current entry is replaced, so the back stack doesn't grow with each back and forward
    private fun show(route: Route) {
        val nav = controller ?: return
        val current = nav.currentDestination?.id
        nav.navigate(
            route,
            navOptions { if (current != null) popUpTo(current) { inclusive = true } })
    }

    fun resumePendingAction() {
        val raw = browser.sessionGet(PENDING_ACTION) ?: return
        browser.sessionRemove(PENDING_ACTION)
        val context =
            runCatching { json.decodeFromString<EntityContext>(raw) }.getOrNull() ?: return
        navigationFor(context.resolvedType, context.context)?.let { push(it) }
    }
}
