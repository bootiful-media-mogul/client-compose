package com.joshlong.mogul.client.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.platform.browser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Clock

// the studio's appearance, as theme.ts had it. the choice is per-browser (localStorage, under
// the same key, so it carries over from the Vue client), not per-mogul: the right answer
// depends on the room and the screen you are sitting at, not on who you are.

enum class ThemeMode(val key: String) { Auto("auto"), Light("light"), Dark("dark") }

enum class DarkPalette(val key: String, val applied: AppliedTheme) {
    Midnight("midnight", AppliedTheme.Midnight),
    Graphite("graphite", AppliedTheme.Graphite),
    Ember("ember", AppliedTheme.Ember),
}

@Serializable
data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.Auto,
    val dark: DarkPalette = DarkPalette.Midnight,
    /**
     * in auto mode, the hours (local, 0-23) between which the studio goes dark even if the OS
     * is still light. evening may be later than morning; the window wraps midnight
     */
    val evening: Int = 19,
    val morning: Int = 7,
    /** in auto mode, also go dark whenever the OS is dark */
    val followSystem: Boolean = true,
)

fun isEvening(hour: Int, evening: Int, morning: Int): Boolean {
    if (evening == morning) return false
    return if (evening < morning) hour in evening until morning else hour >= evening || hour < morning
}

fun resolveTheme(preferences: ThemePreferences, hour: Int, systemDark: Boolean): AppliedTheme {
    val dark = preferences.mode == ThemeMode.Dark ||
        (preferences.mode == ThemeMode.Auto &&
            ((preferences.followSystem && systemDark) || isEvening(hour, preferences.evening, preferences.morning)))
    return if (dark) preferences.dark.applied else AppliedTheme.Light
}

/** reads what theme.ts wrote, falling back field by field, as parsePreferences did */
fun parsePreferences(raw: String?): ThemePreferences {
    val defaults = ThemePreferences()
    if (raw.isNullOrBlank()) return defaults
    val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return defaults
    fun string(name: String) = (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun hour(name: String, fallback: Int) = (o[name] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..23 } ?: fallback
    return ThemePreferences(
        mode = ThemeMode.entries.firstOrNull { it.key == string("mode") } ?: defaults.mode,
        dark = DarkPalette.entries.firstOrNull { it.key == string("dark") } ?: defaults.dark,
        evening = hour("evening", defaults.evening),
        morning = hour("morning", defaults.morning),
        followSystem = (o["followSystem"] as? JsonPrimitive)?.booleanOrNull ?: defaults.followSystem,
    )
}

fun encodePreferences(p: ThemePreferences): String =
    """{"mode":"${p.mode.key}","dark":"${p.dark.key}","evening":${p.evening},"morning":${p.morning},"followSystem":${p.followSystem}}"""

/** the palette showing right now, kept current as the OS, the clock and other tabs change */
object ThemeController {

    private const val STORAGE_KEY = "mogul.theme"

    var preferences by mutableStateOf(parsePreferences(browser.localGet(STORAGE_KEY)))
        private set

    var applied by mutableStateOf(AppliedTheme.Light)
        private set

    private var systemDark = browser.prefersDark()

    private var started = false

    fun update(change: (ThemePreferences) -> ThemePreferences) {
        this.preferences = change(this.preferences)
        browser.localSet(STORAGE_KEY, encodePreferences(this.preferences))
        apply()
    }

    private fun apply() {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        this.applied = resolveTheme(this.preferences, hour, this.systemDark)
    }

    /**
     * applies the theme and keeps it current: when the OS flips, when the clock crosses the
     * evening or morning hour, when a laptop wakes, and when another tab changes the choice
     */
    fun start(scope: CoroutineScope) {
        apply()
        if (this.started) return
        this.started = true
        browser.onPrefersDarkChange { dark ->
            this.systemDark = dark
            apply()
        }
        browser.onVisible { apply() }
        browser.onStorage { key, value ->
            if (key == STORAGE_KEY) {
                this.preferences = parsePreferences(value)
                apply()
            }
        }
        // a minute's resolution is plenty for a dusk switch
        scope.launch {
            while (true) {
                delay(60_000)
                apply()
            }
        }
    }
}
