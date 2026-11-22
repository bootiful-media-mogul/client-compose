package com.joshlong.mogul.client.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.i18n.te
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import kotlinx.datetime.LocalDate
import mogulweb.shared.generated.resources.Res
import mogulweb.shared.generated.resources.pub_download_blog_post_as_markdown
import mogulweb.shared.generated.resources.pub_mock
import mogulweb.shared.generated.resources.pub_publish_to_podbean
import mogulweb.shared.generated.resources.pub_wordpress_og
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Instant

private const val MONTH_LINES = 4

/** a month row; the card is as tall as the tallest month (six rows), whatever it's showing */
private val calendarRow = 112.dp
private val calendarHead = 32.dp

/**
 * the week or month of publications (PublicationsCalendar.vue): a toolbar to move around, and
 * one card holding the grid, or, with a day open, that day in place of the grid
 */
@Composable
internal fun PublicationsCalendar(
    publications: List<Publication>,
    view: CalendarView,
    anchor: LocalDate,
    selected: LocalDate?,
    now: Instant,
    open: Boolean,
    onView: (CalendarView) -> Unit,
    onAnchor: (LocalDate) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onClose: () -> Unit,
    day: @Composable () -> Unit,
) {
    val days = visibleDays(view, anchor)
    val byDay = entriesByDay(publications)
    val today = now.localDate()

    // going somewhere else on the calendar means going back to the grid first
    fun close() {
        if (open) onClose()
    }

    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = Gutter.full),
            horizontalArrangement = Arrangement.spacedBy(Gutter.full),
            verticalArrangement = Arrangement.spacedBy(Gutter.half),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rangeTitle(view, anchor, days),
                    color = palette.ink,
                    fontFamily = fonts.display,
                    fontWeight = FontWeight.Bold,
                    fontSize = TextSize.lg,
                )
                Summary(publications, view, anchor, now)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Gutter.full), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gutter.third)) {
                    CalendarView.entries.forEach { v ->
                        ToggleButton(t("home.calendar.${v.key}"), selected = view == v) {
                            close()
                            // back to today's week when today is in the month being looked at
                            if (v == CalendarView.Week && view == CalendarView.Month &&
                                anchor.month == today.month && anchor.year == today.year
                            ) onAnchor(today)
                            onView(v)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Gutter.third)) {
                    MogulButton("‹", onClick = {
                        close()
                        onAnchor(step(view, anchor, -1))
                    })
                    MogulButton(t("home.calendar.today"), onClick = {
                        close()
                        onAnchor(today)
                    })
                    MogulButton("›", onClick = {
                        close()
                        onAnchor(step(view, anchor, 1))
                    })
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = calendarHead + calendarRow * 6)
                .clip(RoundedCornerShape(Gutter.radius))
                .border(1.dp, palette.rule, RoundedCornerShape(Gutter.radius))
                .background(palette.raised),
        ) {
            if (open) {
                Column(Modifier.fillMaxWidth().padding(Gutter.full)) {
                    Row(Modifier.padding(bottom = Gutter.half)) {
                        MogulButton("‹ " + t("home.calendar.back.${view.key}"), onClick = onClose)
                    }
                    day()
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val narrow = maxWidth < 600.dp
                    if (view == CalendarView.Week) {
                        Week(days, byDay, today, selected, now, narrow, onSelect)
                    } else {
                        Month(days, byDay, anchor, today, selected, now, narrow, onSelect)
                    }
                }
            }
        }
    }
}

