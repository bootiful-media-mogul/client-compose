package com.joshlong.mogul.client.home

import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.api.PublishableReference
import com.joshlong.mogul.client.i18n.t
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// the home page's calendar, as calendar.ts had it: every publication, about anything, on the
// day it went out or is due to. nothing here knows what a plugin does. a publication is a
// plugin, some dates, a state and zero or more outcomes, and that is all the calendar looks at.

internal enum class CalendarView(val key: String) { Week("week"), Month("month") }

/** what a publication is doing, worked out from those generic fields alone */
internal enum class PublicationStatus { Published, Partial, Failed, Publishing, Scheduled, Withdrawn }

internal val zone: TimeZone get() = TimeZone.currentSystemDefault()

internal fun Instant.localDate(): LocalDate = this.toLocalDateTime(zone).date

internal fun Instant.localDateTime(): LocalDateTime = this.toLocalDateTime(zone)

internal fun LocalDate.startInstant(): Instant = this.atStartOfDayIn(zone)

internal fun LocalDate.addDays(n: Int): LocalDate = this.plus(n, DateTimeUnit.DAY)

/** the day as yyyy-mm-dd: what the server calls a mogul status's date */
internal fun dayKey(d: LocalDate): String = d.toString()

/** weeks start on monday */
internal fun startOfWeek(d: LocalDate): LocalDate = d.minus(d.dayOfWeek.ordinal, DateTimeUnit.DAY)

internal fun firstOfMonth(d: LocalDate): LocalDate = LocalDate(d.year, d.month, 1)

/**
 * the moment a publication belongs to: when it went out, else when it's due, else when it
 * was asked for (as soon as possible means right then). the server picks the same one when
 * it decides what's in a range.
 */
internal fun whenOf(p: Publication): Instant = p.published ?: p.scheduled ?: p.created ?: Instant.fromEpochMilliseconds(0)

internal fun statusOf(p: Publication, now: Instant): PublicationStatus {
    if (p.state == "unpublished") return PublicationStatus.Withdrawn
    if (p.state != "published") {
        val scheduled = p.scheduled
        return if (scheduled != null && scheduled > now) PublicationStatus.Scheduled else PublicationStatus.Publishing
    }
    val failed = p.outcomes.count { !it.success }
    if (failed == 0) return PublicationStatus.Published
    return if (failed == p.outcomes.size) PublicationStatus.Failed else PublicationStatus.Partial
}

internal fun hasFailure(s: PublicationStatus) = s == PublicationStatus.Failed || s == PublicationStatus.Partial

/** one publishable on one day: an episode and everything it went out to that day is one card */
internal data class CalendarEntry(
    val key: String,
    val publishable: PublishableReference?,
    val publications: List<Publication>,
    val `when`: Instant,
)

/** a day's entries, by dayKey, each day's in the order they go out */
internal fun entriesByDay(publications: List<Publication>): Map<String, List<CalendarEntry>> {
    val groups = linkedMapOf<String, MutableList<Publication>>()
    for (p in publications) {
        val who = p.publishable?.let { "${it.type}:${it.id}" } ?: "publication:${p.id}"
        groups.getOrPut("$who@${dayKey(whenOf(p).localDate())}") { mutableListOf() }.add(p)
    }
    val days = linkedMapOf<String, MutableList<CalendarEntry>>()
    for ((key, pubs) in groups) {
        val sorted = pubs.sortedBy { whenOf(it) }
        val first = whenOf(sorted.first())
        days.getOrPut(dayKey(first.localDate())) { mutableListOf() }
            .add(CalendarEntry(key, sorted.first().publishable, sorted, first))
    }
    return days.mapValues { (_, list) -> list.sortedBy { it.`when` } }
}

internal data class EntryFlags(val upcoming: Boolean, val failed: Boolean, val withdrawn: Boolean)

/** upcoming if nothing on it has gone out yet, failed if anything on it failed */
internal fun entryFlags(e: CalendarEntry, now: Instant): EntryFlags {
    val statuses = e.publications.map { statusOf(it, now) }
    return EntryFlags(
        upcoming = statuses.all { it == PublicationStatus.Scheduled || it == PublicationStatus.Publishing },
        failed = statuses.any(::hasFailure),
        withdrawn = statuses.all { it == PublicationStatus.Withdrawn },
    )
}

/** the days a view shows: a week, or a month padded out to whole weeks */
internal fun visibleDays(view: CalendarView, anchor: LocalDate): List<LocalDate> {
    if (view == CalendarView.Week) {
        val start = startOfWeek(anchor)
        return (0 until 7).map { start.addDays(it) }
    }
    val first = firstOfMonth(anchor)
    val start = startOfWeek(first)
    val end = startOfWeek(first.plus(1, DateTimeUnit.MONTH).addDays(6))
    val days = mutableListOf<LocalDate>()
    var d = start
    while (d < end) {
        days.add(d)
        d = d.addDays(1)
    }
    return days
}

/** the range to ask the server for: [first visible day, the day after the last) */
internal fun visibleRange(view: CalendarView, anchor: LocalDate): Pair<Instant, Instant> {
    val days = visibleDays(view, anchor)
    return days.first().startInstant() to days.last().addDays(1).startInstant()
}

/** a week or a month on from anchor, either way */
internal fun step(view: CalendarView, anchor: LocalDate, direction: Int): LocalDate =
    if (view == CalendarView.Week) anchor.addDays(7 * direction)
    else firstOfMonth(anchor).plus(direction, DateTimeUnit.MONTH)

// dates in words, in the studio's language. a language that writes its months and weekdays
// small still starts a heading with a capital, hence capitalized

private fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

private fun month(d: LocalDate): String = t("dates.months.${d.month.ordinal + 1}")

internal fun monthName(d: LocalDate): String = month(d).capitalized()

internal fun weekdayShort(d: DayOfWeek): String = t("dates.weekdays.${d.ordinal + 1}").take(3).capitalized()

/** e.g. `Monday, November 22` */
internal fun longDay(d: LocalDate): String =
    t("dates.long-day", "weekday" to t("dates.weekdays.${d.dayOfWeek.ordinal + 1}"), "month" to month(d), "day" to d.day).capitalized()

/** e.g. `9:05 AM` */
internal fun time(i: Instant): String {
    val t = i.localDateTime()
    val hour = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$hour:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "AM" else "PM"}"
}

/** e.g. `9am`, `9:05am` */
internal fun shortTime(i: Instant): String = time(i).replace(":00", "").replace(" ", "").lowercase()

/** e.g. `November 2026`, or `November 16 - 22, 2026` for a week */
internal fun rangeTitle(view: CalendarView, anchor: LocalDate, days: List<LocalDate>): String {
    if (view == CalendarView.Month) return "${monthName(anchor)} ${anchor.year}"
    val s = days.first()
    val e = days.last()
    val end = if (s.month == e.month) "${e.day}" else "${monthName(e)} ${e.day}"
    return "${monthName(s)} ${s.day} - $end, ${e.year}"
}
