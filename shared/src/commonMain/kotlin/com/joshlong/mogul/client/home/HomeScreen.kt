package com.joshlong.mogul.client.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.ListenForNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Clock.System.now()) }
    var view by remember { mutableStateOf(CalendarView.Week) }
    var anchor by remember { mutableStateOf(now.localDate()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var dayOpen by remember { mutableStateOf(false) }

    // every publication in the days the calendar is showing, about anything at all. the day
    // panel reads the picked day's from here too: a day can only be picked from what's shown
    var shown by remember { mutableStateOf<List<Publication>>(emptyList()) }

    // a later range asked for while an earlier one was loading wins, whichever answers first
    var generation by remember { mutableStateOf(0) }

    suspend fun refresh() {
        val mine = ++generation
        now = Clock.System.now()
        val (from, to) = visibleRange(view, anchor)
        val found = runCatching { Mogul.publications.between(from, to) }.getOrElse {
            println("could not load the publications: ${it.message}")
            return
        }
        if (mine == generation) shown = found
    }

    LaunchedEffect(view, anchor) { refresh() }

    // the line between what has gone out and what's still to go moves with the clock
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Clock.System.now()
        }
    }

    // started, so a new row (a scheduled one especially, which won't complete for days) shows
    // up as soon as it exists; completed, so its outcome does
    ListenForNotification("publication-started-event") { scope.launch { refresh() } }
    ListenForNotification("publication-completed-event") { scope.launch { refresh() } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        Text(t("home.title"), style = MaterialTheme.typography.headlineMedium, color = palette.ink)
        FormSection(t("home.publications")) {
            PublicationsCalendar(
                publications = shown,
                view = view,
                anchor = anchor,
                selected = selected,
                now = now,
                open = dayOpen,
                onView = { view = it },
                onAnchor = { anchor = it },
                onSelect = {
                    selected = it
                    dayOpen = true
                },
                onClose = { dayOpen = false },
            ) {
                selected?.let { day ->
                    DayPanel(
                        day = day,
                        publications = shown,
                        now = now,
                        onUnpublish = { publicationId ->
                            scope.launch {
                                runCatching { Mogul.publications.unpublish(publicationId) }
                                refresh()
                            }
                        },
                    )
                }
            }
        }
    }
}