/** a line about the range: how much went out, how much is still to go */
@Composable
private fun Summary(publications: List<Publication>, view: CalendarView, anchor: LocalDate, now: Instant) {
    val (from, to) = visibleRange(view, anchor)
    val statuses = publications.filter { whenOf(it) >= from && whenOf(it) < to }.map { statusOf(it, now) }
    fun n(vararg ss: PublicationStatus) = statuses.count { it in ss }
    val parts = mutableListOf<Pair<String, Boolean>>()
    if (n(PublicationStatus.Published, PublicationStatus.Partial) > 0)
        parts += t("home.calendar.summary.published", "n" to n(PublicationStatus.Published, PublicationStatus.Partial)) to false
    if (n(PublicationStatus.Scheduled, PublicationStatus.Publishing) > 0)
        parts += t("home.calendar.summary.pending", "n" to n(PublicationStatus.Scheduled, PublicationStatus.Publishing)) to false
    if (n(PublicationStatus.Failed, PublicationStatus.Partial) > 0)
        parts += t("home.calendar.summary.failed", "n" to n(PublicationStatus.Failed, PublicationStatus.Partial)) to true
    if (parts.isEmpty()) parts += t("home.calendar.summary.none") to false
    Row {
        parts.forEachIndexed { i, (text, failed) ->
            if (i > 0) Text(" · ", color = palette.muted, fontSize = TextSize.sm)
            Text(text, color = if (failed) palette.danger else palette.muted, fontSize = TextSize.sm)
        }
    }
}

/** week and month: two chrome buttons, the unselected one hollow */
@Composable
private fun ToggleButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        MogulButton(text, onClick = onClick)
    } else {
        Text(
            text,
            modifier = Modifier
                .clip(RoundedCornerShape(Gutter.radius))
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = palette.ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = TextSize.control,
        )
    }
}

