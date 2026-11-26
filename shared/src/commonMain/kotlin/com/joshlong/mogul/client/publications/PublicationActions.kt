package com.joshlong.mogul.client.publications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.MogulButton
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * the top of the next hour: the schedule's time fields show something sensible straight
 * away, instead of sitting blank until a day is picked
 */
fun topOfNextHour(now: Instant, zone: TimeZone): Instant {
    val later = (now + 1.hours).toLocalDateTime(zone)
    return LocalDateTime(later.year, later.month, later.day, later.hour, 0).toInstant(zone)
}

/**
 * when the schedule picker starts: on the day given (the home page's calendar picked it) at
 * nine in the morning, or, for no day, today or a day already gone, at the top of the next hour
 */
fun initialScheduleTime(now: Instant, zone: TimeZone, day: LocalDate?): Instant {
    val today = now.toLocalDateTime(zone).date
    return if (day == null || day <= today) topOfNextHour(now, zone) else day.atTime(9, 0)
        .toInstant(zone)
}

/** the picker opens by itself for a day still to come: whoever picked that day wants to schedule */
fun opensScheduler(schedulable: Boolean, now: Instant, zone: TimeZone, day: LocalDate?): Boolean =
    schedulable && day != null && day > now.toLocalDateTime(zone).date

/** a schedule can be confirmed only for a time still to come, and only when the panel allows publishing */
fun canSchedule(enabled: Boolean, scheduled: Instant?, now: Instant): Boolean =
    enabled && scheduled != null && scheduled > now

/** the main button's text: whatever the panel calls it, or 'Publish' */
fun mainButtonLabel(label: String?): String = label ?: t("publications.plugins.publish")

/**
 * the buttons beneath every publication panel, wherever it is: publish now, or pick a time
 * and publish then. the panel above decides what the main button says, when it can be pressed
 * and what pressing it does; the server decides whether the plugin can be scheduled; how the
 * buttons look, and the scheduling, are the same for every plugin. scheduleOn is the day the
 * picker starts on. onPublished says the press went through, so the section can close the panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PublicationActions(
    label: String?,
    enabled: Boolean,
    schedulable: Boolean,
    scheduleOn: LocalDate?,
    publish: suspend (Instant?) -> Unit,
    onPublished: () -> Unit,
) {

    val coroutines = rememberCoroutineScope()
    // a press is in flight; the buttons stay down so one click can't publish twice

    var busy by remember { mutableStateOf(false) }
    val zone = TimeZone.currentSystemDefault()
    var scheduling by remember(scheduleOn, schedulable) {
        mutableStateOf(
            opensScheduler(
                schedulable,
                Clock.System.now(),
                zone,
                scheduleOn
            )
        )
    }

    fun run(whenever: Instant?) {

        if (busy) {
            return
        }

        busy = true

        coroutines.launch {
            try {
                publish(whenever)
                if (whenever != null) scheduling = false
                onPublished()
            }//
            catch (e: Exception) {
                // the panel stays open, so the user can try again
                println("publishing failed: ${e.message}")
            }//
            finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = Gutter.half)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Gutter.third),
            verticalArrangement = Arrangement.spacedBy(Gutter.third)
        ) {
            if (schedulable) {
                MogulButton(
                    t("publications.schedule.schedule"),
                    onClick = { scheduling = !scheduling },
                    enabled = enabled && !busy,
                )
            }
            MogulButton(
                mainButtonLabel(label),
                onClick = { run(null) },
                enabled = enabled && !busy && !scheduling,
            )
        }
        if (scheduling && schedulable) {
            val now = remember { Clock.System.now() }
            val initial = remember(scheduleOn) { initialScheduleTime(now, zone, scheduleOn) }
            val initialLocal = initial.toLocalDateTime(zone)
            val todayUtcMillis = remember {
                now.toLocalDateTime(zone).date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            }
            val dateState = key(scheduleOn) {
                rememberDatePickerState(
                    initialSelectedDateMillis = initialLocal.date.atStartOfDayIn(TimeZone.UTC)
                        .toEpochMilliseconds(),
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) =
                            utcTimeMillis >= todayUtcMillis - 86_400_000
                    },
                )
            }
            val timeState = key(scheduleOn) {
                rememberTimePickerState(
                    initialHour = initialLocal.hour,
                    initialMinute = initialLocal.minute,
                    is24Hour = true
                )
            }
            val scheduled = dateState.selectedDateMillis?.let { millis ->
                val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                LocalDateTime(
                    date.year,
                    date.month,
                    date.day,
                    timeState.hour,
                    timeState.minute
                ).toInstant(zone)
            }
            Column(
                Modifier
                    .padding(top = Gutter.third)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Gutter.radius))
                    .background(palette.raised)
                    .padding(Gutter.half),
                verticalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                Text(
                    t("publications.schedule.when"),
                    color = palette.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = TextSize.sm
                )
                // inline, not a popover: the publication panel holds it
                DatePicker(
                    state = dateState,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    colors = DatePickerDefaults.colors(containerColor = palette.raised),
                )
                TimeInput(state = timeState)
                Row(horizontalArrangement = Arrangement.spacedBy(Gutter.third)) {
                    MogulButton(
                        t("publications.schedule.confirm"),
                        onClick = { run(scheduled) },
                        enabled = canSchedule(enabled, scheduled, Clock.System.now()) && !busy,
                    )
                    MogulButton(t("publications.schedule.cancel"), onClick = { scheduling = false })
                }
            }
        }
    }
}
