package com.joshlong.mogul.client

import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.api.parseNumberContext
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.i18n.tc
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Urls
import com.joshlong.mogul.client.nav.navigationFor
import com.joshlong.mogul.client.theme.AppliedTheme
import com.joshlong.mogul.client.theme.DarkPalette
import com.joshlong.mogul.client.theme.ThemeMode
import com.joshlong.mogul.client.theme.ThemePreferences
import com.joshlong.mogul.client.theme.encodePreferences
import com.joshlong.mogul.client.theme.isEvening
import com.joshlong.mogul.client.theme.parsePreferences
import com.joshlong.mogul.client.theme.resolveTheme
import com.joshlong.mogul.client.ui.durationToString
import com.joshlong.mogul.client.ui.prettyPrintInBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RouteTest {

    @Test
    fun parsesEveryPage() {
        assertEquals(Route.Home, Urls.routeOf("/"))
        assertEquals(Route.Jobs, Urls.routeOf("/jobs"))
        assertEquals(Route.Podcasts(123), Urls.routeOf("/podcasts?when=123"))
        assertEquals(Route.PodcastEpisodes(3), Urls.routeOf("/podcasts/3/episodes"))
        assertEquals(Route.PodcastEpisode(3, 9), Urls.routeOf("/podcasts/3/episodes/9/"))
        assertEquals(Route.BlogPosts(1), Urls.routeOf("/blogs/1/posts"))
        assertEquals(Route.BlogPost(1, 2), Urls.routeOf("/blogs/1/posts/2"))
        assertEquals(Route.Entity("episode", 7), Urls.routeOf("/entities/episode/7"))
        assertEquals(Route.Search("spring boot"), Urls.routeOf("/search?query=spring%20boot"))
        assertTrue(Urls.routeOf("/nope/x") is Route.NotFound)
    }

    @Test
    fun pathsRoundTrip() {
        listOf(
            Route.Home, Route.Blogs, Route.Settings, Route.About, Route.PodcastEpisode(1, 2),
            Route.BlogPost(4, 5), Route.Search("a&b ü"),
        ).forEach { assertEquals(it, Urls.routeOf(Urls.pathOf(it))) }
    }

    @Test
    fun searchResultsNavigate() {
        assertEquals(Route.PodcastEpisode(1, 2), navigationFor("segment", mapOf("podcastId" to 1L, "episodeId" to 2L)))
        assertEquals(Route.BlogPost(1, 2), navigationFor("post", mapOf("blogId" to 1L, "postId" to 2L)))
        assertEquals(null, navigationFor("unknown", emptyMap()))
    }
}

class I18nTest {

    @Test
    fun interpolatesNamedArguments() {
        assertEquals("Home", t("app.menu.home"))
        assertTrue(t("confirm.deletion", "title" to "My Podcast").contains("My Podcast"))
        assertEquals("no.such.key", t("no.such.key"))
    }

    @Test
    fun choosesPlurals() {
        val key = "publications.plugins.blogPostAyrshare.composer.attachments"
        assertEquals("1 attachment", tc(key, 1))
        assertEquals("3 attachments", tc(key, 3))
    }
}

class ThemeTest {

    @Test
    fun eveningWrapsMidnight() {
        assertTrue(isEvening(22, 19, 7))
        assertTrue(isEvening(3, 19, 7))
        assertFalse(isEvening(12, 19, 7))
        assertFalse(isEvening(12, 7, 7))
    }

    @Test
    fun resolves() {
        val auto = ThemePreferences(followSystem = false)
        assertEquals(AppliedTheme.Light, resolveTheme(auto, 12, systemDark = true))
        assertEquals(AppliedTheme.Midnight, resolveTheme(auto, 21, systemDark = false))
        assertEquals(AppliedTheme.Ember, resolveTheme(ThemePreferences(mode = ThemeMode.Dark, dark = DarkPalette.Ember), 12, false))
        assertEquals(AppliedTheme.Light, resolveTheme(ThemePreferences(mode = ThemeMode.Light), 23, true))
    }

    @Test
    fun readsWhatTheVueClientWrote() {
        val p = parsePreferences("""{"mode":"dark","dark":"graphite","evening":20,"morning":6,"followSystem":false}""")
        assertEquals(ThemePreferences(ThemeMode.Dark, DarkPalette.Graphite, 20, 6, false), p)
        assertEquals(p, parsePreferences(encodePreferences(p)))
        assertEquals(ThemePreferences(), parsePreferences("not json"))
        assertEquals(19, parsePreferences("""{"evening":99}""").evening)
    }
}

class ApiTest {

    @Test
    fun decodesBothDateTimeForms() {
        val iso = json.decodeFromString<Publication>("""{"id":1,"plugin":"p","created":"2026-11-20T10:15:30.123-08:00","state":"pending"}""")
        assertEquals(Instant.parse("2026-11-20T18:15:30.123Z"), iso.created)
        assertTrue(iso.publishing)
        val millis = json.decodeFromString<Publication>("""{"id":"2","plugin":"p","created":1700000000000}""")
        assertEquals(Instant.fromEpochMilliseconds(1700000000000), millis.created)
        assertEquals(2, millis.id)
    }

    @Test
    fun parsesContexts() {
        assertEquals(mapOf("podcastId" to 1L, "episodeId" to 2L), parseNumberContext("""{"podcastId":1,"episodeId":"2","name":"x"}"""))
        assertEquals(emptyMap(), parseNumberContext(null))
    }

    @Test
    fun formats() {
        assertEquals("01:01:01", durationToString(3661_000.0))
        assertEquals(null, durationToString(-1.0))
        assertEquals("1.50 KB", prettyPrintInBytes(1536.0))
        assertEquals("12.00 bytes", prettyPrintInBytes(12.0))
    }
}