@Composable
private fun DayName(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = palette.muted,
        fontFamily = fonts.mono,
        fontWeight = FontWeight.Medium,
        fontSize = TextSize.xs,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun DayNumber(d: LocalDate, today: LocalDate, size: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    val isToday = d == today
    Text(
        "${d.day}",
        modifier = Modifier
            .clip(RoundedCornerShape(Gutter.radius))
            .background(if (isToday) palette.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = if (isToday) palette.accentInk else palette.ink,
        fontFamily = fonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size,
    )
}

/** a day's background: what has passed is shaded */
@Composable
private fun dayBackground(d: LocalDate, today: LocalDate): Color = if (d < today) palette.hoverTint else Color.Transparent

/** a dashed border for what hasn't gone out yet, drawn as a placeholder is */
private fun Modifier.entryBorder(color: Color, dashed: Boolean, radius: Dp, width: Dp = 1.dp): Modifier =
    this.drawBehind {
        val stroke = Stroke(
            width = width.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
        )
        drawRoundRect(color = color, cornerRadius = CornerRadius(radius.toPx()), style = stroke)
    }

// a week: a column a day, a card a publishable
@Composable
private fun Week(
    days: List<LocalDate>,
    byDay: Map<String, List<CalendarEntry>>,
    today: LocalDate,
    selected: LocalDate?,
    now: Instant,
    narrow: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    @Composable
    fun WeekDay(d: LocalDate, modifier: Modifier) {
        val entries = byDay[dayKey(d)] ?: emptyList()
        val highlighted = selected == d
        Column(
            modifier
                .background(dayBackground(d, today))
                .then(if (highlighted) Modifier.border(2.dp, palette.fieldBorder) else Modifier)
                .clickable { onSelect(d) }
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(Gutter.half),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                DayName(weekdayShort(d.dayOfWeek))
                DayNumber(d, today, TextSize.lg) { onSelect(d) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            Column(Modifier.fillMaxWidth().padding(Gutter.half), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
                if (entries.isEmpty()) {
                    Text(
                        if (d < today) t("home.calendar.nothing-went-out") else t("home.calendar.nothing-scheduled"),
                        modifier = Modifier.fillMaxWidth().padding(top = Gutter.full),
                        color = palette.muted,
                        fontSize = TextSize.xs,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                }
                entries.forEach { e -> WeekEntry(e, now) { onSelect(d) } }
            }
        }
    }

    if (narrow) {
        Column(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
                WeekDay(d, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = calendarHead + calendarRow * 6)) {
            days.forEachIndexed { i, d ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(palette.rule))
                WeekDay(d, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** one publishable on one day */
@Composable
private fun WeekEntry(e: CalendarEntry, now: Instant, onClick: () -> Unit) {
    val flags = entryFlags(e, now)
    val border = when {
        flags.failed -> palette.danger
        flags.upcoming -> palette.fieldBorder
        else -> palette.rule
    }
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (flags.withdrawn) 0.5f else 1f)
            .clip(RoundedCornerShape(Gutter.radius))
            .background(if (flags.upcoming) Color.Transparent else palette.raised)
            .entryBorder(border, flags.upcoming, Gutter.radius)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(Gutter.half),
        verticalArrangement = Arrangement.spacedBy(Gutter.third),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.third)) {
            typeIcon(e)?.let { Icon(it, null, size = 16.dp, tint = palette.muted) }
            Text(typeLabel(e), Modifier.weight(1f), color = palette.muted, fontSize = TextSize.xs, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time(e.`when`), color = palette.muted, fontSize = TextSize.xs, fontFamily = fonts.mono, maxLines = 1)
        }
        Text(
            titleOf(e),
            color = palette.ink,
            fontSize = TextSize.sm,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (flags.withdrawn) TextDecoration.LineThrough else null,
        )
        FlowRow(
            Modifier.padding(top = Gutter.third),
            horizontalArrangement = Arrangement.spacedBy(Gutter.third),
            verticalArrangement = Arrangement.spacedBy(Gutter.third),
        ) {
            e.publications.forEach { p -> PluginBadge(p, statusOf(p, now)) }
        }
    }
}

/** a plugin's tile, as on the publications list, with what happened to it pinned to its corner */
@Composable
internal fun PluginBadge(p: Publication, status: PublicationStatus) {
    Box {
        PluginTile(
            p.plugin,
            Modifier.alpha(
                when (status) {
                    PublicationStatus.Scheduled -> 0.45f
                    PublicationStatus.Withdrawn -> 0.3f
                    else -> 1f
                },
            ),
        )
        val mark = when {
            hasFailure(status) -> "!"
            status == PublicationStatus.Publishing -> "🕒"
            else -> null
        }
        if (mark != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(6.dp, (-6).dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (hasFailure(status)) palette.danger else palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Text(mark, color = if (hasFailure(status)) Color.White else palette.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
            }
        }
    }
}

/** every plugin's artwork, by plugin name (plugins.ts); a plugin missing here gets its initials */
private sealed interface Artwork
private data class SvgArtwork(val icon: MogulIcon) : Artwork
private data class ImageArtwork(val resource: DrawableResource) : Artwork

private fun artworkFor(plugin: String): Artwork? = when (plugin) {
    "mogulAyrshare", "podcastEpisodeAyrshare", "blogPostAyrshare" -> SvgArtwork(MogulIcon.Ayrshare)
    "podbean" -> ImageArtwork(Res.drawable.pub_publish_to_podbean)
    "mockPodcastEpisode" -> ImageArtwork(Res.drawable.pub_mock)
    "wordpress" -> ImageArtwork(Res.drawable.pub_wordpress_og)
    "blogPostMarkdownFile" -> ImageArtwork(Res.drawable.pub_download_blog_post_as_markdown)
    "audioFile" -> SvgArtwork(MogulIcon.AudioFile)
    "podcastEpisodeToBlogPost" -> SvgArtwork(MogulIcon.Rss)
    else -> null
}

@Composable
internal fun PluginTile(plugin: String, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val artwork = artworkFor(plugin)
    // the plugins' artwork is drawn for the top bar's near-black, as in the publish toolbar
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Gutter.radius * 0.6f))
            .background(if (artwork != null) palette.topbar else palette.chrome),
        contentAlignment = Alignment.Center,
    ) {
        when (artwork) {
            is SvgArtwork -> Icon(artwork.icon, pluginTitle(plugin), size = size * 0.8f, tint = palette.chromeInk)
            is ImageArtwork -> Image(
                painterResource(artwork.resource),
                pluginTitle(plugin),
                Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
            null -> Text(
                pluginTitle(plugin).take(2),
                color = palette.chromeInk,
                fontFamily = fonts.mono,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
            )
        }
    }
}

// a month: a cell a day, a line a publishable
@Composable
private fun Month(
    days: List<LocalDate>,
    byDay: Map<String, List<CalendarEntry>>,
    anchor: LocalDate,
    today: LocalDate,
    selected: LocalDate?,
    now: Instant,
    narrow: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val rows = days.chunked(7)
    // a five-row month stretches its rows to fill the card a six-row one does
    val rowHeight = calendarRow * 6 / rows.size
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(calendarHead), verticalAlignment = Alignment.CenterVertically) {
            days.take(7).forEach { d -> DayName(weekdayShort(d.dayOfWeek), Modifier.weight(1f).padding(horizontal = Gutter.half)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        rows.forEachIndexed { r, week ->
            if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            Row(Modifier.fillMaxWidth().heightIn(min = rowHeight).height(IntrinsicSize.Min)) {
                week.forEachIndexed { i, d ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(palette.rule))
                    MonthDay(d, byDay[dayKey(d)] ?: emptyList(), d.month != anchor.month, today, selected, now, narrow, onSelect, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun MonthDay(
    d: LocalDate,
    entries: List<CalendarEntry>,
    outside: Boolean,
    today: LocalDate,
    selected: LocalDate?,
    now: Instant,
    narrow: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(dayBackground(d, today))
            .then(if (d == today || selected == d) Modifier.border(2.dp, palette.accent) else Modifier)
            .clickable { onSelect(d) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(Gutter.third)
            .alpha(if (outside) 0.45f else 1f),
        verticalArrangement = Arrangement.spacedBy(if (narrow) 3.dp else 2.dp),
    ) {
        Box(Modifier.padding(bottom = 2.dp)) { DayNumber(d, today, TextSize.sm) { onSelect(d) } }
        entries.take(MONTH_LINES).forEach { e ->
            val flags = entryFlags(e, now)
            if (narrow) {
                // a phone gives each day about 60px, too little for even a time: an entry is a
                // slim bar, its colors still saying upcoming, failed, or withdrawn
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .alpha(if (flags.withdrawn) 0.5f else 1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                flags.failed -> palette.danger
                                flags.upcoming -> Color.Transparent
                                else -> palette.accent
                            },
                        )
                        .entryBorder(if (flags.upcoming) palette.fieldBorder else Color.Transparent, flags.upcoming, 3.dp),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (flags.withdrawn) 0.5f else 1f)
                        .clip(RoundedCornerShape(Gutter.radius * 0.6f))
                        .background(if (flags.upcoming) Color.Transparent else palette.raised)
                        .entryBorder(
                            when {
                                flags.failed -> palette.danger
                                flags.upcoming -> palette.fieldBorder
                                else -> Color.Transparent
                            },
                            flags.upcoming,
                            Gutter.radius * 0.6f,
                        )
                        .clickable { onSelect(d) }
                        .padding(horizontal = Gutter.third, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gutter.third),
                ) {
                    Text(shortTime(e.`when`), color = palette.muted, fontFamily = fonts.mono, fontSize = TextSize.xs, maxLines = 1)
                    Text(
                        titleOf(e),
                        Modifier.weight(1f),
                        color = palette.ink,
                        fontSize = TextSize.xs,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (flags.withdrawn) TextDecoration.LineThrough else null,
                    )
                    if (flags.failed) Text("!", color = palette.danger, fontWeight = FontWeight.Bold, fontSize = TextSize.xs)
                }
            }
        }
        if (entries.size > MONTH_LINES) {
            Text(
                t("home.calendar.more", "n" to entries.size - MONTH_LINES),
                Modifier.padding(start = Gutter.third),
                color = palette.accent,
                fontSize = TextSize.xs,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun typeIcon(e: CalendarEntry): MogulIcon? = when (e.publishable?.type) {
    "episode" -> MogulIcon.Podcast
    "post" -> MogulIcon.Rss
    "mogulStatus" -> MogulIcon.Edit
    else -> null
}

internal fun typeLabel(e: CalendarEntry): String {
    val type = e.publishable?.type ?: return ""
    val key = "home.calendar.types.$type"
    return if (te(key)) t(key) else type
}

// a status has no title of its own, so it goes by what it said: the first post's preview
private fun titleOf(e: CalendarEntry): String {
    if (e.publishable?.type == "mogulStatus") {
        return e.publications.flatMap { it.outcomes }.firstOrNull { it.preview != null }?.preview
            ?: pluginTitle(e.publications.first().plugin)
    }
    return e.publishable?.title ?: t("home.calendar.untitled")
}

internal fun pluginTitle(plugin: String): String {
    val key = "publications.plugins.$plugin.title"
    return if (te(key)) t(key) else plugin
}
