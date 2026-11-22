package com.joshlong.mogul.client.search

import com.joshlong.mogul.client.api.SearchableResult
import com.joshlong.mogul.client.api.parseStringContext
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.navigationFor
import com.joshlong.mogul.client.ui.MogulIcon
import kotlin.time.Clock

// what the palette offers for a term, kept apart from the drawing so it can be tested.
// in order: searching everything for what you typed (first, so typing and pressing enter
// does what the old search box did), the sections whose names match, then live results.

/** live results wait for a pause in the typing */
internal const val PALETTE_DEBOUNCE_MILLIS = 200L

internal const val PALETTE_RESULT_LIMIT = 8

internal const val PALETTE_MINIMUM_TERM_LENGTH = 2

internal class PaletteOption(
    val key: String,
    val label: String,
    val hint: String?,
    val icon: MogulIcon,
    val destination: () -> Route,
)

internal class PaletteGroup(val key: String, val label: String, val options: List<PaletteOption>)

/** the studio's sections, in the order the top bar and the account menu show them */
internal class PaletteSection(val key: String, val label: String, val icon: MogulIcon, val destination: () -> Route)

internal val paletteSections = listOf(
    PaletteSection("home", "app.menu.home", MogulIcon.Home) { Route.Home },
    // a fresh podcasts list each time, as the top bar's link does
    PaletteSection("podcasts", "app.menu.podcasts", MogulIcon.Podcast) { Route.Podcasts(Clock.System.now().toEpochMilliseconds()) },
    PaletteSection("blogs", "app.menu.blogs", MogulIcon.Rss) { Route.Blogs },
    PaletteSection("settings", "app.menu.settings", MogulIcon.Settings) { Route.Settings },
    PaletteSection("jobs", "app.menu.jobs", MogulIcon.Jobs) { Route.Jobs },
)

internal fun iconForResultType(type: String): MogulIcon = when (type) {
    "episode", "segment" -> MogulIcon.Podcast
    "post" -> MogulIcon.Rss
    else -> MogulIcon.Notes
}

/**
 * where a result goes: the entity page, which asks the api what it resolves to. the context
 * names the entity (`{"type": "episode", "id": 42}`); older results that only carry ids go
 * straight to the page those ids name
 */
internal fun destinationFor(result: SearchableResult): Route {
    val context = parseStringContext(result.context)
    val type = context["type"]
    val id = context["id"]?.toDoubleOrNull()?.toLong()
    if (type != null && id != null) return Route.Entity(type, id)
    return navigationFor(result.type, result.contextMap) ?: Route.Entity(result.type, result.aggregateId)
}

internal fun paletteGroups(
    term: String,
    results: List<SearchableResult>,
    translate: (String, Map<String, Any?>) -> String = { key, args -> t(key, args) },
): List<PaletteGroup> {
    val q = term.trim()
    val lower = q.lowercase()
    val groups = mutableListOf<PaletteGroup>()
    if (q.isNotEmpty()) {
        groups += PaletteGroup(
            "everything",
            translate("search.palette.label", emptyMap()),
            listOf(
                PaletteOption("everything", translate("search.palette.everything", mapOf("term" to q)), null, MogulIcon.Search) {
                    Route.Search(q)
                },
            ),
        )
    }
    val matching = paletteSections.filter { q.isEmpty() || translate(it.label, emptyMap()).lowercase().contains(lower) }
    if (matching.isNotEmpty()) {
        groups += PaletteGroup(
            "go-to",
            translate("search.palette.go-to", emptyMap()),
            matching.map { PaletteOption("section-${it.key}", translate(it.label, emptyMap()), null, it.icon, it.destination) },
        )
    }
    if (q.isNotEmpty() && results.isNotEmpty()) {
        groups += PaletteGroup(
            "results",
            translate("search.palette.results", emptyMap()),
            results.take(PALETTE_RESULT_LIMIT).map { r ->
                PaletteOption(
                    "result-${r.searchableId}",
                    r.title ?: "",
                    translate("search.palette.types.${r.type}", emptyMap()),
                    iconForResultType(r.type),
                ) { destinationFor(r) }
            },
        )
    }
    return groups
}

/** the next active option as the arrows walk the list, wrapping at either end */
internal fun movePaletteSelection(active: Int, delta: Int, count: Int): Int =
    if (count == 0) 0 else ((active + delta) % count + count) % count

/** what the palette's foot says about the search */
internal fun paletteStatus(
    term: String,
    searching: Boolean,
    results: List<SearchableResult>,
    translate: (String, Map<String, Any?>) -> String = { key, args -> t(key, args) },
): String {
    val q = term.trim()
    if (q.length < PALETTE_MINIMUM_TERM_LENGTH) return ""
    if (searching && results.isEmpty()) return translate("search.palette.searching", emptyMap())
    if (!searching && results.isEmpty()) return translate("search.palette.nothing", mapOf("term" to q))
    return ""
}
