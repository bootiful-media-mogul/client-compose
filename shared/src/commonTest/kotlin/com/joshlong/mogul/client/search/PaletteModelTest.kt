package com.joshlong.mogul.client.search

import com.joshlong.mogul.client.api.SearchableResult
import com.joshlong.mogul.client.nav.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// the logic half of the Vue client's SearchPalette.spec.ts; the dom half (dialogs, focus,
// axe) has no counterpart in a canvas

class PaletteModelTest {

    private val messages = mapOf(
        "app.menu.home" to "Home",
        "app.menu.podcasts" to "Podcasts",
        "app.menu.blogs" to "Blogs",
        "app.menu.settings" to "Settings",
        "app.menu.jobs" to "Jobs",
        "search.palette.label" to "Search",
        "search.palette.everything" to "Search everything for “{term}”",
        "search.palette.go-to" to "Go to",
        "search.palette.results" to "Results",
        "search.palette.searching" to "Searching…",
        "search.palette.nothing" to "Nothing found for “{term}”",
        "search.palette.types.episode" to "Episode",
        "search.palette.types.post" to "Post",
    )

    private val translate: (String, Map<String, Any?>) -> String = { key, args ->
        args.entries.fold(this.messages[key] ?: key) { message, (name, value) -> message.replace("{$name}", value.toString()) }
    }

    private fun labels(term: String, results: List<SearchableResult> = emptyList()) =
        paletteGroups(term, results, this.translate).flatMap { group -> group.options.map { it.label } }

    @Test
    fun offersEverySectionBeforeAnythingIsTyped() {
        assertEquals(listOf("Home", "Podcasts", "Blogs", "Settings", "Jobs"), labels(""))
    }

    @Test
    fun putsSearchingEverythingFirst() {
        assertEquals(listOf("Search everything for “set”", "Settings"), labels("set"))
        val everything = paletteGroups("set", emptyList(), this.translate).first().options.first()
        assertEquals(Route.Search("set"), everything.destination())
    }

    @Test
    fun walksTheOptionsAndWrapsAtEitherEnd() {
        val count = labels("jobs").size
        assertEquals(2, count)
        assertEquals(1, movePaletteSelection(0, 1, count))
        assertEquals(0, movePaletteSelection(1, 1, count))
        assertEquals(1, movePaletteSelection(0, -1, count))
        assertEquals(0, movePaletteSelection(0, 1, 0))
    }

    @Test
    fun choosingASectionGoesThere() {
        val jobs = paletteGroups("jobs", emptyList(), this.translate).flatMap { it.options }.last()
        assertEquals(Route.Jobs, jobs.destination())
    }

    @Test
    fun showsLiveResultsAndOpensWhatTheyFound() {
        val result = SearchableResult(searchableId = 7, aggregateId = 42, title = "Spring Boot 4", type = "episode", context = """{"id": 42, "type": "episode"}""")
        val options = paletteGroups("spring", listOf(result), this.translate).flatMap { it.options }
        val found = options.single { it.label == "Spring Boot 4" }
        assertEquals("Episode", found.hint)
        assertEquals(Route.Entity("episode", 42), found.destination())
    }

    @Test
    fun aResultWithOnlyIdsGoesStraightToItsPage() {
        val result = SearchableResult(searchableId = 1, aggregateId = 2, title = "Post", type = "post", context = """{"blogId": 1, "postId": 2}""")
        assertEquals(Route.BlogPost(1, 2), destinationFor(result))
    }

    @Test
    fun showsNoMoreThanTheLimitOfResults() {
        val results = (1..20).map { SearchableResult(searchableId = it.toLong(), title = "r$it", type = "post", context = "{}") }
        val group = paletteGroups("rr", results, this.translate).single { it.key == "results" }
        assertEquals(PALETTE_RESULT_LIMIT, group.options.size)
    }

    @Test
    fun saysWhenItIsSearchingAndWhenNothingWasFound() {
        assertEquals("", paletteStatus("z", searching = false, results = emptyList(), translate = this.translate))
        assertEquals("Searching…", paletteStatus("zzz", searching = true, results = emptyList(), translate = this.translate))
        assertEquals("Nothing found for “zzz”", paletteStatus("zzz", searching = false, results = emptyList(), translate = this.translate))
        val one = listOf(SearchableResult(title = "x", type = "post"))
        assertTrue(paletteStatus("zzz", searching = false, results = one, translate = this.translate).isEmpty())
    }
}
