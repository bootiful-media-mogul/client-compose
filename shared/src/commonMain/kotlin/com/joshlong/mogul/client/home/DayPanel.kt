package com.joshlong.mogul.client.home

import com.joshlong.mogul.client.publications.PublicationsList
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.MogulStatus
import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.publications.PublicationsSection
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FieldLabel
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.dateTimeToString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * a day, in the calendar's card in place of its grid (DayPanel.vue): publishing about the day,
 * then everything that went out, or is due to, on it, a block per publishable
 */
@Composable
internal fun DayPanel(day: LocalDate, publications: List<Publication>, now: Instant, onUnpublish: (Long) -> Unit) {
    val today = now.localDate()
    val isToday = day == today
    val isPast = day < today
    val entries = entriesByDay(publications)[dayKey(day)] ?: emptyList()

    // a day ahead goes out at this time on it; today goes out now
    var time by remember(day) { mutableStateOf("09:00") }
    val scheduled: Instant? = if (isToday) null else parseTime(time)?.let { day.atTime(it).toInstant(zone) }

    // what a publication about a day hangs off: that day's status, made if it's not there yet.
    // a past day has nothing more to publish, so there's no reason to make one for it
    var status by remember(day) { mutableStateOf<MogulStatus?>(null) }
    LaunchedEffect(day) {
        status = null
        if (!isPast) status = runCatching { Mogul.mogul.statusForDate(dayKey(day)) }.getOrNull()
    }

    // the composer reads best at about this width
    Column(Modifier.widthIn(max = 960.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        Text(longDay(day), color = palette.ink, fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = TextSize.lg)

        SectionTitle(if (isToday) t("home.day.publish") else t("home.day.schedule"))
        if (isPast) {
            Muted(t("home.day.past"))
        } else {
            if (!isToday) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                    FieldLabel(t("home.day.time"), required = true)
                    MogulTextField(time, { time = it }, Modifier.width(120.dp), placeholder = "09:00")
                }
            }
            status?.let { s ->
                key(s.id) {
                    PublicationsSection(
                        publishableType = "mogulStatus",
                        publishableId = s.id,
                        showExistingPublications = false,
                        scheduled = scheduled,
                    )
                }
            }
        }

        SectionTitle(t("home.day.publications"))
        if (entries.isEmpty()) Muted(t("home.day.nothing"))
        entries.forEachIndexed { i, e ->
            if (i > 0) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            Column(Modifier.fillMaxWidth().padding(top = Gutter.half), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half), verticalArrangement = Arrangement.Center) {
                    Text(
                        typeLabel(e).uppercase(),
                        color = palette.muted,
                        fontFamily = fonts.mono,
                        fontSize = TextSize.xs,
                        letterSpacing = 1.sp,
                    )
                    Text(e.publishable?.title ?: "", color = palette.ink, fontWeight = FontWeight.SemiBold)
                    // a status is this page; episodes and posts have editors to go to
                    val p = e.publishable
                    if (p != null && p.type != "mogulStatus" && p.title != null) {
                        Text(
                            t("home.day.open"),
                            Modifier.clickable { Router.push(Route.Entity(p.type, p.id)) }.pointerHoverIcon(PointerIcon.Hand),
                            color = palette.ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = TextSize.sm,
                        )
                    }
                }
                PublicationsList(e.publications, unpublishable = true, onUnpublish = { onUnpublish(it.id) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = palette.label, fontFamily = fonts.display, fontWeight = FontWeight.Medium, fontSize = TextSize.lg)
}

@Composable
private fun Muted(text: String) {
    Text(text, color = palette.muted, fontStyle = FontStyle.Italic)
}

/** `HH:mm`, as an <input type="time"> gives it, or null if it isn't one */
private fun parseTime(text: String): LocalTime? {
    val parts = text.trim().split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return if (h in 0..23 && m in 0..59) LocalTime(h, m) else null
}

