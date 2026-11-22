package com.joshlong.mogul.client.podcasts

import com.joshlong.mogul.client.ui.FormSectionTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.joshlong.mogul.client.compositions.CompositionPanel
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.InputTool
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// the bits the podcast pages share

/** a page's heading, as the views' h1 */
@Composable
internal fun PageTitle(text: String) = com.joshlong.mogul.client.ui.PageTitle(text)

/** a heading inside a fieldset, as .form-section-title */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) = FormSectionTitle(text, modifier)

/** a coroutine whose failure is reported rather than taking the app down with it */
internal fun CoroutineScope.launchSafely(block: suspend CoroutineScope.() -> Unit) {
    launch {
        try {
            block()
        } catch (e: Exception) {
            println("podcasts: ${e.message}")
        }
    }
}

/** an id from a notification's context (a string, maybe written as a double) matches */
internal fun sameId(value: String?, id: Long): Boolean = value?.toDoubleOrNull()?.toLong() == id

/**
 * a date and time, in the browser's zone. clicking the field opens the picker (a date, then
 * the time under it); it never opens on hover or focus
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(label: String, value: Instant?, onValueChange: (Instant) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val zone = TimeZone.currentSystemDefault()
    val local = value?.toLocalDateTime(zone)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = true }) {
            // the field itself is read only: the picker is how it changes
            MogulTextField(
                value = local?.let { formatLocal(it) } ?: "",
                onValueChange = {},
                label = label,
                enabled = false,
            )
        }
    }
    if (open) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = local?.date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        )
        val timeState = rememberTimePickerState(
            initialHour = local?.hour ?: 0,
            initialMinute = local?.minute ?: 0,
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    val millis = dateState.selectedDateMillis ?: return@TextButton
                    val date: LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    val chosen = LocalDateTime(date, LocalTime(timeState.hour, timeState.minute, local?.second ?: 0))
                    onValueChange(chosen.toInstant(zone))
                }) { Text(t("labels.ok")) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(t("labels.cancel")) } },
        ) {
            DatePicker(state = dateState)
            Row(Modifier.fillMaxWidth().padding(Gutter.full), horizontalArrangement = Arrangement.Center) {
                TimeInput(state = timeState)
            }
        }
    }
}

private fun two(n: Int) = n.toString().padStart(2, '0')

private fun formatLocal(t: LocalDateTime) =
    "${two(t.month.ordinal + 1)}/${two(t.day)}/${t.year} ${two(t.hour)}:${two(t.minute)}"
